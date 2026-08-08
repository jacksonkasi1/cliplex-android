package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jacksonkasi.cliplex.BuildConfig
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.ModelDownloadProgress
import com.jacksonkasi.cliplex.domain.model.ModelType
import com.jacksonkasi.cliplex.domain.model.RecognitionMode
import com.jacksonkasi.cliplex.domain.model.SpeechQuality
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.viewmodel.ModelManagementUiState
import com.jacksonkasi.cliplex.ui.viewmodel.ModelManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
	showBackButton: Boolean = true,
	showSystemSettings: Boolean = false,
	overlayGranted: Boolean = false,
	onOpenOverlaySettings: () -> Unit = {},
	onOpenNotificationSettings: () -> Unit = {},
	onChangeLanguage: () -> Unit = {},
) {
	val factory = (androidx.compose.ui.platform.LocalContext.current.applicationContext
		as com.jacksonkasi.cliplex.ClipLexApplication).serviceLocator.modelManagementViewModelFactory
	val viewModel: ModelManagementViewModel = viewModel(factory = factory)
	val uiState by viewModel.uiState.collectAsState()

	Scaffold(
		topBar = {
			TopAppBar(
					title = { Text(if (showSystemSettings) "Advanced Settings" else "Download speech model") },
				navigationIcon = {
					if (showBackButton) IconButton(onClick = onBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
					}
				},
			)
		},
	) { padding ->
		Column(
			modifier = modifier.fillMaxSize().padding(padding).padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Text(
				if (showSystemSettings) "Speech and capture controls"
				else "Download an offline model",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)

			if (uiState.isChecking) {
				Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
			} else {
				LazyColumn(
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier.weight(1f),
				) {
					item(key = "recognition_engine") {
						RecognitionEngineCard(uiState, viewModel::selectRecognitionMode)
					}
					item(key = "model_catalog") {
						SpeechModelCatalog(uiState, viewModel::selectWhisperModel)
					}
					item(key = "required_speech_model") {
						RequiredSpeechModelCard(uiState, viewModel::downloadRequiredModel)
					}
					item(key = "downloaded_models") {
						DownloadedModelsCard(uiState, viewModel::deleteModel)
					}
				}

				if (showSystemSettings) {
					Card(Modifier.fillMaxWidth()) {
						Row(
							modifier = Modifier.fillMaxWidth().padding(16.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(12.dp),
						) {
							Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
								Text("Capture video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
								Text(
									"Save video with each lesson.",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							Switch(checked = uiState.captureVideo, onCheckedChange = viewModel::setCaptureVideo)
						}
					}
					OutlinedButton(onClick = onChangeLanguage, modifier = Modifier.fillMaxWidth()) {
						Text("Languages")
					}
					Text("Floating control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
					Text(
						if (overlayGranted) "Ready" else "Show the button over videos.",
						style = MaterialTheme.typography.bodySmall,
					)
					OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
						Text(if (overlayGranted) "Floating button" else "Enable floating button")
					}
					OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
						Text("Notifications")
					}
				} else if (!uiState.requiredModelReady) {
					Text("Download the selected model to continue", color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
		}
	}
}

@Composable
private fun SpeechModelCatalog(
	uiState: ModelManagementUiState,
	onSelect: (ModelType) -> Unit,
) {
	val language = uiState.selectedLearningLanguage ?: return
	Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
		Text("Whisper model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
		Text(
			if (language == LearningLanguage.ENGLISH) "English-only models"
			else "Larger = more accurate, but slower",
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		uiState.whisperModels.forEach { item ->
			val model = item.modelType
			val selected = uiState.configuration?.modelType == model
			ClipLexCard(
				modifier = Modifier.fillMaxWidth().clickable { onSelect(model) },
				containerColor = if (selected) ClipLexColors.GreenSoft else ClipLexColors.Surface,
			) {
				Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
						Text(model.friendlyName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
						Text(
							"${model.metadata.displaySize} · ${model.speedDescription()}" +
								if (item.isDownloaded) " · downloaded" else "",
							style = MaterialTheme.typography.bodySmall,
							color = if (selected) ClipLexColors.GreenDark else ClipLexColors.InkMuted,
						)
					}
					if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = ClipLexColors.Green)
				}
			}
		}
	}
}

@Composable
private fun RecognitionEngineCard(
	uiState: ModelManagementUiState,
	onSelect: (RecognitionMode) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
		Text("Recognition engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
		RecognitionMode.entries.forEach { mode ->
			val selected = uiState.recognitionMode == mode
			ClipLexCard(
				modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) },
				containerColor = if (selected) ClipLexColors.GreenSoft else ClipLexColors.Surface,
			) {
				Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
					Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
						Text(mode.displayName, fontWeight = FontWeight.Bold)
						Text(
							when (mode) {
								RecognitionMode.AUTOMATIC -> "Android, then Whisper if needed"
								RecognitionMode.ANDROID_ONLY -> "Phone's offline recognizer"
								RecognitionMode.WHISPER_ONLY -> "Always use selected Whisper model"
							},
							style = MaterialTheme.typography.bodySmall,
							color = ClipLexColors.InkMuted,
						)
					}
					if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = ClipLexColors.Green)
				}
			}
		}
	}
}

