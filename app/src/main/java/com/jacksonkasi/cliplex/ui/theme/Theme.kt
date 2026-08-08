package com.jacksonkasi.cliplex.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
 primary = ClipLexColors.Green,
 onPrimary = Color.White,
 primaryContainer = Color(0xFFDDF7E9),
 onPrimaryContainer = Color(0xFF063C27),
 secondary = Color(0xFF1769E0),
 onSecondary = Color.White,
 tertiary = Color(0xFF7C3AED),
 onTertiary = Color.White,
 surface = Color(0xFFFBFDFC),
 onSurface = Color(0xFF10231B),
 surfaceVariant = Color(0xFFEAF2EE),
 onSurfaceVariant = Color(0xFF52645C),
 background = ClipLexColors.Canvas,
 onBackground = ClipLexColors.Ink
)

@Composable
fun ClipLexTheme(
 darkTheme: Boolean = false,
 content: @Composable () -> Unit
) {
 val colorScheme = LightColorScheme
 val view = LocalView.current

 if (!view.isInEditMode) {
 SideEffect {
 val window = (view.context as Activity).window
 window.statusBarColor = colorScheme.surface.toArgb()
 WindowCompat.getInsetsController(window, view)
 .isAppearanceLightStatusBars = !darkTheme
 }
 }

 CompositionLocalProvider(LocalClipLexSpacing provides ClipLexSpacing()) {
  MaterialTheme(colorScheme = colorScheme, typography = ClipLexTypography, content = content)
 }
}
