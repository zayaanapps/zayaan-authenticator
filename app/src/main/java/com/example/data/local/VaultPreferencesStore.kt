package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.crypto.Argon2KdfParams
import com.example.crypto.WrappedVaultKeyPayload
import com.example.data.model.AccountSortMode
import com.example.data.model.AppThemeMode
import com.example.data.model.AutoLockTimeout
import com.example.data.model.SecuritySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "zayan_vault_preferences"
)

data class StoredVaultMetadata(
    val deviceWrappedVaultKey: String?,
    val deviceWrappedVaultKeyIv: String?,
    val vaultUuid: String?,
    val localVaultVersion: Long,
    val lastSyncedEpochMs: Long,
    val lastSyncedDeviceName: String?,
    val cloudWrappedKey: WrappedVaultKeyPayload?
)

data class AppLockCredentials(
    val enabled: Boolean,
    val biometricEnabled: Boolean,
    val pinHashBase64: String?,
    val pinSaltBase64: String?
)

class VaultPreferencesStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.vaultDataStore)

    private object Keys {
        val DEVICE_WRAPPED_VAULT_KEY = stringPreferencesKey("device_wrapped_vault_key")
        val DEVICE_WRAPPED_VAULT_KEY_IV = stringPreferencesKey("device_wrapped_vault_key_iv")
        val VAULT_UUID = stringPreferencesKey("vault_uuid")
        val LOCAL_VAULT_VERSION = longPreferencesKey("local_vault_version")
        val LAST_SYNCED_EPOCH_MS = longPreferencesKey("last_synced_epoch_ms")
        val LAST_SYNCED_DEVICE_NAME = stringPreferencesKey("last_synced_device_name")

        // Zero-knowledge recovery metadata (NEVER stores the recovery password!)
        val CLOUD_WRAPPED_VAULT_KEY = stringPreferencesKey("cloud_wrapped_vault_key")
        val CLOUD_WRAPPED_VAULT_KEY_IV = stringPreferencesKey("cloud_wrapped_vault_key_iv")
        val CLOUD_KDF_SALT = stringPreferencesKey("cloud_kdf_salt")
        val CLOUD_KDF_MEMORY_KB = intPreferencesKey("cloud_kdf_memory_kb")
        val CLOUD_KDF_ITERATIONS = intPreferencesKey("cloud_kdf_iterations")
        val CLOUD_KDF_PARALLELISM = intPreferencesKey("cloud_kdf_parallelism")

        // App Lock & Security Preferences
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val AUTO_LOCK_TIMEOUT = stringPreferencesKey("auto_lock_timeout")
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_CLOUD_BACKUP = booleanPreferencesKey("auto_cloud_backup")
        val PRIVACY_MASK_CODES = booleanPreferencesKey("privacy_mask_codes")
        val SHOW_NEXT_CODE_PREVIEW = booleanPreferencesKey("show_next_code_preview")
        val ACCOUNT_SORT_MODE = stringPreferencesKey("account_sort_mode")
    }

    val securitySettingsFlow: Flow<SecuritySettings> = dataStore.data.map { prefs ->
        SecuritySettings(
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            autoLockTimeout = AutoLockTimeout.fromRaw(prefs[Keys.AUTO_LOCK_TIMEOUT]),
            screenshotProtectionEnabled = prefs[Keys.SCREENSHOT_PROTECTION] ?: true,
            themeMode = AppThemeMode.fromRaw(prefs[Keys.THEME_MODE]),
            autoCloudBackupEnabled = prefs[Keys.AUTO_CLOUD_BACKUP] ?: true,
            recoveryPasswordConfigured = !prefs[Keys.CLOUD_WRAPPED_VAULT_KEY].isNullOrBlank(),
            privacyMaskCodesEnabled = prefs[Keys.PRIVACY_MASK_CODES] ?: false,
            showNextCodePreview = prefs[Keys.SHOW_NEXT_CODE_PREVIEW] ?: true,
            sortMode = AccountSortMode.fromRaw(prefs[Keys.ACCOUNT_SORT_MODE])
        )
    }

    val vaultMetadataFlow: Flow<StoredVaultMetadata> = dataStore.data.map { prefs ->
        val cloudWrapped = prefs[Keys.CLOUD_WRAPPED_VAULT_KEY]
        val cloudWrappedIv = prefs[Keys.CLOUD_WRAPPED_VAULT_KEY_IV]
        val kdfSalt = prefs[Keys.CLOUD_KDF_SALT]
        val wrappedPayload = if (!cloudWrapped.isNullOrBlank() &&
            !cloudWrappedIv.isNullOrBlank() &&
            !kdfSalt.isNullOrBlank()
        ) {
            WrappedVaultKeyPayload(
                wrappedKeyBase64 = cloudWrapped,
                wrappedKeyIvBase64 = cloudWrappedIv,
                kdfParams = Argon2KdfParams(
                    saltBase64 = kdfSalt,
                    memoryKb = prefs[Keys.CLOUD_KDF_MEMORY_KB] ?: Argon2KdfParams.DEFAULT_MEMORY_KB,
                    iterations = prefs[Keys.CLOUD_KDF_ITERATIONS] ?: Argon2KdfParams.DEFAULT_ITERATIONS,
                    parallelism = prefs[Keys.CLOUD_KDF_PARALLELISM] ?: Argon2KdfParams.DEFAULT_PARALLELISM
                )
            )
        } else null

        StoredVaultMetadata(
            deviceWrappedVaultKey = prefs[Keys.DEVICE_WRAPPED_VAULT_KEY],
            deviceWrappedVaultKeyIv = prefs[Keys.DEVICE_WRAPPED_VAULT_KEY_IV],
            vaultUuid = prefs[Keys.VAULT_UUID],
            localVaultVersion = prefs[Keys.LOCAL_VAULT_VERSION] ?: 0L,
            lastSyncedEpochMs = prefs[Keys.LAST_SYNCED_EPOCH_MS] ?: 0L,
            lastSyncedDeviceName = prefs[Keys.LAST_SYNCED_DEVICE_NAME],
            cloudWrappedKey = wrappedPayload
        )
    }

    suspend fun getVaultMetadataOnce(): StoredVaultMetadata = vaultMetadataFlow.first()

    suspend fun getAppLockCredentials(): AppLockCredentials {
        val prefs = dataStore.data.first()
        return AppLockCredentials(
            enabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            pinHashBase64 = prefs[Keys.PIN_HASH],
            pinSaltBase64 = prefs[Keys.PIN_SALT]
        )
    }

    suspend fun saveDeviceWrappedVaultKey(
        ciphertextBase64: String,
        ivBase64: String,
        vaultUuid: String
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.DEVICE_WRAPPED_VAULT_KEY] = ciphertextBase64
            prefs[Keys.DEVICE_WRAPPED_VAULT_KEY_IV] = ivBase64
            prefs[Keys.VAULT_UUID] = vaultUuid
        }
    }

    suspend fun saveCloudWrappedKey(payload: WrappedVaultKeyPayload) {
        dataStore.edit { prefs ->
            prefs[Keys.CLOUD_WRAPPED_VAULT_KEY] = payload.wrappedKeyBase64
            prefs[Keys.CLOUD_WRAPPED_VAULT_KEY_IV] = payload.wrappedKeyIvBase64
            prefs[Keys.CLOUD_KDF_SALT] = payload.kdfParams.saltBase64
            prefs[Keys.CLOUD_KDF_MEMORY_KB] = payload.kdfParams.memoryKb
            prefs[Keys.CLOUD_KDF_ITERATIONS] = payload.kdfParams.iterations
            prefs[Keys.CLOUD_KDF_PARALLELISM] = payload.kdfParams.parallelism
        }
    }

    suspend fun clearCloudRecoveryMetadata() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.CLOUD_WRAPPED_VAULT_KEY)
            prefs.remove(Keys.CLOUD_WRAPPED_VAULT_KEY_IV)
            prefs.remove(Keys.CLOUD_KDF_SALT)
            prefs.remove(Keys.CLOUD_KDF_MEMORY_KB)
            prefs.remove(Keys.CLOUD_KDF_ITERATIONS)
            prefs.remove(Keys.CLOUD_KDF_PARALLELISM)
            prefs[Keys.LOCAL_VAULT_VERSION] = 0L
            prefs[Keys.LAST_SYNCED_EPOCH_MS] = 0L
        }
    }

    suspend fun updateSyncProgress(version: Long, syncedEpochMs: Long, deviceName: String) {
        dataStore.edit { prefs ->
            prefs[Keys.LOCAL_VAULT_VERSION] = version
            prefs[Keys.LAST_SYNCED_EPOCH_MS] = syncedEpochMs
            prefs[Keys.LAST_SYNCED_DEVICE_NAME] = deviceName
        }
    }

    suspend fun saveAppLockPin(hashBase64: String, saltBase64: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hashBase64
            prefs[Keys.PIN_SALT] = saltBase64
            prefs[Keys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = enabled
            if (!enabled) {
                prefs[Keys.BIOMETRIC_ENABLED] = false
            }
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_LOCK_TIMEOUT] = timeout.name
        }
    }

    suspend fun setScreenshotProtectionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SCREENSHOT_PROTECTION] = enabled
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setAutoCloudBackupEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_CLOUD_BACKUP] = enabled
        }
    }

    suspend fun setPrivacyMaskCodesEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.PRIVACY_MASK_CODES] = enabled
        }
    }

    suspend fun setShowNextCodePreview(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SHOW_NEXT_CODE_PREVIEW] = enabled
        }
    }

    suspend fun setAccountSortMode(mode: AccountSortMode) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCOUNT_SORT_MODE] = mode.name
        }
    }
}
