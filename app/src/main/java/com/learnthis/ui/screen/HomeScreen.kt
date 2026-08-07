package com.learnthis.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnthis.common.AppLanguage
import com.learnthis.BuildConfig
import com.learnthis.domain.model.TranscriptionSegment
import com.learnthis.domain.model.toUserMessage
import com.learnthis.service.CaptureService
import com.learnthis.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
	selectedLanguage: AppLanguage?,
	homeViewModel: HomeViewModel,
	onStartLearning: () -> Unit,
	onBeginCapture: () -> Unit,
	onChangeLanguage: () -> Unit,
	onOpenHistory: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val state by homeViewModel.uiState.collectAsState()
	Scaffold(
		topBar = { TopAppBar(
			title = { Text("Learn from any video") },
			actions = {
				TextButton(onClick = onOpenHistory) { Text("History") }
				TextButton(onClick = onChangeLanguage) { Text("Language") }
			},
		) },
		modifier = modifier,
	) { padding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			item { Spacer(Modifier.height(4.dp)) }
			item {
				Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Text("Mother tongue: ${selectedLanguage?.displayName ?: "Not selected"}", fontWeight = FontWeight.Bold)
					Text(if (state.isModelReady) "Speech model: ready" else "Speech model: loading…")
					state.modelError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
					Text("Audio is processed temporarily on this device and is not uploaded.", style = MaterialTheme.typography.bodySmall)
					if (BuildConfig.DEBUG) {
						TextButton(onClick = { homeViewModel.runKnownGoodAsrTest(context) }, enabled = state.isModelReady && !state.isProcessing) {
							Text("Run known-good ASR test")
						}
						state.debugMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
					}
				} }
			}
			item {
				Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(statusText(state.captureState, state.captureDurationMs, state.isProcessing))
					when (state.captureState) {
						CaptureService.CaptureState.Idle, is CaptureService.CaptureState.Error ->
							Button(onClick = onStartLearning, enabled = state.isModelReady) { Text("Start Learning Mode") }
						CaptureService.CaptureState.Armed -> Button(onClick = onBeginCapture) { Text("Start Capture") }
						CaptureService.CaptureState.Capturing -> Button(onClick = { homeViewModel.finishCapture(context) }) {
							Icon(Icons.Default.Stop, null); Text("Finish Capture")
						}
						CaptureService.CaptureState.Preparing, CaptureService.CaptureState.Processing -> CircularProgressIndicator()
					}
					if (state.captureState != CaptureService.CaptureState.Idle && state.captureState !is CaptureService.CaptureState.Error) {
						OutlinedButton(onClick = { homeViewModel.stopLearningMode(context) }) { Text("Stop Learning Mode") }
					}
				}
			}
			state.error?.let { error -> item {
				Card(Modifier.fillMaxWidth()) { Text(error.toUserMessage(), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
			} }
			state.audioHealth?.let { health -> item {
				Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
					Text("Audio diagnostics", fontWeight = FontWeight.Bold)
					Text("${health.durationMs / 1000f}s • RMS ${"%.0f".format(health.rmsLevel)} • peak ${"%.0f".format(health.peakAmplitude)}")
					Text("${"%.1f".format(health.dbfs)} dBFS • ${"%.1f".format(health.zeroSamplePercent)}% zeros")
					if (BuildConfig.DEBUG) {
						TextButton(onClick = { homeViewModel.saveDebugWav(context) }) { Text("Save latest 10 seconds as WAV") }
						state.debugMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
					}
				} }
			} }
			if (state.segments.isNotEmpty()) {
				item { Text("Learning result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
				items(state.segments, key = { "${it.startTimeMs}-${it.endTimeMs}-${it.text}" }) { segment ->
					SegmentCard(segment, onPlay = { homeViewModel.playSegment(segment) }, onCopy = { copy(context, segment) })
				}
			}
			item { Spacer(Modifier.height(24.dp)) }
		}
	}
}

private fun statusText(state: CaptureService.CaptureState, duration: Long, processing: Boolean): String = when {
	processing -> "Transcribing and translating on device…"
	state == CaptureService.CaptureState.Armed -> "Ready. Play a video, then tap the floating button or Start Capture."
	state == CaptureService.CaptureState.Capturing -> "Capturing playback audio… ${duration / 1000}s"
	state == CaptureService.CaptureState.Preparing -> "Preparing playback capture…"
	state is CaptureService.CaptureState.Error -> "Learning mode stopped with an error"
	else -> "Start Learning Mode to capture audio from a supported video app."
}

@Composable
private fun SegmentCard(segment: TranscriptionSegment, onPlay: () -> Unit, onCopy: () -> Unit) {
	Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(segment.text, fontWeight = FontWeight.Medium)
		segment.translatedText?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
		Text("${segment.language.ifBlank { "unknown" }} • ${segment.startTimeMs / 1000f}s–${segment.endTimeMs / 1000f}s", style = MaterialTheme.typography.bodySmall)
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
			IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, "Replay sentence") }
			IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy sentence") }
		}
	} }
}

private fun copy(context: Context, segment: TranscriptionSegment) {
	val text = listOfNotNull(segment.text, segment.translatedText).joinToString("\n")
	(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
		.setPrimaryClip(ClipData.newPlainText("Learn This sentence", text))
}
