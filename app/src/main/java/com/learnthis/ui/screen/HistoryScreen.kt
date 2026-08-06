package com.learnthis.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.learnthis.data.local.SessionEntity
import com.learnthis.ui.viewmodel.HistoryViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
 onBack: () -> Unit,
 historyViewModel: HistoryViewModel = viewModel(),
) {
 val uiState by historyViewModel.uiState.collectAsState()

 Column(modifier = Modifier.fillMaxSize()) {
 TopAppBar(
 title = { Text("History") },
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

 if (uiState.sessions.isEmpty()) {
 Column(
 modifier = Modifier.fillMaxSize(),
 horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
 verticalArrangement = Arrangement.Center,
 ) {
 Text("No sessions yet", style = MaterialTheme.typography.bodyLarge)
 }
 } else {
 LazyColumn(modifier = Modifier.fillMaxSize()) {
 items(uiState.sessions, key = { it.id }) { session ->
 SessionItem(
 session = session,
 onDelete = { historyViewModel.deleteSession(session.id) },
 )
 }
 }
 }
 }
}

@Composable
fun SessionItem(
 session: SessionEntity,
 onDelete: () -> Unit,
 modifier: Modifier = Modifier,
) {
 androidx.compose.material3.Card(
 modifier = modifier.fillMaxWidth(),
 ) {
 Column(modifier = Modifier.padding(16.dp)) {
 Text(text = "Session #${session.id}", style = MaterialTheme.typography.titleMedium)
 Text(text = "Duration: ${session.durationMs / 1000}s", style = MaterialTheme.typography.bodyMedium)
 Text(text = "Segments: ${session.segmentCount}", style = MaterialTheme.typography.bodySmall)
 }
 }
}
