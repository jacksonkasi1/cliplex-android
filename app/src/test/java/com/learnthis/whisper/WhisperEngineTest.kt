package com.learnthis.whisper

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WhisperEngineTest {
	@Test
	fun `detailed result exposes native runtime and phase timings`() = runBlocking {
		val native = FakeNativeApi()
		val engine = WhisperEngine(native)

		val result = engine.ensureModelAndTranscribe(
			modelPath = "tiny.en.bin",
			samples = ShortArray(16_000),
			options = WhisperTranscriptionOptions(
				language = "en",
				nThreads = 3,
				shortEnglishFastMode = true,
			),
		).getOrThrow()

		assertEquals("en", result.detectedLanguage)
		assertEquals(listOf("hello", "world"), result.segments.map { it.text })
		assertEquals(listOf(120L, 520L), result.segments.map { it.startTimeMs })
		assertEquals(listOf(500L, 920L), result.segments.map { it.endTimeMs })
		assertEquals("tiny", result.runtime.model?.modelType)
		assertFalse(result.runtime.model!!.isMultilingual)
		assertEquals("fake arm64 system", result.runtime.systemInfo)
		assertEquals(3, result.diagnostics.threadCount)
		assertTrue(result.diagnostics.fastModeRequested)
		assertTrue(result.diagnostics.fastModeApplied)
		assertEquals(512, result.diagnostics.audioContextOverride)
		assertEquals("tiny.en.bin", result.diagnostics.loadedModelFile)
		assertFalse(result.diagnostics.modelIsMultilingual)
		assertEquals("en", result.diagnostics.requestedLanguage)
		assertFalse(result.diagnostics.translationEnabled)
		assertFalse(result.diagnostics.detectLanguageEnabled)
		assertFalse(result.diagnostics.modelWasWarm)
		assertEquals(7.5, result.diagnostics.timings.whisperEncodeMsPerRun, 0.001)
		assertEquals(80.0, result.diagnostics.timings.nativeTotalMs, 0.001)
		assertTrue(result.diagnostics.timings.jsonParseMs >= 0.0)
		assertTrue(result.diagnostics.timings.kotlinTotalMs >= result.diagnostics.timings.jsonParseMs)
		assertFalse(result.modelLoadDiagnostics!!.cacheHit)
	}

	@Test
	fun `ensure model and transcribe cannot interleave model identities`() = runBlocking {
		val native = FakeNativeApi(blockFirstLoad = true)
		val engineA = WhisperEngine(native)
		val engineB = WhisperEngine(native)

		val first = async(Dispatchers.Default) {
			engineA.ensureModelAndTranscribe("model-a.bin", ShortArray(16_000))
		}
		assertTrue(native.firstLoadEntered.await(2, TimeUnit.SECONDS))
		val second = async(Dispatchers.Default) {
			engineB.ensureModelAndTranscribe("model-b.bin", ShortArray(16_000))
		}
		delay(25)
		native.allowFirstLoad.countDown()

		first.await().getOrThrow()
		second.await().getOrThrow()
		assertEquals(
			listOf(
				"load:model-a.bin",
				"transcribe:model-a.bin",
				"load:model-b.bin",
				"transcribe:model-b.bin",
			),
			native.events,
		)
	}

	@Test
	fun `optional callback emits a source segment and final result stays authoritative`() = runBlocking {
		val native = FakeNativeApi()
		val engine = WhisperEngine(native)
		val partials = mutableListOf<String>()

		val result = engine.ensureModelAndTranscribe(
			modelPath = "tiny.en.bin",
			samples = ShortArray(16_000),
			options = WhisperTranscriptionOptions(language = "en"),
			onNewSegment = WhisperSegmentCallback { partials += it.text },
		).getOrThrow()

		assertEquals(listOf("partial source"), partials)
		assertEquals(listOf("hello", "world"), result.segments.map { it.text })
		assertTrue(result.diagnostics.segmentCallbackRequested)
		assertFalse(result.diagnostics.segmentCallbackFailed)
		assertEquals(1, result.diagnostics.callbackSegmentsEmitted)
	}

	@Test
	fun `multilingual model accepts forced and automatic transcription without translate or detect-only mode`() = runBlocking {
		val native = FakeNativeApi()
		val engine = WhisperEngine(native)

		for (language in listOf("en", "hi", "ta", "auto")) {
			val result = engine.ensureModelAndTranscribe(
				modelPath = "tiny.bin",
				samples = ShortArray(16_000),
				options = WhisperTranscriptionOptions(
					language = language,
					shortEnglishFastMode = language == "en",
				),
			).getOrThrow()

			assertTrue(result.runtime.model!!.isMultilingual)
			assertEquals("tiny.bin", result.diagnostics.loadedModelFile)
			assertTrue(result.diagnostics.modelIsMultilingual)
			assertEquals(language, result.diagnostics.requestedLanguage)
			assertFalse(result.diagnostics.translationEnabled)
			assertFalse(result.diagnostics.detectLanguageEnabled)
			assertEquals(WhisperEngine.DEFAULT_N_THREADS, result.diagnostics.threadCount)
			assertEquals(if (language == "en") 512 else 0, result.diagnostics.audioContextOverride)
		}
		assertEquals(4, native.inferenceRuns)
	}

	@Test
	fun `English-only model rejects automatic and non-English requests before inference`() = runBlocking {
		val native = FakeNativeApi()
		val engine = WhisperEngine(native)

		for (language in listOf("auto", "hi", "ta")) {
			val error = engine.ensureModelAndTranscribe(
				modelPath = "tiny.en.bin",
				samples = ShortArray(16_000),
				options = WhisperTranscriptionOptions(language = language),
			).exceptionOrNull() as WhisperNativeException

			assertEquals("MODEL_LANGUAGE_MISMATCH", error.errorCode)
			assertEquals("tiny.en.bin", error.inferenceDiagnostics?.loadedModelFile)
			assertFalse(error.inferenceDiagnostics!!.modelIsMultilingual)
			assertEquals(language, error.inferenceDiagnostics!!.requestedLanguage)
			assertFalse(error.inferenceDiagnostics!!.translationEnabled)
			assertFalse(error.inferenceDiagnostics!!.detectLanguageEnabled)
		}
		assertEquals(0, native.inferenceRuns)
	}

	@Test
	fun `native failures retain structured error code`() {
		val error = runCatching {
			WhisperJsonParser.parseModelLoad(
				"""
					{
					  "success": false,
					  "errorCode": "MODEL_LOAD_FAILED",
					  "errorMessage": "bad model",
					  "cacheHit": false,
					  "nativeLoadMs": 13.5,
					  "systemInfo": "fake arm64 system",
					  "model": null
					}
				""".trimIndent(),
			)
		}.exceptionOrNull() as WhisperNativeException

		assertEquals("MODEL_LOAD_FAILED", error.errorCode)
		assertEquals("bad model", error.message)
		assertEquals("fake arm64 system", error.runtime?.systemInfo)
		assertEquals(13.5, error.modelLoadDiagnostics?.nativeLoadMs ?: -1.0, 0.001)
	}

	@Test
	fun `transcription failures retain native timings and atomic load diagnostics`() = runBlocking {
		val native = FakeNativeApi(failTranscription = true)
		val engine = WhisperEngine(native)

		val error = engine.ensureModelAndTranscribe(
			modelPath = "tiny.en.bin",
			samples = ShortArray(16_000),
			options = WhisperTranscriptionOptions(language = "en", shortEnglishFastMode = true),
			onNewSegment = WhisperSegmentCallback { },
		).exceptionOrNull() as WhisperNativeException

		assertEquals("WHISPER_FULL_FAILED", error.errorCode)
		assertEquals("fake arm64 system", error.runtime?.systemInfo)
		assertNotNull(error.modelLoadDiagnostics)
		assertFalse(error.modelLoadDiagnostics!!.cacheHit)
		assertNotNull(error.inferenceDiagnostics)
		assertEquals(17.0, error.inferenceDiagnostics!!.timings.nativeTotalMs, 0.001)
		assertTrue(error.inferenceDiagnostics!!.segmentCallbackFailed)
		assertTrue(error.inferenceDiagnostics!!.timings.jsonParseMs >= 0.0)
		assertTrue(
			error.inferenceDiagnostics!!.timings.kotlinTotalMs >=
				error.inferenceDiagnostics!!.timings.jsonParseMs,
		)
	}
}

