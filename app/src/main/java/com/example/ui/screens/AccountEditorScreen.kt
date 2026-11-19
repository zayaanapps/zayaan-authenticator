package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.crypto.TotpGenerator
import com.example.data.model.AccountCategory
import com.example.data.model.AuthenticatorAccount
import com.example.data.model.HashAlgorithm
import com.example.ui.theme.TotpCodeTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorScreen(
    existingAccount: AuthenticatorAccount?,
    currentTimeMillis: Long,
    onSaveNew: (
        issuer: String,
        accountName: String,
        secret: String,
        algorithm: HashAlgorithm,
        digits: Int,
        periodSeconds: Int,
        category: AccountCategory
    ) -> Unit,
    onSaveEdit: (AuthenticatorAccount) -> Unit,
    onDeleteExisting: (AuthenticatorAccount) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isEditMode = existingAccount != null
    var issuer by remember(existingAccount) { mutableStateOf(existingAccount?.issuer ?: "") }
    var accountName by remember(existingAccount) { mutableStateOf(existingAccount?.accountName ?: "") }
    var secretKey by remember(existingAccount) { mutableStateOf(existingAccount?.secretBase32 ?: "") }
    var showSecret by remember { mutableStateOf(false) }
    var algorithm by remember(existingAccount) { mutableStateOf(existingAccount?.algorithm ?: HashAlgorithm.SHA1) }
    var digits by remember(existingAccount) { mutableIntStateOf(existingAccount?.digits ?: 6) }
    var periodSeconds by remember(existingAccount) { mutableIntStateOf(existingAccount?.periodSeconds ?: 30) }
    var category by remember(existingAccount) { mutableStateOf(existingAccount?.category ?: AccountCategory.GENERAL) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val normalizedSecret = TotpGenerator.normalizeBase32(secretKey)
    val isSecretValid = TotpGenerator.isValidBase32(normalizedSecret)
    val canSave = (issuer.isNotBlank() || accountName.isNotBlank()) && isSecretValid

    val previewCode = remember(normalizedSecret, algorithm, digits, periodSeconds, currentTimeMillis / 1000L) {
        if (!isSecretValid) null
        else try {
            TotpGenerator.formatCodeForDisplay(
                TotpGenerator.generateCode(
                    secretBase32 = normalizedSecret,
                    timeSeconds = currentTimeMillis / 1000L,
                    periodSeconds = periodSeconds,
                    digits = digits,
                    algorithm = algorithm
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Account" else "Manual Setup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("delete_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete account",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    label = { Text("Service / Issuer (e.g. Google, GitHub, Microsoft)") },
                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("issuer_input")
                )

                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("Account Name / Email") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_name_input")
                )

                if (!isEditMode) {
                    OutlinedTextField(
                        value = secretKey,
                        onValueChange = { secretKey = it },
                        label = { Text("Secret Key (Base32)") },
                        placeholder = { Text("e.g. JBSW Y3DP EHPK 3PXP") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showSecret) "Hide secret key" else "Show secret key"
                                )
                            }
                        },
                        visualTransformation = if (showSecret) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        isError = secretKey.isNotEmpty() && !isSecretValid,
                        supportingText = {
                            if (secretKey.isNotEmpty() && !isSecretValid) {
                                Text("Enter a valid Base32 key (A–Z, 2–7, at least 8 characters).")
                            } else {
                                Text("Spaces and dashes are ignored automatically. Encrypted with AES-256-GCM.")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("secret_key_input")
                    )
                }

                // Category selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        AccountCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.displayName) }
                            )
                        }
                    }
                }

                if (!isEditMode) {
                    // Hash Algorithm selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Hash Algorithm (RFC 6238)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            HashAlgorithm.entries.forEachIndexed { index, algo ->
                                SegmentedButton(
                                    selected = algorithm == algo,
                                    onClick = { algorithm = algo },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = HashAlgorithm.entries.size
                                    )
                                ) {
                                    Text(algo.label)
                                }
                            }
                        }
                    }

                    // Digits & Period selectors
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Digits",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val digitOptions = listOf(6, 8)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                digitOptions.forEachIndexed { index, d ->
                                    SegmentedButton(
                                        selected = digits == d,
                                        onClick = { digits = d },
                                        shape = SegmentedButtonDefaults.itemShape(index, digitOptions.size)
                                    ) {
                                        Text("$d digits")
                                    }
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Interval",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val periodOptions = listOf(30, 60)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                periodOptions.forEachIndexed { index, p ->
                                    SegmentedButton(
                                        selected = periodSeconds == p,
                                        onClick = { periodSeconds = p },
                                        shape = SegmentedButtonDefaults.itemShape(index, periodOptions.size)
                                    ) {
                                        Text("${p}s")
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Verification Code Preview
                previewCode?.let { liveCode ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Live Code Preview (${algorithm.label} • ${digits}d • ${periodSeconds}s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = liveCode,
                                style = TotpCodeTextStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (existingAccount != null) {
                            onSaveEdit(
                                existingAccount.copy(
                                    issuer = issuer.trim().ifBlank { "Account" },
                                    accountName = accountName.trim(),
                                    category = category
                                )
                            )
                        } else {
                            onSaveNew(
                                issuer,
                                accountName,
                                normalizedSecret,
                                algorithm,
                                digits,
                                periodSeconds,
                                category
                            )
                        }
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_account_button")
                ) {
                    Text(if (isEditMode) "Save Changes" else "Encrypt & Save Account")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm && existingAccount != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${existingAccount.displayTitle}?") },
            text = {
                Text("This permanently removes the verification code from this device (and syncs removal if cloud backup is active).")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteExisting(existingAccount)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
