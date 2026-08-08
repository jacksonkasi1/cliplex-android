package com.learnthis.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.learnthis.common.AppLanguage
import com.learnthis.data.repository.PreferencesRepository
import com.learnthis.domain.model.LearningLanguage
import com.learnthis.domain.model.SpeechQuality
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
	@get:Rule
	val temporaryFolder = TemporaryFolder()

	@Test
	fun speechQualityIsRelevantForNonEnglishAndAnyLanguageOnly() {
		val english = OnboardingUiState(
			learningLanguage = LearningLanguage.ENGLISH,
			speechQuality = SpeechQuality.RECOMMENDED,
		)
		val hindi = english.copy(learningLanguage = LearningLanguage.HINDI)
		val anyLanguage = english.copy(learningLanguage = LearningLanguage.ANY_LANGUAGE)

		assertFalse(english.showSpeechQuality)
		assertEquals(SpeechQuality.FAST, english.effectiveSpeechQuality)
		assertTrue(hindi.showSpeechQuality)
		assertEquals(SpeechQuality.RECOMMENDED, hindi.effectiveSpeechQuality)
		assertTrue(anyLanguage.showSpeechQuality)
	}

	@Test
	fun unavailableQualityIsRejectedAndEnglishResetsToFast() = runTest {
		val repository = createRepository(backgroundScope)
		repository.completeOnboarding(
			motherTongue = AppLanguage.TAMIL,
			learningLanguage = LearningLanguage.HINDI,
			speechQuality = SpeechQuality.FAST,
		)
		repository.restartOnboarding()
		val viewModel = OnboardingViewModel(repository, backgroundScope)
		viewModel.uiState.first { it.isLoaded }

		viewModel.selectSpeechQuality(SpeechQuality.HIGH_ACCURACY)
		assertEquals(SpeechQuality.FAST, viewModel.uiState.value.speechQuality)

		viewModel.selectSpeechQuality(SpeechQuality.RECOMMENDED)
		assertEquals(SpeechQuality.RECOMMENDED, viewModel.uiState.value.speechQuality)

		viewModel.selectLearningLanguage(LearningLanguage.ENGLISH)
		assertFalse(viewModel.uiState.value.showSpeechQuality)
		assertEquals(SpeechQuality.FAST, viewModel.uiState.value.speechQuality)
	}

	@Test
	fun continuePersistsMotherTongueLearningLanguageAndQualityTogether() = runTest {
		val repository = createRepository(backgroundScope)
		val viewModel = OnboardingViewModel(repository, backgroundScope)
		viewModel.uiState.first { it.isLoaded }

		viewModel.selectMotherTongue(AppLanguage.TAMIL)
		viewModel.selectLearningLanguage(LearningLanguage.TELUGU)
		viewModel.selectSpeechQuality(SpeechQuality.RECOMMENDED)
		assertTrue(viewModel.uiState.value.canContinue)

		viewModel.completeOnboarding()

		assertTrue(repository.isOnboardingCompleted.first { it })
		assertEquals(AppLanguage.TAMIL, repository.motherTongue.first())
		assertEquals(LearningLanguage.TELUGU, repository.learningLanguage.first())
		assertEquals(SpeechQuality.RECOMMENDED, repository.speechQuality.first())
	}

	private fun createRepository(scope: CoroutineScope): PreferencesRepository {
		val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
			File(temporaryFolder.root, "settings-${System.nanoTime()}.preferences_pb")
		}
		return PreferencesRepository(dataStore)
	}
}