private class FakeNativeApi(
	private val blockFirstLoad: Boolean = false,
	private val failTranscription: Boolean = false,
) : WhisperNativeApi {
	val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
	val firstLoadEntered = CountDownLatch(1)
	val allowFirstLoad = CountDownLatch(1)
	var inferenceRuns: Int = 0
		private set
	private var currentModel = ""
	private var inferenceCount = 0L

	override fun getNativeSystemInfo(): String = "fake arm64 system"

	override fun whisperLoadModelDetailed(modelPath: String): String {
		events += "load:$modelPath"
		if (blockFirstLoad && modelPath == "model-a.bin") {
			firstLoadEntered.countDown()
			check(allowFirstLoad.await(2, TimeUnit.SECONDS))
		}
		val cacheHit = currentModel == modelPath
		if (!cacheHit) inferenceCount = 0
		currentModel = modelPath
		return """
			{
			  "success": true,
			  "errorCode": "",
			  "errorMessage": "",
			  "cacheHit": $cacheHit,
			  "nativeLoadMs": ${if (cacheHit) 0.0 else 42.0},
			  "systemInfo": "fake arm64 system",
			  "model": ${modelJson(modelPath)}
			}
		""".trimIndent()
	}

	override fun whisperTranscribeDetailed(
		samples: ShortArray,
		language: String,
		nThreads: Int,
		shortEnglishFastMode: Boolean,
		segmentCallback: NativeWhisperSegmentCallback?,
	): String {
		events += "transcribe:$currentModel"
		val requestedLanguage = language.trim().lowercase().ifBlank { "auto" }
		val isMultilingualModel = modelIsMultilingual(currentModel)
		if (!isMultilingualModel && requestedLanguage != "en") {
			return failureJson(
				errorCode = "MODEL_LANGUAGE_MISMATCH",
				errorMessage = "Loaded English-only model cannot transcribe '$requestedLanguage'",
				samples = samples,
				requestedLanguage = requestedLanguage,
				nThreads = nThreads,
				shortEnglishFastMode = shortEnglishFastMode,
				segmentCallback = segmentCallback,
			)
		}
		if (failTranscription) {
			return failureJson(
				errorCode = "WHISPER_FULL_FAILED",
				errorMessage = "whisper_full failed with code -1",
				samples = samples,
				requestedLanguage = requestedLanguage,
				nThreads = nThreads,
				shortEnglishFastMode = shortEnglishFastMode,
				segmentCallback = segmentCallback,
				segmentCallbackFailed = true,
				nativeTotalMs = 17.0,
			)
		}
		val wasWarm = inferenceCount > 0
		inferenceCount++
		inferenceRuns++
		val fastModeApplied = shortEnglishFastMode &&
			requestedLanguage == "en" &&
			samples.size in 1_600..160_000
		val detectedLanguage = if (requestedLanguage == "auto") "hi" else requestedLanguage
		segmentCallback?.onSegment(0, 500, " partial source ", detectedLanguage, 0.1f)
		return """
			{
			  "success": true,
			  "errorCode": "",
			  "errorMessage": "",
			  "language": "$detectedLanguage",
			  "segments": [
			    {"start": 120, "end": 500, "text": " hello ", "noSpeechProb": 0.1},
			    {"start": 520, "end": 920, "text": " world ", "noSpeechProb": 0.1}
			  ],
			  "diagnostics": {
			    "sampleCount": ${samples.size},
			    "audioDurationMs": ${samples.size * 1000L / 16000L},
			    "threadCount": $nThreads,
			    "fastModeRequested": $shortEnglishFastMode,
			    "fastModeApplied": $fastModeApplied,
			    "audioContextOverride": ${if (fastModeApplied) 512 else 0},
			    "modelWasWarm": $wasWarm,
			    "inferenceIndex": $inferenceCount,
			    "modelLoadMs": 42.0,
			    "segmentCallbackRequested": ${segmentCallback != null},
			    "segmentCallbackFailed": false,
			    "callbackSegmentsEmitted": ${if (segmentCallback == null) 0 else 1},
			    "loadedModelFile": "$currentModel",
			    "modelIsMultilingual": $isMultilingualModel,
			    "requestedLanguage": "$requestedLanguage",
			    "translationEnabled": false,
			    "detectLanguageEnabled": false,
			    "jniPcmConversionMs": 1.5,
			    "whisperMelAndPreEncodeMs": 2.5,
			    "whisperSampleMsPerRun": 3.5,
			    "whisperEncodeMsPerRun": 7.5,
			    "whisperDecodeMsPerRun": 8.5,
			    "whisperBatchDecodeMsPerRun": 4.5,
			    "whisperPromptMsPerRun": 0.5,
			    "whisperInferenceMs": 75.0,
			    "nativeTotalMs": 80.0
			  },
			  "systemInfo": "fake arm64 system",
			  "model": ${modelJson(currentModel)}
			}
		""".trimIndent()
	}

	private fun failureJson(
		errorCode: String,
		errorMessage: String,
		samples: ShortArray,
		requestedLanguage: String,
		nThreads: Int,
		shortEnglishFastMode: Boolean,
		segmentCallback: NativeWhisperSegmentCallback?,
		segmentCallbackFailed: Boolean = false,
		nativeTotalMs: Double = 1.0,
	): String {
		val isMultilingualModel = modelIsMultilingual(currentModel)
		val fastModeApplied = shortEnglishFastMode &&
			requestedLanguage == "en" &&
			samples.size in 1_600..160_000
		return """
			{
			  "success": false,
			  "errorCode": "$errorCode",
			  "errorMessage": "$errorMessage",
			  "language": "",
			  "segments": [],
			  "diagnostics": {
			    "sampleCount": ${samples.size},
			    "audioDurationMs": ${samples.size * 1000L / 16000L},
			    "threadCount": $nThreads,
			    "fastModeRequested": $shortEnglishFastMode,
			    "fastModeApplied": $fastModeApplied,
			    "audioContextOverride": ${if (fastModeApplied) 512 else 0},
			    "modelWasWarm": ${inferenceCount > 0},
			    "inferenceIndex": ${inferenceCount + 1},
			    "modelLoadMs": 42.0,
			    "segmentCallbackRequested": ${segmentCallback != null},
			    "segmentCallbackFailed": $segmentCallbackFailed,
			    "callbackSegmentsEmitted": 0,
			    "loadedModelFile": "$currentModel",
			    "modelIsMultilingual": $isMultilingualModel,
			    "requestedLanguage": "$requestedLanguage",
			    "translationEnabled": false,
			    "detectLanguageEnabled": false,
			    "jniPcmConversionMs": 1.0,
			    "whisperMelAndPreEncodeMs": 2.0,
			    "whisperSampleMsPerRun": 3.0,
			    "whisperEncodeMsPerRun": 4.0,
			    "whisperDecodeMsPerRun": 5.0,
			    "whisperBatchDecodeMsPerRun": 6.0,
			    "whisperPromptMsPerRun": 7.0,
			    "whisperInferenceMs": 16.0,
			    "nativeTotalMs": $nativeTotalMs
			  },
			  "systemInfo": "fake arm64 system",
			  "model": ${modelJson(currentModel)}
			}
		""".trimIndent()
	}

	override fun whisperFreeModel() {
		currentModel = ""
		inferenceCount = 0
		inferenceRuns = 0
	}

	private fun modelIsMultilingual(path: String): Boolean = !path.contains(".en.", ignoreCase = true)

	private fun modelJson(path: String): String = """
		{
		  "fileName": "$path",
		  "type": "tiny",
		  "isMultilingual": ${modelIsMultilingual(path)},
		  "vocabularySize": 51864,
		  "audioContextSize": 1500,
		  "textContextSize": 448,
		  "melBins": 80,
		  "quantizationType": 8
		}
	""".trimIndent()
}
