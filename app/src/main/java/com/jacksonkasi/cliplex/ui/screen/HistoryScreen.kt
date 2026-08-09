package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
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
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes
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
                .background(ClipLexColors.Canvas)
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ClipLexColors.Ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Your lessons", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                Text("Captured moments ready to revisit", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
            }
            Text(
                "${uiState.sessions.size} lessons",
                style = MaterialTheme.typography.labelMedium,
                color = ClipLexColors.InkMuted,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
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
                    Text("Recent", style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ClipLexShapes.Hero,
        color = ClipLexColors.Night,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 19.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Learning archive", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.62f))
                Text(
                    if (lessonCount > 0) "Every useful moment stays within reach." else "Your first lesson will appear here.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricBlock(value = lessonCount.toString(), label = "lessons", modifier = Modifier.weight(1f))
                Box(Modifier.size(width = 1.dp, height = 44.dp).background(Color.White.copy(alpha = 0.16f)))
                MetricBlock(value = totalMinutes.toString(), label = "minutes", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = ClipLexColors.AccentBright)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
    }
}

@Composable
private fun EmptyHistoryState() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = ClipLexShapes.Hero, color = ClipLexColors.AccentWash) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LexiMascot(modifier = Modifier.size(124.dp), mood = LexiMood.READY)
            Text("No lessons yet", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
            Text(
                "Return home, play a permitted clip and start listening.",
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
        depth = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClipLexIconBadge(
                    icon = if (session.videoPath != null) Icons.Default.Videocam else Icons.Default.Headphones,
                    contentDescription = null,
                    background = ClipLexColors.SurfaceMuted,
                    contentColor = ClipLexColors.Accent,
                    size = 44.dp,
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
                        "${formatDate(session.createdAt)}  /  ${formatHistoryDuration(session.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ClipLexColors.InkMuted,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete lesson", tint = ClipLexColors.InkFaint, modifier = Modifier.size(19.dp))
                }
            }

            HorizontalDivider(color = ClipLexColors.Border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${session.segmentCount} moments",
                    style = MaterialTheme.typography.labelMedium,
                    color = ClipLexColors.InkMuted,
                )
                Text("/", style = MaterialTheme.typography.labelMedium, color = ClipLexColors.InkFaint)
                Text(
                    "${languageLabel(session.sourceLanguage)} → ${languageLabel(session.targetLanguage)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ClipLexColors.AccentStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = ClipLexShapes.Small, color = ClipLexColors.Accent, contentColor = Color.White) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Open lesson", modifier = Modifier.padding(8.dp).size(19.dp))
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
