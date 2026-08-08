package com.learnthis.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnthis.BuildConfig
import com.learnthis.domain.model.LearningLanguage
import com.learnthis.domain.model.ModelDownloadProgress
import com.learnthis.domain.model.SpeechQuality
import com.learnthis.ui.viewmodel.ModelManagementUiState
import com.learnthis.ui.viewmodel.ModelManagementViewModel

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
		as com.learnthis.LearnThisApplication).serviceLocator.modelManagementViewModelFactory
	val viewModel: ModelManagementViewModel = viewModel(factory = factory)
	val uiState by viewModel.uiState.collectAsState()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(if (showSystemSettings) "Settings" else "Download speech model") },
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
				"Only the speech model needed for your learning language is downloaded.",
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
					item(key = "required_speech_model") {
						RequiredSpeechModelCard(uiState, viewModel::downloadRequiredModel)
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
									"Save a private video lesson with captured playback audio. Turn off for audio-only lessons.",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							Switch(checked = uiState.captureVideo, onCheckedChange = viewModel::setCaptureVideo)
						}
					}
					OutlinedButton(onClick = onChangeLanguage, modifier = Modifier.fillMaxWidth()) {
						Text("Change learning and explanation languages")
					}
					Text("Floating control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
					Text(
						if (overlayGranted) "Allowed. The floating Learn This button appears during Learning Mode."
						else "Optional. Enable this to use the floating button over video apps.",
						style = MaterialTheme.typography.bodySmall,
					)
					OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
						Text(if (overlayGranted) "Floating button settings" else "Enable floating button")
					}
					OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
						Text("Notification settings")
					}
				} else if (!uiState.requiredModelReady) {
					Text("Download the selected model to continue", color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
		}
	}
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
				language == LearningLanguage.ENGLISH -> "Fast & Optimized"
				configuration.speechQuality == SpeechQuality.RECOMMENDED -> "Recommended · Better recognition"
				else -> "Fast · Faster processing"
			}
			Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
			Text(qualityDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

			if (BuildConfig.DEBUG) {
				Text(
					"${configuration.modelType.metadata.technicalName} • ${configuration.modelType.metadata.displaySize} • language=${configuration.transcriptionLanguage}",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}

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
