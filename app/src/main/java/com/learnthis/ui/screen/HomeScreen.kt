package com.learnthis.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnthis.common.AppLanguage
import com.learnthis.domain.model.TranscriptionSegment
import com.learnthis.ui.navigation.LearnThisDestination
import com.learnthis.ui.viewmodel.HomeUiState
import com.learnthis.ui.viewmodel.HomeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
 selectedLanguage: AppLanguage?,
 onStartLearning: () -> Unit,
 onChangeLanguage: () -> Unit,
 modifier: Modifier = Modifier,
) {
 val context = androidx.compose.ui.platform.LocalContext.current
 val homeViewModel: HomeViewModel = viewModel(
 factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
 context.applicationContext as android.app.Application
 )
 )
 val uiState by homeViewModel.uiState.collectAsState()

 Scaffold(
 topBar = {
 TopAppBar(
 title = { Text("Learn This") },
 colors = TopAppBarDefaults.topAppBarColors(
 containerColor = MaterialTheme.colorScheme.primaryContainer,
 titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
 ),
 navigationIcon = {
 IconButton(onClick = onChangeLanguage) {
 Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Change language")
 }
 }
 )
 },
 modifier = modifier,
 ) { paddingValues ->
 Column(
 modifier = Modifier
 .fillMaxSize()
 .padding(paddingValues)
 .padding(16.dp),
 verticalArrangement = Arrangement.spacedBy(16.dp),
 ) {
 Card(
 modifier = Modifier.fillMaxWidth(),
 colors = CardDefaults.cardColors(
 containerColor = MaterialTheme.colorScheme.secondaryContainer
 ),
 ) {
 Row(
 modifier = Modifier.padding(16.dp),
 verticalAlignment = Alignment.CenterVertically,
 ) {
 Text(
 text = "Learning: ${selectedLanguage?.displayName ?: "Not selected"}",
 style = MaterialTheme.typography.titleMedium,
 fontWeight = FontWeight.Bold,
 )
 }
 }

 if (uiState.segments.isNotEmpty()) {
 Text(
 text = "Detected Sentences",
 style = MaterialTheme.typography.titleMedium,
 fontWeight = FontWeight.Bold,
 )
 LazyColumn(
 modifier = Modifier.height(400.dp),
 verticalArrangement = Arrangement.spacedBy(8.dp),
 ) {
 items(uiState.segments, key = { it.startTimeMs }) { segment ->
 SegmentCard(
 segment = segment,
 isTranslating = uiState.translatingIds.contains(segment.startTimeMs),
 onTranslate = { homeViewModel.translateSegment(segment) },
 )
 }
 }
 }

 val statusText = if (uiState.isCapturing) "Listening... Speak now" else "Tap the microphone to start learning"
 Box(
 modifier = Modifier.fillMaxWidth(),
 contentAlignment = Alignment.Center,
 ) {
 Text(
 text = statusText,
 style = MaterialTheme.typography.bodyLarge,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }

 Row(
 modifier = Modifier.fillMaxWidth(),
 horizontalArrangement = Arrangement.Center,
 ) {
 if (uiState.isCapturing) {
 Button(
 onClick = { homeViewModel.stopCapture(context) },
 colors = ButtonDefaults.buttonColors(
 containerColor = MaterialTheme.colorScheme.error,
 ),
 ) {
 Icon(Icons.Default.Stop, contentDescription = null)
 Spacer(modifier = Modifier.padding(horizontal = 8.dp))
 Text("Stop")
 }
 } else {
 Button(
 onClick = onStartLearning,
 ) {
 Icon(Icons.Default.Mic, contentDescription = null)
 Spacer(modifier = Modifier.padding(horizontal = 8.dp))
 Text("Start Listening")
 }
 }
 }
 }
 }
}

@Composable
fun SegmentCard(
 segment: TranscriptionSegment,
 isTranslating: Boolean,
 onTranslate: () -> Unit,
 modifier: Modifier = Modifier,
) {
 Card(
 modifier = modifier.fillMaxWidth(),
 elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
 ) {
 Column(
 modifier = Modifier.padding(16.dp),
 verticalArrangement = Arrangement.spacedBy(8.dp),
 ) {
 Text(
 text = segment.text,
 style = MaterialTheme.typography.bodyLarge,
 fontWeight = FontWeight.Medium,
 )

 if (segment.translatedText != null) {
 Box(
 modifier = Modifier
 .clip(RoundedCornerShape(8.dp))
 .background(MaterialTheme.colorScheme.primaryContainer)
 .padding(12.dp),
 ) {
 Text(
 text = segment.translatedText ?: "",
 style = MaterialTheme.typography.bodyMedium,
 color = MaterialTheme.colorScheme.onPrimaryContainer,
 )
 }
 } else if (!isTranslating) {
 Button(
 onClick = onTranslate,
 modifier = Modifier.align(Alignment.End),
 contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
 ) {
 Text("Translate")
 }
 }

 if (isTranslating) {
 Row(
 verticalAlignment = Alignment.CenterVertically,
 horizontalArrangement = Arrangement.spacedBy(8.dp),
 ) {
 CircularProgressIndicator(
 modifier = Modifier.height(16.dp),
 strokeWidth = 2.dp,
 )
 Text(
 text = "Translating...",
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }
 }
 }
 }
}
