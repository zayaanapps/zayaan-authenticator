package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemeMode
import com.example.data.model.AutoLockTimeout
import com.example.data.model.SecuritySettings
import com.example.security.BiometricAvailability
import com.example.ui.CloudStatusUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    settings: SecuritySettings,
    cloudStatus: CloudStatusUi,
    biometricAvailability: BiometricAvailability,
    onSetPin: (String) -> Unit,
    onDisableAppLock: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onSelectAutoLockTimeout: (AutoLockTimeout) -> Unit,
    onToggleScreenshotProtection: (Boolean) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onOpenCloudBackup: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Security & Settings") },
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cloud Backup & Recovery Hub Card
                SettingsSectionCard(title = "Encrypted Cloud Backup & Sync") {
                    ListItem(
                        headlineContent = { Text("Cloud Backup & Recovery") },
                        supportingContent = { Text(cloudStatus.statusDetail) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable(onClick = onOpenCloudBackup)
                            .testTag("settings_open_cloud_backup")
                    )
                }

                // App Lock & Privacy Controls
                SettingsSectionCard(title = "Vault Lock & Privacy") {
                    ListItem(
                        headlineContent = { Text("App Lock (PIN / Passcode)") },
                        supportingContent = {
                            Text(
                                if (settings.appLockEnabled) {
                                    "Protected with Argon2id-hashed passcode"
                                } else {
                                    "Require passcode or biometrics to open vault"
                                }
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.appLockEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) showPinDialog = true else onDisableAppLock()
                                },
                                modifier = Modifier.testTag("app_lock_switch")
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    if (settings.appLockEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Change PIN / Passcode") },
                            supportingContent = { Text("Update your local vault unlock passcode") },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .clickable { showPinDialog = true }
                                .testTag("change_pin_item")
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Biometric Unlock") },
                            supportingContent = {
                                Text(
                                    when (biometricAvailability) {
                                        BiometricAvailability.AVAILABLE -> "Use fingerprint or face unlock alongside your passcode"
                                        BiometricAvailability.NOT_ENROLLED -> "Enroll biometrics in Android Settings first"
                                        BiometricAvailability.UNAVAILABLE -> "Biometric hardware unavailable on this device"
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            },
                            trailingContent = {
                                Switch(
                                    checked = settings.biometricEnabled,
                                    enabled = biometricAvailability == BiometricAvailability.AVAILABLE,
                                    onCheckedChange = onToggleBiometric,
                                    modifier = Modifier.testTag("biometric_switch")
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Auto-Lock After Inactivity") },
                            supportingContent = { Text(settings.autoLockTimeout.displayName) },
                            leadingContent = {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .clickable { showTimeoutDialog = true }
                                .testTag("auto_lock_timeout_item")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Block Screenshots & Screen Recording") },
                        supportingContent = {
                            Text("Applies Android FLAG_SECURE to prevent code capture")
                        },
                        leadingContent = {
                            Icon(Icons.Default.NoPhotography, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.screenshotProtectionEnabled,
                                onCheckedChange = onToggleScreenshotProtection,
                                modifier = Modifier.testTag("screenshot_protection_switch")
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                // Appearance
                SettingsSectionCard(title = "Appearance") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Theme", style = MaterialTheme.typography.titleSmall)
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AppThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { onSelectThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, AppThemeMode.entries.size)
                                ) {
                                    Text(
                                        when (mode) {
                                            AppThemeMode.SYSTEM -> "System"
                                            AppThemeMode.LIGHT -> "Light"
                                            AppThemeMode.DARK -> "Dark"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Zero-Knowledge Cryptography Transparency Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Cryptographic Architecture",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "• Local vault: AES-256-GCM authenticated encryption per account\n" +
                                "• Device key protection: Android Keystore hardware-backed master key\n" +
                                "• Cloud recovery: Random 256-bit vault key wrapped with RFC 9106 Argon2id + AES-256-GCM\n" +
                                "• Zero-knowledge guarantee: Plaintext secrets and your recovery password never leave this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showPinDialog) {
        SetupPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { newPin ->
                showPinDialog = false
                onSetPin(newPin)
            }
        )
    }

    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("Auto-Lock Timeout") },
            text = {
                Column {
                    AutoLockTimeout.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectAutoLockTimeout(option)
                                    showTimeoutDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = settings.autoLockTimeout == option,
                                onClick = {
                                    onSelectAutoLockTimeout(option)
                                    showTimeoutDialog = false
                                }
                            )
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun SetupPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set App Lock Passcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter a 6+ character PIN or passcode. Stored locally as a salted Argon2id hash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    label = { Text("PIN or Passcode") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_pin_input")
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it; error = null },
                    label = { Text("Confirm Passcode") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_pin_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        pin.length < 6 -> error = "Use at least 6 characters for security."
                        pin != confirmPin -> error = "Passcodes do not match."
                        else -> onConfirm(pin)
                    }
                },
                modifier = Modifier.testTag("save_pin_confirm_button")
            ) {
                Text("Save Passcode")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
