package com.socialitefree.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryBlueDark,
    onPrimaryContainer = PrimaryBlueLight,
    secondary = SecondaryTealLight,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryTealDark,
    onSecondaryContainer = SecondaryTealLight,
    tertiary = TertiaryAmberLight,
    onTertiary = TertiaryAmberDark,
    error = ErrorRedDark,
    onError = ErrorRed,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceDark,
    outline = OutlineDark,
    surfaceContainerHigh = SurfaceContainerDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryBlueLight,
    onPrimaryContainer = PrimaryBlueDark,
    secondary = SecondaryTeal,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryTealLight,
    onSecondaryContainer = SecondaryTealDark,
    tertiary = TertiaryAmber,
    onTertiary = TertiaryAmberDark,
    error = ErrorRed,
    onError = OnPrimaryLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceLight,
    outline = OutlineLight,
    surfaceContainerHigh = SurfaceContainerLight,
)

@Composable
fun SocialLiteFreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
