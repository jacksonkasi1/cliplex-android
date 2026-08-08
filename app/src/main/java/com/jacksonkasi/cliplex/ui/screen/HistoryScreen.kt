package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.components.ClipLexProgressBar
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit = {},
    historyViewModel: HistoryViewModel = viewModel(),
) {
    val uiState by historyViewModel.uiState.collectAsState()
    val totalMinutes = uiState.sessions.sumOf { it.durationMs }.coerceAtLeast(0L) / 60_000L

    Column(modifier = Modifier.fillMaxSize().background(ClipLexColors.Canvas)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClipLexColors.Surface)
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ClipLexColors.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text("Learning trail", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                Text("Every clip you turned into progress", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
            }
            Surface(shape = CircleShape, color = ClipLexColors.WarmSoft, contentColor = ClipLexColors.WarmDark) {
                Text("${uiState.sessions.size} ★", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "history_summary") {
                HistorySummaryCard(
                    lessonCount = uiState.sessions.size,
                    totalMinutes = totalMinutes,
                )
            }

            if (uiState.sessions.isEmpty()) {
                item(key = "empty") { EmptyHistoryState() }
            } else {
                item(key = "title") {
                    Text("Your lessons", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                }
                items(uiState.sessions, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
                        onOpen = { onOpenSession(session.id) },
                        onDelete = { historyViewModel.deleteSession(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(lessonCount: Int, totalMinutes: Long) {
    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = ClipLexColors.GreenWash,
        borderColor = ClipLexColors.Green.copy(alpha = 0.22f),
        depth = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LexiMascot(
                modifier = Modifier.size(104.dp),
                mood = if (lessonCount > 0) LexiMood.CELEBRATING else LexiMood.READY,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (lessonCount > 0) "Look at that progress!" else "Your trail starts here",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ClipLexColors.Ink,
                )
                Text(
                    if (lessonCount > 0) "$lessonCount lessons · $totalMinutes minutes captured" else "Capture one clear moment and ClipLex will build your first lesson.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ClipLexColors.InkMuted,
                )
                ClipLexProgressBar(
                    progress = (lessonCount.coerceAtMost(7) / 7f),
                    progressColor = ClipLexColors.Green,
                    height = 9.dp,
                )
                Text("Weekly goal · $lessonCount / 7 lessons", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.GreenDark)
            }
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    ClipLexCard(modifier = Modifier.fillMaxWidth(), depth = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LexiMascot(modifier = Modifier.size(130.dp), mood = LexiMood.READY)
            Text("No lessons yet", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
            Text(
                "Return home, play a permitted clip and tap Start listening.",
                style = MaterialTheme.typography.bodyMedium,
                color = ClipLexColors.InkMuted,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
    }
}

@Composable
private fun SessionItem(
    session: SessionEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClipLexCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        depth = 3.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClipLexIconBadge(
                    icon = if (session.videoPath != null) Icons.Default.Videocam else Icons.Default.Headphones,
                    contentDescription = null,
                    background = if (session.videoPath != null) ClipLexColors.GreenSoft else ClipLexColors.BlueSoft,
                    contentColor = if (session.videoPath != null) ClipLexColors.Green else ClipLexColors.Blue,
                    size = 48.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        session.title.ifBlank { "Captured lesson" },
                        style = MaterialTheme.typography.titleMedium,
                        color = ClipLexColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatDate(session.createdAt)} · ${formatHistoryDuration(session.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ClipLexColors.InkMuted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete lesson", tint = ClipLexColors.InkFaint, modifier = Modifier.size(20.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = CircleShape, color = ClipLexColors.SurfaceMuted, contentColor = ClipLexColors.InkMuted) {
                    Text("${session.segmentCount} moments", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Surface(shape = CircleShape, color = ClipLexColors.GreenSoft, contentColor = ClipLexColors.GreenDark) {
                    Text("${languageLabel(session.sourceLanguage)} → ${languageLabel(session.targetLanguage)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(36.dp).background(ClipLexColors.Green, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Open lesson", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String = runCatching {
    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
}.getOrDefault("Recent")

private fun formatHistoryDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
}

private fun languageLabel(tag: String): String = tag
    .takeIf(String::isNotBlank)
    ?.let { Locale.forLanguageTag(it).displayLanguage }
    ?.takeIf(String::isNotBlank)
    ?: "Auto"
