package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.crypto.CryptoEngine
import com.example.crypto.PasswordStrength
import com.example.data.BackupOperationResult
import com.example.data.RestoreOperationResult
import com.example.data.cloud.GoogleAuthHelper
import com.example.data.model.CloudSyncState
import com.example.data.model.SecuritySettings
import com.example.ui.CloudStatusUi
import com.example.ui.theme.TimerCriticalDark
import com.example.ui.theme.TimerHealthyDark
import com.example.ui.theme.TimerWarningDark
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    cloudStatus: CloudStatusUi,
    settings: SecuritySettings,
    localAccountCount: Int,
    snackbarHostState: SnackbarHostState,
    onStopCloudListenerBeforeSignOut: () -> Unit,
    onAuthMessage: (String) -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onSetupRecoveryPassword: (String, Boolean, (BackupOperationResult) -> Unit) -> Unit,
    onChangeRecoveryPassword: (String, (BackupOperationResult) -> Unit) -> Unit,
    onManualBackup: (Boolean) -> Unit,
    onRestoreFromCloud: (String, Boolean, (RestoreOperationResult) -> Unit) -> Unit,
    onDeleteCloudBackup: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var showCreateRecoveryDialog by remember { mutableStateOf(false) }
    var showChangeRecoveryDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showForceReplaceConfirm by remember { mutableStateOf(false) }
    var showDeleteCloudConfirm by remember { mutableStateOf(false) }
    var pendingForceReplacePassword by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cloud Backup & Recovery") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Google Account Authentication Card (Jetpack Credential Manager)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        val user = cloudStatus.signedInUser
                        if (user == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column {
                                    Text("Encrypted Cloud Backup Off", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Sign in with Google to back up & sync your encrypted vault across devices.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    val activity = context as? Activity
                                    if (activity == null) {
                                        onAuthMessage("Unable to launch Google Sign-In from this context.")
                                        return@Button
                                    }
                                    isSigningIn = true
                                    GoogleAuthHelper.onGoogleSignInClicked(
                                        activity = activity,
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onAuthSuccess = {
                                            isSigningIn = false
                                            onAuthMessage("Signed in with Google")
                                        },
                                        onAuthError = { msg ->
                                            isSigningIn = false
                                            onAuthMessage(msg)
                                        },
                                        onAuthCancelled = {
                                            isSigningIn = false
                                        }
                                    )
                                },
                                enabled = !isSigningIn,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("google_sign_in_button")
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Sign in with Google")
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName?.takeIf { it.isNotBlank() } ?: "Google Account",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = user.email ?: user.uid,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        GoogleAuthHelper.signOut(
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onBeforeSignOut = onStopCloudListenerBeforeSignOut,
                                            onSignOutComplete = {
                                                onAuthMessage("Signed out — local offline codes remain safe")
                                            }
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("google_sign_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                }

                // 2. Existing Backup Conflict Banner (NEVER silently overwrite!)
                cloudStatus.conflict?.let { conflict ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = TimerWarningDark.copy(alpha = 0.14f)),
                        border = BorderStroke(1.dp, TimerWarningDark.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_conflict_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = TimerWarningDark)
                                Text(
                                    text = "Existing Encrypted Backup Detected",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = conflict.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Zayan Authenticator never silently overwrites an existing cloud vault. Restore it using your recovery password, or explicitly choose Replace.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { showRestoreDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("conflict_restore_button")
                                ) {
                                    Text("Restore / Merge")
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (settings.recoveryPasswordConfigured) {
                                            showForceReplaceConfirm = true
                                        } else {
                                            showCreateRecoveryDialog = true
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("conflict_replace_button")
                                ) {
                                    Text("Replace Cloud")
                                }
                            }
                        }
                    }
                }

                // 3. Vault Sync Status & Actions (when signed in)
                if (cloudStatus.signedInUser != null) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (cloudStatus.syncState == CloudSyncState.SYNCED) {
                                        TimerHealthyDark
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Backup & Sync Status", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = cloudStatus.statusDetail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (cloudStatus.isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                }
                            }

                            val remote = cloudStatus.remoteVault
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "Local vault: $localAccountCount active account(s)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (remote != null) {
                                        val updatedText = remote.updatedAt?.toDate()?.let {
                                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
                                        } ?: "Just now"
                                        Text(
                                            text = "Cloud vault: ${remote.accountCount} account(s) • Revision v${remote.vaultVersion}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Last backed up: $updatedText from ${remote.deviceName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "Cloud vault: No encrypted backup uploaded yet",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val lastSyncedMs = cloudStatus.localMetadata?.lastSyncedEpochMs ?: 0L
                                    if (lastSyncedMs > 0L) {
                                        val localTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(lastSyncedMs))
                                        Text(
                                            text = "This device last synced: $localTime",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            ListItem(
                                headlineContent = { Text("Automatic Encrypted Backup") },
                                supportingContent = { Text("Automatically upload encrypted changes after adding, editing, or deleting accounts") },
                                trailingContent = {
                                    Switch(
                                        checked = settings.autoCloudBackupEnabled,
                                        onCheckedChange = onToggleAutoBackup,
                                        modifier = Modifier.testTag("auto_backup_switch")
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider()

                            if (!settings.recoveryPasswordConfigured) {
                                Button(
                                    onClick = { showCreateRecoveryDialog = true },
                                    enabled = !cloudStatus.isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("setup_recovery_password_button")
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Create Recovery Password & Back Up")
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { onManualBackup(false) },
                                        enabled = !cloudStatus.isBusy,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("manual_backup_button")
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text("Back Up Now")
                                    }
                                    FilledTonalButton(
                                        onClick = { showRestoreDialog = true },
                                        enabled = !cloudStatus.isBusy && remote != null,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("manual_restore_button")
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text("Restore")
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showChangeRecoveryDialog = true },
                                    enabled = !cloudStatus.isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("change_recovery_password_button")
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Change Recovery Password")
                                }
                            }

                            if (remote != null && !settings.recoveryPasswordConfigured) {
                                FilledTonalButton(
                                    onClick = { showRestoreDialog = true },
                                    enabled = !cloudStatus.isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("restore_on_new_device_button")
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Restore Existing Cloud Vault")
                                }
                            }

                            if (remote != null) {
                                TextButton(
                                    onClick = { showDeleteCloudConfirm = true },
                                    enabled = !cloudStatus.isBusy,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .testTag("delete_cloud_backup_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Delete Cloud Backup", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // 4. Recovery Instructions & Zero-Knowledge Warning Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("How Zero-Knowledge Recovery Works", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "1. Zayan Authenticator generates a random 256-bit vault encryption key on your device.\n" +
                                "2. Your recovery password derives a key-wrapping key using Argon2id with a unique salt and wraps the 256-bit vault key via AES-256-GCM.\n" +
                                "3. Only the encrypted vault and wrapped key are uploaded to Firebase Cloud Firestore. Neither Firebase nor Google can read your TOTP secrets.\n" +
                                "4. To recover on a new device: install Zayan Authenticator, sign in with the same Google account, tap Restore, and enter your recovery password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Important: Your recovery password is NEVER stored or sent to any server. If you forget it, your encrypted cloud backup cannot be decrypted or recovered by anyone. Write it down and keep it in a safe place.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showCreateRecoveryDialog) {
        RecoveryPasswordSetupDialog(
            title = "Create Recovery Password",
            confirmLabel = "Encrypt & Back Up",
            onDismiss = { showCreateRecoveryDialog = false },
            onSubmit = { password ->
                showCreateRecoveryDialog = false
                onSetupRecoveryPassword(password, false) { result ->
                    if (result is BackupOperationResult.ConflictDetected) {
                        pendingForceReplacePassword = password
                    }
                }
            }
        )
    }

    if (showChangeRecoveryDialog) {
        RecoveryPasswordSetupDialog(
            title = "Change Recovery Password",
            confirmLabel = "Re-Wrap & Update",
            onDismiss = { showChangeRecoveryDialog = false },
            onSubmit = { newPassword ->
                showChangeRecoveryDialog = false
                onChangeRecoveryPassword(newPassword) {}
            }
        )
    }

    if (showRestoreDialog) {
        RestoreVaultDialog(
            hasLocalAccounts = localAccountCount > 0,
            onDismiss = { showRestoreDialog = false },
            onRestore = { password, merge ->
                onRestoreFromCloud(password, merge) { res ->
                    if (res is RestoreOperationResult.Success) {
                        showRestoreDialog = false
                    }
                }
            }
        )
    }

    if (showForceReplaceConfirm) {
        AlertDialog(
            onDismissRequest = { showForceReplaceConfirm = false },
            title = { Text("Replace Existing Cloud Backup?") },
            text = {
                Text(
                    "This will permanently replace the existing cloud backup (${cloudStatus.remoteVault?.accountCount ?: 0} accounts from ${cloudStatus.remoteVault?.deviceName ?: "another device"}) with the $localAccountCount account(s) currently on this device. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForceReplaceConfirm = false
                        val pendingPw = pendingForceReplacePassword
                        pendingForceReplacePassword = null
                        if (!pendingPw.isNullOrBlank()) {
                            onSetupRecoveryPassword(pendingPw, true) {}
                        } else {
                            onManualBackup(true)
                        }
                    }
                ) {
                    Text("Yes, Replace Backup", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForceReplaceConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteCloudConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteCloudConfirm = false },
            title = { Text("Delete Cloud Backup?") },
            text = {
                Text("This permanently deletes your encrypted vault from Cloud Firestore. Your local authenticator accounts on this device will not be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteCloudConfirm = false
                        onDeleteCloudBackup()
                    }
                ) {
                    Text("Delete Cloud Data", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCloudConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RecoveryPasswordSetupDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var acknowledgedLossRisk by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val strength = remember(password) { CryptoEngine.evaluatePasswordStrength(password) }
    val strengthColor = when (strength) {
        PasswordStrength.WEAK -> TimerCriticalDark
        PasswordStrength.FAIR -> TimerWarningDark
        PasswordStrength.STRONG, PasswordStrength.EXCELLENT -> TimerHealthyDark
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Used locally with Argon2id to wrap your 256-bit vault key. Never uploaded or stored on any server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Recovery Password (10+ chars)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recovery_password_input")
                )

                if (password.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { strength.score },
                        color = strengthColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = strength.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = strengthColor
                    )
                }

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Confirm Recovery Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_recovery_password_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = acknowledgedLossRisk,
                        onCheckedChange = { acknowledgedLossRisk = it },
                        modifier = Modifier.testTag("acknowledge_recovery_risk_checkbox")
                    )
                    Text(
                        text = "I understand that if I forget this password, my cloud backup cannot be decrypted or recovered.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        password.length < 10 -> error = "Use at least 10 characters for a strong recovery password."
                        password != confirmPassword -> error = "Passwords do not match."
                        !acknowledgedLossRisk -> error = "Please confirm you understand the recovery password warning."
                        else -> onSubmit(password)
                    }
                },
                modifier = Modifier.testTag("confirm_recovery_setup_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RestoreVaultDialog(
    hasLocalAccounts: Boolean,
    onDismiss: () -> Unit,
    onRestore: (String, Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var mergeWithLocal by remember { mutableStateOf(hasLocalAccounts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Encrypted Cloud Vault") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter your recovery password to unwrap your 256-bit vault key via Argon2id and decrypt your accounts locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Recovery Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_password_input")
                )
                if (hasLocalAccounts) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = mergeWithLocal,
                            onCheckedChange = { mergeWithLocal = it }
                        )
                        Text(
                            text = "Merge with existing local accounts on this device (unchecked replaces local accounts)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRestore(password, mergeWithLocal) },
                enabled = password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.testTag("confirm_restore_button")
            ) {
                Text("Decrypt & Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