private fun ModelType.friendlyName(): String = when {
	name.startsWith("TINY") -> "Tiny${if (englishOnly) " English" else " Multilingual"}"
	name.startsWith("BASE") -> "Base${if (englishOnly) " English" else " Multilingual"}"
	name.startsWith("SMALL") -> "Small${if (englishOnly) " English" else " Multilingual"}"
	else -> "Medium${if (englishOnly) " English" else " Multilingual"}"
}

private fun ModelType.speedDescription(): String = when {
	name.startsWith("TINY") -> "fastest"
	name.startsWith("BASE") -> "balanced"
	name.startsWith("SMALL") -> "high accuracy"
	else -> "maximum accuracy · slowest"
}

@Composable
private fun RequiredSpeechModelCard(
	uiState: ModelManagementUiState,
	onDownload: () -> Unit,
) {
	val configuration = uiState.configuration
	Card(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			if (configuration == null) {
				Text("Speech model unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
				Text(
					uiState.error ?: "Choose a learning language to continue.",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
				)
				return@Column
			}

			val language = configuration.learningLanguage
			val title = if (language == LearningLanguage.ANY_LANGUAGE) {
				"Any-language learning"
			} else {
				"${language.displayName} learning"
			}
			val qualityDescription = when {
				configuration.modelType.name.startsWith("MEDIUM") -> "Maximum · strongest available mobile model"
				configuration.modelType.name.startsWith("SMALL") -> "High Accuracy · stronger recognition"
				configuration.modelType.name.startsWith("BASE") -> "Balanced · better recognition"
				else -> "Fast · quickest processing"
			}
			Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
			Text(qualityDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

			Text(
				"${configuration.modelType.metadata.technicalName} • ${configuration.modelType.metadata.displaySize}" +
					if (BuildConfig.DEBUG) " • language=${configuration.transcriptionLanguage}" else "",
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)

			when (val progress = uiState.requiredModelProgress) {
				ModelDownloadProgress.Ready -> Text(
					"Ready offline",
					color = MaterialTheme.colorScheme.primary,
				)
				is ModelDownloadProgress.Downloading -> {
					val fraction = if (progress.totalBytes > 0) {
						(progress.bytesDownloaded.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
					} else 0f
					Text("Downloading ${(fraction * 100).toInt()}%")
					LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
				}
				is ModelDownloadProgress.Verifying -> Text(progress.message)
				is ModelDownloadProgress.Error -> {
					Text(progress.message, color = MaterialTheme.colorScheme.error)
					Button(onClick = onDownload) { Text("Retry download") }
				}
				ModelDownloadProgress.Idle -> when {
					uiState.requiredModelDownloaded -> Text("Ready offline", color = MaterialTheme.colorScheme.primary)
					else -> Button(onClick = onDownload) {
						Icon(Icons.Default.Download, contentDescription = null)
						Text("Download ${configuration.modelType.metadata.displaySize}")
					}
				}
			}
		}
	}
}

@Composable
private fun DownloadedModelsCard(
	uiState: ModelManagementUiState,
	onRemove: (ModelType) -> Unit,
) {
	var pendingRemoval by remember { mutableStateOf<ModelType?>(null) }
	Card(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
			Text("Downloaded models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
			if (uiState.downloadedModels.isEmpty()) {
				Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
			} else {
				uiState.downloadedModels.sortedBy { it.expectedByteSize }.forEach { model ->
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Column(Modifier.weight(1f)) {
							Text(model.friendlyName(), fontWeight = FontWeight.SemiBold)
							Text(model.metadata.displaySize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
						OutlinedButton(onClick = { pendingRemoval = model }) { Text("Remove") }
					}
				}
			}
		}
	}
	pendingRemoval?.let { model ->
		AlertDialog(
			onDismissRequest = { pendingRemoval = null },
			title = { Text("Remove model?") },
			text = { Text("${model.friendlyName()} · ${model.metadata.displaySize}") },
			confirmButton = {
				TextButton(onClick = {
					pendingRemoval = null
					onRemove(model)
				}) { Text("Remove") }
			},
			dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } },
		)
	}
}
