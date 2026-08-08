package com.learnthis.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelResolverTest {
	private val resolver = ModelResolver()

	@Test
	fun languageCatalogHasStableCodesAndDisplayNames() {
		val expected = listOf(
			LearningLanguage.ENGLISH to ("en" to "English"),
			LearningLanguage.HINDI to ("hi" to "Hindi"),
			LearningLanguage.TAMIL to ("ta" to "Tamil"),
			LearningLanguage.TELUGU to ("te" to "Telugu"),
			LearningLanguage.MALAYALAM to ("ml" to "Malayalam"),
			LearningLanguage.KANNADA to ("kn" to "Kannada"),
			LearningLanguage.BENGALI to ("bn" to "Bengali"),
			LearningLanguage.MARATHI to ("mr" to "Marathi"),
			LearningLanguage.ANY_LANGUAGE to ("auto" to "Any Language"),
		)

		assertEquals(expected.size, LearningLanguage.entries.size)
		expected.forEach { (language, values) ->
			assertEquals(values.first, language.code)
			assertEquals(values.second, language.displayName)
			assertSame(language, LearningLanguage.fromStorageValue(values.first))
			assertSame(language, LearningLanguage.fromStorageValue(language.name))
		}
	}

	@Test
	fun speechQualityDefaultsToFastAndHasStableStorageKeys() {
		assertSame(SpeechQuality.FAST, SpeechQuality.DEFAULT)
		assertSame(SpeechQuality.FAST, SpeechQuality.fromStorageValue("fast"))
		assertSame(SpeechQuality.RECOMMENDED, SpeechQuality.fromStorageValue("recommended"))
		assertSame(SpeechQuality.HIGH_ACCURACY, SpeechQuality.fromStorageValue("high_accuracy"))
		assertSame(SpeechQuality.RECOMMENDED, SpeechQuality.fromStorageValue("RECOMMENDED"))
	}

	@Test
	fun englishAlwaysUsesTinyEnglishWithForcedEnglishForAvailableTiers() {
		listOf(SpeechQuality.FAST, SpeechQuality.RECOMMENDED).forEach { quality ->
			assertAvailable(
				configuration = resolver.resolve(LearningLanguage.ENGLISH, quality),
				modelType = ModelType.TINY_EN_Q5_1,
				language = "en",
				quality = quality,
			)
		}
	}

	@Test
	fun knownNonEnglishLanguagesRouteFastToTinyAndRecommendedToBaseWithForcedCode() {
		LearningLanguage.entries
			.filterNot { it == LearningLanguage.ENGLISH || it == LearningLanguage.ANY_LANGUAGE }
			.forEach { language ->
				assertAvailable(
					configuration = resolver.resolve(language, SpeechQuality.FAST),
					modelType = ModelType.TINY_MULTILINGUAL_Q5_1,
					language = language.code,
					quality = SpeechQuality.FAST,
				)
				assertAvailable(
					configuration = resolver.resolve(language, SpeechQuality.RECOMMENDED),
					modelType = ModelType.BASE_MULTILINGUAL_Q5_1,
					language = language.code,
					quality = SpeechQuality.RECOMMENDED,
				)
			}
	}

	@Test
	fun anyLanguageUsesMultilingualModelsAndAutomaticLanguageDetection() {
		assertAvailable(
			configuration = resolver.resolve(LearningLanguage.ANY_LANGUAGE, SpeechQuality.FAST),
			modelType = ModelType.TINY_MULTILINGUAL_Q5_1,
			language = "auto",
			quality = SpeechQuality.FAST,
		)
		assertAvailable(
			configuration = resolver.resolve(LearningLanguage.ANY_LANGUAGE, SpeechQuality.RECOMMENDED),
			modelType = ModelType.BASE_MULTILINGUAL_Q5_1,
			language = "auto",
			quality = SpeechQuality.RECOMMENDED,
		)
	}

	@Test
	fun highAccuracyIsUnavailableWithoutAProviderVerifiedModel() {
		LearningLanguage.entries.forEach { language ->
			val configuration = resolver.resolve(language, SpeechQuality.HIGH_ACCURACY)
			assertTrue(configuration is WhisperConfiguration.Unavailable)
			configuration as WhisperConfiguration.Unavailable
			assertSame(
				WhisperConfiguration.Unavailable.Reason.HIGH_ACCURACY_MODEL_NOT_VERIFIED,
				configuration.reason,
			)
		}
	}

	private fun assertAvailable(
		configuration: WhisperConfiguration,
		modelType: ModelType,
		language: String,
		quality: SpeechQuality,
	) {
		assertTrue(configuration is WhisperConfiguration.Available)
		configuration as WhisperConfiguration.Available
		assertSame(modelType, configuration.modelType)
		assertEquals(language, configuration.transcriptionLanguage)
		assertSame(quality, configuration.speechQuality)
	}
}
