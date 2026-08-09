package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jacksonkasi.cliplex.BuildConfig
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.ModelDownloadProgress
import com.jacksonkasi.cliplex.domain.model.ModelType
import com.jacksonkasi.cliplex.domain.model.RecognitionMode
import com.jacksonkasi.cliplex.ui.components.ClipLexActionButton
import com.jacksonkasi.cliplex.ui.components.ClipLexButtonStyle
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes
import com.jacksonkasi.cliplex.ui.viewmodel.ModelManagementUiState
import com.jacksonkasi.cliplex.ui.viewmodel.ModelManagementViewModel

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

    Column(modifier = modifier.fillMaxSize().background(ClipLexColors.Canvas)) {
        SettingsTopBar(
            title = if (showSystemSettings) "Profile & settings" else "Offline speech",
            showBackButton = showBackButton,
            onBack = onBack,
        )

        if (uiState.isChecking) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ClipLexColors.Green)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "settings_hero") {
                    SettingsHero(showSystemSettings, uiState)
                }
                item(key = "recognition_title") { SettingsSectionTitle("Recognition engine", "Choose how speech is recognized") }
                item(key = "recognition_engine") {
                    RecognitionEngineCards(uiState, viewModel::selectRecognitionMode)
                }
                item(key = "model_title") { SettingsSectionTitle("Whisper model", "Download only what this device needs") }
                item(key = "model_catalog") {
                    SpeechModelCatalog(uiState, viewModel::selectWhisperModel)
                }
                item(key = "required_speech_model") {
                    RequiredSpeechModelCard(uiState, viewModel::downloadRequiredModel)
                }
                item(key = "downloaded_models") {
                    DownloadedModelsCard(uiState, viewModel::deleteModel)
                }

                if (showSystemSettings) {
                    item(key = "capture_title") { SettingsSectionTitle("Capture experience", "Control what ClipLex saves") }
                    item(key = "capture_video") {
                        SettingToggleCard(
                            icon = Icons.Default.Videocam,
                            title = "Capture video",
                            subtitle = "Keep the visual clip with each lesson. Audio is always retained for learning.",
                            checked = uiState.captureVideo,
                            onCheckedChange = viewModel::setCaptureVideo,
                        )
                    }
                    item(key = "language") {
                        SettingsActionCard(
                            icon = Icons.Default.Language,
                            title = "Learning languages",
                            subtitle = "Change the language you learn and the language used for explanations.",
                            action = "Change",
                            onClick = onChangeLanguage,
                        )
                    }
                    item(key = "floating") {
                        SettingsActionCard(
                            icon = Icons.Default.AutoAwesome,
                            title = "Floating capture control",
                            subtitle = if (overlayGranted) "Ready to appear over permitted video apps." else "Allow ClipLex to show a small capture button over videos.",
                            action = if (overlayGranted) "Open" else "Enable",
                            onClick = onOpenOverlaySettings,
                        )
                    }
                    item(key = "notifications") {
                        SettingsActionCard(
                            icon = Icons.Default.Notifications,
                            title = "Notifications",
                            subtitle = "Manage capture and processing notifications in Android settings.",
                            action = "Manage",
                            onClick = onOpenNotificationSettings,
                        )
                    }
                    item(key = "privacy") {
                        ClipLexCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = ClipLexColors.GreenWash,
                            borderColor = ClipLexColors.Green.copy(alpha = 0.2f),
                            depth = 2.dp,
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("PRIVATE BY DESIGN", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.GreenDark)
                                Text("Your clips and learning data stay on this phone", style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink, modifier = Modifier.padding(top = 5.dp))
                                Text("Model downloads come from their published sources; transcription, translation and practice run locally.", style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }

                item(key = "bottom_space") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, showBackButton: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClipLexColors.Surface)
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBackButton) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ClipLexColors.Ink)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink, modifier = Modifier.weight(1f))
        ClipLexIconBadge(
            icon = Icons.Default.Settings,
            contentDescription = null,
            background = ClipLexColors.SurfaceMuted,
            contentColor = ClipLexColors.InkMuted,
            size = 40.dp,
        )
    }
}

