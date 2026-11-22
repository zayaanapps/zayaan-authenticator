package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.crypto.OtpAuthUriParser
import com.example.crypto.QrCodeGenerator
import com.example.data.model.AccountCategory
import com.example.data.model.AuthenticatorAccount
import com.example.ui.theme.IssuerPalette
import kotlin.math.abs

data class IssuerVisual(
    val color: Color,
    val icon: ImageVector?,
    val monogram: String
)

fun categoryIcon(category: AccountCategory?): ImageVector = when (category) {
    null -> Icons.Default.Apps
    AccountCategory.GENERAL -> Icons.Default.Key
    AccountCategory.WORK -> Icons.Default.WorkOutline
    AccountCategory.PERSONAL -> Icons.Default.PersonOutline
    AccountCategory.FINANCE -> Icons.Default.AccountBalance
    AccountCategory.SOCIAL -> Icons.Default.Forum
    AccountCategory.DEVELOPER -> Icons.Default.Code
}

fun resolveIssuerVisual(issuer: String, accountName: String, category: AccountCategory): IssuerVisual {
    val key = "${issuer.trim()} ${accountName.trim()}".lowercase()
    val display = issuer.ifBlank { accountName.ifBlank { "Account" } }
    val monogram = display
        .split(" ", ".", "-", "_", "@")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { display.take(2).uppercase() }

    val matched: Pair<Color, ImageVector>? = when {
        "google" in key || "gmail" in key -> Color(0xFF2563EB) to Icons.Default.AlternateEmail
        "github" in key || "gitlab" in key || "bitbucket" in key -> Color(0xFF6366F1) to Icons.Default.Code
        "microsoft" in key || "azure" in key || "office" in key || "outlook" in key -> Color(0xFF0284C7) to Icons.Default.WorkOutline
        "aws" in key || "amazon" in key -> Color(0xFFD97706) to Icons.Default.Cloud
        "apple" in key || "icloud" in key -> Color(0xFF64748B) to Icons.Default.PhoneIphone
        "cloudflare" in key || "digitalocean" in key || "heroku" in key || "vercel" in key -> Color(0xFF0EA5E9) to Icons.Default.Terminal
        "discord" in key || "slack" in key || "telegram" in key || "whatsapp" in key -> Color(0xFF7C3AED) to Icons.Default.Forum
        "facebook" in key || "meta" in key || "instagram" in key || "twitter" in key || "reddit" in key || "linkedin" in key -> Color(0xFFDB2777) to Icons.Default.Language
        "paypal" in key || "stripe" in key || "visa" in key || "mastercard" in key -> Color(0xFF0D9488) to Icons.Default.CreditCard
        "bank" in key || "chase" in key || "citi" in key || "hsbc" in key || "fidelity" in key -> Color(0xFF059669) to Icons.Default.AccountBalance
        "coinbase" in key || "binance" in key || "kraken" in key || "crypto" in key || "bitcoin" in key -> Color(0xFFF59E0B) to Icons.Default.CurrencyBitcoin
        "steam" in key || "playstation" in key || "xbox" in key || "epic" in key || "twitch" in key -> Color(0xFF4F46E5) to Icons.Default.SportsEsports
        "shopify" in key || "ebay" in key || "etsy" in key -> Color(0xFF10B981) to Icons.Default.ShoppingBag
        "proton" in key || "bitwarden" in key || "1password" in key || "dashlane" in key -> Color(0xFF10B981) to Icons.Default.Security
        "zoom" in key || "meet" in key -> Color(0xFF2563EB) to Icons.Default.Videocam
        "spotify" in key -> Color(0xFF16A34A) to Icons.Default.MusicNote
        "netflix" in key || "youtube" in key || "disney" in key -> Color(0xFFDC2626) to Icons.Default.Movie
        "mail" in key || "yahoo" in key -> Color(0xFF0284C7) to Icons.Default.Mail
        else -> null
    }

    if (matched != null) {
        return IssuerVisual(color = matched.first, icon = matched.second, monogram = monogram)
    }

    val deterministicColor = IssuerPalette[abs(display.hashCode()) % IssuerPalette.size]
    val fallbackIcon = if (category != AccountCategory.GENERAL) categoryIcon(category) else null
    return IssuerVisual(color = deterministicColor, icon = fallbackIcon, monogram = monogram)
}

/**
 * Framed service issuer badge with subtle tonal gradient fill and luminous border.
 */
@Composable
fun IssuerBadge(
    issuer: String,
    accountName: String,
    category: AccountCategory,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val visual = remember(issuer, accountName, category) {
        resolveIssuerVisual(issuer, accountName, category)
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        visual.color.copy(alpha = 0.22f),
                        visual.color.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        visual.color.copy(alpha = 0.55f),
                        visual.color.copy(alpha = 0.18f)
                    )
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (visual.icon != null) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = visual.color,
                modifier = Modifier.size(size * 0.50f)
            )
        } else {
            Text(
                text = visual.monogram,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = visual.color
            )
        }
    }
}

/**
 * Framed squircle icon tile used across Settings, Cloud Backup, Bottom Sheets, and Dialogs.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.14f))
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.30f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Crisp section header with icon accent and label.
 */
@Composable
fun VaultSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Displays a single account's standard otpauth://totp/... QR code for quick migration to another device.
 */
@Composable
fun AccountQrDialog(
    account: AuthenticatorAccount,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(account.id, account.secretBase32, account.issuer, account.accountName) {
        val uri = OtpAuthUriParser.buildOtpAuthUri(account)
        QrCodeGenerator.generateQrBitmap(uri, sizePx = 680)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            IconTile(
                icon = Icons.Default.QrCode2,
                tint = MaterialTheme.colorScheme.primary,
                size = 44.dp,
                iconSize = 24.dp
            )
        },
        title = {
            Text(
                text = account.displayTitle,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (account.displaySubtitle.isNotBlank()) {
                    Text(
                        text = account.displaySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    shadowElevation = 2.dp
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR code for ${account.displayTitle}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(14.dp)
                                .size(220.dp)
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(220.dp)
                        ) {
                            Text("Unable to generate QR", color = Color.Black)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Keep this QR private. Anyone who scans it can generate codes for this account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
