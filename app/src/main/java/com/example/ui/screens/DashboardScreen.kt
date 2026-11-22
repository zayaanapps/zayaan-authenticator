package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.crypto.TotpGenerator
import com.example.data.model.AccountCategory
import com.example.data.model.AccountSortMode
import com.example.data.model.AuthenticatorAccount
import com.example.data.model.CloudSyncState
import com.example.data.model.SecuritySettings
import com.example.ui.CloudStatusUi
import com.example.ui.components.AccountQrDialog
import com.example.ui.components.IconTile
import com.example.ui.components.IssuerBadge
import com.example.ui.components.SensitiveClipboardHelper
import com.example.ui.components.categoryIcon
import com.example.ui.theme.TimerCriticalDark
import com.example.ui.theme.TimerHealthyDark
import com.example.ui.theme.TimerWarningDark
import com.example.ui.theme.TotpCodeTextStyle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    accounts: List<AuthenticatorAccount>,
    totalAccountCount: Int,
    currentTimeMillis: Long,
    searchQuery: String,
    selectedCategory: AccountCategory?,
    isReorderMode: Boolean,
    cloudStatus: CloudStatusUi,
    securitySettings: SecuritySettings,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (AccountCategory?) -> Unit,
    onSelectSortMode: (AccountSortMode) -> Unit,
    onTogglePrivacyMask: (Boolean) -> Unit,
    onToggleReorderMode: () -> Unit,
    onTogglePinAccount: (AuthenticatorAccount) -> Unit,
    onMoveAccount: (String, Boolean) -> Unit,
    onCopyCode: (AuthenticatorAccount, String) -> Unit,
    onOpenQrScanner: () -> Unit,
    onImportQrFromPhoto: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onOpenTransferAccounts: () -> Unit,
    onEditAccount: (AuthenticatorAccount) -> Unit,
    onDeleteAccount: (AuthenticatorAccount) -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onOpenCloudBackup: () -> Unit,
    onLockNow: () -> Unit
) {
    var showSearchBar by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var accountPendingDelete by remember { mutableStateOf<AuthenticatorAccount?>(null) }
    var accountShowingQr by remember { mutableStateOf<AuthenticatorAccount?>(null) }
    var recentlyCopiedId by remember { mutableStateOf<String?>(null) }
    var revealedAccountId by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(recentlyCopiedId) {
        if (recentlyCopiedId != null) {
            delay(1800L)
            recentlyCopiedId = null
        }
    }

    LaunchedEffect(revealedAccountId) {
        if (revealedAccountId != null) {
            delay(12_000L)
            revealedAccountId = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconTile(
                            icon = Icons.Default.Shield,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 36.dp,
                            iconSize = 20.dp
                        )
                        Column {
                            Text(
                                text = "Zayaan Authenticator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(if (totalAccountCount == 1) "1 account" else "$totalAccountCount accounts")
                                    if (securitySettings.sortMode != AccountSortMode.CUSTOM) {
                                        append(" • ${securitySettings.sortMode.displayName}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (totalAccountCount > 0) {
                        IconButton(
                            onClick = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) onSearchQueryChange("")
                            },
                            modifier = Modifier.testTag("toggle_search_button")
                        ) {
                            Icon(
                                imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search accounts",
                                tint = if (showSearchBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                val next = !securitySettings.privacyMaskCodesEnabled
                                onTogglePrivacyMask(next)
                                if (!next) revealedAccountId = null
                            },
                            modifier = Modifier.testTag("toggle_privacy_mask_button")
                        ) {
                            Icon(
                                imageVector = if (securitySettings.privacyMaskCodesEnabled) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (securitySettings.privacyMaskCodesEnabled) {
                                    "Disable tap-to-reveal privacy mode"
                                } else {
                                    "Enable tap-to-reveal privacy mode"
                                },
                                tint = if (securitySettings.privacyMaskCodesEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    if (securitySettings.appLockEnabled) {
                        IconButton(
                            onClick = onLockNow,
                            modifier = Modifier.testTag("lock_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock vault now"
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showTopOverflowMenu = true },
                            modifier = Modifier.testTag("dashboard_more_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More vault options"
                            )
                        }
                        DropdownMenu(
                            expanded = showTopOverflowMenu,
                            onDismissRequest = { showTopOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Transfer Accounts (QR)") },
                                leadingIcon = {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    onOpenTransferAccounts()
                                },
                                modifier = Modifier.testTag("menu_transfer_accounts")
                            )
                            if (totalAccountCount > 0) {
                                DropdownMenuItem(
                                    text = { Text(if (isReorderMode) "Done Organizing" else "Organize & Reorder") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isReorderMode) Icons.Default.Check else Icons.Default.SwapVert,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        onToggleReorderMode()
                                    },
                                    modifier = Modifier.testTag("toggle_reorder_button")
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort: ${securitySettings.sortMode.displayName}") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        showSortDialog = true
                                    },
                                    modifier = Modifier.testTag("menu_sort_accounts")
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Cloud Backup & Sync") },
                                leadingIcon = {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    onOpenCloudBackup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Security & Settings") },
                                leadingIcon = {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    onOpenSecuritySettings()
                                },
                                modifier = Modifier.testTag("open_security_settings_button")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Account", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_account_fab")
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
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                // Cloud Backup & Sync Status Strip
                CloudStatusStrip(
                    cloudStatus = cloudStatus,
                    onClick = onOpenCloudBackup,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                // Search bar
                AnimatedVisibility(visible = showSearchBar) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search issuer, email, or category...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("search_accounts_input")
                    )
                }

                // Category filter chips with crisp leading icons & borders
                if (totalAccountCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VaultCategoryChip(
                            label = "All",
                            icon = categoryIcon(null),
                            selected = selectedCategory == null,
                            onClick = { onSelectCategory(null) }
                        )
                        AccountCategory.entries.forEach { category ->
                            VaultCategoryChip(
                                label = category.displayName,
                                icon = categoryIcon(category),
                                selected = selectedCategory == category,
                                onClick = {
                                    onSelectCategory(if (selectedCategory == category) null else category)
                                }
                            )
                        }
                    }
                }

                if (isReorderMode && accounts.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Organize Mode: Reorder, pin, edit, or remove accounts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            TextButton(onClick = onToggleReorderMode) {
                                Text("Done")
                            }
                        }
                    }
                }

                if (accounts.isEmpty()) {
                    EmptyVaultState(
                        hasAnyAccounts = totalAccountCount > 0,
                        onScanQr = onOpenQrScanner,
                        onImportQrImage = onImportQrFromPhoto,
                        onManualEntry = onOpenManualEntry,
                        onOpenCloudRestore = onOpenCloudBackup
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("accounts_list")
                    ) {
                        itemsIndexed(
                            items = accounts,
                            key = { _, item -> item.id }
                        ) { index, account ->
                            val timeSeconds = currentTimeMillis / 1000L
                            val periodStep = Math.floorDiv(timeSeconds, account.periodSeconds.toLong())
                            val code = remember(
                                account.secretBase32,
                                account.algorithm,
                                account.digits,
                                account.periodSeconds,
                                periodStep
                            ) {
                                try {
                                    TotpGenerator.generateCode(
                                        secretBase32 = account.secretBase32,
                                        timeSeconds = timeSeconds,
                                        periodSeconds = account.periodSeconds,
                                        digits = account.digits,
                                        algorithm = account.algorithm
                                    )
                                } catch (_: Exception) {
                                    "------"
                                }
                            }
                            val nextCode = remember(
                                account.secretBase32,
                                account.algorithm,
                                account.digits,
                                account.periodSeconds,
                                periodStep
                            ) {
                                try {
                                    TotpGenerator.generateCode(
                                        secretBase32 = account.secretBase32,
                                        timeSeconds = timeSeconds + account.periodSeconds,
                                        periodSeconds = account.periodSeconds,
                                        digits = account.digits,
                                        algorithm = account.algorithm
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            val remainingSeconds = TotpGenerator.remainingSeconds(
                                timeSeconds = timeSeconds,
                                periodSeconds = account.periodSeconds
                            )
                            val progress = TotpGenerator.remainingProgress(
                                timeMillis = currentTimeMillis,
                                periodSeconds = account.periodSeconds
                            )
                            val isMasked = securitySettings.privacyMaskCodesEnabled &&
                                revealedAccountId != account.id

                            AuthenticatorAccountCard(
                                account = account,
                                rawCode = code,
                                nextRawCode = nextCode,
                                remainingSeconds = remainingSeconds,
                                progress = progress,
                                isCopied = recentlyCopiedId == account.id,
                                isMasked = isMasked,
                                showNextCodePreview = securitySettings.showNextCodePreview &&
                                    remainingSeconds <= 7 &&
                                    !isMasked &&
                                    nextCode != null,
                                isReorderMode = isReorderMode,
                                canMoveUp = index > 0,
                                canMoveDown = index < accounts.lastIndex,
                                onCardTap = {
                                    if (isMasked) {
                                        revealedAccountId = account.id
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else {
                                        SensitiveClipboardHelper.copySensitiveCode(
                                            context = context,
                                            label = "${account.displayTitle} code",
                                            code = code
                                        )
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        recentlyCopiedId = account.id
                                        onCopyCode(account, code)
                                    }
                                },
                                onCopyCurrentCode = {
                                    SensitiveClipboardHelper.copySensitiveCode(
                                        context = context,
                                        label = "${account.displayTitle} code",
                                        code = code
                                    )
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    recentlyCopiedId = account.id
                                    onCopyCode(account, code)
                                },
                                onCopyNextCode = { upcoming ->
                                    SensitiveClipboardHelper.copySensitiveCode(
                                        context = context,
                                        label = "${account.displayTitle} next code",
                                        code = upcoming
                                    )
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    recentlyCopiedId = account.id
                                    onCopyCode(account, upcoming)
                                },
                                onTogglePin = { onTogglePinAccount(account) },
                                onShowQr = { accountShowingQr = account },
                                onMoveUp = { onMoveAccount(account.id, true) },
                                onMoveDown = { onMoveAccount(account.id, false) },
                                onEdit = { onEditAccount(account) },
                                onDelete = { accountPendingDelete = account }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    IconTile(
                        icon = Icons.Default.Add,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    Column {
                        Text(
                            text = "Add Authenticator Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Choose how to add or transfer your 2FA keys",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AddOptionCard(
                    title = "Scan a QR Code",
                    subtitle = "Supports standard TOTP & Google Authenticator export QR codes",
                    icon = Icons.Default.QrCodeScanner,
                    accentColor = MaterialTheme.colorScheme.primary,
                    testTag = "sheet_scan_qr",
                    onClick = {
                        showAddSheet = false
                        onOpenQrScanner()
                    }
                )
                AddOptionCard(
                    title = "Import QR Code Image",
                    subtitle = "Pick a saved QR screenshot via zero-permission Photo Picker",
                    icon = Icons.Default.Image,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    testTag = "sheet_import_qr_image",
                    onClick = {
                        showAddSheet = false
                        onImportQrFromPhoto()
                    }
                )
                AddOptionCard(
                    title = "Enter a Setup Key",
                    subtitle = "Type or paste a Base32 secret or otpauth:// link manually",
                    icon = Icons.Default.Keyboard,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    testTag = "sheet_manual_entry",
                    onClick = {
                        showAddSheet = false
                        onOpenManualEntry()
                    }
                )
                AddOptionCard(
                    title = "Transfer Accounts (QR Export / Import)",
                    subtitle = "Move accounts offline to or from another device",
                    icon = Icons.Default.SwapHoriz,
                    accentColor = MaterialTheme.colorScheme.primary,
                    testTag = "sheet_transfer_accounts",
                    onClick = {
                        showAddSheet = false
                        onOpenTransferAccounts()
                    }
                )
            }
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            icon = {
                IconTile(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Sort Accounts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Pinned accounts always appear at the top of your vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    AccountSortMode.entries.forEach { mode ->
                        Surface(
                            onClick = {
                                onSelectSortMode(mode)
                                showSortDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (securitySettings.sortMode == mode) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                Color.Transparent
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (securitySettings.sortMode == mode) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                RadioButton(
                                    selected = securitySettings.sortMode == mode,
                                    onClick = {
                                        onSelectSortMode(mode)
                                        showSortDialog = false
                                    }
                                )
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    accountShowingQr?.let { account ->
        AccountQrDialog(
            account = account,
            onDismiss = { accountShowingQr = null }
        )
    }

    accountPendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountPendingDelete = null },
            icon = {
                IconTile(
                    icon = Icons.Default.DeleteOutline,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete ${account.displayTitle}?") },
            text = {
                Text(
                    "Removing this account deletes its verification code from Zayaan Authenticator. Make sure you have disabled 2FA or saved backup codes for this service first."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAccount(account)
                        accountPendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VaultCategoryChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.2.dp
        )
    )
}

@Composable
private fun AddOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            IconTile(
                icon = icon,
                tint = accentColor,
                size = 42.dp,
                iconSize = 22.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CloudStatusStrip(
    cloudStatus: CloudStatusUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, tint, container) = when (cloudStatus.syncState) {
        CloudSyncState.SYNCED -> Triple(
            Icons.Default.CloudDone,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        )
        CloudSyncState.SYNCING -> Triple(
            Icons.Default.CloudSync,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        )
        CloudSyncState.CONFLICT_DETECTED -> Triple(
            Icons.Default.WarningAmber,
            TimerWarningDark,
            TimerWarningDark.copy(alpha = 0.16f)
        )
        CloudSyncState.OFFLINE_LOCAL_ONLY -> Triple(
            Icons.Default.CloudOff,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
        else -> Triple(
            Icons.Default.CloudSync,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.2.dp, tint.copy(alpha = 0.32f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_status_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconTile(
                icon = icon,
                tint = tint,
                size = 32.dp,
                iconSize = 18.dp
            )
            Text(
                text = cloudStatus.statusDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tint.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.30f))
            ) {
                Text(
                    text = if (cloudStatus.signedInUser == null) "Backup" else "Manage",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthenticatorAccountCard(
    account: AuthenticatorAccount,
    rawCode: String,
    nextRawCode: String?,
    remainingSeconds: Int,
    progress: Float,
    isCopied: Boolean,
    isMasked: Boolean,
    showNextCodePreview: Boolean,
    isReorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onCardTap: () -> Unit,
    onCopyCurrentCode: () -> Unit,
    onCopyNextCode: (String) -> Unit,
    onTogglePin: () -> Unit,
    onShowQr: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showCardMenu by remember { mutableStateOf(false) }

    val targetTimerColor = when {
        remainingSeconds <= 5 -> TimerCriticalDark
        remainingSeconds <= 10 -> TimerWarningDark
        else -> TimerHealthyDark
    }
    val animatedTimerColor by animateColorAsState(
        targetValue = targetTimerColor,
        animationSpec = tween(250),
        label = "timerColor"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isCopied -> MaterialTheme.colorScheme.primary
            remainingSeconds <= 5 && !isMasked -> TimerCriticalDark.copy(alpha = 0.55f)
            account.isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        },
        animationSpec = tween(250),
        label = "cardBorderColor"
    )

    Card(
        onClick = { if (!isReorderMode) onCardTap() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isCopied || account.isPinned) 1.4.dp else 1.dp,
            color = cardBorderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_card_${account.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IssuerBadge(
                    issuer = account.issuer,
                    accountName = account.accountName,
                    category = account.category,
                    size = 44.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (account.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned account",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = account.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (account.category != AccountCategory.GENERAL) {
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = categoryIcon(account.category),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = account.category.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
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

                if (isReorderMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit account")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete account",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Box {
                        IconButton(
                            onClick = { showCardMenu = true },
                            modifier = Modifier.testTag("account_menu_${account.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options for ${account.displayTitle}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showCardMenu,
                            onDismissRequest = { showCardMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy Current Code") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showCardMenu = false
                                    onCopyCurrentCode()
                                }
                            )
                            if (nextRawCode != null) {
                                DropdownMenuItem(
                                    text = { Text("Copy Next Code") },
                                    leadingIcon = { Icon(Icons.Default.FastForward, contentDescription = null) },
                                    onClick = {
                                        showCardMenu = false
                                        onCopyNextCode(nextRawCode)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (account.isPinned) "Unpin from Top" else "Pin to Top") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (account.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showCardMenu = false
                                    onTogglePin()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Show QR Code") },
                                leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                                onClick = {
                                    showCardMenu = false
                                    onShowQr()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Account") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showCardMenu = false
                                    onEdit()
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Delete Account", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showCardMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val displayedCode = if (isMasked) {
                        if (account.digits == 8) "•••• ••••" else "••• •••"
                    } else {
                        TotpGenerator.formatCodeForDisplay(rawCode)
                    }
                    Text(
                        text = displayedCode,
                        style = TotpCodeTextStyle,
                        color = when {
                            isMasked -> MaterialTheme.colorScheme.onSurfaceVariant
                            remainingSeconds <= 5 -> animatedTimerColor
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.testTag("totp_code_${account.id}")
                    )
                    Surface(
                        onClick = if (isMasked) onCardTap else onCopyCurrentCode,
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isCopied -> MaterialTheme.colorScheme.primaryContainer
                            isMasked -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = when {
                                isCopied -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                isMasked -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            }
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isCopied -> Icons.Default.Check
                                    isMasked -> Icons.Default.Visibility
                                    else -> Icons.Default.ContentCopy
                                },
                                contentDescription = when {
                                    isCopied -> "Copied"
                                    isMasked -> "Reveal code"
                                    else -> "Copy code"
                                },
                                tint = when {
                                    isCopied -> MaterialTheme.colorScheme.primary
                                    isMasked -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = when {
                                    isCopied -> "Copied"
                                    isMasked -> "Reveal"
                                    else -> "Copy"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isCopied -> MaterialTheme.colorScheme.primary
                                    isMasked -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                CircularCountdownTimer(
                    remainingSeconds = remainingSeconds,
                    progress = progress,
                    ringColor = animatedTimerColor
                )
            }

            // Google Authenticator / power-user convenience: preview & one-tap copy upcoming code when expiring soon
            AnimatedVisibility(
                visible = showNextCodePreview && nextRawCode != null,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                nextRawCode?.let { upcoming ->
                    Surface(
                        onClick = { onCopyNextCode(upcoming) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("next_code_chip_${account.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Next code:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = TotpGenerator.formatCodeForDisplay(upcoming),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(
                                text = "Tap to copy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularCountdownTimer(
    remainingSeconds: Int,
    progress: Float,
    ringColor: Color
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(ringColor.copy(alpha = 0.08f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.5.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            // Subtle Google Authenticator-inspired inner pie sweep
            drawArc(
                color = ringColor.copy(alpha = 0.14f),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = true,
                topLeft = Offset(inset + 3.dp.toPx(), inset + 3.dp.toPx()),
                size = Size(arcSize.width - 6.dp.toPx(), arcSize.height - 6.dp.toPx())
            )
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = remainingSeconds.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ringColor
        )
    }
}

@Composable
private fun EmptyVaultState(
    hasAnyAccounts: Boolean,
    onScanQr: () -> Unit,
    onImportQrImage: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenCloudRestore: () -> Unit
) {
    val primaryGlow = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            if (!hasAnyAccounts) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(164.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        primaryGlow.copy(alpha = 0.24f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.minDimension * 0.58f
                            )
                        }
                ) {
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(2.dp, primaryGlow.copy(alpha = 0.45f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(128.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vault_shield_1795105196684),
                            contentDescription = "Zero-knowledge security vault shield",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Your Encrypted Vault is Ready",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All secrets are encrypted locally with AES-256-GCM and protected by Android Keystore. Works 100% offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onScanQr,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("empty_scan_qr_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan QR")
                    }
                    OutlinedButton(
                        onClick = onManualEntry,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("empty_manual_entry_button")
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enter Key")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onImportQrImage,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import QR Code Image")
                }
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onOpenCloudRestore,
                    modifier = Modifier.testTag("empty_restore_cloud_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore from Encrypted Cloud Backup")
                }
            } else {
                IconTile(
                    icon = Icons.Default.Search,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 52.dp,
                    iconSize = 26.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No matching accounts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Try clearing your search query or category filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
