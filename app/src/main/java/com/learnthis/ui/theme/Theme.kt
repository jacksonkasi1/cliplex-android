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
 primary = Color(0xFF079455),
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
 background = Color(0xFFF4F8F6),
 onBackground = Color(0xFF10231B)
)

private val DarkColorScheme = darkColorScheme(
 primary = Color(0xFF5DDA99),
 onPrimary = Color(0xFF003921),
 primaryContainer = Color(0xFF075F3B),
 onPrimaryContainer = Color(0xFFB7F4D2),
 secondary = Color(0xFF9FC2FF),
 onSecondary = Color(0xFF00315F),
 tertiary = Color(0xFFCAB4FF),
 onTertiary = Color(0xFF3B176F),
 surface = Color(0xFF101814),
 onSurface = Color(0xFFE1EAE5),
 surfaceVariant = Color(0xFF25352D),
 onSurfaceVariant = Color(0xFFBAC9C1),
 background = Color(0xFF0D1511),
 onBackground = Color(0xFFE1EAE5)
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
 window.statusBarColor = colorScheme.surface.toArgb()
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
