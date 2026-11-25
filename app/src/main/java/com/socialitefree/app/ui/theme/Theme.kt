package com.socialitefree.app.ui.theme

import android.app.Activity
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
    primary = SocialPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2D2452),
    onPrimaryContainer = SocialPurpleSoft,
    secondary = SocialGreen,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkCard,
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = Color(0xFF3A3A44),
    error = SocialCoral
)

private val LightColorScheme = lightColorScheme(
    primary = SocialPurple,
    onPrimary = Color.White,
    primaryContainer = SocialPurpleSoft,
    onPrimaryContainer = Color(0xFF321D8A),
    secondary = SocialGreen,
    onSecondary = Color.White,
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
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
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
