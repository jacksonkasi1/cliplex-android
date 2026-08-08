package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.ui.viewmodel.SettingsViewModel
import com.jacksonkasi.cliplex.translation.TranslationEngine
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
 onBack: () -> Unit,
 settingsViewModel: SettingsViewModel = viewModel(),
) {
 val uiState by settingsViewModel.uiState.collectAsState()

 Column(modifier = Modifier.fillMaxSize()) {
 TopAppBar(
 title = { Text("Settings") },
 colors = TopAppBarDefaults.topAppBarColors(
 containerColor = MaterialTheme.colorScheme.primaryContainer,
 titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
 ),
 navigationIcon = {
 IconButton(onClick = onBack) {
 Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
 }
 }
 )

 Column(
 modifier = Modifier
 .fillMaxSize()
 .verticalScroll(rememberScrollState())
 .padding(16.dp),
 verticalArrangement = Arrangement.spacedBy(12.dp),
 ) {
 Card(
 modifier = Modifier.fillMaxWidth(),
 colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
 ) {
 Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
 Text("Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
 AppLanguage.entries.filter { it.tag in TranslationEngine.supportedLanguages() }.forEach { language ->
 Row(
 modifier = Modifier.fillMaxWidth(),
 horizontalArrangement = Arrangement.SpaceBetween,
 verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
 ) {
 Text(language.displayName)
 Switch(
 checked = uiState.selectedLanguage == language,
 onCheckedChange = { settingsViewModel.selectLanguage(language) },
 )
 }
 }
 }
 }

 Card(
 modifier = Modifier.fillMaxWidth(),
 colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
 ) {
 Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
 Text("Capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
 Row(
 modifier = Modifier.fillMaxWidth(),
 horizontalArrangement = Arrangement.SpaceBetween,
 verticalAlignment = Alignment.CenterVertically,
 ) {
 Column(Modifier.weight(1f).padding(end = 12.dp)) {
 Text("Capture video with learning sessions")
 Text(
 "Keep the video clip so you can review it while learning.",
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }
 Switch(
 checked = uiState.captureVideo,
 onCheckedChange = settingsViewModel::setCaptureVideo,
 )
 }
 Row(
 modifier = Modifier.fillMaxWidth(),
 horizontalArrangement = Arrangement.SpaceBetween,
 verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
 ) {
 Text("Auto-translate after capture")
 Switch(
 checked = uiState.autoTranslate,
 onCheckedChange = { settingsViewModel.setAutoTranslate(it) },
 )
 }
 }
 }
 }
 }
}
