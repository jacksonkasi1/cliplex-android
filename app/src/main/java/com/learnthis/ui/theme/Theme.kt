package com.learnthis.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
 primary = Color(0xFF6750A4),
 onPrimary = Color.White,
 secondary = Color(0xFF625B71),
 onSecondary = Color.White,
 tertiary = Color(0xFF7D5260),
 onTertiary = Color.White,
 surface = Color(0xFFFFFBFE),
 onSurface = Color(0xFF1C1B1F),
 background = Color(0xFFFFFBFE),
 onBackground = Color(0xFF1C1B1F)
)

private val DarkColorScheme = darkColorScheme(
 primary = Color(0xFFD0BCFF),
 onPrimary = Color(0xFF381E72),
 secondary = Color(0xFFCCC2DC),
 onSecondary = Color(0xFF332D41),
 tertiary = Color(0xFFEFB8C8),
 onTertiary = Color(0xFF492532),
 surface = Color(0xFF1C1B1F),
 onSurface = Color(0xFFE6E1E5),
 background = Color(0xFF1C1B1F),
 onBackground = Color(0xFFE6E1E5)
)

@Composable
fun LearnThisTheme(
 darkTheme: Boolean = isSystemInDarkTheme(),
 content: @Composable () -> Unit
) {
 val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
 val view = LocalView.current

 if (!view.isInEditMode) {
 SideEffect {
 val window = (view.context as Activity).window
 window.statusBarColor = colorScheme.primary.toArgb()
 WindowCompat.getInsetsController(window, view)
 .isAppearanceLightStatusBars = !darkTheme
 }
 }

 MaterialTheme(
 colorScheme = colorScheme,
 typography = androidx.compose.material3.Typography(),
 content = content
 )
}
