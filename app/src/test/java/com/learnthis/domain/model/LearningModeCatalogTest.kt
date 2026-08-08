package com.learnthis.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningModeCatalogTest {
	@Test
	fun learningModesHaveStableStorageAndInferenceContracts() {
		assertEquals("english_only", LearningMode.ENGLISH_ONLY.storageKey)
		assertSame(ModelType.TINY_EN_Q5_1, LearningMode.ENGLISH_ONLY.requiredWhisperModel)
		assertEquals("en", LearningMode.ENGLISH_ONLY.transcriptionLanguage)

		assertEquals("multilingual", LearningMode.MULTILINGUAL.storageKey)
		assertSame(ModelType.TINY_MULTILINGUAL_Q5_1, LearningMode.MULTILINGUAL.requiredWhisperModel)
		assertEquals("auto", LearningMode.MULTILINGUAL.transcriptionLanguage)
	}

	@Test
	fun persistedModeParserAcceptsStableKeysAndLegacyEnumNames() {
		assertSame(LearningMode.ENGLISH_ONLY, LearningMode.fromStorageValue("english_only"))
		assertSame(LearningMode.ENGLISH_ONLY, LearningMode.fromStorageValue("ENGLISH_ONLY"))
		assertSame(LearningMode.MULTILINGUAL, LearningMode.fromStorageValue("multilingual"))
		assertSame(LearningMode.MULTILINGUAL, LearningMode.fromStorageValue("MULTILINGUAL"))
		assertEquals(null, LearningMode.fromStorageValue("unknown"))
		assertEquals(null, LearningMode.fromStorageValue(null))
	}

	@Test
	fun modelMetadataMatchesPublishedArtifacts() {
		assertMetadata(
			model = ModelType.TINY_EN_Q5_1,
			fileName = "ggml-tiny.en-q5_1.bin",
			expectedBytes = 32_166_155L,
			sha256 = "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b",
		)
		assertMetadata(
			model = ModelType.TINY_MULTILINGUAL_Q5_1,
			fileName = "ggml-tiny-q5_1.bin",
			expectedBytes = 32_152_673L,
			sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
		)
		assertMetadata(
			model = ModelType.BASE_MULTILINGUAL_Q5_1,
			fileName = "ggml-base-q5_1.bin",
			expectedBytes = 59_707_625L,
			sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
		)
	}

	@Test
	fun userFacingModesUseDistinctSelectableModels() {
		val requiredModels = LearningMode.entries.map { it.requiredWhisperModel }
		assertEquals(requiredModels.size, requiredModels.distinct().size)
		assertTrue(requiredModels.all(ModelType::userSelectable))
		assertFalse(ModelType.BASE_MULTILINGUAL_Q5_1.userSelectable)
		assertFalse(ModelType.BASE_MULTILINGUAL_Q5_1 in requiredModels)
	}

	private fun assertMetadata(
		model: ModelType,
		fileName: String,
		expectedBytes: Long,
		sha256: String,
	) {
		assertEquals(fileName, model.fileName)
		assertEquals(expectedBytes, model.expectedByteSize)
		assertEquals(sha256, model.sha256)
		assertEquals(
			"https://huggingface.co/ggerganov/whisper.cpp/resolve/c521a4b02f422512d734391fdf08bb08c0862f68/$fileName",
			model.downloadUrl,
		)
		assertEquals(fileName, model.localAssetName)
		assertTrue(model.sha256.matches(Regex("[0-9a-f]{64}")))
	}
}
