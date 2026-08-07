package com.learnthis.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import com.learnthis.domain.model.ModelDownloadProgress
import com.learnthis.domain.model.ModelType
import com.learnthis.ui.viewmodel.ModelItemUiState
import com.learnthis.ui.viewmodel.ModelManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
	onBack: () -> Unit,
	showBackButton: Boolean = true,
	showSystemSettings: Boolean = false,
	overlayGranted: Boolean = false,
	onOpenOverlaySettings: () -> Unit = {},
	onOpenNotificationSettings: () -> Unit = {},
	onChangeLanguage: () -> Unit = {},
	modifier: Modifier = Modifier
) {
	val factory = (androidx.compose.ui.platform.LocalContext.current.applicationContext
			as com.learnthis.LearnThisApplication)
		.serviceLocator.modelManagementViewModelFactory

	val viewModel: ModelManagementViewModel = viewModel(factory = factory)
	val uiState by viewModel.uiState.collectAsState()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(if (showSystemSettings) "Settings" else "Speech model") },
				navigationIcon = {
					if (showBackButton) IconButton(onClick = onBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
					}
				}
			)
		}
	) { padding ->
		Column(
			modifier = modifier
				.fillMaxSize()
				.padding(padding)
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Text(
				text = "Choose one active model. Tiny is faster; Base can be more accurate.",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			if (uiState.isChecking) {
				Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
					CircularProgressIndicator()
				}
			} else {
				LazyColumn(
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier.weight(1f)
				) {
					items(uiState.models) { modelState ->
						ModelCard(
							modelState = modelState,
							isSelected = uiState.activeModel == modelState.modelType,
							onDownload = { viewModel.downloadModel(modelState.modelType) },
							onSelect = { viewModel.selectModel(modelState.modelType) },
							onDelete = { viewModel.deleteModel(modelState.modelType) }
						)
					}
				}

				if (showSystemSettings) {
					OutlinedButton(onClick = onChangeLanguage, modifier = Modifier.fillMaxWidth()) {
						Text("Change mother tongue")
					}
					Text("Floating control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
					Text(
						if (overlayGranted) "Allowed. The floating Learn This button will appear during Learning Mode."
						else "Optional. Enable this to use the floating button over YouTube or Instagram.",
						style = MaterialTheme.typography.bodySmall,
					)
					OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
						Text(if (overlayGranted) "Floating button settings" else "Enable floating button")
					}
					OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
						Text("Notification settings")
					}
				} else if (uiState.activeModel == null) {
					Text("Download a model to continue", color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
		}
	}
}

@Composable
private fun ModelCard(
	modelState: ModelItemUiState,
	isSelected: Boolean,
	onDownload: () -> Unit,
	onSelect: () -> Unit,
	onDelete: () -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			if (modelState.isDownloaded) RadioButton(selected = isSelected, onClick = onSelect)
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = modelState.modelType.displayName,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = if (modelState.modelType.isDefault) FontWeight.Bold else FontWeight.Normal
				)
				Spacer(modifier = Modifier.height(4.dp))

				when (val progress = modelState.progress) {
					is ModelDownloadProgress.Ready -> {
						Text(
							text = if (isSelected) "Selected" else "Downloaded",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.primary
						)
					}
					is ModelDownloadProgress.Downloading -> {
						val pct = if (progress.totalBytes > 0) {
							(progress.bytesDownloaded * 100 / progress.totalBytes).toInt()
						} else 0
						Text(
							text = "$pct% — ${formatBytes(progress.bytesDownloaded)} of ${formatBytes(progress.totalBytes)}",
							style = MaterialTheme.typography.bodySmall
						)
						LinearProgressIndicator(
							progress = { (progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f) },
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = 4.dp)
						)
					}
					is ModelDownloadProgress.Verifying -> {
						Text(
							text = progress.message,
							style = MaterialTheme.typography.bodySmall
						)
					}
					is ModelDownloadProgress.Error -> {
						Text(
							text = "Error: ${progress.message}",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.error
						)
					}
					is ModelDownloadProgress.Idle -> {
						Text(
							text = if (modelState.isDownloaded) "Downloaded" else "${formatBytes(modelState.modelType.fileSizeBytes)}",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}

			when {
				modelState.progress is ModelDownloadProgress.Ready -> {
					Column(horizontalAlignment = Alignment.End) {
						if (!isSelected) OutlinedButton(onClick = onSelect) { Text("Use") }
						IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
					}
				}
				modelState.progress is ModelDownloadProgress.Downloading -> {
					CircularProgressIndicator(
						progress = {
							(modelState.progress.bytesDownloaded.toFloat() / modelState.progress.totalBytes.toFloat()).coerceIn(0f, 1f)
						},
						modifier = Modifier.size(40.dp)
					)
				}
				else -> {
					Button(onClick = onDownload) {
						Icon(Icons.Default.Download, contentDescription = "Download")
						Text("Download")
					}
				}
			}
		}
	}
}

private fun formatBytes(bytes: Long): String {
	val mb = bytes / (1024.0 * 1024.0)
	return String.format("%.1f MB", mb)
}
