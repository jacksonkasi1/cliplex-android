package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.service.CaptureService
import com.jacksonkasi.cliplex.ui.components.ClipLexActionButton
import com.jacksonkasi.cliplex.ui.components.ClipLexButtonStyle
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.components.ClipLexPill
import com.jacksonkasi.cliplex.ui.components.ClipLexProgressBar
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes
import kotlin.math.sin

@Composable
internal fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(ClipLexShapes.Small)
                    .background(ClipLexColors.Green),
                contentAlignment = Alignment.Center,
            ) {
                Text("L", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Clip", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                    Text("Lex", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Green)
                }
                Text("Learn from what you watch", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
            }
        }
        ClipLexIconBadge(
            icon = Icons.Default.Person,
            contentDescription = "Profile and settings",
            background = ClipLexColors.GreenSoft,
            contentColor = ClipLexColors.GreenDark,
            onClick = onOpenSettings,
        )
    }
}

@Composable
internal fun LanguageAndStreakRow(source: String, target: String, onChangeLanguage: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClipLexPill(
            text = "$source  →  $target",
            icon = Icons.Default.Translate,
            onClick = onChangeLanguage,
            modifier = Modifier.weight(1f),
            background = ClipLexColors.Surface,
        )
        ClipLexPill(
            text = "7 🔥",
            background = ClipLexColors.WarmSoft,
            contentColor = ClipLexColors.WarmDark,
            borderColor = ClipLexColors.Warm.copy(alpha = 0.25f),
        )
    }
}

@Composable
internal fun CaptureDashboard(
    isListening: Boolean,
    isBusy: Boolean,
    isModelReady: Boolean,
    durationMs: Long,
    captureState: CaptureService.CaptureState,
    primaryAction: () -> Unit,
) {
    val title = when {
        isBusy -> "Building your lesson…"
        isListening -> "I’m listening"
        captureState == CaptureService.CaptureState.Armed -> "Ready when the video starts"
        else -> "Turn any clip into a lesson"
    }
    val subtitle = when {
        isBusy -> "Transcript, translation and practice are being prepared on your phone."
        isListening -> "${formatElapsed(durationMs)} captured · tap Finish when your moment is complete."
        captureState == CaptureService.CaptureState.Armed -> "Start the video, then begin capture."
        else -> "Play a permitted video or audio clip and ClipLex will learn along with you."
    }
    val buttonText = when {
        isBusy -> "Preparing lesson…"
        isListening -> "Finish & create lesson"
        captureState == CaptureService.CaptureState.Armed -> "Start capture"
        else -> "Start listening"
    }
    val buttonIcon = if (isListening) Icons.Default.Stop else Icons.Default.Mic

    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = ClipLexColors.GreenWash,
        borderColor = ClipLexColors.Green.copy(alpha = 0.25f),
        depth = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LexiMascot(
                    mood = when {
                        isBusy -> LexiMood.THINKING
                        isListening -> LexiMood.LISTENING
                        else -> LexiMood.READY
                    },
                    modifier = Modifier.size(112.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isListening) "LIVE LESSON" else "TODAY’S MISSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isListening) ClipLexColors.Coral else ClipLexColors.GreenDark,
                    )
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = ClipLexColors.Ink)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
                }
            }

            ListeningWaveform(
                active = isListening,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )

            ClipLexActionButton(
                text = buttonText,
                icon = buttonIcon,
                onClick = primaryAction,
                enabled = isModelReady && !isBusy,
                style = if (isListening) ClipLexButtonStyle.DANGER else ClipLexButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    DailyProgressCard(
        completed = when {
            isBusy -> 2
            isListening -> 1
            captureState == CaptureService.CaptureState.Armed -> 1
            else -> 0
        },
    )
}

@Composable
internal fun ListeningWaveform(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "home-listening-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "home-wave-phase",
    )
    Canvas(modifier) {
        val count = 31
        val gap = size.width / count
        repeat(count) { index ->
            val animated = if (active) sin(phase + index * 0.62f) * 0.5f + 0.5f else sin(index * 1.21f) * 0.18f + 0.34f
            val barHeight = size.height * (0.14f + animated * 0.76f)
            drawLine(
                color = if (index % 6 == 0) ClipLexColors.GreenDark else ClipLexColors.Green.copy(alpha = 0.62f),
                start = Offset(gap * index + gap / 2f, (size.height - barHeight) / 2f),
                end = Offset(gap * index + gap / 2f, (size.height + barHeight) / 2f),
                strokeWidth = gap * 0.34f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun DailyProgressCard(completed: Int) {
    ClipLexCard(modifier = Modifier.fillMaxWidth(), depth = 2.dp) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            ClipLexIconBadge(
                icon = Icons.Default.AutoAwesome,
                contentDescription = null,
                background = ClipLexColors.BlueSoft,
                contentColor = ClipLexColors.Blue,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daily learning goal", style = MaterialTheme.typography.titleSmall, color = ClipLexColors.Ink)
                    Text("$completed / 3", style = MaterialTheme.typography.labelLarge, color = ClipLexColors.BlueDark)
                }
                ClipLexProgressBar(
                    progress = completed / 3f,
                    progressColor = ClipLexColors.Blue,
                    height = 10.dp,
                )
                Text("Capture · understand · practise", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
            }
        }
    }
}

@Composable
internal fun FirstLessonHint() {
    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = ClipLexColors.BlueSoft,
        borderColor = ClipLexColors.Blue.copy(alpha = 0.18f),
        depth = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            ClipLexIconBadge(
                icon = Icons.Default.PlayArrow,
                contentDescription = null,
                background = Color.White.copy(alpha = 0.78f),
                contentColor = ClipLexColors.Blue,
            )
            Column(Modifier.weight(1f)) {
                Text("Your first lesson starts with one clip", style = MaterialTheme.typography.titleSmall, color = ClipLexColors.Ink)
                Text("Choose a clear 10–30 second moment for the best learning experience.", style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
            }
        }
    }
}

internal fun compactLanguageName(name: String): String = if (name == "Any Language") "Any" else name

internal fun formatElapsed(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes == 0L) "${seconds}s" else "%d:%02d".format(minutes, seconds)
}
