package com.example.crypto

import com.example.data.model.AccountCategory
import com.example.data.model.AuthenticatorAccount
import com.example.data.model.HashAlgorithm
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AesGcmCiphertext(
    val ciphertextBase64: String,
    val ivBase64: String
)

data class Argon2KdfParams(
    val saltBase64: String,
    val memoryKb: Int = DEFAULT_MEMORY_KB,
    val iterations: Int = DEFAULT_ITERATIONS,
    val parallelism: Int = DEFAULT_PARALLELISM
) {
    companion object {
        const val ALGORITHM_NAME = "ARGON2ID"
        const val DEFAULT_MEMORY_KB = 32768 // 32 MiB — memory-hard & safe on all Android devices
        const val DEFAULT_ITERATIONS = 3
        const val DEFAULT_PARALLELISM = 1
        const val SALT_LENGTH_BYTES = 16
        const val KEY_LENGTH_BYTES = 32
    }
}

data class WrappedVaultKeyPayload(
    val wrappedKeyBase64: String,
    val wrappedKeyIvBase64: String,
    val kdfParams: Argon2KdfParams
)

enum class PasswordStrength(val label: String, val score: Float) {
    WEAK("Weak — use at least 10 characters with letters, numbers & symbols", 0.25f),
    FAIR("Fair — add more length or symbols", 0.55f),
    STRONG("Strong recovery password", 0.80f),
    EXCELLENT("Excellent zero-knowledge passphrase", 1.0f)
}

/**
 * Cryptographic engine providing:
 * - AES-256-GCM authenticated encryption with random 96-bit IVs and AAD
 * - RFC 9106 Argon2id key derivation (BouncyCastle) with unique cryptographic salts
 * - Zero-knowledge 256-bit vault key generation, wrapping, and vault payload encryption
 */
object CryptoEngine {

    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val VAULT_KEY_LENGTH_BYTES = 32
    private const val VAULT_KEY_WRAP_AAD = "zayan-vault-key-wrap-v1"
    private const val CLOUD_VAULT_AAD_PREFIX = "zayan-vault-payload-v1:"
    private const val LOCAL_ACCOUNT_AAD_PREFIX = "zayan-local-account-v1:"

    private val secureRandom = SecureRandom()

    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun generateVaultKey(): ByteArray = generateRandomBytes(VAULT_KEY_LENGTH_BYTES)

    fun generateSalt(length: Int = Argon2KdfParams.SALT_LENGTH_BYTES): ByteArray =
        generateRandomBytes(length)

    fun encryptAesGcm(
        plaintext: ByteArray,
        keyBytes: ByteArray,
        aad: String? = null
    ): AesGcmCiphertext {
        require(keyBytes.size == VAULT_KEY_LENGTH_BYTES) { "AES-256 requires a 32-byte key" }
        val iv = generateRandomBytes(GCM_IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (!aad.isNullOrEmpty()) {
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        }
        val ciphertext = cipher.doFinal(plaintext)
        return AesGcmCiphertext(
            ciphertextBase64 = SafeBase64.encode(ciphertext),
            ivBase64 = SafeBase64.encode(iv)
        )
    }

    fun decryptAesGcm(
        ciphertextBase64: String,
        ivBase64: String,
        keyBytes: ByteArray,
        aad: String? = null
    ): ByteArray {
        require(keyBytes.size == VAULT_KEY_LENGTH_BYTES) { "AES-256 requires a 32-byte key" }
        val ciphertext = SafeBase64.decode(ciphertextBase64)
        val iv = SafeBase64.decode(ivBase64)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (!aad.isNullOrEmpty()) {
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        }
        return cipher.doFinal(ciphertext)
    }

    /**
     * Derives a 256-bit key using RFC 9106 Argon2id.
     */
    fun deriveKeyArgon2id(
        password: CharArray,
        salt: ByteArray,
        memoryKb: Int = Argon2KdfParams.DEFAULT_MEMORY_KB,
        iterations: Int = Argon2KdfParams.DEFAULT_ITERATIONS,
        parallelism: Int = Argon2KdfParams.DEFAULT_PARALLELISM,
        hashLengthBytes: Int = Argon2KdfParams.KEY_LENGTH_BYTES
    ): ByteArray {
        require(salt.size >= 8) { "Argon2id salt must be at least 8 bytes" }
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)
        val output = ByteArray(hashLengthBytes)
        generator.generateBytes(password, output)
        return output
    }

