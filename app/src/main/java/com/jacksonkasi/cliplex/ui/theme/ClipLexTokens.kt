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
 * ClipLex visual language.
 *
 * The palette stays recognisably ClipLex while using playful, high-contrast surfaces and tactile
 * pressed states that make learning actions feel rewarding rather than administrative.
 */
object ClipLexColors {
    val Ink = Color(0xFF17223B)
    val InkSoft = Color(0xFF344054)
    val InkMuted = Color(0xFF667085)
    val InkFaint = Color(0xFF98A2B3)

    val Green = Color(0xFF12B76A)
    val GreenDark = Color(0xFF087A4A)
    val GreenPressed = Color(0xFF0B8F55)
    val GreenSoft = Color(0xFFE8F8F0)
    val GreenWash = Color(0xFFF2FCF7)

    val Lime = Color(0xFF78C91F)
    val LimeSoft = Color(0xFFF0F9E8)
    val Blue = Color(0xFF2E90FA)
    val BlueDark = Color(0xFF175CD3)
    val BlueSoft = Color(0xFFEAF4FF)
    val Purple = Color(0xFF7F56D9)
    val PurpleSoft = Color(0xFFF3EEFF)
    val Warm = Color(0xFFF79009)
    val WarmDark = Color(0xFFC96A00)
    val WarmSoft = Color(0xFFFFF4E5)
    val Coral = Color(0xFFF04438)
    val CoralDark = Color(0xFFB42318)
    val CoralSoft = Color(0xFFFFEEEC)

    val Canvas = Color(0xFFF7F9FC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF2F4F7)
    val Border = Color(0xFFE4E7EC)
    val BorderStrong = Color(0xFFD0D5DD)
    val Shadow = Color(0xFF101828)
}

@Immutable
data class ClipLexSpacing(
    val tiny: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val section: Dp = 20.dp,
    val page: Dp = 24.dp,
)

val LocalClipLexSpacing = androidx.compose.runtime.staticCompositionLocalOf { ClipLexSpacing() }

object ClipLexShapes {
    val Tiny = RoundedCornerShape(8.dp)
    val Small = RoundedCornerShape(12.dp)
    val Control = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(20.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Pill = RoundedCornerShape(100.dp)
    val Sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
}

private val RoundedSystemFont = FontFamily.SansSerif

val ClipLexTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.35).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = RoundedSystemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.15.sp,
    ),
)
