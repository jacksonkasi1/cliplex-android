package com.jacksonkasi.cliplex.ui.screen

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.domain.model.MediaView
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes

@Composable
internal fun VideoHero(
    videoSource: String,
    playerState: LessonPlayerState,
    currentSegment: TranscriptionSegment?,
    displayPositionMs: Long,
    displayMode: LearningDisplayMode,
    processingStage: String?,
    onWordTap: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val aspectRatio = (playerState.videoAspectRatio ?: (9f / 16f)).coerceIn(9f / 16f, 16f / 9f)
        val heroHeight = (maxWidth / aspectRatio).coerceIn(240.dp, 520.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clip(ClipLexShapes.Hero)
                .background(ClipLexColors.Night),
        ) {
            AndroidView(
                factory = { context -> VideoView(context).also { playerState.bindVideo(it, videoSource) } },
                update = { playerState.bindVideo(it, videoSource) },
                modifier = Modifier.fillMaxSize(),
            )

            processingStage?.let {
                ProcessingPill(
                    text = it,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                )
            }

            SubtitleSurface(
                segment = currentSegment,
                positionMs = displayPositionMs,
                mode = displayMode,
                onWordTap = onWordTap,
                compactVideo = true,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun AudioHero(
    state: LessonPlayerState,
    title: String,
    currentSegment: TranscriptionSegment?,
    displayPositionMs: Long,
    displayMode: LearningDisplayMode,
    processingStage: String?,
    playbackError: String?,
    onWordTap: (String) -> Unit,
    isScrubbing: Boolean,
    scrubPositionMs: Float,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(ClipLexShapes.Hero)
            .background(Brush.verticalGradient(listOf(ClipLexColors.NightSoft, ClipLexColors.Night)))
            .padding(18.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClipLexIconBadge(
                    icon = Icons.Default.Headphones,
                    contentDescription = null,
                    background = Color.White.copy(alpha = 0.10f),
                    contentColor = ClipLexColors.AccentBright,
                    size = 43.dp,
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Audio lesson", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                processingStage?.let { ProcessingPill(text = it) }
            }

            AudioWaveform(
                progress = if (state.durationMs > 0L) displayPositionMs.toFloat() / state.durationMs else 0f,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )

            PlaybackControls(
                state = state,
                isScrubbing = isScrubbing,
                scrubPositionMs = scrubPositionMs,
                onScrubChange = onScrubChange,
                onScrubFinished = onScrubFinished,
                dark = true,
            )

            playbackError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = ClipLexColors.CoralSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                )
            }

            SubtitleSurface(
                segment = currentSegment,
                positionMs = displayPositionMs,
                mode = displayMode,
                onWordTap = onWordTap,
                compactVideo = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun LessonOverviewCard(session: SessionEntity, mediaView: MediaView) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = ClipLexShapes.Card,
        color = ClipLexColors.AccentWash,
        contentColor = ClipLexColors.Ink,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClipLexIconBadge(
                icon = if (mediaView == MediaView.VIDEO) Icons.Default.Videocam else Icons.Default.Headphones,
                contentDescription = null,
                background = ClipLexColors.Surface,
                contentColor = ClipLexColors.Accent,
                size = 42.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    session.title.ifBlank { "Captured lesson" },
                    style = MaterialTheme.typography.titleMedium,
                    color = ClipLexColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatDuration(session.durationMs)}  /  ${session.segmentCount} moments  /  ${mediaView.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ClipLexColors.InkMuted,
                )
            }
        }
    }
}

@Composable
internal fun AudioWaveform(progress: Float, modifier: Modifier = Modifier) {
    val heights = listOf(12, 21, 16, 30, 18, 25, 34, 17, 28, 20, 36, 23, 15, 31, 19, 27, 35, 18, 25, 14, 29, 21, 33, 17)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { index, barHeight ->
            val played = index.toFloat() / heights.size <= progress.coerceIn(0f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height(barHeight.dp)
                    .background(if (played) ClipLexColors.AccentBright else Color.White.copy(alpha = 0.18f), CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackControls(
    state: LessonPlayerState,
    isScrubbing: Boolean,
    scrubPositionMs: Float,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    val displayedPosition = if (isScrubbing) scrubPositionMs else state.positionMs.toFloat()
    val foreground = if (dark) Color.White else ClipLexColors.Ink
    val muted = if (dark) Color.White.copy(alpha = 0.58f) else ClipLexColors.InkMuted
    val sliderColors = SliderDefaults.colors(
        thumbColor = ClipLexColors.AccentBright,
        activeTrackColor = ClipLexColors.AccentBright,
        inactiveTrackColor = muted.copy(alpha = 0.24f),
    )
    val sliderInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (dark) Modifier else Modifier.background(ClipLexColors.Surface, ClipLexShapes.Control))
            .padding(horizontal = if (dark) 0.dp else 9.dp, vertical = if (dark) 2.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = ClipLexColors.Accent, contentColor = Color.White, shadowElevation = 1.dp) {
            IconButton(onClick = state::togglePlayback, enabled = state.isPrepared, modifier = Modifier.size(46.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause lesson" else "Play lesson",
                    modifier = Modifier.size(27.dp),
                )
            }
        }
        IconButton(onClick = { state.seekBy(-10_000L) }, enabled = state.isPrepared, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds", tint = foreground, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 5.dp)) {
            Slider(
                value = displayedPosition.coerceIn(0f, duration.toFloat()),
                onValueChange = onScrubChange,
                onValueChangeFinished = onScrubFinished,
                enabled = state.isPrepared && state.durationMs > 0L,
                valueRange = 0f..duration.toFloat(),
                colors = sliderColors,
                interactionSource = sliderInteractionSource,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = sliderInteractionSource,
                        modifier = Modifier.size(13.dp),
                        colors = sliderColors,
                        enabled = state.isPrepared,
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(3.dp),
                        colors = sliderColors,
                        enabled = state.isPrepared,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(22.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(displayedPosition.toLong()), style = MaterialTheme.typography.labelSmall, color = muted)
                Text(
                    "-${formatDuration((state.durationMs - displayedPosition.toLong()).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
        IconButton(onClick = { state.seekBy(10_000L) }, enabled = state.isPrepared, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = foreground, modifier = Modifier.size(21.dp))
        }
    }
}
