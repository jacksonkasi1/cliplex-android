package com.jacksonkasi.cliplex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand tokens shared by every ClipLex surface. */
object ClipLexColors {
	val Ink = Color(0xFF101936)
	val InkMuted = Color(0xFF667085)
	val Green = Color(0xFF08A957)
	val GreenDark = Color(0xFF068447)
	val GreenSoft = Color(0xFFE9F9EF)
	val GreenWash = Color(0xFFF3FFF7)
	val Blue = Color(0xFF176DF5)
	val BlueSoft = Color(0xFFEAF2FF)
	val Purple = Color(0xFF8547F5)
	val Warm = Color(0xFFFFB323)
	val WarmSoft = Color(0xFFFFF4D9)
	val Coral = Color(0xFFFF4E63)
	val Canvas = Color(0xFFF8FAFC)
	val Surface = Color(0xFFFFFFFF)
	val Border = Color(0xFFE7ECF2)
	val Shadow = Color(0xFF12305B)
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
	val Small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
	val Control = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
	val Card = androidx.compose.foundation.shape.RoundedCornerShape(21.dp)
	val Hero = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
	val Sheet = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
}

private val RoundedSystemFont = FontFamily.SansSerif

val ClipLexTypography = Typography(
	displaySmall = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
	headlineMedium = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
	titleLarge = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
	titleMedium = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
	titleSmall = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
	bodyLarge = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
	bodyMedium = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
	bodySmall = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
	labelLarge = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
	labelMedium = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
	labelSmall = TextStyle(fontFamily = RoundedSystemFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