    /**
     * Wraps the 256-bit vault key using an Argon2id-derived key and AES-256-GCM.
     */
    fun wrapVaultKeyWithRecoveryPassword(
        vaultKey: ByteArray,
        recoveryPassword: CharArray,
        memoryKb: Int = Argon2KdfParams.DEFAULT_MEMORY_KB,
        iterations: Int = Argon2KdfParams.DEFAULT_ITERATIONS,
        parallelism: Int = Argon2KdfParams.DEFAULT_PARALLELISM
    ): WrappedVaultKeyPayload {
        val salt = generateSalt()
        val kek = deriveKeyArgon2id(
            password = recoveryPassword,
            salt = salt,
            memoryKb = memoryKb,
            iterations = iterations,
            parallelism = parallelism
        )
        return try {
            val wrapped = encryptAesGcm(
                plaintext = vaultKey,
                keyBytes = kek,
                aad = VAULT_KEY_WRAP_AAD
            )
            WrappedVaultKeyPayload(
                wrappedKeyBase64 = wrapped.ciphertextBase64,
                wrappedKeyIvBase64 = wrapped.ivBase64,
                kdfParams = Argon2KdfParams(
                    saltBase64 = SafeBase64.encode(salt),
                    memoryKb = memoryKb,
                    iterations = iterations,
                    parallelism = parallelism
                )
            )
        } finally {
            kek.fill(0)
        }
    }

    /**
     * Unwraps the 256-bit vault key using the user's recovery password and stored Argon2id parameters.
     * Throws AEADBadTagException if the recovery password is wrong or data is tampered.
     */
    fun unwrapVaultKeyWithRecoveryPassword(
        wrappedKeyBase64: String,
        wrappedKeyIvBase64: String,
        kdfParams: Argon2KdfParams,
        recoveryPassword: CharArray
    ): ByteArray {
        val salt = SafeBase64.decode(kdfParams.saltBase64)
        val kek = deriveKeyArgon2id(
            password = recoveryPassword,
            salt = salt,
            memoryKb = kdfParams.memoryKb,
            iterations = kdfParams.iterations,
            parallelism = kdfParams.parallelism
        )
        return try {
            decryptAesGcm(
                ciphertextBase64 = wrappedKeyBase64,
                ivBase64 = wrappedKeyIvBase64,
                keyBytes = kek,
                aad = VAULT_KEY_WRAP_AAD
            )
        } finally {
            kek.fill(0)
        }
    }

    /**
     * Hashes a local app-lock PIN/passcode using Argon2id with random salt.
     */
    fun hashAppLockSecret(secret: CharArray): Pair<String, String> {
        val salt = generateSalt()
        // Lightweight memory-hard Argon2id parameters for fast (<250ms) responsive unlock
        val hash = deriveKeyArgon2id(
            password = secret,
            salt = salt,
            memoryKb = 16384,
            iterations = 2,
            parallelism = 1,
            hashLengthBytes = 32
        )
        return try {
            SafeBase64.encode(hash) to SafeBase64.encode(salt)
        } finally {
            hash.fill(0)
        }
    }

