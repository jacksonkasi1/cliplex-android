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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
	onFinishCapture: () -> Unit,
	onOpenHistory: () -> Unit,
	onOpenSettings: () -> Unit,
	overlayGranted: Boolean,
	setupMessage: String?,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val state by homeViewModel.uiState.collectAsState()
	var showDiagnostics by remember { mutableStateOf(false) }
	Scaffold(
		topBar = { TopAppBar(
			title = { Text("Learn from any video") },
			actions = {
				IconButton(onClick = onOpenHistory) { Icon(Icons.Default.History, "History") }
				IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
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
					Text(if (state.isModelReady) "Learning mode: ${state.modelName ?: "Selected"}" else "Speech model: loading…")
					state.modelError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
					Text(
						if (state.captureVideo) "Video lessons stay private in this app." else "Audio lessons stay private in this app.",
						style = MaterialTheme.typography.bodySmall,
					)
				} }
			}
			setupMessage?.let { message -> item {
				Card(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
			} }
			item {
				Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(statusText(
						state.captureState,
						state.captureDurationMs,
						state.isProcessing,
						state.processingStage,
						state.overlayStatus,
						overlayGranted,
						state.captureVideo,
					))
					when {
						state.isProcessing -> CircularProgressIndicator()
						state.captureState == CaptureService.CaptureState.Idle || state.captureState is CaptureService.CaptureState.Error ->
							Button(onClick = onStartLearning, enabled = state.isModelReady) { Text("Start Learning") }
						state.captureState == CaptureService.CaptureState.Armed -> Button(onClick = onBeginCapture) { Text("Start Capture") }
						state.captureState == CaptureService.CaptureState.Capturing -> Button(onClick = onFinishCapture) {
							Icon(Icons.Default.Stop, null); Text("Finish Capture")
						}
						else -> CircularProgressIndicator()
					}
					if (state.captureState != CaptureService.CaptureState.Idle && state.captureState !is CaptureService.CaptureState.Error) {
						OutlinedButton(onClick = { homeViewModel.stopLearningMode(context) }) { Text("Stop Learning Mode") }
					}
				}
			}
			state.error?.let { error -> item {
				Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
					Text(error.toUserMessage(), color = MaterialTheme.colorScheme.error)
					if (BuildConfig.DEBUG) state.captureErrorDetail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
				} }
			} }
			state.audioHealth?.let { health -> item {
				Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
					Text("Audio diagnostics", fontWeight = FontWeight.Bold)
					Text("${health.durationMs / 1000f}s • RMS ${"%.0f".format(health.rmsLevel)} • peak ${"%.0f".format(health.peakAmplitude)}")
					Text("${"%.1f".format(health.dbfs)} dBFS • ${"%.1f".format(health.zeroSamplePercent)}% zeros")
				} }
			} }
			if (BuildConfig.DEBUG) item {
				Card(Modifier.fillMaxWidth()) {
					Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedButton(onClick = { showDiagnostics = !showDiagnostics }, modifier = Modifier.fillMaxWidth()) {
							Text(if (showDiagnostics) "Hide technical diagnostics" else "Show technical diagnostics")
						}
						if (showDiagnostics) {
						Text("ASR diagnostics", fontWeight = FontWeight.Bold)
						state.debugMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
						state.asrDiagnostics?.let { diagnostics ->
							Text("Status: ${diagnostics.status}", color = if (diagnostics.status == "PASS" || diagnostics.status == "READY") {
								MaterialTheme.colorScheme.primary
							} else MaterialTheme.colorScheme.error)
							Text("Model: ${diagnostics.model}${diagnostics.modelFile?.let { " • $it" }.orEmpty()}")
							diagnostics.audioDurationMs?.let { duration ->
								Text("Audio: ${duration / 1000f}s • 16 kHz mono • RMS ${"%.0f".format(diagnostics.rms ?: 0f)}")
							}
							diagnostics.inferenceMs?.let { inference ->
								Text("Inference: ${"%.0f".format(inference)} ms • ${diagnostics.threadCount ?: 0} threads")
							}
							diagnostics.finishToSourceMs?.let { firstVisible ->
								Text("Finish to visible source: ${"%.0f".format(firstVisible)} ms")
							}
							diagnostics.translationMs?.let { translation -> Text("Translation: ${"%.0f".format(translation)} ms") }
							diagnostics.resultPreview?.let { Text("Result: “$it”", style = MaterialTheme.typography.bodySmall) }
							diagnostics.nativeBackend?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
						}
						OutlinedButton(
							onClick = { homeViewModel.runKnownGoodAsrTest(context) },
							enabled = state.isModelReady && !state.isProcessing,
							modifier = Modifier.fillMaxWidth(),
						) { Text("Run Known-Good English Test") }
						OutlinedButton(
							onClick = homeViewModel::runLastCapturedAsrTest,
							enabled = state.isModelReady && !state.isProcessing,
							modifier = Modifier.fillMaxWidth(),
						) { Text("Run Last Captured Audio Test") }
						OutlinedButton(onClick = { homeViewModel.saveDebugWav(context) }, modifier = Modifier.fillMaxWidth()) {
							Text("Save Captured WAV")
						}
						OutlinedButton(
							onClick = { state.asrDiagnostics?.let { copyText(context, "ASR diagnostics", it.asText()) } },
							enabled = state.asrDiagnostics != null,
							modifier = Modifier.fillMaxWidth(),
						) { Text("Copy ASR Diagnostics") }
						}
					}
				}
			}
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

private fun statusText(
	state: CaptureService.CaptureState,
	duration: Long,
	processing: Boolean,
	processingStage: String?,
	overlayStatus: CaptureService.OverlayStatus,
	overlayGranted: Boolean,
	captureVideo: Boolean,
): String = when {
	processing -> processingStage ?: "Processing on device…"
	state == CaptureService.CaptureState.Armed -> when (overlayStatus) {
		CaptureService.OverlayStatus.Visible -> "Ready. Open a video and tap the floating Learn This button."
		is CaptureService.OverlayStatus.Error -> "Ready, but the floating button could not be shown. Use Start Capture here or the notification."
		else -> "Ready. Play a video, then tap Start Capture${if (overlayGranted) " here while the floating button loads" else " here or in the notification"}."
	}
	state == CaptureService.CaptureState.Capturing -> "Capturing ${if (captureVideo) "video and playback audio" else "playback audio"}… ${duration / 1000}s. Tap the floating red button to finish."
	state == CaptureService.CaptureState.Preparing -> "Preparing playback capture…"
	state is CaptureService.CaptureState.Error -> "Learning mode stopped with an error"
	else -> "Start Learning to capture audio from a supported video app."
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
	copyText(context, "Learn This sentence", text)
}

private fun copyText(context: Context, label: String, text: String) {
	(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
		.setPrimaryClip(ClipData.newPlainText(label, text))
}
