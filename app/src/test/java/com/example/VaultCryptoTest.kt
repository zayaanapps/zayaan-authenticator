package com.example

import com.example.crypto.CryptoEngine
import com.example.data.model.AccountCategory
import com.example.data.model.AuthenticatorAccount
import com.example.data.model.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultCryptoTest {

    @Test
    fun `aes256gcm encrypts decrypts and detects tampering or AAD mismatch`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "TOP_SECRET_TOTP_SEED_JBSWY3DPEHPK3PXP".toByteArray(Charsets.UTF_8)

        val enc1 = CryptoEngine.encryptAesGcm(plaintext, key, aad = "account-1")
        val enc2 = CryptoEngine.encryptAesGcm(plaintext, key, aad = "account-1")

        // Random 96-bit IV guarantees distinct ciphertexts for identical plaintexts
        assertNotEquals(enc1.ivBase64, enc2.ivBase64)
        assertNotEquals(enc1.ciphertextBase64, enc2.ciphertextBase64)

        val decrypted = CryptoEngine.decryptAesGcm(enc1.ciphertextBase64, enc1.ivBase64, key, aad = "account-1")
        assertArrayEquals(plaintext, decrypted)

        // Wrong AAD must fail AEAD authentication
        var aadFailed = false
        try {
            CryptoEngine.decryptAesGcm(enc1.ciphertextBase64, enc1.ivBase64, key, aad = "account-2")
        } catch (_: AEADBadTagException) {
            aadFailed = true
        }
        assertTrue("Expected AEADBadTagException on AAD mismatch", aadFailed)
    }

    @Test
    fun `argon2id wraps and unwraps 256-bit vault key and rejects wrong recovery password`() {
        val vaultKey = CryptoEngine.generateVaultKey()
        val password = "Correct-Horse-Battery-99!".toCharArray()
        val wrongPassword = "Wrong-Horse-Battery-99!".toCharArray()

        // Use fast test memory cost while still exercising full RFC 9106 Argon2id code path
        val wrapped = CryptoEngine.wrapVaultKeyWithRecoveryPassword(
            vaultKey = vaultKey,
            recoveryPassword = password,
            memoryKb = 4096,
            iterations = 2,
            parallelism = 1
        )

        val unwrapped = CryptoEngine.unwrapVaultKeyWithRecoveryPassword(
            wrappedKeyBase64 = wrapped.wrappedKeyBase64,
            wrappedKeyIvBase64 = wrapped.wrappedKeyIvBase64,
            kdfParams = wrapped.kdfParams,
            recoveryPassword = password
        )
        assertArrayEquals(vaultKey, unwrapped)

        var rejected = false
        try {
            CryptoEngine.unwrapVaultKeyWithRecoveryPassword(
                wrappedKeyBase64 = wrapped.wrappedKeyBase64,
                wrappedKeyIvBase64 = wrapped.wrappedKeyIvBase64,
                kdfParams = wrapped.kdfParams,
                recoveryPassword = wrongPassword
            )
        } catch (_: AEADBadTagException) {
            rejected = true
        }
        assertTrue("Wrong recovery password must fail AES-GCM tag verification", rejected)
    }

    @Test
    fun `zero-knowledge cloud vault encryption never exposes plaintext TOTP secrets`() {
        val vaultKey = CryptoEngine.generateVaultKey()
        val secret = "JBSWY3DPEHPK3PXP"
        val accounts = listOf(
            AuthenticatorAccount(
                id = "acc-1",
                issuer = "GitHub",
                accountName = "zayan@example.com",
                secretBase32 = secret,
                algorithm = HashAlgorithm.SHA256,
                digits = 8,
                periodSeconds = 60,
                category = AccountCategory.DEVELOPER
            )
        )

        val encrypted = CryptoEngine.encryptCloudVaultPayload(accounts, vaultKey, userId = "uid_alice")
        assertFalse(encrypted.ciphertextBase64.contains(secret))
        assertFalse(encrypted.ciphertextBase64.contains("GitHub"))
        assertFalse(encrypted.ciphertextBase64.contains("zayan@example.com"))

        val decrypted = CryptoEngine.decryptCloudVaultPayload(
            ciphertextBase64 = encrypted.ciphertextBase64,
            ivBase64 = encrypted.ivBase64,
            vaultKey = vaultKey,
            userId = "uid_alice"
        )
        assertEquals(1, decrypted.size)
        assertEquals(secret, decrypted.first().secretBase32)
        assertEquals("GitHub", decrypted.first().issuer)
        assertEquals(HashAlgorithm.SHA256, decrypted.first().algorithm)
        assertEquals(8, decrypted.first().digits)
        assertEquals(60, decrypted.first().periodSeconds)
    }

    @Test
    fun `app lock PIN Argon2id hash verifies valid and rejects invalid PINs`() {
        val (hash, salt) = CryptoEngine.hashAppLockSecret("482910".toCharArray())
        assertTrue(CryptoEngine.verifyAppLockSecret("482910".toCharArray(), hash, salt))
        assertFalse(CryptoEngine.verifyAppLockSecret("482911".toCharArray(), hash, salt))
        assertFalse(CryptoEngine.verifyAppLockSecret("".toCharArray(), hash, salt))
    }
}
