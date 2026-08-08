package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.domain.model.MediaView
import com.jacksonkasi.cliplex.ui.components.ClipLexProgressBar
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes

@Composable
internal fun LearningTopBar(
    session: SessionEntity,
    playerState: LessonPlayerState,
    onBack: () -> Unit,
    onReanalyze: () -> Unit,
    onChangeLanguage: () -> Unit,
    processing: Boolean,
    optionsExpanded: Boolean,
    onOptionsExpandedChange: (Boolean) -> Unit,
    hasVideo: Boolean,
    onDeleteVideo: () -> Unit,
    onDeleteLesson: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClipLexColors.Surface)
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ClipLexColors.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text("Learning session", style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
                Text(
                    "${languageDisplayName(session.sourceLanguage)} → ${languageDisplayName(session.targetLanguage)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClipLexColors.GreenDark,
                    modifier = Modifier.clickable(onClick = onChangeLanguage),
                )
            }
            IconButton(onClick = onReanalyze, enabled = !processing) {
                Icon(Icons.Default.Refresh, contentDescription = "Re-analyze lesson", tint = ClipLexColors.Green)
            }
            Box {
                IconButton(onClick = { onOptionsExpandedChange(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Lesson options", tint = ClipLexColors.Ink)
                }
                DropdownMenu(
                    expanded = optionsExpanded,
                    onDismissRequest = { onOptionsExpandedChange(false) },
                ) {
                    if (hasVideo) {
                        DropdownMenuItem(
                            text = { Text("Delete video only") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                onOptionsExpandedChange(false)
                                onDeleteVideo()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete lesson") },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                        onClick = {
                            onOptionsExpandedChange(false)
                            onDeleteLesson()
                        },
                    )
                }
            }
        }
        ClipLexProgressBar(
            progress = if (playerState.durationMs > 0L) {
                playerState.positionMs.toFloat() / playerState.durationMs
            } else {
                0f
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            height = 6.dp,
        )
    }
}

@Composable
internal fun LessonSelectors(
    selectedMediaView: MediaView,
    hasVideo: Boolean,
    selectedMode: LearningDisplayMode,
    onMediaViewSelected: (MediaView) -> Unit,
    onModeSelected: (LearningDisplayMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaViewMenu(
            selectedView = selectedMediaView,
            hasVideo = hasVideo,
            onViewSelected = onMediaViewSelected,
            modifier = Modifier.weight(1f),
        )
        ModeMenu(
            selectedMode = selectedMode,
            onModeSelected = onModeSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ModeMenu(
    selectedMode: LearningDisplayMode,
    onModeSelected: (LearningDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            shape = ClipLexShapes.Pill,
            color = ClipLexColors.BlueSoft,
            contentColor = ClipLexColors.BlueDark,
            border = BorderStroke(1.dp, ClipLexColors.Blue.copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(selectedMode.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Default.ExpandMore, contentDescription = "Change learning mode", modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LearningDisplayMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(if (mode == selectedMode) "✓  ${mode.label}" else mode.label) },
                    onClick = {
                        expanded = false
                        onModeSelected(mode)
                    },
                )
            }
        }
    }
}

@Composable
internal fun MediaViewMenu(
    selectedView: MediaView,
    hasVideo: Boolean,
    onViewSelected: (MediaView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            shape = ClipLexShapes.Pill,
            color = ClipLexColors.GreenSoft,
            contentColor = ClipLexColors.GreenDark,
            border = BorderStroke(1.dp, ClipLexColors.Green.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().clickable(enabled = hasVideo) { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (selectedView == MediaView.VIDEO) Icons.Default.Videocam else Icons.Default.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(selectedView.label, style = MaterialTheme.typography.labelLarge)
                if (hasVideo) Icon(Icons.Default.ExpandMore, contentDescription = "Change media view", modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MediaView.entries.filter { it != MediaView.VIDEO || hasVideo }.forEach { view ->
                DropdownMenuItem(
                    text = { Text(if (view == selectedView) "✓  ${view.label}" else view.label) },
                    leadingIcon = {
                        Icon(
                            if (view == MediaView.VIDEO) Icons.Default.Videocam else Icons.Default.Headphones,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onViewSelected(view)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ProcessingPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = ClipLexShapes.Pill,
        color = Color.Black.copy(alpha = 0.68f),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(Modifier.width(7.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}
