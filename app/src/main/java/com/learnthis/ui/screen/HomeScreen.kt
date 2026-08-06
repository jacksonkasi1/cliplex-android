package com.learnthis.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.learnthis.common.AppLanguage

@Composable
fun HomeScreen(
	selectedLanguage: AppLanguage?,
	onStartLearning: () -> Unit,
	onChangeLanguage: () -> Unit,
	modifier: Modifier = Modifier
) {
	var isCapturing by remember { mutableStateOf(false) }

	Column(
		modifier = modifier.fillMaxSize(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Text(
			text = "Learn This",
			style = MaterialTheme.typography.headlineMedium,
			modifier = Modifier.padding(bottom = 8.dp)
		)

		selectedLanguage?.let {
			Text(
				text = "Translating to: ${it.displayName}",
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier.padding(bottom = 24.dp)
			)
		}

		Button(
			onClick = onStartLearning,
			modifier = Modifier
				.fillMaxWidth(fraction = 0.8f)
				.padding(vertical = 4.dp),
			enabled = !isCapturing
		) {
			Text(if (isCapturing) "Listening..." else "Start Learning Mode")
		}

		OutlinedButton(
			onClick = onChangeLanguage,
			modifier = Modifier
				.fillMaxWidth(fraction = 0.8f)
				.padding(top = 12.dp)
		) {
			Text("Change Language")
		}

		if (isCapturing) {
			Text(
				text = "Speak or play audio on another app. Learn This will transcribe in real-time.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 24.dp)
			)
		}
	}
}
