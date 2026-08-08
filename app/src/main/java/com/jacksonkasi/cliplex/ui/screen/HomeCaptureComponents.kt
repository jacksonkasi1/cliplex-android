package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.service.CaptureService
import com.jacksonkasi.cliplex.ui.components.ClipLexActionButton
import com.jacksonkasi.cliplex.ui.components.ClipLexButtonStyle
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
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
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Clip", style = MaterialTheme.typography.headlineSmall, color = ClipLexColors.Ink)
                Text("Lex", style = MaterialTheme.typography.headlineSmall, color = ClipLexColors.Accent)
            }
            Text("Learn from the moments you watch", style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
        }
        ClipLexIconBadge(
            icon = Icons.Default.Person,
            contentDescription = "Profile and settings",
            background = ClipLexColors.SurfaceMuted,
            contentColor = ClipLexColors.InkSoft,
            size = 42.dp,
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
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onChangeLanguage),
            shape = ClipLexShapes.Control,
            color = ClipLexColors.Surface,
            contentColor = ClipLexColors.Ink,
            border = androidx.compose.foundation.BorderStroke(1.dp, ClipLexColors.Border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Translate, contentDescription = null, tint = ClipLexColors.Accent, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "$source  →  $target",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ClipLexColors.InkFaint, modifier = Modifier.size(19.dp))
            }
        }
        Surface(
            shape = ClipLexShapes.Control,
            color = ClipLexColors.WarmSoft,
            contentColor = ClipLexColors.WarmDark,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Today", style = MaterialTheme.typography.labelMedium)
            }
        }
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
        isBusy -> "Building your lesson"
        isListening -> "Capturing this moment"
        captureState == CaptureService.CaptureState.Armed -> "Ready for playback"
        else -> "Listen to a moment"
    }
    val subtitle = when {
        isBusy -> "Transcript, translation and practice are being prepared on this phone."
        isListening -> "Keep the clip focused. Finish when the useful sentence ends."
        captureState == CaptureService.CaptureState.Armed -> "Start the video, then begin capture."
        else -> "Play a clear video or audio clip and turn it into a private lesson."
    }
    val buttonText = when {
        isBusy -> "Preparing lesson"
        isListening -> "Finish and create lesson"
        captureState == CaptureService.CaptureState.Armed -> "Begin capture"
        else -> "Start listening"
    }
    val buttonIcon = if (isListening) Icons.Default.Stop else Icons.Default.Mic

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClipLexShapes.Hero)
            .background(
                Brush.verticalGradient(
                    colors = listOf(ClipLexColors.NightSoft, ClipLexColors.Night),
                ),
            )
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = ClipLexShapes.Small,
                    color = if (isListening) ClipLexColors.Coral.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                    contentColor = if (isListening) ClipLexColors.CoralSoft else Color.White.copy(alpha = 0.82f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(if (isListening) ClipLexColors.Coral else ClipLexColors.AccentBright, CircleShape),
                        )
                        Text(
                            when {
                                isBusy -> "Processing on device"
                                isListening -> "Listening live"
                                captureState == CaptureService.CaptureState.Armed -> "Capture ready"
                                else -> "Private on-device lesson"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (isListening) {
                    Text(formatElapsed(durationMs), style = MaterialTheme.typography.titleMedium, color = Color.White)
                } else {
                    Icon(Icons.Default.Headphones, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(23.dp))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ListeningWaveform(
                active = isListening,
                modifier = Modifier.fillMaxWidth().height(76.dp),
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
        val count = 39
        val gap = size.width / count
        repeat(count) { index ->
            val animated = if (active) {
                sin(phase + index * 0.58f) * 0.5f + 0.5f
            } else {
                sin(index * 0.92f) * 0.13f + 0.31f
            }
            val barHeight = size.height * (0.10f + animated * 0.74f)
            drawLine(
                color = if (active && index % 7 == 0) {
                    Color.White.copy(alpha = 0.92f)
                } else {
                    ClipLexColors.AccentBright.copy(alpha = if (active) 0.86f else 0.52f)
                },
                start = Offset(gap * index + gap / 2f, (size.height - barHeight) / 2f),
                end = Offset(gap * index + gap / 2f, (size.height + barHeight) / 2f),
                strokeWidth = gap * 0.27f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun DailyProgressCard(completed: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Today’s learning path", style = MaterialTheme.typography.titleSmall, color = ClipLexColors.Ink)
            Text("${completed.coerceIn(0, 3)} of 3", style = MaterialTheme.typography.labelMedium, color = ClipLexColors.InkMuted)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ClipLexShapes.Card,
            color = ClipLexColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ClipLexColors.Border),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LearningStep("Capture", 0, completed, Modifier.weight(1f))
                StepConnector(done = completed > 0)
                LearningStep("Understand", 1, completed, Modifier.weight(1f))
                StepConnector(done = completed > 1)
                LearningStep("Practice", 2, completed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LearningStep(label: String, index: Int, completed: Int, modifier: Modifier = Modifier) {
    val done = completed > index
    val active = completed == index
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    when {
                        done -> ClipLexColors.Accent
                        active -> ClipLexColors.AccentSoft
                        else -> ClipLexColors.SurfaceMuted
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                Box(
                    Modifier
                        .size(if (active) 8.dp else 6.dp)
                        .background(if (active) ClipLexColors.Accent else ClipLexColors.InkFaint, CircleShape),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (done || active) ClipLexColors.InkSoft else ClipLexColors.InkMuted,
        )
    }
}

@Composable
private fun StepConnector(done: Boolean) {
    Box(
        Modifier
            .width(18.dp)
            .height(1.dp)
            .background(if (done) ClipLexColors.Accent else ClipLexColors.BorderStrong),
    )
}

@Composable
internal fun FirstLessonHint() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ClipLexShapes.Card,
        color = ClipLexColors.AccentWash,
        contentColor = ClipLexColors.Ink,
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClipLexIconBadge(
                icon = Icons.Default.PlayArrow,
                contentDescription = null,
                background = ClipLexColors.Surface,
                contentColor = ClipLexColors.Accent,
                size = 40.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Start with one clear sentence", style = MaterialTheme.typography.titleSmall, color = ClipLexColors.Ink)
                Text("A focused 10 to 30 second clip creates a better first lesson.", style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
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
