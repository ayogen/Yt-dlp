package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ElegantDarkColorScheme = darkColorScheme(
    primary = ElegantLavenderPrimary,
    onPrimary = ElegantLavenderOnPrimary,
    primaryContainer = ElegantLavenderContainer,
    onPrimaryContainer = ElegantLavenderOnContainer,
    secondary = ElegantLavenderPrimary,
    onSecondary = ElegantLavenderOnPrimary,
    secondaryContainer = ElegantDarkSurfaceVariant,
    onSecondaryContainer = ElegantTextPrimary,
    background = ElegantDarkBackground,
    onBackground = ElegantTextPrimary,
    surface = ElegantDarkSurface,
    onSurface = ElegantTextPrimary,
    surfaceVariant = ElegantDarkSurfaceVariant,
    onSurfaceVariant = ElegantTextSecondary,
    outline = ElegantDarkBorder,
    outlineVariant = ElegantDarkBorderSubtle,
    error = ElegantRed,
    onError = ElegantLavenderOnPrimary
)

private val ElegantLightColorScheme = lightColorScheme(
    primary = ElegantLightPrimary,
    onPrimary = ElegantDarkBackground,
    background = ElegantLightBackground,
    surface = ElegantLightSurface,
    onBackground = ElegantLightTextPrimary,
    onSurface = ElegantLightTextPrimary
)

@Composable
fun VideoDownloaderTheme(
    darkTheme: Boolean = true, // Default to Elegant Dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ElegantDarkColorScheme else ElegantLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
