package com.jacksonkasi.cliplex.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ClipLex V2 visual tokens.
 *
 * The system uses one mineral-teal product accent, warm amber only for learning momentum, and
 * coral only for destructive or error states. Existing semantic aliases remain so feature code can
 * migrate gradually without reintroducing a multi-accent interface.
 */
object ClipLexColors {
    val Ink = Color(0xFF15211D)
    val InkSoft = Color(0xFF34443E)
    val InkMuted = Color(0xFF68766F)
    val InkFaint = Color(0xFF98A39E)

    val Accent = Color(0xFF0F7B69)
    val AccentStrong = Color(0xFF07594E)
    val AccentPressed = Color(0xFF0A685A)
    val AccentBright = Color(0xFF2AA68D)
    val AccentSoft = Color(0xFFE2F1EC)
    val AccentWash = Color(0xFFF1F8F5)

    val Night = Color(0xFF14231F)
    val NightSoft = Color(0xFF20352E)
    val NightMuted = Color(0xFF365148)

    val Warm = Color(0xFFE99A2E)
    val WarmDark = Color(0xFF9B5B08)
    val WarmSoft = Color(0xFFFFF1D8)

    val Coral = Color(0xFFD94B45)
    val CoralDark = Color(0xFF96302C)
    val CoralSoft = Color(0xFFFFE9E7)

    val Canvas = Color(0xFFF5F7F4)
    val Surface = Color(0xFFFCFDFC)
    val SurfaceMuted = Color(0xFFEAF0ED)
    val SurfaceRaised = Color(0xFFFFFFFF)
    val Border = Color(0xFFD9E2DE)
    val BorderStrong = Color(0xFFBCCAC4)
    val Shadow = Color(0xFF0F211A)

    // Compatibility aliases. They intentionally resolve to the same product accent family.
    val Green = Accent
    val GreenDark = AccentStrong
    val GreenPressed = AccentPressed
    val GreenSoft = AccentSoft
    val GreenWash = AccentWash
    val Lime = AccentBright
    val LimeSoft = AccentSoft
    val Blue = Accent
    val BlueDark = AccentStrong
    val BlueSoft = AccentSoft
    val Purple = AccentStrong
    val PurpleSoft = AccentWash
}

@Immutable
data class ClipLexSpacing(
    val tiny: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val section: Dp = 22.dp,
    val page: Dp = 20.dp,
)

val LocalClipLexSpacing = androidx.compose.runtime.staticCompositionLocalOf { ClipLexSpacing() }

object ClipLexShapes {
    val Tiny = RoundedCornerShape(7.dp)
    val Small = RoundedCornerShape(11.dp)
    val Control = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(18.dp)
    val Hero = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(100.dp)
    val Sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
}

private val ProductSans = FontFamily.SansSerif

val ClipLexTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.45).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.15).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.05.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.05.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.08.sp,
    ),
)
