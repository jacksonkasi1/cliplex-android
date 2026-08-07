package com.learnthis.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				Text("No sessions yet", style = MaterialTheme.typography.bodyLarge)
			}
		} else {
			LazyColumn(modifier = Modifier.fillMaxSize()) {
				item {
					Text(
						text = "Past Sessions",
						style = MaterialTheme.typography.titleMedium,
						modifier = Modifier.padding(16.dp),
						fontWeight = FontWeight.Bold,
					)
				}
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
private fun SessionItem(
	session: com.learnthis.data.local.SessionEntity,
	onDelete: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Card(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp),
	) {
		Row(
			modifier = Modifier.padding(16.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column {
				Text(text = "Session #${session.id}", style = MaterialTheme.typography.titleMedium)
				Text(
					text = "${session.durationMs / 1000}s · ${session.segmentCount} segments",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			IconButton(onClick = onDelete) {
				Icon(Icons.Default.Delete, contentDescription = "Delete")
			}
		}
	}
}
