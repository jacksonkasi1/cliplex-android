package com.learnthis.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnthis.common.AppLanguage
import com.learnthis.domain.model.LearningLanguage
import com.learnthis.domain.model.LearningMode
import com.learnthis.domain.model.SpeechQuality

/**
 * First-run language setup. The language spoken in lessons and the language used for explanations
 * are intentionally separate choices.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
	motherTongueLanguages: List<AppLanguage>,
	selectedMotherTongue: AppLanguage?,
	selectedLearningLanguage: LearningLanguage?,
	selectedSpeechQuality: SpeechQuality,
	isSaving: Boolean,
	onMotherTongueSelected: (AppLanguage) -> Unit,
	onLearningLanguageSelected: (LearningLanguage) -> Unit,
	onSpeechQualitySelected: (SpeechQuality) -> Unit,
	onContinue: () -> Unit,
	errorMessage: String? = null,
	modifier: Modifier = Modifier,
) {
	val showSpeechQuality = selectedLearningLanguage != null &&
		selectedLearningLanguage != LearningLanguage.ENGLISH

	Scaffold(
		modifier = modifier,
		topBar = { TopAppBar(title = { Text("Set up Learn This") }) },
	) { contentPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(contentPadding)
				.padding(horizontal = 20.dp),
		) {
			LazyColumn(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(20.dp),
			) {
				item(key = "intro") {
					Column(modifier = Modifier.padding(top = 8.dp)) {
						Text(
							text = "Learn from the videos you already watch",
							style = MaterialTheme.typography.headlineSmall,
							fontWeight = FontWeight.Bold,
						)
						Text(
							text = "Choose your learning language and how you want explanations shown.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(top = 6.dp),
						)
					}
				}

				item(key = "learning_language") {
					OnboardingSection(
						title = "Language I want to learn",
						description = "Choose the language spoken in the videos.",
					) {
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(2.dp),
							modifier = Modifier.selectableGroup(),
						) {
							LearningLanguage.entries.forEach { language ->
								FilterChip(
									selected = selectedLearningLanguage == language,
									onClick = { onLearningLanguageSelected(language) },
									label = { Text(language.displayName) },
									leadingIcon = if (selectedLearningLanguage == language) {
										{
											Icon(
												imageVector = Icons.Default.Check,
												contentDescription = null,
												modifier = Modifier.size(18.dp),
											)
										}
									} else null,
								)
							}
						}
					}
				}

				item(key = "mother_tongue") {
					OnboardingSection(
						title = "My language",
						description = "We’ll explain what you hear in this language.",
					) {
						FlowRow(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(2.dp),
							modifier = Modifier.selectableGroup(),
						) {
							motherTongueLanguages.forEach { language ->
								FilterChip(
									selected = selectedMotherTongue == language,
									onClick = { onMotherTongueSelected(language) },
									label = { Text(language.displayName) },
									leadingIcon = if (selectedMotherTongue == language) {
										{
											Icon(
												imageVector = Icons.Default.Check,
												contentDescription = null,
												modifier = Modifier.size(18.dp),
											)
										}
									} else null,
								)
							}
						}
					}
				}

				if (showSpeechQuality) {
					item(key = "speech_quality") {
						OnboardingSection(
							title = "Speech quality",
							description = "Fast works well for most short lessons. You can change this later.",
						) {
							Column(
								verticalArrangement = Arrangement.spacedBy(8.dp),
								modifier = Modifier.selectableGroup(),
							) {
								SpeechQualityChoice(
									quality = SpeechQuality.FAST,
									description = "Starts sooner and uses less storage",
									badge = "Default",
									selected = selectedSpeechQuality == SpeechQuality.FAST,
									onClick = { onSpeechQualitySelected(SpeechQuality.FAST) },
								)
								SpeechQualityChoice(
									quality = SpeechQuality.RECOMMENDED,
									description = "Better recognition with a larger download and slower processing",
									selected = selectedSpeechQuality == SpeechQuality.RECOMMENDED,
									onClick = { onSpeechQualitySelected(SpeechQuality.RECOMMENDED) },
								)
								SpeechQualityChoice(
									quality = SpeechQuality.HIGH_ACCURACY,
									description = "For the most challenging speech",
									badge = "Coming later",
									selected = false,
									enabled = false,
									onClick = { },
								)
							}
						}
					}
				}

				item(key = "bottom_space") { Spacer(Modifier.height(4.dp)) }
			}

			if (errorMessage != null) {
				Text(
					text = errorMessage,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(top = 8.dp),
				)
			}

			Button(
				onClick = onContinue,
				enabled = selectedMotherTongue != null && selectedLearningLanguage != null && !isSaving,
				modifier = Modifier
					.fillMaxWidth()
					.padding(vertical = 12.dp),
			) {
				if (isSaving) {
					CircularProgressIndicator(
						modifier = Modifier.size(18.dp),
						strokeWidth = 2.dp,
						color = MaterialTheme.colorScheme.onPrimary,
					)
					Spacer(Modifier.width(8.dp))
				}
				Text(if (isSaving) "Saving…" else "Continue")
			}
		}
	}
}

@Composable
private fun OnboardingSection(
	title: String,
	description: String,
	content: @Composable () -> Unit,
) {
	Column {
		Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
		Text(
			text = description,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
		)
		content()
	}
}

@Composable
private fun SpeechQualityChoice(
	quality: SpeechQuality,
	description: String,
	selected: Boolean,
	onClick: () -> Unit,
	badge: String? = null,
	enabled: Boolean = true,
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.selectable(
				selected = selected,
				enabled = enabled,
				role = Role.RadioButton,
				onClick = onClick,
			),
		shape = RoundedCornerShape(14.dp),
		colors = CardDefaults.cardColors(
			containerColor = if (selected) {
				MaterialTheme.colorScheme.primaryContainer
			} else {
				MaterialTheme.colorScheme.surfaceVariant
			},
			disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
			disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
		),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			RadioButton(selected = selected, enabled = enabled, onClick = null)
			Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(quality.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
					if (badge != null) {
						Spacer(Modifier.width(8.dp))
						Surface(
							shape = RoundedCornerShape(10.dp),
							color = MaterialTheme.colorScheme.secondaryContainer,
							contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
						) {
							Text(
								text = badge,
								style = MaterialTheme.typography.labelSmall,
								modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
							)
						}
					}
				}
				Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
	}
}

/** Temporary source-compatible bridge while the activity migrates to the explicit language API. */
@Deprecated("Use the LearningLanguage and SpeechQuality overload")
@Composable
fun OnboardingScreen(
	languages: List<AppLanguage>,
	selectedLanguage: AppLanguage?,
	selectedLearningMode: LearningMode?,
	onLanguageSelected: (AppLanguage) -> Unit,
	onLearningModeSelected: (LearningMode) -> Unit,
	onContinue: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var learningLanguage by remember(selectedLearningMode) {
		mutableStateOf(
			when (selectedLearningMode) {
				LearningMode.ENGLISH_ONLY -> LearningLanguage.ENGLISH
				LearningMode.MULTILINGUAL -> LearningLanguage.ANY_LANGUAGE
				null -> null
			}
		)
	}
	var speechQuality by remember { mutableStateOf(SpeechQuality.FAST) }
	OnboardingScreen(
		motherTongueLanguages = languages,
		selectedMotherTongue = selectedLanguage,
		selectedLearningLanguage = learningLanguage,
		selectedSpeechQuality = speechQuality,
		isSaving = false,
		onMotherTongueSelected = onLanguageSelected,
		onLearningLanguageSelected = { selected ->
			learningLanguage = selected
			onLearningModeSelected(
				if (selected == LearningLanguage.ENGLISH) LearningMode.ENGLISH_ONLY else LearningMode.MULTILINGUAL,
			)
		},
		onSpeechQualitySelected = { speechQuality = it },
		onContinue = onContinue,
		modifier = modifier,
	)
}
