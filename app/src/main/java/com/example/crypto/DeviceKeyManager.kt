package com.example.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Abstraction for device-level key protection wrapping the random 256-bit vault key.
 * Production uses AndroidKeyStore (hardware-backed AES-256-GCM when supported by hardware).
 */
interface DeviceKeyManager {
    fun wrapVaultKeyForDevice(vaultKey: ByteArray): AesGcmCiphertext
    fun unwrapVaultKeyForDevice(ciphertextBase64: String, ivBase64: String): ByteArray
}

class AndroidKeystoreDeviceKeyManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : DeviceKeyManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun wrapVaultKeyForDevice(vaultKey: ByteArray): AesGcmCiphertext {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        cipher.updateAAD(DEVICE_WRAP_AAD.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(vaultKey)
        val iv = cipher.iv
        return AesGcmCiphertext(
            ciphertextBase64 = SafeBase64.encode(ciphertext),
            ivBase64 = SafeBase64.encode(iv)
        )
    }

    override fun unwrapVaultKeyForDevice(ciphertextBase64: String, ivBase64: String): ByteArray {
        val secretKey = getOrCreateMasterKey()
        val ciphertext = SafeBase64.decode(ciphertextBase64)
        val iv = SafeBase64.decode(ivBase64)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(DEVICE_WRAP_AAD.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "zayan_authenticator_master_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val DEVICE_WRAP_AAD = "zayan-device-vault-key-v1"
    }
}

/**
 * Deterministic in-memory AES-256-GCM DeviceKeyManager for JVM/Robolectric tests
 * where AndroidKeyStore hardware provider is unavailable.
 */
class InMemoryDeviceKeyManager(
    private val keyBytes: ByteArray = CryptoEngine.generateVaultKey()
) : DeviceKeyManager {
    override fun wrapVaultKeyForDevice(vaultKey: ByteArray): AesGcmCiphertext {
        return CryptoEngine.encryptAesGcm(vaultKey, keyBytes, "zayan-device-vault-key-v1")
    }

    override fun unwrapVaultKeyForDevice(ciphertextBase64: String, ivBase64: String): ByteArray {
        return CryptoEngine.decryptAesGcm(ciphertextBase64, ivBase64, keyBytes, "zayan-device-vault-key-v1")
    }
}
