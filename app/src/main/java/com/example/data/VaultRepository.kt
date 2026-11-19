package com.example.data

import android.os.Build
import com.example.crypto.Argon2KdfParams
import com.example.crypto.CryptoEngine
import com.example.crypto.DeviceKeyManager
import com.example.crypto.WrappedVaultKeyPayload
import com.example.data.cloud.CloudVaultDataSource
import com.example.data.cloud.CloudVaultSyncUpdatePayload
import com.example.data.cloud.CloudVaultWritePayload
import com.example.data.local.EncryptedAccountDao
import com.example.data.local.EncryptedAccountEntity
import com.example.data.local.StoredVaultMetadata
import com.example.data.local.VaultPreferencesStore
import com.example.data.model.AppThemeMode
import com.example.data.model.AuthenticatorAccount
import com.example.data.model.AutoLockTimeout
import com.example.data.model.CloudConflictInfo
import com.example.data.model.EncryptedCloudVault
import com.example.data.model.SecuritySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.crypto.AEADBadTagException

sealed interface BackupOperationResult {
    data class Success(val version: Long, val accountCount: Int) : BackupOperationResult
    data class ConflictDetected(val conflict: CloudConflictInfo) : BackupOperationResult
    data class NeedsRecoveryPasswordSetup(val message: String) : BackupOperationResult
    data class Failure(val message: String) : BackupOperationResult
}

sealed interface RestoreOperationResult {
    data class Success(val restoredCount: Int, val version: Long) : RestoreOperationResult
    data class WrongPassword(val message: String) : RestoreOperationResult
    data class NoRemoteBackup(val message: String) : RestoreOperationResult
    data class Failure(val message: String) : RestoreOperationResult
}

/**
 * Coordinates AES-256-GCM encrypted local Room persistence, Android Keystore device key wrapping,
 * Argon2id recovery key wrapping, and zero-knowledge Firebase Cloud Firestore backup & sync.
 */