@Composable
private fun SettingsHero(showSystemSettings: Boolean, uiState: ModelManagementUiState) {
    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = ClipLexColors.BlueSoft,
        borderColor = ClipLexColors.Blue.copy(alpha = 0.2f),
        depth = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LexiMascot(modifier = Modifier.size(104.dp), mood = LexiMood.READY)
            Column(Modifier.weight(1f)) {
                Text(
                    if (showSystemSettings) "Make ClipLex yours" else "Speech that stays with you",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ClipLexColors.Ink,
                )
                Text(
                    if (showSystemSettings) "Fine-tune capture, language and offline AI without cluttering your lessons."
                    else "Android recognition comes first; Whisper is ready as the private offline fallback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ClipLexColors.InkMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.78f),
                    contentColor = if (uiState.requiredModelReady) ClipLexColors.GreenDark else ClipLexColors.WarmDark,
                    modifier = Modifier.padding(top = 9.dp),
                ) {
                    Text(
                        if (uiState.requiredModelReady) "✓ Offline model ready" else "Offline model setup needed",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun RecognitionEngineCards(uiState: ModelManagementUiState, onSelect: (RecognitionMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RecognitionMode.entries.forEach { mode ->
            val selected = uiState.recognitionMode == mode
            ClipLexCard(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) },
                containerColor = if (selected) ClipLexColors.GreenSoft else ClipLexColors.Surface,
                borderColor = if (selected) ClipLexColors.Green else ClipLexColors.Border,
                depth = if (selected) 4.dp else 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ClipLexIconBadge(
                        icon = if (mode == RecognitionMode.AUTOMATIC) Icons.Default.AutoAwesome else Icons.Default.SmartToy,
                        contentDescription = null,
                        background = if (selected) Color.White.copy(alpha = 0.78f) else ClipLexColors.SurfaceMuted,
                        contentColor = if (selected) ClipLexColors.Green else ClipLexColors.InkMuted,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(mode.displayName, style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
                        Text(
                            when (mode) {
                                RecognitionMode.AUTOMATIC -> "Android first, then Whisper only when needed"
                                RecognitionMode.ANDROID_ONLY -> "Use the phone’s offline recognizer"
                                RecognitionMode.WHISPER_ONLY -> "Always use the selected Whisper model"
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

@Composable
private fun SpeechModelCatalog(uiState: ModelManagementUiState, onSelect: (ModelType) -> Unit) {
    val language = uiState.selectedLearningLanguage ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        uiState.whisperModels.forEach { item ->
            val model = item.modelType
            val selected = uiState.configuration?.modelType == model
            ClipLexCard(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(model) },
                containerColor = if (selected) ClipLexColors.PurpleSoft else ClipLexColors.Surface,
                borderColor = if (selected) ClipLexColors.Purple else ClipLexColors.Border,
                depth = if (selected) 4.dp else 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ClipLexIconBadge(
                        icon = Icons.Default.Memory,
                        contentDescription = null,
                        background = if (selected) Color.White.copy(alpha = 0.78f) else ClipLexColors.PurpleSoft,
                        contentColor = ClipLexColors.Purple,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(model.friendlyName(), style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
                        Text(
                            "${model.metadata.displaySize} · ${model.speedDescription()}${if (item.isDownloaded) " · downloaded" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClipLexColors.InkMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (language == LearningLanguage.ENGLISH && model.englishOnly) {
                            Text("Optimized for English", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.GreenDark)
                        }
                    }
                    if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = ClipLexColors.Purple)
                }
            }
        }
    }
}

@Composable
private fun RequiredSpeechModelCard(uiState: ModelManagementUiState, onDownload: () -> Unit) {
    val configuration = uiState.configuration
    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (uiState.requiredModelReady) ClipLexColors.GreenWash else ClipLexColors.WarmSoft,
        borderColor = if (uiState.requiredModelReady) ClipLexColors.Green.copy(alpha = 0.3f) else ClipLexColors.Warm.copy(alpha = 0.3f),
        depth = 3.dp,
    ) {
        Column(Modifier.padding(17.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (configuration == null) {
                Text("Speech model unavailable", style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
                Text(uiState.error ?: "Choose a learning language to continue.", style = MaterialTheme.typography.bodySmall, color = ClipLexColors.CoralDark)
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Selected offline model", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
                    Text(configuration.modelType.friendlyName(), style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                    Text(
                        "${configuration.modelType.metadata.displaySize} · ${configuration.modelType.speedDescription()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ClipLexColors.InkMuted,
                    )
                }
                ClipLexIconBadge(
                    icon = if (uiState.requiredModelReady) Icons.Default.Check else Icons.Default.Download,
                    contentDescription = null,
                    background = Color.White.copy(alpha = 0.78f),
                    contentColor = if (uiState.requiredModelReady) ClipLexColors.Green else ClipLexColors.Warm,
                )
            }

            if (BuildConfig.DEBUG) {
                Text(
                    "${configuration.modelType.metadata.technicalName} · language=${configuration.transcriptionLanguage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClipLexColors.InkMuted,
                )
            }

            when (val progress = uiState.requiredModelProgress) {
                ModelDownloadProgress.Ready -> Text("Ready offline", color = ClipLexColors.GreenDark, style = MaterialTheme.typography.labelLarge)
                is ModelDownloadProgress.Downloading -> {
                    val fraction = if (progress.totalBytes > 0) {
                        (progress.bytesDownloaded.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
                    } else 0f
                    Text("Downloading ${(fraction * 100).toInt()}%", color = ClipLexColors.BlueDark)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = ClipLexColors.Blue,
                        trackColor = Color.White.copy(alpha = 0.72f),
                    )
                }
                is ModelDownloadProgress.Verifying -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ClipLexColors.Blue)
                        Spacer(Modifier.width(9.dp))
                        Text(progress.message, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
                    }
                }
                is ModelDownloadProgress.Error -> {
                    Text(progress.message, color = ClipLexColors.CoralDark, style = MaterialTheme.typography.bodySmall)
                    ClipLexActionButton(
                        text = "Retry download",
                        icon = Icons.Default.Download,
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ModelDownloadProgress.Idle -> if (uiState.requiredModelDownloaded) {
                    Text("Ready offline", color = ClipLexColors.GreenDark, style = MaterialTheme.typography.labelLarge)
                } else {
                    ClipLexActionButton(
                        text = "Download ${configuration.modelType.metadata.displaySize}",
                        icon = Icons.Default.Download,
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedModelsCard(uiState: ModelManagementUiState, onRemove: (ModelType) -> Unit) {
    var pendingRemoval by remember { mutableStateOf<ModelType?>(null) }
    ClipLexCard(modifier = Modifier.fillMaxWidth(), depth = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Downloaded models", style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
            if (uiState.downloadedModels.isEmpty()) {
                Text("No optional Whisper models downloaded yet.", color = ClipLexColors.InkMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                uiState.downloadedModels.sortedBy { it.expectedByteSize }.forEach { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ClipLexIconBadge(
                            icon = Icons.Default.Memory,
                            contentDescription = null,
                            background = ClipLexColors.SurfaceMuted,
                            contentColor = ClipLexColors.InkMuted,
                            size = 40.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(model.friendlyName(), style = MaterialTheme.typography.titleSmall, color = ClipLexColors.Ink)
                            Text(model.metadata.displaySize, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
                        }
                        IconButton(onClick = { pendingRemoval = model }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove model", tint = ClipLexColors.Coral)
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ClipLexColors.Coral) },
            title = { Text("Remove model?") },
            text = { Text("${model.friendlyName()} · ${model.metadata.displaySize}. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    onRemove(model)
                }) { Text("Remove", color = ClipLexColors.Coral) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SettingToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ClipLexCard(modifier = Modifier.fillMaxWidth(), depth = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClipLexIconBadge(
                icon = icon,
                contentDescription = null,
                background = ClipLexColors.GreenSoft,
                contentColor = ClipLexColors.Green,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
) {
    ClipLexCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), depth = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClipLexIconBadge(
                icon = icon,
                contentDescription = null,
                background = ClipLexColors.BlueSoft,
                contentColor = ClipLexColors.Blue,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
            }
            Surface(shape = CircleShape, color = ClipLexColors.BlueSoft, contentColor = ClipLexColors.BlueDark) {
                Text(action, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
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
