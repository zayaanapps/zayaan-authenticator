package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.model.AppThemeMode

private val ZayanDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldOnContainerDark,
    secondary = CyanSecondaryDark,
    onSecondary = CyanOnSecondaryDark,
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = CyanOnContainerDark,
    tertiary = IndigoTertiaryDark,
    onTertiary = Color.White,
    background = ObsidianBackground,
    onBackground = ObsidianOnSurface,
    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    outline = ObsidianOutline,
    error = TimerCriticalDark,
    onError = Color(0xFF450A0A)
)

private val ZayanLightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldOnContainerLight,
    secondary = CyanSecondaryLight,
    onSecondary = CyanOnSecondaryLight,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = CyanOnContainerLight,
    tertiary = IndigoTertiaryLight,
    onTertiary = Color.White,
    background = AlabasterBackground,
    onBackground = AlabasterOnSurface,
    surface = AlabasterSurface,
    onSurface = AlabasterOnSurface,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = AlabasterOnSurfaceVariant,
    outline = AlabasterOutline,
    error = TimerCriticalLight,
    onError = Color.White
)

@Composable
fun ZayanAuthenticatorTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = if (useDark) ZayanDarkColorScheme else ZayanLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZayanTypography,
        content = content
    )
}
