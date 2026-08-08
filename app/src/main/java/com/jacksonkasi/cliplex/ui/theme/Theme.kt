package com.jacksonkasi.cliplex.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = ClipLexColors.Accent,
    onPrimary = Color.White,
    primaryContainer = ClipLexColors.AccentSoft,
    onPrimaryContainer = ClipLexColors.AccentStrong,
    secondary = ClipLexColors.AccentStrong,
    onSecondary = Color.White,
    secondaryContainer = ClipLexColors.AccentWash,
    onSecondaryContainer = ClipLexColors.Ink,
    tertiary = ClipLexColors.Warm,
    onTertiary = ClipLexColors.Ink,
    tertiaryContainer = ClipLexColors.WarmSoft,
    onTertiaryContainer = ClipLexColors.WarmDark,
    error = ClipLexColors.Coral,
    onError = Color.White,
    errorContainer = ClipLexColors.CoralSoft,
    onErrorContainer = ClipLexColors.CoralDark,
    surface = ClipLexColors.Surface,
    onSurface = ClipLexColors.Ink,
    surfaceVariant = ClipLexColors.SurfaceMuted,
    onSurfaceVariant = ClipLexColors.InkMuted,
    outline = ClipLexColors.BorderStrong,
    outlineVariant = ClipLexColors.Border,
    background = ClipLexColors.Canvas,
    onBackground = ClipLexColors.Ink,
)

private val ClipLexMaterialShapes = Shapes(
    extraSmall = ClipLexShapes.Tiny,
    small = ClipLexShapes.Small,
    medium = ClipLexShapes.Control,
    large = ClipLexShapes.Card,
    extraLarge = ClipLexShapes.Hero,
)

@Composable
fun ClipLexTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    // ClipLex uses a locked light product theme with dark media canvases inside learning screens.
    val colorScheme = LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    CompositionLocalProvider(LocalClipLexSpacing provides ClipLexSpacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ClipLexTypography,
            shapes = ClipLexMaterialShapes,
            content = content,
        )
    }
}
