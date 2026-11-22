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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.crypto.OtpAuthUriParser
import com.example.crypto.TotpGenerator
import com.example.data.model.AccountCategory
import com.example.data.model.AuthenticatorAccount
import com.example.data.model.HashAlgorithm
import com.example.ui.components.AccountQrDialog
import com.example.ui.components.IconTile
import com.example.ui.components.IssuerBadge
import com.example.ui.components.SensitiveClipboardHelper
import com.example.ui.components.VaultSectionHeader
import com.example.ui.components.categoryIcon
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
    val context = LocalContext.current
    val isEditMode = existingAccount != null
    var issuer by remember(existingAccount) { mutableStateOf(existingAccount?.issuer ?: "") }
    var accountName by remember(existingAccount) { mutableStateOf(existingAccount?.accountName ?: "") }
    var secretKey by remember(existingAccount) { mutableStateOf(existingAccount?.secretBase32 ?: "") }
    var showSecret by remember { mutableStateOf(false) }
    var algorithm by remember(existingAccount) { mutableStateOf(existingAccount?.algorithm ?: HashAlgorithm.SHA1) }
    var digits by remember(existingAccount) { mutableIntStateOf(existingAccount?.digits ?: 6) }
    var periodSeconds by remember(existingAccount) { mutableIntStateOf(existingAccount?.periodSeconds ?: 30) }
    var category by remember(existingAccount) { mutableStateOf(existingAccount?.category ?: AccountCategory.GENERAL) }
    var isPinned by remember(existingAccount) { mutableStateOf(existingAccount?.isPinned ?: false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

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

    fun applySmartPaste(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            when (val parsed = OtpAuthUriParser.parse(trimmed)) {
                is OtpAuthUriParser.ParseResult.Success -> {
                    val acc = parsed.accounts.firstOrNull()
                    if (acc != null) {
                        issuer = acc.issuer
                        accountName = acc.accountName
                        secretKey = acc.secretBase32
                        algorithm = acc.algorithm
                        digits = acc.digits
                        periodSeconds = acc.periodSeconds
                        return
                    }
                }
                else -> Unit
            }
        }
        secretKey = trimmed
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconTile(
                            icon = if (isEditMode) Icons.Default.Tune else Icons.Default.Key,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 34.dp,
                            iconSize = 18.dp
                        )
                        Text(if (isEditMode) "Edit Account" else "Manual Setup")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(
                            onClick = { showQrDialog = true },
                            modifier = Modifier.testTag("editor_show_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "Show account QR code",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Live Brand Badge + Verification Preview Header Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = 1.2.dp,
                        color = if (previewCode != null) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        IssuerBadge(
                            issuer = issuer.ifBlank { "Service" },
                            accountName = accountName,
                            category = category,
                            size = 50.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = issuer.ifBlank { accountName.ifBlank { "New Authenticator" } },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = accountName.ifBlank { "${algorithm.label} • ${digits} digits • ${periodSeconds}s" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            previewCode?.let { liveCode ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = liveCode,
                                    style = TotpCodeTextStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Account Identity Fields Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        VaultSectionHeader(
                            title = "Account Details",
                            icon = Icons.Default.VerifiedUser
                        )
                        OutlinedTextField(
                            value = issuer,
                            onValueChange = { issuer = it },
                            label = { Text("Service / Issuer (e.g. Google, GitHub)") },
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
                                onValueChange = { value ->
                                    if (value.startsWith("otpauth://", ignoreCase = true)) {
                                        applySmartPaste(value)
                                    } else {
                                        secretKey = value
                                    }
                                },
                                label = { Text("Secret Key (Base32 or otpauth://)") },
                                placeholder = { Text("e.g. JBSW Y3DP EHPK 3PXP") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                SensitiveClipboardHelper.readPlainText(context)?.let { pasted ->
                                                    applySmartPaste(pasted)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Paste secret key or otpauth link"
                                            )
                                        }
                                        IconButton(onClick = { showSecret = !showSecret }) {
                                            Icon(
                                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showSecret) "Hide secret key" else "Show secret key"
                                            )
                                        }
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
                                        Text("Paste a Base32 key or full otpauth:// URI. Encrypted locally with AES-256-GCM.")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("secret_key_input")
                            )
                        }
                    }
                }

                // Category & Pin Organization Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        VaultSectionHeader(
                            title = "Organization",
                            icon = Icons.Default.Category
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
                                    leadingIcon = {
                                        Icon(
                                            imageVector = categoryIcon(cat),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(cat.displayName) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = category == cat,
                                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                    )
                                )
                            }
                        }

                        if (isEditMode) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    IconTile(
                                        icon = Icons.Default.PushPin,
                                        tint = MaterialTheme.colorScheme.primary,
                                        size = 36.dp,
                                        iconSize = 18.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Pin to Top", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Always keep this account at the top of your dashboard",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isPinned,
                                        onCheckedChange = { isPinned = it }
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isEditMode) {
                    // Advanced RFC 6238 Parameters Card
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            VaultSectionHeader(
                                title = "TOTP Parameters (RFC 6238)",
                                icon = Icons.Default.Tune
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Hash Algorithm",
                                    style = MaterialTheme.typography.labelMedium,
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

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Digits",
                                        style = MaterialTheme.typography.labelMedium,
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
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Interval",
                                        style = MaterialTheme.typography.labelMedium,
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
                    }
                }

                Button(
                    onClick = {
                        if (existingAccount != null) {
                            onSaveEdit(
                                existingAccount.copy(
                                    issuer = issuer.trim().ifBlank { "Account" },
                                    accountName = accountName.trim(),
                                    category = category,
                                    isPinned = isPinned
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
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEditMode) "Save Changes" else "Encrypt & Save Account")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showQrDialog && existingAccount != null) {
        AccountQrDialog(
            account = existingAccount,
            onDismiss = { showQrDialog = false }
        )
    }

    if (showDeleteConfirm && existingAccount != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                IconTile(
                    icon = Icons.Default.DeleteOutline,
                    tint = MaterialTheme.colorScheme.error
                )
            },
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
