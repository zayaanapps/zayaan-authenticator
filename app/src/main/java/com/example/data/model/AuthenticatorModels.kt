package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.UUID

enum class HashAlgorithm(val label: String, val jcaName: String, val uriValue: String) {
    SHA1("SHA-1", "HmacSHA1", "SHA1"),
    SHA256("SHA-256", "HmacSHA256", "SHA256"),
    SHA512("SHA-512", "HmacSHA512", "SHA512");

    companion object {
        fun fromRaw(raw: String?): HashAlgorithm {
            val normalized = raw?.trim()?.uppercase()?.replace("-", "") ?: return SHA1
            return entries.firstOrNull { it.uriValue == normalized || it.name == normalized } ?: SHA1
        }
    }
}

enum class AccountCategory(val displayName: String) {
    GENERAL("General"),
    WORK("Work"),
    PERSONAL("Personal"),
    FINANCE("Finance"),
    SOCIAL("Social"),
    DEVELOPER("Developer");

    companion object {
        fun fromRaw(raw: String?): AccountCategory {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GENERAL
        }
    }
}

data class AuthenticatorAccount(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String = "",
    val accountName: String = "",
    val secretBase32: String = "",
    val algorithm: HashAlgorithm = HashAlgorithm.SHA1,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val category: AccountCategory = AccountCategory.GENERAL,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
) {
    val displayTitle: String
        get() = issuer.ifBlank { accountName.ifBlank { "Account" } }

    val displaySubtitle: String
        get() = if (issuer.isNotBlank() && accountName.isNotBlank()) accountName else ""
}

enum class AccountSortMode(val displayName: String) {
    CUSTOM("Custom Order"),
    NAME_ASC("Name (A–Z)"),
    RECENT("Recently Added");

    companion object {
        fun fromRaw(raw: String?): AccountSortMode =
            entries.firstOrNull { it.name == raw } ?: CUSTOM
    }
}

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromRaw(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class AutoLockTimeout(val displayName: String, val durationMs: Long) {
    IMMEDIATE("Immediately", 0L),
    THIRTY_SECONDS("30 seconds", 30_000L),
    ONE_MINUTE("1 minute", 60_000L),
    FIVE_MINUTES("5 minutes", 300_000L),
    NEVER("Never", Long.MAX_VALUE);

    companion object {
        fun fromRaw(raw: String?): AutoLockTimeout =
            entries.firstOrNull { it.name == raw } ?: ONE_MINUTE
    }
}

data class SecuritySettings(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
    val screenshotProtectionEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val autoCloudBackupEnabled: Boolean = true,
    val recoveryPasswordConfigured: Boolean = false,
    val privacyMaskCodesEnabled: Boolean = false,
    val showNextCodePreview: Boolean = true,
    val sortMode: AccountSortMode = AccountSortMode.CUSTOM
)

enum class CloudSyncState {
    OFFLINE_LOCAL_ONLY,
    SIGNED_IN_SETUP_REQUIRED,
    SYNCING,
    SYNCED,
    PENDING_BACKUP,
    CONFLICT_DETECTED,
    ERROR
}

data class CloudConflictInfo(
    val remoteVault: EncryptedCloudVault,
    val reason: String,
    val isNewDeviceOrDifferentVault: Boolean
)

/**
 * Zero-knowledge encrypted vault stored in Firestore at /users/{userId}/vaults/primary.
 * Contains ONLY ciphertext and Argon2id KDF parameters. Never stores plaintext TOTP secrets
 * or the user's recovery password.
 */
@Keep
@IgnoreExtraProperties
data class EncryptedCloudVault(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vaultUuid: String = "",
    val schemaVersion: Long = 1L,
    val vaultVersion: Long = 1L,
    val accountCount: Long = 0L,
    val deviceName: String = "",
    val kdfAlgorithm: String = "ARGON2ID",
    val kdfSalt: String = "",
    val kdfMemoryKb: Long = 32768L,
    val kdfIterations: Long = 3L,
    val kdfParallelism: Long = 1L,
    val wrappedVaultKey: String = "",
    val wrappedVaultKeyIv: String = "",
    val encryptedVault: String = "",
    val encryptedVaultIv: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
