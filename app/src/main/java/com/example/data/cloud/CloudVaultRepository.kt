package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.data.model.EncryptedCloudVault
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface CloudVaultDataSource {
    fun observePrimaryVault(userId: String): Flow<EncryptedCloudVault?>
    suspend fun fetchPrimaryVault(userId: String): Result<EncryptedCloudVault?>
    suspend fun createPrimaryVault(userId: String, payload: CloudVaultWritePayload): Result<Unit>
    suspend fun updateSyncPayload(userId: String, payload: CloudVaultSyncUpdatePayload): Result<Unit>
    suspend fun updateKeyRotationOrReplace(userId: String, payload: CloudVaultWritePayload): Result<Unit>
    suspend fun deletePrimaryVault(userId: String): Result<Unit>
}

data class CloudVaultWritePayload(
    val vaultUuid: String,
    val vaultVersion: Long,
    val accountCount: Long,
    val deviceName: String,
    val kdfAlgorithm: String,
    val kdfSalt: String,
    val kdfMemoryKb: Long,
    val kdfIterations: Long,
    val kdfParallelism: Long,
    val wrappedVaultKey: String,
    val wrappedVaultKeyIv: String,
    val encryptedVault: String,
    val encryptedVaultIv: String
)

data class CloudVaultSyncUpdatePayload(
    val vaultVersion: Long,
    val accountCount: Long,
    val deviceName: String,
    val encryptedVault: String,
    val encryptedVaultIv: String
)

/**
 * Firestore repository strictly bound to the provisioned named database (R.string.firestore_database_id)
 * and scoped exclusively to /users/{uid}/vaults/primary.
 * Stores ONLY encrypted vault ciphertext and Argon2id KDF parameters.
 */
class FirestoreCloudVaultRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : CloudVaultDataSource {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthenticatedUserId(expectedUserId: String): String {
        val currentUid = auth.currentUser?.uid
        check(!currentUid.isNullOrBlank()) {
            "User must be signed in with Google before accessing encrypted cloud backup."
        }
        check(currentUid == expectedUserId) {
            "Authenticated user mismatch; refusing cross-user Firestore operation."
        }
        return currentUid
    }

