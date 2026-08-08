package com.jacksonkasi.cliplex.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.LearningMode
import com.jacksonkasi.cliplex.domain.model.SpeechQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreferencesRepositoryLearningModeTest {
	@get:Rule
	val temporaryFolder = TemporaryFolder()

	private var dataStoreScope: CoroutineScope? = null

	@After
	fun tearDown() {
		dataStoreScope?.cancel()
	}

	@Test
	fun legacyActiveModelsMapToTheirCompatibleLearningModes() {
		assertEquals(
			LearningMode.ENGLISH_ONLY,
			PreferencesRepository.learningModeFromStoredValues(null, "TINY_EN_Q5_1"),
		)
		assertEquals(
			LearningMode.ENGLISH_ONLY,
			PreferencesRepository.learningModeFromStoredValues(null, "BASE_EN_Q5_1"),
		)
		listOf("TINY_Q5_1", "BASE_Q5_1", "TINY_MULTILINGUAL_Q5_1", "BASE_MULTILINGUAL_Q5_1")
			.forEach { legacyModel ->
				assertEquals(
					LearningMode.MULTILINGUAL,
					PreferencesRepository.learningModeFromStoredValues(null, legacyModel),
				)
			}
		assertNull(PreferencesRepository.learningModeFromStoredValues(null, "UNKNOWN_MODEL"))
	}

	@Test
	fun explicitLearningModeTakesPrecedenceOverLegacyModel() {
		assertEquals(
			LearningMode.ENGLISH_ONLY,
			PreferencesRepository.learningModeFromStoredValues("english_only", "BASE_Q5_1"),
		)
		assertEquals(
			LearningMode.MULTILINGUAL,
			PreferencesRepository.learningModeFromStoredValues("MULTILINGUAL", "TINY_EN_Q5_1"),
		)
	}

	@Test
	fun legacyLearningModesMigrateToEnglishOrAnyLanguage() {
		assertEquals(
			LearningLanguage.ENGLISH,
			PreferencesRepository.learningLanguageFromStoredValues(
				explicitLanguage = null,
				legacyLearningMode = "english_only",
				legacyActiveModel = null,
			),
		)
		assertEquals(
			LearningLanguage.ANY_LANGUAGE,
			PreferencesRepository.learningLanguageFromStoredValues(
				explicitLanguage = null,
				legacyLearningMode = "MULTILINGUAL",
				legacyActiveModel = null,
			),
		)
		assertEquals(
			LearningLanguage.HINDI,
			PreferencesRepository.learningLanguageFromStoredValues(
				explicitLanguage = "hi",
				legacyLearningMode = "english_only",
				legacyActiveModel = "TINY_EN_Q5_1",
			),
		)
	}

	@Test
	fun legacyCompleteOnboardingPersistsCanonicalStrategyAndRemovesLegacySelection() = runTest {
		val (repository, dataStore) = createRepository()
		dataStore.edit { it[PreferencesRepository.ACTIVE_MODEL] = "BASE_Q5_1" }

		repository.completeOnboarding(AppLanguage.TAMIL, LearningMode.ENGLISH_ONLY)

		val values = dataStore.data.first()
		assertEquals("ta", values[PreferencesRepository.MOTHER_TONGUE])
		assertEquals("en", values[PreferencesRepository.LEARNING_LANGUAGE])
		assertEquals("fast", values[PreferencesRepository.SPEECH_QUALITY])
		assertEquals("true", values[PreferencesRepository.ONBOARDING_COMPLETED])
		assertFalse(values.contains(PreferencesRepository.ACTIVE_MODEL))
		assertFalse(values.contains(PreferencesRepository.LEARNING_MODE))
		assertEquals(LearningLanguage.ENGLISH, repository.learningLanguage.first())
		assertEquals(SpeechQuality.FAST, repository.speechQuality.first())
		assertEquals(LearningMode.ENGLISH_ONLY, repository.learningMode.first())
	}

	@Test
	fun legacyModeSetterPersistsCanonicalLanguageAndRemovesLegacySelection() = runTest {
		val (repository, dataStore) = createRepository()
		dataStore.edit { it[PreferencesRepository.ACTIVE_MODEL] = "TINY_EN_Q5_1" }

		repository.setLearningMode(LearningMode.MULTILINGUAL)

		val values = dataStore.data.first()
		assertEquals("auto", values[PreferencesRepository.LEARNING_LANGUAGE])
		assertFalse(values.contains(PreferencesRepository.LEARNING_MODE))
		assertFalse(values.contains(PreferencesRepository.ACTIVE_MODEL))
		assertEquals(LearningLanguage.ANY_LANGUAGE, repository.learningLanguage.first())
		assertEquals(LearningMode.MULTILINGUAL, repository.learningMode.first())
	}

	@Test
	fun onboardingAtomicallySeparatesMotherTongueLearningLanguageAndQuality() = runTest {
		val (repository, dataStore) = createRepository()
		dataStore.edit {
			it[PreferencesRepository.LEARNING_MODE] = "english_only"
			it[PreferencesRepository.ACTIVE_MODEL] = "TINY_EN_Q5_1"
			it[PreferencesRepository.SELECTED_LANGUAGE] = AppLanguage.ENGLISH.tag
		}

		repository.completeOnboarding(
			motherTongue = AppLanguage.TAMIL,
			learningLanguage = LearningLanguage.HINDI,
			speechQuality = SpeechQuality.RECOMMENDED,
		)

		val values = dataStore.data.first()
		assertEquals(AppLanguage.TAMIL.tag, values[PreferencesRepository.MOTHER_TONGUE])
		assertEquals(LearningLanguage.HINDI.code, values[PreferencesRepository.LEARNING_LANGUAGE])
		assertEquals(SpeechQuality.RECOMMENDED.storageKey, values[PreferencesRepository.SPEECH_QUALITY])
		assertEquals("true", values[PreferencesRepository.ONBOARDING_COMPLETED])
		assertFalse(values.contains(PreferencesRepository.SELECTED_LANGUAGE))
		assertFalse(values.contains(PreferencesRepository.LEARNING_MODE))
		assertFalse(values.contains(PreferencesRepository.ACTIVE_MODEL))
		assertEquals(AppLanguage.TAMIL, repository.motherTongue.first())
		assertEquals(LearningLanguage.HINDI, repository.learningLanguage.first())
		assertEquals(SpeechQuality.RECOMMENDED, repository.speechQuality.first())
		assertEquals(LearningMode.MULTILINGUAL, repository.learningMode.first())
	}

	@Test
	fun missingOrInvalidQualityUsesFastWithoutWritingBaseSelection() = runTest {
		val (repository, dataStore) = createRepository()

		assertEquals(SpeechQuality.FAST, repository.speechQuality.first())
		assertNull(dataStore.data.first()[PreferencesRepository.SPEECH_QUALITY])

		dataStore.edit { it[PreferencesRepository.SPEECH_QUALITY] = "unknown" }

		assertEquals(SpeechQuality.FAST, repository.speechQuality.first())
	}

	@Test
	fun repositoryReadsLegacySelectionBeforeItIsNormalized() = runTest {
		val (repository, dataStore) = createRepository()
		dataStore.edit { it[PreferencesRepository.ACTIVE_MODEL] = "TINY_Q5_1" }

		assertEquals(LearningLanguage.ANY_LANGUAGE, repository.learningLanguage.first())
		assertEquals(LearningMode.MULTILINGUAL, repository.learningMode.first())
		assertNull(dataStore.data.first()[PreferencesRepository.LEARNING_MODE])
	}

	@Test
	fun legacySelectedLanguageFallsBackToMotherTongueAndCanonicalWriteRemovesIt() = runTest {
		val (repository, dataStore) = createRepository()
		dataStore.edit { it[PreferencesRepository.SELECTED_LANGUAGE] = AppLanguage.HINDI.tag }

		assertEquals(AppLanguage.HINDI, repository.motherTongue.first())

		repository.setMotherTongue(AppLanguage.TAMIL)

		val values = dataStore.data.first()
		assertEquals(AppLanguage.TAMIL.tag, values[PreferencesRepository.MOTHER_TONGUE])
		assertFalse(values.contains(PreferencesRepository.SELECTED_LANGUAGE))
		assertEquals(AppLanguage.TAMIL, repository.motherTongue.first())
	}

	private fun createRepository(): Pair<PreferencesRepository, DataStore<Preferences>> {
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		dataStoreScope = scope
		val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
			File(temporaryFolder.root, "settings.preferences_pb")
		}
		return PreferencesRepository(dataStore) to dataStore
	}
}
