package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.crypto.OtpAuthUriParser
import com.example.crypto.QrCodeGenerator
import com.example.data.model.AuthenticatorAccount
import com.example.ui.components.IconTile
import com.example.ui.components.IssuerBadge
import com.example.ui.components.VaultSectionHeader
import com.example.ui.theme.TimerWarningDark

/**
 * Google Authenticator-inspired Transfer Accounts screen:
 * - Export selected accounts as standard otpauth-migration:// QR codes (batches of up to 10)
 * - Import accounts from another device by scanning or picking an export QR code
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferAccountsScreen(
    accounts: List<AuthenticatorAccount>,
    onScanExportQr: () -> Unit,
    onImportExportQrImage: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Export, 1 = Import
    var selectedIds by remember(accounts) { mutableStateOf(accounts.map { it.id }.toSet()) }
    var activeExportUris by remember { mutableStateOf<List<String>?>(null) }
    var currentBatchIndex by remember { mutableIntStateOf(0) }

    BackHandler(enabled = activeExportUris != null) {
        activeExportUris = null
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
                            icon = Icons.Default.SwapHoriz,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 34.dp,
                            iconSize = 18.dp
                        )
                        Text("Transfer Accounts")
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeExportUris != null) {
                                activeExportUris = null
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
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
                val exportUris = activeExportUris
                if (exportUris != null) {
                    ExportQrDisplayView(
                        exportUris = exportUris,
                        currentBatchIndex = currentBatchIndex,
                        selectedCount = selectedIds.size,
                        onPrevious = { if (currentBatchIndex > 0) currentBatchIndex-- },
                        onNext = { if (currentBatchIndex < exportUris.lastIndex) currentBatchIndex++ },
                        onDone = { activeExportUris = null }
                    )
                } else {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("transfer_tab_export")
                        ) {
                            Text("Export QR")
                        }
                        SegmentedButton(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("transfer_tab_import")
                        ) {
                            Text("Import QR")
                        }
                    }

                    if (selectedTab == 0) {
                        ExportSelectionSection(
                            accounts = accounts,
                            selectedIds = selectedIds,
                            onToggleAccount = { id ->
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            },
                            onSelectAll = { selectedIds = accounts.map { it.id }.toSet() },
                            onClearAll = { selectedIds = emptySet() },
                            onGenerateQr = {
                                val chosen = accounts.filter { it.id in selectedIds }
                                if (chosen.isNotEmpty()) {
                                    currentBatchIndex = 0
                                    activeExportUris = OtpAuthUriParser.buildMigrationUris(chosen)
                                }
                            }
                        )
                    } else {
                        ImportTransferSection(
                            onScanExportQr = onScanExportQr,
                            onImportExportQrImage = onImportExportQrImage
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ExportSelectionSection(
    accounts: List<AuthenticatorAccount>,
    selectedIds: Set<String>,
    onToggleAccount: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onGenerateQr: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            IconTile(
                icon = Icons.Default.Security,
                tint = MaterialTheme.colorScheme.primary,
                size = 40.dp,
                iconSize = 20.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Compatible with Google Authenticator",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Select accounts to generate offline transfer QR code(s). Scan them on your new phone using Zayaan Authenticator or Google Authenticator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (accounts.isEmpty()) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp)
            ) {
                IconTile(
                    icon = Icons.Default.QrCode2,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 48.dp,
                    iconSize = 26.dp
                )
                Text("No accounts to export", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Add at least one authenticator account first to export via QR code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        VaultSectionHeader(
            title = "Select Accounts (${selectedIds.size}/${accounts.size})",
            icon = Icons.Default.CheckCircle
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSelectAll) { Text("Select All") }
            TextButton(onClick = onClearAll) { Text("Clear") }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            accounts.forEachIndexed { index, account ->
                val isSelected = account.id in selectedIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleAccount(account.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("export_select_${account.id}")
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleAccount(account.id) }
                    )
                    IssuerBadge(
                        issuer = account.issuer,
                        accountName = account.accountName,
                        category = account.category,
                        size = 38.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (account.displaySubtitle.isNotBlank()) {
                            Text(
                                text = account.displaySubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (index < accounts.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    Button(
        onClick = onGenerateQr,
        enabled = selectedIds.isNotEmpty(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("generate_export_qr_button")
    ) {
        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Generate Transfer QR (${selectedIds.size})")
    }
}

@Composable
private fun ExportQrDisplayView(
    exportUris: List<String>,
    currentBatchIndex: Int,
    selectedCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit
) {
    val activeUri = exportUris.getOrNull(currentBatchIndex).orEmpty()
    val qrBitmap = remember(activeUri) {
        QrCodeGenerator.generateQrBitmap(activeUri, sizePx = 760)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export_qr_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Scan on Your New Device",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (exportUris.size > 1) {
                    "QR Code ${currentBatchIndex + 1} of ${exportUris.size} • Exporting $selectedCount accounts"
                } else {
                    "Exporting $selectedCount account(s) via Google Authenticator format"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                shadowElevation = 3.dp
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Account transfer QR code ${currentBatchIndex + 1} of ${exportUris.size}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(260.dp)
                    )
                }
            }

            if (exportUris.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = currentBatchIndex > 0,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Previous")
                    }
                    Button(
                        onClick = onNext,
                        enabled = currentBatchIndex < exportUris.lastIndex,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Next QR")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = TimerWarningDark.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, TimerWarningDark.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = TimerWarningDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "This QR code contains your unencrypted account keys. Close this screen immediately after scanning on your other device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            FilledTonalButton(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Finish Export")
            }
        }
    }
}

@Composable
private fun ImportTransferSection(
    onScanExportQr: () -> Unit,
    onImportExportQrImage: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconTile(
                    icon = Icons.Default.QrCodeScanner,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 44.dp,
                    iconSize = 22.dp
                )
                Column {
                    Text(
                        text = "Import From Another Phone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Supports Google Authenticator batch export QR codes and standard TOTP QR codes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onScanExportQr,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("transfer_scan_qr_button")
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Transfer QR Code")
            }

            OutlinedButton(
                onClick = onImportExportQrImage,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("transfer_import_image_button")
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import QR Code Image")
            }
        }
    }
}