    fun verifyAppLockSecret(
        candidate: CharArray,
        storedHashBase64: String,
        storedSaltBase64: String
    ): Boolean {
        return try {
            val expectedHash = SafeBase64.decode(storedHashBase64)
            val salt = SafeBase64.decode(storedSaltBase64)
            val computed = deriveKeyArgon2id(
                password = candidate,
                salt = salt,
                memoryKb = 16384,
                iterations = 2,
                parallelism = 1,
                hashLengthBytes = expectedHash.size
            )
            try {
                MessageDigest.isEqual(expectedHash, computed)
            } finally {
                computed.fill(0)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun encryptLocalAccount(account: AuthenticatorAccount, vaultKey: ByteArray): AesGcmCiphertext {
        val json = accountToJson(account).toString().toByteArray(Charsets.UTF_8)
        return try {
            encryptAesGcm(
                plaintext = json,
                keyBytes = vaultKey,
                aad = "$LOCAL_ACCOUNT_AAD_PREFIX${account.id}"
            )
        } finally {
            json.fill(0)
        }
    }

    fun decryptLocalAccount(
        accountId: String,
        ciphertextBase64: String,
        ivBase64: String,
        vaultKey: ByteArray
    ): AuthenticatorAccount {
        val bytes = decryptAesGcm(
            ciphertextBase64 = ciphertextBase64,
            ivBase64 = ivBase64,
            keyBytes = vaultKey,
            aad = "$LOCAL_ACCOUNT_AAD_PREFIX$accountId"
        )
        return try {
            jsonToAccount(JSONObject(String(bytes, Charsets.UTF_8)))
        } finally {
            bytes.fill(0)
        }
    }

    fun encryptCloudVaultPayload(
        accounts: List<AuthenticatorAccount>,
        vaultKey: ByteArray,
        userId: String
    ): AesGcmCiphertext {
        val root = JSONObject().apply {
            put("schemaVersion", 1)
            val arr = JSONArray()
            accounts.forEach { arr.put(accountToJson(it)) }
            put("accounts", arr)
        }
        val plaintext = root.toString().toByteArray(Charsets.UTF_8)
        return try {
            encryptAesGcm(
                plaintext = plaintext,
                keyBytes = vaultKey,
                aad = "$CLOUD_VAULT_AAD_PREFIX$userId"
            )
        } finally {
            plaintext.fill(0)
        }
    }

    fun decryptCloudVaultPayload(
        ciphertextBase64: String,
        ivBase64: String,
        vaultKey: ByteArray,
        userId: String
    ): List<AuthenticatorAccount> {
        val plaintext = decryptAesGcm(
            ciphertextBase64 = ciphertextBase64,
            ivBase64 = ivBase64,
            keyBytes = vaultKey,
            aad = "$CLOUD_VAULT_AAD_PREFIX$userId"
        )
        return try {
            val root = JSONObject(String(plaintext, Charsets.UTF_8))
            val arr = root.optJSONArray("accounts") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    add(jsonToAccount(arr.getJSONObject(i)))
                }
            }
        } finally {
            plaintext.fill(0)
        }
    }

    fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.length < 8) return PasswordStrength.WEAK
        var points = 0
        if (password.length >= 10) points++
        if (password.length >= 14) points++
        if (password.any { it.isLowerCase() } && password.any { it.isUpperCase() }) points++
        if (password.any { it.isDigit() }) points++
        if (password.any { !it.isLetterOrDigit() }) points++
        return when {
            points <= 1 -> PasswordStrength.WEAK
            points == 2 -> PasswordStrength.FAIR
            points == 3 -> PasswordStrength.STRONG
            else -> PasswordStrength.EXCELLENT
        }
    }

    private fun accountToJson(account: AuthenticatorAccount): JSONObject {
        return JSONObject().apply {
            put("id", account.id)
            put("issuer", account.issuer)
            put("accountName", account.accountName)
            put("secretBase32", account.secretBase32)
            put("algorithm", account.algorithm.name)
            put("digits", account.digits)
            put("periodSeconds", account.periodSeconds)
            put("category", account.category.name)
            put("sortOrder", account.sortOrder)
            put("createdAtEpochMs", account.createdAtEpochMs)
            put("updatedAtEpochMs", account.updatedAtEpochMs)
            put("deleted", account.deleted)
        }
    }

    private fun jsonToAccount(obj: JSONObject): AuthenticatorAccount {
        return AuthenticatorAccount(
            id = obj.getString("id"),
            issuer = obj.optString("issuer", ""),
            accountName = obj.optString("accountName", ""),
            secretBase32 = obj.getString("secretBase32"),
            algorithm = HashAlgorithm.fromRaw(obj.optString("algorithm", "SHA1")),
            digits = obj.optInt("digits", 6).takeIf { it == 6 || it == 8 } ?: 6,
            periodSeconds = obj.optInt("periodSeconds", 30).takeIf { it in 10..300 } ?: 30,
            category = AccountCategory.fromRaw(obj.optString("category", "GENERAL")),
            sortOrder = obj.optInt("sortOrder", 0),
            createdAtEpochMs = obj.optLong("createdAtEpochMs", 0L),
            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
            deleted = obj.optBoolean("deleted", false)
        )
    }
}
