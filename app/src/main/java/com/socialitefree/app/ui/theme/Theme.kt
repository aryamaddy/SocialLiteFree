package com.socialitefree.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NoScrollRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF311920),
    onPrimaryContainer = Color(0xFFFFB4B8),
    secondary = NoScrollBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1B2B45),
    onSecondaryContainer = Color(0xFFBFDBFE),
    background = NoScrollBg,
    onBackground = NoScrollTextPrimary,
    surface = NoScrollSurface,
    onSurface = NoScrollTextPrimary,
    surfaceVariant = NoScrollCard,
    onSurfaceVariant = NoScrollTextSecondary,
    outline = NoScrollBorder,
    error = NoScrollRed
)

private val LightColorScheme = lightColorScheme(
    primary = NoScrollRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E6),
    onPrimaryContainer = Color(0xFF881337),
    secondary = NoScrollBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    background = CreamBackground,
    onBackground = InkPrimary,
    surface = CreamSurface,
    onSurface = InkPrimary,
    surfaceVariant = CreamCardSubtle,
    onSurfaceVariant = InkSecondary,
    outline = CreamDivider,
    error = SocialCoral
)

@Composable
fun SocialLiteFreeTheme(
    appTheme: String = "DARK",
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme.uppercase()) {
        "LIGHT" -> false
        "SYSTEM_DEFAULT" -> isSystemInDarkTheme()
        else -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