    private fun vaultDocRef(userId: String) =
        db.collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_VAULTS)
            .document(PRIMARY_VAULT_DOC_ID)

    override fun observePrimaryVault(userId: String): Flow<EncryptedCloudVault?> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(null)
            return@flow
        }
        val docRef = vaultDocRef(userId)
        val path = docRef.path
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            EncryptedCloudVault::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun fetchPrimaryVault(userId: String): Result<EncryptedCloudVault?> {
        val path = "$COLLECTION_USERS/$userId/$COLLECTION_VAULTS/$PRIMARY_VAULT_DOC_ID"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            val snapshot = vaultDocRef(uid).get().await()
            val vault = if (snapshot.exists()) {
                snapshot.toObject(
                    EncryptedCloudVault::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(vault)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    override suspend fun createPrimaryVault(
        userId: String,
        payload: CloudVaultWritePayload
    ): Result<Unit> {
        val path = "$COLLECTION_USERS/$userId/$COLLECTION_VAULTS/$PRIMARY_VAULT_DOC_ID"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            validateFullPayload(payload)
            val docMap = mapOf(
                "userId" to uid,
                "vaultUuid" to payload.vaultUuid,
                "schemaVersion" to SCHEMA_VERSION,
                "vaultVersion" to payload.vaultVersion,
                "accountCount" to payload.accountCount,
                "deviceName" to payload.deviceName.take(100),
                "kdfAlgorithm" to payload.kdfAlgorithm,
                "kdfSalt" to payload.kdfSalt,
                "kdfMemoryKb" to payload.kdfMemoryKb,
                "kdfIterations" to payload.kdfIterations,
                "kdfParallelism" to payload.kdfParallelism,
                "wrappedVaultKey" to payload.wrappedVaultKey,
                "wrappedVaultKeyIv" to payload.wrappedVaultKeyIv,
                "encryptedVault" to payload.encryptedVault,
                "encryptedVaultIv" to payload.encryptedVaultIv,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            vaultDocRef(uid).set(docMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateSyncPayload(
        userId: String,
        payload: CloudVaultSyncUpdatePayload
    ): Result<Unit> {
        val path = "$COLLECTION_USERS/$userId/$COLLECTION_VAULTS/$PRIMARY_VAULT_DOC_ID"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            require(payload.vaultVersion in 1L..1_000_000L) { "Invalid vaultVersion" }
            require(payload.accountCount in 0L..5000L) { "Invalid accountCount" }
            require(payload.deviceName.isNotBlank()) { "Device name cannot be blank" }
            require(payload.encryptedVault.length in 16..700_000) { "Encrypted vault exceeds size limits" }
            require(payload.encryptedVaultIv.length in 12..64) { "Invalid encryptedVaultIv" }

            val updateMap = mapOf(
                "vaultVersion" to payload.vaultVersion,
                "accountCount" to payload.accountCount,
                "deviceName" to payload.deviceName.take(100),
                "encryptedVault" to payload.encryptedVault,
                "encryptedVaultIv" to payload.encryptedVaultIv,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            vaultDocRef(uid).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateKeyRotationOrReplace(
        userId: String,
        payload: CloudVaultWritePayload
    ): Result<Unit> {
        val path = "$COLLECTION_USERS/$userId/$COLLECTION_VAULTS/$PRIMARY_VAULT_DOC_ID"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            validateFullPayload(payload)
            val updateMap = mapOf(
                "vaultUuid" to payload.vaultUuid,
                "vaultVersion" to payload.vaultVersion,
                "accountCount" to payload.accountCount,
                "deviceName" to payload.deviceName.take(100),
                "kdfAlgorithm" to payload.kdfAlgorithm,
                "kdfSalt" to payload.kdfSalt,
                "kdfMemoryKb" to payload.kdfMemoryKb,
                "kdfIterations" to payload.kdfIterations,
                "kdfParallelism" to payload.kdfParallelism,
                "wrappedVaultKey" to payload.wrappedVaultKey,
                "wrappedVaultKeyIv" to payload.wrappedVaultKeyIv,
                "encryptedVault" to payload.encryptedVault,
                "encryptedVaultIv" to payload.encryptedVaultIv,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            vaultDocRef(uid).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deletePrimaryVault(userId: String): Result<Unit> {
        val path = "$COLLECTION_USERS/$userId/$COLLECTION_VAULTS/$PRIMARY_VAULT_DOC_ID"
        return try {
            val uid = requireAuthenticatedUserId(userId)
            vaultDocRef(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun validateFullPayload(payload: CloudVaultWritePayload) {
        require(payload.vaultUuid.length in 1..64) { "Invalid vaultUuid" }
        require(payload.vaultVersion in 1L..1_000_000L) { "Invalid vaultVersion" }
        require(payload.accountCount in 0L..5000L) { "Invalid accountCount" }
        require(payload.deviceName.isNotBlank()) { "Device name cannot be blank" }
        require(payload.kdfAlgorithm == "ARGON2ID") { "Only ARGON2ID KDF is supported" }
        require(payload.kdfSalt.length in 16..128) { "Invalid kdfSalt" }
        require(payload.kdfMemoryKb in 4096L..262144L) { "Invalid kdfMemoryKb" }
        require(payload.kdfIterations in 1L..20L) { "Invalid kdfIterations" }
        require(payload.kdfParallelism in 1L..8L) { "Invalid kdfParallelism" }
        require(payload.wrappedVaultKey.length in 24..256) { "Invalid wrappedVaultKey" }
        require(payload.wrappedVaultKeyIv.length in 12..64) { "Invalid wrappedVaultKeyIv" }
        require(payload.encryptedVault.length in 16..700_000) { "Encrypted vault exceeds size limits" }
        require(payload.encryptedVaultIv.length in 12..64) { "Invalid encryptedVaultIv" }
    }

    companion object {
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_VAULTS = "vaults"
        const val PRIMARY_VAULT_DOC_ID = "primary"
        private const val SCHEMA_VERSION = 1L
    }
}
