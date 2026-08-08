package com.jacksonkasi.cliplex.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors

enum class LexiMood {
    READY,
    LISTENING,
    CELEBRATING,
    THINKING,
}

/** Original ClipLex learning companion drawn entirely with Compose primitives. */
@Composable
fun LexiMascot(
    modifier: Modifier = Modifier,
    mood: LexiMood = LexiMood.READY,
    animate: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "lexi-mascot")
    val floatY by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animate) -5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lexi-float",
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animate) 0.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(170, delayMillis = 2_600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lexi-blink",
    )

    Canvas(modifier = modifier.graphicsLayer { translationY = floatY }) {
        val w = size.width
        val h = size.height
        val bodyLeft = w * 0.17f
        val bodyTop = h * 0.14f
        val bodyWidth = w * 0.66f
        val bodyHeight = h * 0.67f
        val radius = w * 0.16f

        drawOval(
            color = ClipLexColors.GreenDark.copy(alpha = 0.12f),
            topLeft = Offset(w * 0.2f, h * 0.78f),
            size = Size(w * 0.6f, h * 0.12f),
        )

        val leftArm = Path().apply {
            moveTo(bodyLeft + w * 0.03f, bodyTop + bodyHeight * 0.52f)
            cubicTo(w * 0.04f, h * 0.48f, w * 0.04f, h * 0.68f, w * 0.13f, h * 0.71f)
        }
        val rightArm = Path().apply {
            moveTo(bodyLeft + bodyWidth - w * 0.03f, bodyTop + bodyHeight * 0.52f)
            cubicTo(w * 0.96f, h * 0.46f, w * 0.96f, h * 0.66f, w * 0.87f, h * 0.70f)
        }
        drawPath(leftArm, ClipLexColors.GreenDark, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))
        drawPath(rightArm, ClipLexColors.GreenDark, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))

        drawRoundRect(
            color = ClipLexColors.Green,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.13f),
            topLeft = Offset(bodyLeft + w * 0.04f, bodyTop + h * 0.04f),
            size = Size(bodyWidth - w * 0.08f, bodyHeight * 0.24f),
            cornerRadius = CornerRadius(radius * 0.72f, radius * 0.72f),
        )

        val eyeY = bodyTop + bodyHeight * 0.37f
        val eyeRadius = w * 0.055f
        val eyeHeight = eyeRadius * 2f * blink.coerceAtLeast(0.12f)
        listOf(w * 0.39f, w * 0.61f).forEach { eyeX ->
            drawOval(
                color = Color.White,
                topLeft = Offset(eyeX - eyeRadius, eyeY - eyeHeight / 2f),
                size = Size(eyeRadius * 2f, eyeHeight),
            )
            if (blink > 0.35f) {
                drawCircle(ClipLexColors.Ink, radius = eyeRadius * 0.42f, center = Offset(eyeX, eyeY + eyeRadius * 0.08f))
                drawCircle(Color.White, radius = eyeRadius * 0.13f, center = Offset(eyeX - eyeRadius * 0.12f, eyeY - eyeRadius * 0.08f))
            }
        }

        when (mood) {
            LexiMood.THINKING -> {
                drawArc(
                    color = ClipLexColors.Ink,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.41f, h * 0.56f),
                    size = Size(w * 0.18f, h * 0.08f),
                    style = Stroke(width = w * 0.024f, cap = StrokeCap.Round),
                )
            }
            else -> {
                drawArc(
                    color = ClipLexColors.Ink,
                    startAngle = 12f,
                    sweepAngle = 156f,
                    useCenter = false,
                    topLeft = Offset(w * 0.40f, h * 0.48f),
                    size = Size(w * 0.20f, h * 0.18f),
                    style = Stroke(width = w * 0.026f, cap = StrokeCap.Round),
                )
            }
        }

        drawCircle(
            color = ClipLexColors.Warm,
            radius = w * 0.035f,
            center = Offset(bodyLeft + bodyWidth * 0.5f, bodyTop + bodyHeight * 0.78f),
        )

        if (mood == LexiMood.LISTENING) {
            drawArc(
                color = ClipLexColors.Blue,
                startAngle = 185f,
                sweepAngle = 170f,
                useCenter = false,
                topLeft = Offset(w * 0.22f, h * 0.16f),
                size = Size(w * 0.56f, h * 0.50f),
                style = Stroke(width = w * 0.05f, cap = StrokeCap.Round),
            )
            drawRoundRect(
                color = ClipLexColors.BlueDark,
                topLeft = Offset(w * 0.17f, h * 0.38f),
                size = Size(w * 0.09f, h * 0.20f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
            )
            drawRoundRect(
                color = ClipLexColors.BlueDark,
                topLeft = Offset(w * 0.74f, h * 0.38f),
                size = Size(w * 0.09f, h * 0.20f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
            )
        }

        if (mood == LexiMood.CELEBRATING) {
            val confetti = listOf(
                Offset(w * 0.08f, h * 0.15f) to ClipLexColors.Warm,
                Offset(w * 0.90f, h * 0.14f) to ClipLexColors.Blue,
                Offset(w * 0.10f, h * 0.35f) to ClipLexColors.Purple,
                Offset(w * 0.91f, h * 0.38f) to ClipLexColors.Coral,
            )
            confetti.forEachIndexed { index, (point, color) ->
                drawLine(
                    color = color,
                    start = point,
                    end = point + Offset(if (index % 2 == 0) w * 0.045f else -w * 0.04f, h * 0.05f),
                    strokeWidth = w * 0.025f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
