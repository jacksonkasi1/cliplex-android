package com.learnthis.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.learnthis.common.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
 languages: List<AppLanguage>,
 selectedLanguage: AppLanguage?,
 onLanguageSelected: (AppLanguage) -> Unit,
 onContinue: () -> Unit,
 modifier: Modifier = Modifier
) {
 Scaffold(
 topBar = {
 TopAppBar(title = { Text("Select Your Mother Tongue") })
 }
 ) { padding ->
 Column(
 modifier = modifier
 .fillMaxSize()
 .padding(padding)
 .padding(16.dp),
 horizontalAlignment = Alignment.CenterHorizontally
 ) {
 Text(
 text = "Choose the language you want translations in",
 style = MaterialTheme.typography.bodyLarge,
 modifier = Modifier.padding(bottom = 16.dp)
 )

 LazyColumn(
 modifier = Modifier.weight(1f),
 verticalArrangement = Arrangement.spacedBy(4.dp)
 ) {
 items(languages) { language ->
 Row(
 modifier = Modifier
 .fillMaxWidth()
 .selectable(
 selected = selectedLanguage == language,
 onClick = { onLanguageSelected(language) }
 )
 .padding(vertical = 8.dp),
 verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
 ) {
 RadioButton(
 selected = selectedLanguage == language,
 onClick = null
 )
 Text(
 text = language.displayName,
 style = MaterialTheme.typography.bodyLarge,
 modifier = Modifier.padding(start = 16.dp)
 )
 }
 }
 }

 Button(
 onClick = onContinue,
 enabled = selectedLanguage != null,
 modifier = Modifier.fillMaxWidth()
 ) {
 Text("Continue")
 }
 }
 }
}
