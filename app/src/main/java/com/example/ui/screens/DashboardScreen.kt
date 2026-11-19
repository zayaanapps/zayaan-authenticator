package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.crypto.TotpGenerator
import com.example.data.model.AccountCategory
import com.example.data.model.AuthenticatorAccount
import com.example.data.model.CloudSyncState
import com.example.data.model.SecuritySettings
import com.example.ui.CloudStatusUi
import com.example.ui.theme.IssuerPalette
import com.example.ui.theme.TimerCriticalDark
import com.example.ui.theme.TimerHealthyDark
import com.example.ui.theme.TimerWarningDark
import com.example.ui.theme.TotpCodeTextStyle
import kotlinx.coroutines.delay
import kotlin.math.abs

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
    onToggleReorderMode: () -> Unit,
    onMoveAccount: (String, Boolean) -> Unit,
    onCopyCode: (AuthenticatorAccount, String) -> Unit,
    onOpenQrScanner: () -> Unit,
    onImportQrFromPhoto: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onEditAccount: (AuthenticatorAccount) -> Unit,
    onDeleteAccount: (AuthenticatorAccount) -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onOpenCloudBackup: () -> Unit,
    onLockNow: () -> Unit
) {
    var showSearchBar by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var accountPendingDelete by remember { mutableStateOf<AuthenticatorAccount?>(null) }
    var recentlyCopiedId by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(recentlyCopiedId) {
        if (recentlyCopiedId != null) {
            delay(1800L)
            recentlyCopiedId = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Zayaan Authenticator",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (totalAccountCount == 1) "1 encrypted account" else "$totalAccountCount encrypted accounts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                contentDescription = "Search accounts"
                            )
                        }
                        IconButton(
                            onClick = onToggleReorderMode,
                            modifier = Modifier.testTag("toggle_reorder_button")
                        ) {
                            Icon(
                                imageVector = if (isReorderMode) Icons.Default.Check else Icons.Default.SwapVert,
                                contentDescription = if (isReorderMode) "Done organizing" else "Reorder and manage accounts",
                                tint = if (isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                    IconButton(
                        onClick = onOpenSecuritySettings,
                        modifier = Modifier.testTag("open_security_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security and app settings"
                        )
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
                text = { Text("Add Account") },
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
                // Cloud Backup & Sync Status Strip (tappable to open Cloud Backup & Recovery Hub)
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

                // Category filter chips
                if (totalAccountCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text("All") }
                        )
                        AccountCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = {
                                    onSelectCategory(if (selectedCategory == category) null else category)
                                },
                                label = { Text(category.displayName) }
                            )
                        }
                    }
                }

                if (isReorderMode && accounts.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Organize Mode: Use arrows to reorder, or tap Edit / Delete.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                if (accounts.isEmpty()) {
                    EmptyVaultState(
                        hasAnyAccounts = totalAccountCount > 0,
                        onScanQr = onOpenQrScanner,
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
                            val code = remember(
                                account.secretBase32,
                                account.algorithm,
                                account.digits,
                                account.periodSeconds,
                                Math.floorDiv(timeSeconds, account.periodSeconds.toLong())
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
                            val remainingSeconds = TotpGenerator.remainingSeconds(
                                timeSeconds = timeSeconds,
                                periodSeconds = account.periodSeconds
                            )
                            val progress = TotpGenerator.remainingProgress(
                                timeMillis = currentTimeMillis,
                                periodSeconds = account.periodSeconds
                            )

                            AuthenticatorAccountCard(
                                account = account,
                                rawCode = code,
                                remainingSeconds = remainingSeconds,
                                progress = progress,
                                isCopied = recentlyCopiedId == account.id,
                                isReorderMode = isReorderMode,
                                canMoveUp = index > 0,
                                canMoveDown = index < accounts.lastIndex,
                                onTapCopy = {
                                    clipboardManager.setText(AnnotatedString(code))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    recentlyCopiedId = account.id
                                    onCopyCode(account, code)
                                },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Add Authenticator Account",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Scan QR Code") },
                    supportingContent = { Text("Supports standard TOTP & Google Authenticator export QR codes") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .clickable {
                            showAddSheet = false
                            onOpenQrScanner()
                        }
                        .testTag("sheet_scan_qr")
                )
                ListItem(
                    headlineContent = { Text("Import QR Code Image") },
                    supportingContent = { Text("Select a saved QR screenshot via secure Android Photo Picker") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    modifier = Modifier
                        .clickable {
                            showAddSheet = false
                            onImportQrFromPhoto()
                        }
                        .testTag("sheet_import_qr_image")
                )
                ListItem(
                    headlineContent = { Text("Enter Setup Key Manually") },
                    supportingContent = { Text("Type issuer, account name, Base32 secret, algorithm & interval") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    },
                    modifier = Modifier
                        .clickable {
                            showAddSheet = false
                            onOpenManualEntry()
                        }
                        .testTag("sheet_manual_entry")
                )
            }
        }
    }

    accountPendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountPendingDelete = null },
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
private fun CloudStatusStrip(
    cloudStatus: CloudStatusUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, tint, container) = when (cloudStatus.syncState) {
        CloudSyncState.SYNCED -> Triple(
            Icons.Default.CloudDone,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
        CloudSyncState.SYNCING -> Triple(
            Icons.Default.CloudSync,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
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
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_status_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Cloud backup status",
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = cloudStatus.statusDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (cloudStatus.signedInUser == null) "Backup" else "Manage",
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}

@Composable
private fun AuthenticatorAccountCard(
    account: AuthenticatorAccount,
    rawCode: String,
    remainingSeconds: Int,
    progress: Float,
    isCopied: Boolean,
    isReorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTapCopy: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeColor = remember(account.displayTitle) {
        IssuerPalette[abs(account.displayTitle.hashCode()) % IssuerPalette.size]
    }
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

    Card(
        onClick = { if (!isReorderMode) onTapCopy() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isCopied) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = account.displayTitle.take(2).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = account.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (account.category != AccountCategory.GENERAL) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = account.category.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
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
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit ${account.displayTitle}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = TotpGenerator.formatCodeForDisplay(rawCode),
                        style = TotpCodeTextStyle,
                        color = if (remainingSeconds <= 5) animatedTimerColor else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("totp_code_${account.id}")
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCopied) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = if (isCopied) "Copied" else "Tap to copy",
                                tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isCopied) "Copied" else "Copy",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier.size(44.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.5.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
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
    onManualEntry: () -> Unit,
    onOpenCloudRestore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            if (!hasAnyAccounts) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(132.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_vault_shield_1795105196684),
                        contentDescription = "Zero-knowledge security vault shield",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Your Encrypted Vault is Ready",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All secrets are encrypted locally with AES-256-GCM and protected by Android Keystore. Works 100% offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onManualEntry,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("empty_manual_entry_button")
                    ) {
                        Text("Enter Key")
                    }
                    OutlinedButton(
                        onClick = onScanQr,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("empty_scan_qr_button")
                    ) {
                        Text("Scan QR")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onOpenCloudRestore,
                    modifier = Modifier.testTag("empty_restore_cloud_button")
                ) {
                    Text("Restore from Encrypted Cloud Backup")
                }
            } else {
                Text(
                    text = "No matching accounts",
                    style = MaterialTheme.typography.titleMedium
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