class VaultRepository(
    private val accountDao: EncryptedAccountDao,
    private val preferencesStore: VaultPreferencesStore,
    private val deviceKeyManager: DeviceKeyManager,
    private val cloudDataSource: CloudVaultDataSource,
    private val deviceNameProvider: () -> String = { defaultDeviceName() }
) {

    private val keyMutex = Mutex()
    @Volatile
    private var cachedVaultKey: ByteArray? = null

    val securitySettingsFlow: Flow<SecuritySettings> = preferencesStore.securitySettingsFlow
    val vaultMetadataFlow: Flow<StoredVaultMetadata> = preferencesStore.vaultMetadataFlow

    /**
     * Reactive decrypted list of active authenticator accounts. Works 100% offline.
     */
    fun observeActiveAccounts(): Flow<List<AuthenticatorAccount>> =
        accountDao.observeActiveEncryptedAccounts().map { entities ->
            if (entities.isEmpty()) return@map emptyList()
            val vaultKey = getOrCreateVaultKey()
            withContext(Dispatchers.Default) {
                entities.mapNotNull { entity ->
                    try {
                        CryptoEngine.decryptLocalAccount(
                            accountId = entity.id,
                            ciphertextBase64 = entity.encryptedPayloadBase64,
                            ivBase64 = entity.ivBase64,
                            vaultKey = vaultKey
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.filterNot { it.deleted }.sortedBy { it.sortOrder }
            }
        }

    suspend fun getAllAccountsIncludingTombstones(): List<AuthenticatorAccount> {
        val entities = accountDao.getAllEncryptedAccountsOnce()
        if (entities.isEmpty()) return emptyList()
        val vaultKey = getOrCreateVaultKey()
        return withContext(Dispatchers.Default) {
            entities.mapNotNull { entity ->
                try {
                    CryptoEngine.decryptLocalAccount(
                        accountId = entity.id,
                        ciphertextBase64 = entity.encryptedPayloadBase64,
                        ivBase64 = entity.ivBase64,
                        vaultKey = vaultKey
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    suspend fun getOrCreateVaultKey(): ByteArray {
        cachedVaultKey?.let { return it }
        return keyMutex.withLock {
            cachedVaultKey?.let { return@withLock it }
            val meta = preferencesStore.getVaultMetadataOnce()
            val key = if (!meta.deviceWrappedVaultKey.isNullOrBlank() &&
                !meta.deviceWrappedVaultKeyIv.isNullOrBlank()
            ) {
                withContext(Dispatchers.Default) {
                    deviceKeyManager.unwrapVaultKeyForDevice(
                        ciphertextBase64 = meta.deviceWrappedVaultKey,
                        ivBase64 = meta.deviceWrappedVaultKeyIv
                    )
                }
            } else {
                val freshKey = CryptoEngine.generateVaultKey()
                val wrapped = withContext(Dispatchers.Default) {
                    deviceKeyManager.wrapVaultKeyForDevice(freshKey)
                }
                val uuid = meta.vaultUuid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                preferencesStore.saveDeviceWrappedVaultKey(
                    ciphertextBase64 = wrapped.ciphertextBase64,
                    ivBase64 = wrapped.ivBase64,
                    vaultUuid = uuid
                )
                freshKey
            }
            cachedVaultKey = key
            key
        }
    }

    suspend fun addAccounts(accounts: List<AuthenticatorAccount>): Int {
        if (accounts.isEmpty()) return 0
        val vaultKey = getOrCreateVaultKey()
        val existing = getAllAccountsIncludingTombstones().filterNot { it.deleted }
        var nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val now = System.currentTimeMillis()

        val entities = withContext(Dispatchers.Default) {
            accounts.map { account ->
                val prepared = account.copy(
                    sortOrder = nextOrder++,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    deleted = false
                )
                encryptAccountEntity(prepared, vaultKey)
            }
        }
        accountDao.upsertAll(entities)
        return entities.size
    }

    suspend fun updateAccount(account: AuthenticatorAccount) {
        val vaultKey = getOrCreateVaultKey()
        val updated = account.copy(updatedAtEpochMs = System.currentTimeMillis())
        val entity = withContext(Dispatchers.Default) {
            encryptAccountEntity(updated, vaultKey)
        }
        accountDao.upsert(entity)
    }

    /**
     * Soft-deletes an account with a tombstone so multi-device sync propagates deletions cleanly.
     */
    suspend fun deleteAccount(account: AuthenticatorAccount) {
        val vaultKey = getOrCreateVaultKey()
        val tombstoned = account.copy(
            deleted = true,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        val entity = withContext(Dispatchers.Default) {
            encryptAccountEntity(tombstoned, vaultKey)
        }
        accountDao.upsert(entity)
    }

    suspend fun reorderAccounts(reorderedActive: List<AuthenticatorAccount>) {
        val vaultKey = getOrCreateVaultKey()
        val now = System.currentTimeMillis()
        val entities = withContext(Dispatchers.Default) {
            reorderedActive.mapIndexed { index, account ->
                val updated = account.copy(sortOrder = index, updatedAtEpochMs = now)
                encryptAccountEntity(updated, vaultKey)
            }
        }
        accountDao.upsertAll(entities)
    }

    // --- App Lock & Security Preferences ---

    suspend fun setAppLockPin(pinOrPassword: CharArray) {
        val (hash, salt) = withContext(Dispatchers.Default) {
            CryptoEngine.hashAppLockSecret(pinOrPassword)
        }
        preferencesStore.saveAppLockPin(hash, salt, enabled = true)
    }

    suspend fun verifyAppLockPin(candidate: CharArray): Boolean {
        val creds = preferencesStore.getAppLockCredentials()
        val hash = creds.pinHashBase64 ?: return false
        val salt = creds.pinSaltBase64 ?: return false
        return withContext(Dispatchers.Default) {
            CryptoEngine.verifyAppLockSecret(candidate, hash, salt)
        }
    }

    suspend fun disableAppLock() = preferencesStore.setAppLockEnabled(false)
    suspend fun setBiometricEnabled(enabled: Boolean) = preferencesStore.setBiometricEnabled(enabled)
    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) = preferencesStore.setAutoLockTimeout(timeout)
    suspend fun setScreenshotProtectionEnabled(enabled: Boolean) =
        preferencesStore.setScreenshotProtectionEnabled(enabled)
    suspend fun setThemeMode(mode: AppThemeMode) = preferencesStore.setThemeMode(mode)
    suspend fun setAutoCloudBackupEnabled(enabled: Boolean) =
        preferencesStore.setAutoCloudBackupEnabled(enabled)

    // --- Zero-Knowledge Cloud Backup, Sync & Recovery ---

    fun observeRemoteCloudVault(userId: String): Flow<EncryptedCloudVault?> =
        cloudDataSource.observePrimaryVault(userId)

    suspend fun fetchRemoteCloudVault(userId: String): Result<EncryptedCloudVault?> =
        cloudDataSource.fetchPrimaryVault(userId)

    /**
     * Configures a strong recovery password by deriving a 256-bit key via Argon2id and wrapping
     * the local 256-bit vault key with AES-256-GCM. Never stores or uploads the recovery password.
     * Checks Firestore first so an existing cloud backup is NEVER silently overwritten!
     */
    suspend fun setupRecoveryPasswordAndBackup(
        userId: String,
        recoveryPassword: CharArray,
        forceReplaceExisting: Boolean = false
    ): BackupOperationResult {
        val vaultKey = getOrCreateVaultKey()
        val meta = preferencesStore.getVaultMetadataOnce()
        val vaultUuid = meta.vaultUuid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        val remoteFetch = cloudDataSource.fetchPrimaryVault(userId)
        val remoteVault = remoteFetch.getOrElse { err ->
            return BackupOperationResult.Failure(
                err.localizedMessage ?: "Unable to verify existing cloud backup. Check your connection."
            )
        }

        if (remoteVault != null && !forceReplaceExisting && remoteVault.vaultUuid != vaultUuid) {
            return BackupOperationResult.ConflictDetected(
                CloudConflictInfo(
                    remoteVault = remoteVault,
                    reason = "An encrypted cloud backup from another device (${remoteVault.deviceName}, ${remoteVault.accountCount} accounts) already exists. Restore it with your existing recovery password, or explicitly choose Replace.",
                    isNewDeviceOrDifferentVault = true
                )
            )
        }

        val wrappedPayload = withContext(Dispatchers.Default) {
            CryptoEngine.wrapVaultKeyWithRecoveryPassword(
                vaultKey = vaultKey,
                recoveryPassword = recoveryPassword
            )
        }
        preferencesStore.saveCloudWrappedKey(wrappedPayload)

        val allAccounts = getAllAccountsIncludingTombstones()
        val activeCount = allAccounts.count { !it.deleted }.toLong()
        val encryptedBlob = withContext(Dispatchers.Default) {
            CryptoEngine.encryptCloudVaultPayload(allAccounts, vaultKey, userId)
        }

        val nextVersion = if (remoteVault != null) remoteVault.vaultVersion + 1L else 1L
        val deviceName = deviceNameProvider()
        val writePayload = CloudVaultWritePayload(
            vaultUuid = vaultUuid,
            vaultVersion = nextVersion,
            accountCount = activeCount,
            deviceName = deviceName,
            kdfAlgorithm = Argon2KdfParams.ALGORITHM_NAME,
            kdfSalt = wrappedPayload.kdfParams.saltBase64,
            kdfMemoryKb = wrappedPayload.kdfParams.memoryKb.toLong(),
            kdfIterations = wrappedPayload.kdfParams.iterations.toLong(),
            kdfParallelism = wrappedPayload.kdfParams.parallelism.toLong(),
            wrappedVaultKey = wrappedPayload.wrappedKeyBase64,
            wrappedVaultKeyIv = wrappedPayload.wrappedKeyIvBase64,
            encryptedVault = encryptedBlob.ciphertextBase64,
            encryptedVaultIv = encryptedBlob.ivBase64
        )

        val writeResult = if (remoteVault == null) {
            cloudDataSource.createPrimaryVault(userId, writePayload)
        } else {
            cloudDataSource.updateKeyRotationOrReplace(userId, writePayload)
        }

        return writeResult.fold(
            onSuccess = {
                preferencesStore.updateSyncProgress(nextVersion, System.currentTimeMillis(), deviceName)
                BackupOperationResult.Success(nextVersion, activeCount.toInt())
            },
            onFailure = { err ->
                BackupOperationResult.Failure(err.localizedMessage ?: "Encrypted backup upload failed.")
            }
        )
    }

    /**
     * Rotates the recovery password for the current vault by re-wrapping the existing 256-bit vault key
     * with a fresh Argon2id salt and updating Firestore.
     */
    suspend fun changeRecoveryPassword(
        userId: String,
        newRecoveryPassword: CharArray
    ): BackupOperationResult {
        return setupRecoveryPasswordAndBackup(
            userId = userId,
            recoveryPassword = newRecoveryPassword,
            forceReplaceExisting = false
        )
    }

    /**
     * Performs an encrypted backup to Firestore. Never silently overwrites a remote vault that has
     * a different vaultUuid or a newer version from another device.
     */
    suspend fun backupNow(
        userId: String,
        forceOverwrite: Boolean = false
    ): BackupOperationResult {
        val meta = preferencesStore.getVaultMetadataOnce()
        val cloudWrapped = meta.cloudWrappedKey
            ?: return BackupOperationResult.NeedsRecoveryPasswordSetup(
                "Create a recovery password first to enable zero-knowledge encrypted cloud backup."
            )

        val vaultKey = getOrCreateVaultKey()
        val vaultUuid = meta.vaultUuid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        val remoteFetch = cloudDataSource.fetchPrimaryVault(userId)
        val remoteVault = remoteFetch.getOrElse { err ->
            return BackupOperationResult.Failure(
                err.localizedMessage ?: "Offline or unable to reach cloud backup."
            )
        }

        if (remoteVault != null && !forceOverwrite) {
            if (remoteVault.vaultUuid != vaultUuid) {
                return BackupOperationResult.ConflictDetected(
                    CloudConflictInfo(
                        remoteVault = remoteVault,
                        reason = "A different encrypted vault exists in the cloud (${remoteVault.deviceName}, ${remoteVault.accountCount} accounts). Restore or Merge instead of overwriting.",
                        isNewDeviceOrDifferentVault = true
                    )
                )
            }
            if (remoteVault.vaultVersion > meta.localVaultVersion) {
                // Same vault key! We can auto-merge safely instead of overwriting!
                val mergeResult = mergeWithSameKeyRemoteVault(userId, remoteVault, vaultKey)
                if (mergeResult != null) {
                    return mergeResult
                }
                return BackupOperationResult.ConflictDetected(
                    CloudConflictInfo(
                        remoteVault = remoteVault,
                        reason = "Your cloud backup was updated by ${remoteVault.deviceName} (v${remoteVault.vaultVersion}). Choose how to resolve.",
                        isNewDeviceOrDifferentVault = false
                    )
                )
            }
        }

        val allAccounts = getAllAccountsIncludingTombstones()
        val activeCount = allAccounts.count { !it.deleted }.toLong()
        val encryptedBlob = withContext(Dispatchers.Default) {
            CryptoEngine.encryptCloudVaultPayload(allAccounts, vaultKey, userId)
        }
        val nextVersion = (remoteVault?.vaultVersion ?: meta.localVaultVersion) + 1L
        val deviceName = deviceNameProvider()

        val writeResult = if (remoteVault == null) {
            cloudDataSource.createPrimaryVault(
                userId = userId,
                payload = CloudVaultWritePayload(
                    vaultUuid = vaultUuid,
                    vaultVersion = nextVersion,
                    accountCount = activeCount,
                    deviceName = deviceName,
                    kdfAlgorithm = Argon2KdfParams.ALGORITHM_NAME,
                    kdfSalt = cloudWrapped.kdfParams.saltBase64,
                    kdfMemoryKb = cloudWrapped.kdfParams.memoryKb.toLong(),
                    kdfIterations = cloudWrapped.kdfParams.iterations.toLong(),
                    kdfParallelism = cloudWrapped.kdfParams.parallelism.toLong(),
                    wrappedVaultKey = cloudWrapped.wrappedKeyBase64,
                    wrappedVaultKeyIv = cloudWrapped.wrappedKeyIvBase64,
                    encryptedVault = encryptedBlob.ciphertextBase64,
                    encryptedVaultIv = encryptedBlob.ivBase64
                )
            )
        } else if (forceOverwrite && remoteVault.vaultUuid != vaultUuid) {
            cloudDataSource.updateKeyRotationOrReplace(
                userId = userId,
                payload = CloudVaultWritePayload(
                    vaultUuid = vaultUuid,
                    vaultVersion = nextVersion,
                    accountCount = activeCount,
                    deviceName = deviceName,
                    kdfAlgorithm = Argon2KdfParams.ALGORITHM_NAME,
                    kdfSalt = cloudWrapped.kdfParams.saltBase64,
                    kdfMemoryKb = cloudWrapped.kdfParams.memoryKb.toLong(),
                    kdfIterations = cloudWrapped.kdfParams.iterations.toLong(),
                    kdfParallelism = cloudWrapped.kdfParams.parallelism.toLong(),
                    wrappedVaultKey = cloudWrapped.wrappedKeyBase64,
                    wrappedVaultKeyIv = cloudWrapped.wrappedKeyIvBase64,
                    encryptedVault = encryptedBlob.ciphertextBase64,
                    encryptedVaultIv = encryptedBlob.ivBase64
                )
            )
        } else {
            cloudDataSource.updateSyncPayload(
                userId = userId,
                payload = CloudVaultSyncUpdatePayload(
                    vaultVersion = nextVersion,
                    accountCount = activeCount,
                    deviceName = deviceName,
                    encryptedVault = encryptedBlob.ciphertextBase64,
                    encryptedVaultIv = encryptedBlob.ivBase64
                )
            )
        }

        return writeResult.fold(
            onSuccess = {
                preferencesStore.updateSyncProgress(nextVersion, System.currentTimeMillis(), deviceName)
                BackupOperationResult.Success(nextVersion, activeCount.toInt())
            },
            onFailure = { err ->
                BackupOperationResult.Failure(err.localizedMessage ?: "Failed to upload encrypted backup.")
            }
        )
    }

    /**
     * Multi-device real-time sync: when a remote vault with the SAME vaultUuid and higher version
     * arrives from another device, decrypt with our local Keystore-protected vaultKey and merge
     * by last-write-wins per account ID (respecting deletion tombstones).
     */
    suspend fun syncIncomingRemoteVaultIfCompatible(
        userId: String,
        remoteVault: EncryptedCloudVault
    ): Boolean {
        val meta = preferencesStore.getVaultMetadataOnce()
        if (meta.vaultUuid != remoteVault.vaultUuid) return false
        if (remoteVault.vaultVersion <= meta.localVaultVersion) return false

        val vaultKey = getOrCreateVaultKey()
        return try {
            val remoteAccounts = withContext(Dispatchers.Default) {
                CryptoEngine.decryptCloudVaultPayload(
                    ciphertextBase64 = remoteVault.encryptedVault,
                    ivBase64 = remoteVault.encryptedVaultIv,
                    vaultKey = vaultKey,
                    userId = userId
                )
            }
            val localAccounts = getAllAccountsIncludingTombstones()
            val merged = mergeAccountLists(localAccounts, remoteAccounts)
            val entities = withContext(Dispatchers.Default) {
                merged.map { encryptAccountEntity(it, vaultKey) }
            }
            accountDao.replaceAll(entities)

            // Sync KDF metadata in case recovery password was rotated on another device
            preferencesStore.saveCloudWrappedKey(
                WrappedVaultKeyPayload(
                    wrappedKeyBase64 = remoteVault.wrappedVaultKey,
                    wrappedKeyIvBase64 = remoteVault.wrappedVaultKeyIv,
                    kdfParams = Argon2KdfParams(
                        saltBase64 = remoteVault.kdfSalt,
                        memoryKb = remoteVault.kdfMemoryKb.toInt(),
                        iterations = remoteVault.kdfIterations.toInt(),
                        parallelism = remoteVault.kdfParallelism.toInt()
                    )
                )
            )
            preferencesStore.updateSyncProgress(
                version = remoteVault.vaultVersion,
                syncedEpochMs = System.currentTimeMillis(),
                deviceName = remoteVault.deviceName
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Recovers an encrypted cloud vault on a new device or after reinstalling using Google Sign-In
     * and the user's recovery password (Argon2id + AES-256-GCM unwrap).
     */
    suspend fun restoreFromCloudWithRecoveryPassword(
        userId: String,
        recoveryPassword: CharArray,
        mergeWithLocal: Boolean = false
    ): RestoreOperationResult {
        val remoteFetch = cloudDataSource.fetchPrimaryVault(userId)
        val remoteVault = remoteFetch.getOrElse { err ->
            return RestoreOperationResult.Failure(
                err.localizedMessage ?: "Failed to fetch encrypted cloud backup."
            )
        } ?: return RestoreOperationResult.NoRemoteBackup(
            "No encrypted cloud backup was found for this Google account."
        )

        val kdfParams = Argon2KdfParams(
            saltBase64 = remoteVault.kdfSalt,
            memoryKb = remoteVault.kdfMemoryKb.toInt(),
            iterations = remoteVault.kdfIterations.toInt(),
            parallelism = remoteVault.kdfParallelism.toInt()
        )

        val unwrappedVaultKey = try {
            withContext(Dispatchers.Default) {
                CryptoEngine.unwrapVaultKeyWithRecoveryPassword(
                    wrappedKeyBase64 = remoteVault.wrappedVaultKey,
                    wrappedKeyIvBase64 = remoteVault.wrappedVaultKeyIv,
                    kdfParams = kdfParams,
                    recoveryPassword = recoveryPassword
                )
            }
        } catch (_: AEADBadTagException) {
            return RestoreOperationResult.WrongPassword(
                "Incorrect recovery password. Please check your password and try again."
            )
        } catch (e: Exception) {
            return RestoreOperationResult.WrongPassword(
                "Could not unlock vault key. Verify your recovery password."
            )
        }

        val decryptedRemoteAccounts = try {
            withContext(Dispatchers.Default) {
                CryptoEngine.decryptCloudVaultPayload(
                    ciphertextBase64 = remoteVault.encryptedVault,
                    ivBase64 = remoteVault.encryptedVaultIv,
                    vaultKey = unwrappedVaultKey,
                    userId = userId
                )
            }
        } catch (e: Exception) {
            unwrappedVaultKey.fill(0)
            return RestoreOperationResult.Failure("Cloud vault payload failed integrity verification.")
        }

        val finalAccounts = if (mergeWithLocal) {
            val localAccounts = try {
                getAllAccountsIncludingTombstones()
            } catch (_: Exception) {
                emptyList()
            }
            mergeAccountLists(localAccounts, decryptedRemoteAccounts)
        } else {
            decryptedRemoteAccounts
        }

        // Re-wrap the recovered 256-bit vault key with this device's Android Keystore
        val deviceWrapped = withContext(Dispatchers.Default) {
            deviceKeyManager.wrapVaultKeyForDevice(unwrappedVaultKey)
        }
        keyMutex.withLock {
            cachedVaultKey = unwrappedVaultKey
        }
        preferencesStore.saveDeviceWrappedVaultKey(
            ciphertextBase64 = deviceWrapped.ciphertextBase64,
            ivBase64 = deviceWrapped.ivBase64,
            vaultUuid = remoteVault.vaultUuid
        )
        preferencesStore.saveCloudWrappedKey(
            WrappedVaultKeyPayload(
                wrappedKeyBase64 = remoteVault.wrappedVaultKey,
                wrappedKeyIvBase64 = remoteVault.wrappedVaultKeyIv,
                kdfParams = kdfParams
            )
        )

        val encryptedEntities = withContext(Dispatchers.Default) {
            finalAccounts.map { encryptAccountEntity(it, unwrappedVaultKey) }
        }
        accountDao.replaceAll(encryptedEntities)

        preferencesStore.updateSyncProgress(
            version = remoteVault.vaultVersion,
            syncedEpochMs = System.currentTimeMillis(),
            deviceName = remoteVault.deviceName
        )

        // If merged with local accounts that weren't in cloud yet, push the merged version up
        if (mergeWithLocal) {
            backupNow(userId, forceOverwrite = false)
        }

        val activeCount = finalAccounts.count { !it.deleted }
        return RestoreOperationResult.Success(activeCount, remoteVault.vaultVersion)
    }

    suspend fun deleteCloudBackup(userId: String): Result<Unit> {
        val result = cloudDataSource.deletePrimaryVault(userId)
        if (result.isSuccess) {
            preferencesStore.clearCloudRecoveryMetadata()
        }
        return result
    }

    private suspend fun mergeWithSameKeyRemoteVault(
        userId: String,
        remoteVault: EncryptedCloudVault,
        vaultKey: ByteArray
    ): BackupOperationResult? {
        return try {
            val remoteAccounts = withContext(Dispatchers.Default) {
                CryptoEngine.decryptCloudVaultPayload(
                    ciphertextBase64 = remoteVault.encryptedVault,
                    ivBase64 = remoteVault.encryptedVaultIv,
                    vaultKey = vaultKey,
                    userId = userId
                )
            }
            val localAccounts = getAllAccountsIncludingTombstones()
            val merged = mergeAccountLists(localAccounts, remoteAccounts)
            val entities = withContext(Dispatchers.Default) {
                merged.map { encryptAccountEntity(it, vaultKey) }
            }
            accountDao.replaceAll(entities)

            val activeCount = merged.count { !it.deleted }.toLong()
            val nextVersion = remoteVault.vaultVersion + 1L
            val deviceName = deviceNameProvider()
            val encryptedBlob = withContext(Dispatchers.Default) {
                CryptoEngine.encryptCloudVaultPayload(merged, vaultKey, userId)
            }
            val updateResult = cloudDataSource.updateSyncPayload(
                userId = userId,
                payload = CloudVaultSyncUpdatePayload(
                    vaultVersion = nextVersion,
                    accountCount = activeCount,
                    deviceName = deviceName,
                    encryptedVault = encryptedBlob.ciphertextBase64,
                    encryptedVaultIv = encryptedBlob.ivBase64
                )
            )
            updateResult.fold(
                onSuccess = {
                    preferencesStore.updateSyncProgress(nextVersion, System.currentTimeMillis(), deviceName)
                    BackupOperationResult.Success(nextVersion, activeCount.toInt())
                },
                onFailure = { err ->
                    BackupOperationResult.Failure(err.localizedMessage ?: "Failed to sync merged vault.")
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptAccountEntity(
        account: AuthenticatorAccount,
        vaultKey: ByteArray
    ): EncryptedAccountEntity {
        val encrypted = CryptoEngine.encryptLocalAccount(account, vaultKey)
        return EncryptedAccountEntity(
            id = account.id,
            encryptedPayloadBase64 = encrypted.ciphertextBase64,
            ivBase64 = encrypted.ivBase64,
            sortOrder = account.sortOrder,
            updatedAtEpochMs = account.updatedAtEpochMs,
            deleted = account.deleted
        )
    }

    companion object {
        fun mergeAccountLists(
            local: List<AuthenticatorAccount>,
            remote: List<AuthenticatorAccount>
        ): List<AuthenticatorAccount> {
            val byId = LinkedHashMap<String, AuthenticatorAccount>()
            for (acc in remote) {
                byId[acc.id] = acc
            }
            for (acc in local) {
                val existing = byId[acc.id]
                if (existing == null || acc.updatedAtEpochMs >= existing.updatedAtEpochMs) {
                    byId[acc.id] = acc
                }
            }
            return byId.values.sortedBy { it.sortOrder }
        }

        fun defaultDeviceName(): String {
            val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
            val model = Build.MODEL?.trim().orEmpty()
            val combined = when {
                model.isBlank() -> "Android Device"
                manufacturer.isNotBlank() && model.startsWith(manufacturer, ignoreCase = true) -> model
                manufacturer.isNotBlank() -> "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
                else -> model
            }
            return combined.take(100)
        }
    }
}
