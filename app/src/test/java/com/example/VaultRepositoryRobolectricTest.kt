package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.InMemoryDeviceKeyManager
import com.example.data.BackupOperationResult
import com.example.data.RestoreOperationResult
import com.example.data.VaultRepository
import com.example.data.cloud.CloudVaultDataSource
import com.example.data.cloud.CloudVaultSyncUpdatePayload
import com.example.data.cloud.CloudVaultWritePayload
import com.example.data.local.VaultDatabase
import com.example.data.local.VaultPreferencesStore
import com.example.data.model.AuthenticatorAccount
import com.example.data.model.EncryptedCloudVault
import com.example.data.model.HashAlgorithm
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * End-to-end Robolectric integration tests exercising:
 * 1. Offline local AES-256-GCM encrypted Room persistence (zero plaintext secrets in SQLite)
 * 2. Encrypted cloud backup (zero plaintext secrets or recovery passwords uploaded)
 * 3. Conflict protection (never silently overwriting an existing cloud backup)
 * 4. New-device recovery with Google UID + recovery password (and wrong-password rejection)
 * 5. Multi-device synchronization across two independent Android device vaults
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultRepositoryRobolectricTest {

    private class FakeCloudVaultDataSource : CloudVaultDataSource {
        private val vaultsByUser = MutableStateFlow<Map<String, EncryptedCloudVault>>(emptyMap())

        fun currentVault(userId: String): EncryptedCloudVault? = vaultsByUser.value[userId]

        override fun observePrimaryVault(userId: String): Flow<EncryptedCloudVault?> =
            vaultsByUser.map { it[userId] }

        override suspend fun fetchPrimaryVault(userId: String): Result<EncryptedCloudVault?> =
            Result.success(vaultsByUser.value[userId])

        override suspend fun createPrimaryVault(
            userId: String,
            payload: CloudVaultWritePayload
        ): Result<Unit> {
            val now = Timestamp.now()
            val doc = EncryptedCloudVault(
                id = "primary",
                userId = userId,
                vaultUuid = payload.vaultUuid,
                schemaVersion = 1L,
                vaultVersion = payload.vaultVersion,
                accountCount = payload.accountCount,
                deviceName = payload.deviceName,
                kdfAlgorithm = payload.kdfAlgorithm,
                kdfSalt = payload.kdfSalt,
                kdfMemoryKb = payload.kdfMemoryKb,
                kdfIterations = payload.kdfIterations,
                kdfParallelism = payload.kdfParallelism,
                wrappedVaultKey = payload.wrappedVaultKey,
                wrappedVaultKeyIv = payload.wrappedVaultKeyIv,
                encryptedVault = payload.encryptedVault,
                encryptedVaultIv = payload.encryptedVaultIv,
                createdAt = now,
                updatedAt = now
            )
            vaultsByUser.value = vaultsByUser.value + (userId to doc)
            return Result.success(Unit)
        }

        override suspend fun updateSyncPayload(
            userId: String,
            payload: CloudVaultSyncUpdatePayload
        ): Result<Unit> {
            val existing = vaultsByUser.value[userId]
                ?: return Result.failure(IllegalStateException("Vault does not exist"))
            val updated = existing.copy(
                vaultVersion = payload.vaultVersion,
                accountCount = payload.accountCount,
                deviceName = payload.deviceName,
                encryptedVault = payload.encryptedVault,
                encryptedVaultIv = payload.encryptedVaultIv,
                updatedAt = Timestamp.now()
            )
            vaultsByUser.value = vaultsByUser.value + (userId to updated)
            return Result.success(Unit)
        }

        override suspend fun updateKeyRotationOrReplace(
            userId: String,
            payload: CloudVaultWritePayload
        ): Result<Unit> {
            val existing = vaultsByUser.value[userId]
            val created = existing?.createdAt ?: Timestamp.now()
            val updated = EncryptedCloudVault(
                id = "primary",
                userId = userId,
                vaultUuid = payload.vaultUuid,
                schemaVersion = 1L,
                vaultVersion = payload.vaultVersion,
                accountCount = payload.accountCount,
                deviceName = payload.deviceName,
                kdfAlgorithm = payload.kdfAlgorithm,
                kdfSalt = payload.kdfSalt,
                kdfMemoryKb = payload.kdfMemoryKb,
                kdfIterations = payload.kdfIterations,
                kdfParallelism = payload.kdfParallelism,
                wrappedVaultKey = payload.wrappedVaultKey,
                wrappedVaultKeyIv = payload.wrappedVaultKeyIv,
                encryptedVault = payload.encryptedVault,
                encryptedVaultIv = payload.encryptedVaultIv,
                createdAt = created,
                updatedAt = Timestamp.now()
            )
            vaultsByUser.value = vaultsByUser.value + (userId to updated)
            return Result.success(Unit)
        }

        override suspend fun deletePrimaryVault(userId: String): Result<Unit> {
            vaultsByUser.value = vaultsByUser.value - userId
            return Result.success(Unit)
        }
    }

    private fun createIsolatedDevice(
        context: Context,
        cloud: CloudVaultDataSource,
        deviceName: String
    ): Pair<VaultDatabase, VaultRepository> {
        val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStoreFile = File(context.cacheDir, "test_prefs_${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { dataStoreFile }
        )
        val repo = VaultRepository(
            accountDao = db.encryptedAccountDao(),
            preferencesStore = VaultPreferencesStore(dataStore),
            deviceKeyManager = InMemoryDeviceKeyManager(),
            cloudDataSource = cloud,
            deviceNameProvider = { deviceName }
        )
        return db to repo
    }

    @Test
    fun `offline storage encrypts secrets at rest and supports full backup recovery conflict protection and multi-device sync`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val cloud = FakeCloudVaultDataSource()
            val uid = "google_user_zayan"
            val recoveryPassword = "Strong!Vault#Recovery2026"

            val (dbA, deviceA) = createIsolatedDevice(context, cloud, "Pixel 9 (Device A)")
            val (dbB, deviceB) = createIsolatedDevice(context, cloud, "Galaxy S25 (Device B)")

            try {
                // 1. Add account completely offline on Device A
                val secret = "JBSWY3DPEHPK3PXP"
                val added = deviceA.addAccounts(
                    listOf(
                        AuthenticatorAccount(
                            id = "acc_github",
                            issuer = "GitHub",
                            accountName = "zayan@example.com",
                            secretBase32 = secret,
                            algorithm = HashAlgorithm.SHA1,
                            digits = 6,
                            periodSeconds = 30
                        )
                    )
                )
                assertEquals(1, added)

                // Verify SQLite Room table NEVER stores plaintext TOTP secret or issuer
                val rawRoomRows = dbA.encryptedAccountDao().getAllEncryptedAccountsOnce()
                assertEquals(1, rawRoomRows.size)
                assertFalse(rawRoomRows.first().encryptedPayloadBase64.contains(secret))
                assertFalse(rawRoomRows.first().encryptedPayloadBase64.contains("GitHub"))

                // 2. Setup recovery password & upload encrypted vault from Device A
                val backupRes = deviceA.setupRecoveryPasswordAndBackup(
                    userId = uid,
                    recoveryPassword = recoveryPassword.toCharArray()
                )
                assertTrue(backupRes is BackupOperationResult.Success)
                val cloudDoc = cloud.currentVault(uid)
                assertNotNull(cloudDoc)
                assertFalse(cloudDoc!!.encryptedVault.contains(secret))
                assertFalse(cloudDoc.wrappedVaultKey.contains(recoveryPassword))
                assertEquals("ARGON2ID", cloudDoc.kdfAlgorithm)

                // 3. Conflict safety: Device B (brand new install) MUST NOT silently overwrite Device A's backup!
                val conflictRes = deviceB.setupRecoveryPasswordAndBackup(
                    userId = uid,
                    recoveryPassword = "AnotherPassword#2026".toCharArray(),
                    forceReplaceExisting = false
                )
                assertTrue(
                    "Device B must detect conflict instead of silently overwriting existing backup",
                    conflictRes is BackupOperationResult.ConflictDetected
                )

                // 4. Wrong recovery password rejected on Device B
                val wrongRestore = deviceB.restoreFromCloudWithRecoveryPassword(
                    userId = uid,
                    recoveryPassword = "WrongPassword#2026".toCharArray()
                )
                assertTrue(wrongRestore is RestoreOperationResult.WrongPassword)

                // 5. Correct recovery password restores Device A's vault onto Device B
                val restoreRes = deviceB.restoreFromCloudWithRecoveryPassword(
                    userId = uid,
                    recoveryPassword = recoveryPassword.toCharArray()
                )
                assertTrue(restoreRes is RestoreOperationResult.Success)
                val restoredAccounts = deviceB.observeActiveAccounts().first()
                assertEquals(1, restoredAccounts.size)
                assertEquals("GitHub", restoredAccounts.first().issuer)
                assertEquals(secret, restoredAccounts.first().secretBase32)

                // 6. Multi-device sync: Device A adds a second account & backs up -> Device B syncs automatically!
                deviceA.addAccounts(
                    listOf(
                        AuthenticatorAccount(
                            id = "acc_microsoft",
                            issuer = "Microsoft",
                            accountName = "work@zayaanapps.com",
                            secretBase32 = "GEZDGNBVGY3TQOJQ",
                            algorithm = HashAlgorithm.SHA256,
                            digits = 8,
                            periodSeconds = 60
                        )
                    )
                )
                val backup2 = deviceA.backupNow(uid)
                assertTrue(backup2 is BackupOperationResult.Success)
                val latestRemote = cloud.currentVault(uid)!!
                assertEquals(2L, latestRemote.vaultVersion)

                val syncedOnB = deviceB.syncIncomingRemoteVaultIfCompatible(uid, latestRemote)
                assertTrue("Device B should sync updated vault automatically", syncedOnB)
                val deviceBAccounts = deviceB.observeActiveAccounts().first()
                assertEquals(2, deviceBAccounts.size)
                assertTrue(deviceBAccounts.any { it.issuer == "Microsoft" && it.secretBase32 == "GEZDGNBVGY3TQOJQ" })
            } finally {
                dbA.close()
                dbB.close()
            }
        }
}
