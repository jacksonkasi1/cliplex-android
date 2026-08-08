package com.jacksonkasi.cliplex.speech

import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.RecognitionMode
import com.jacksonkasi.cliplex.domain.model.TranscriptionResult
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechRecognitionCoordinatorTest {
	@Test
	fun `uses Android on-device engine first when exact request is available`() = runTest {
		val android = FakeEngine(SpeechEngine.ANDROID_ON_DEVICE, available = true, text = "hello")
		val whisper = FakeEngine(SpeechEngine.WHISPER_FALLBACK, available = true, text = "fallback")
		val result = SpeechRecognitionCoordinator(android, whisper)
			.transcribe(AudioInput(shortArrayOf(1, 2)), LearningLanguage.ENGLISH)

		assertEquals(SpeechEngine.ANDROID_ON_DEVICE, result.result.engine)
		assertNull(result.fallbackReason)
		assertEquals(1, android.transcriptionCalls)
		assertEquals(0, whisper.transcriptionCalls)
	}

	@Test
	fun `falls back automatically when injected audio is unsupported`() = runTest {
		val android = FakeEngine(
			SpeechEngine.ANDROID_ON_DEVICE,
			available = false,
			text = "",
			unavailableReason = SpeechFallbackReason.AUDIO_INJECTION_UNSUPPORTED,
		)
		val whisper = FakeEngine(SpeechEngine.WHISPER_FALLBACK, available = true, text = "local result")
		val result = SpeechRecognitionCoordinator(android, whisper)
			.transcribe(AudioInput(shortArrayOf(1, 2)), LearningLanguage.HINDI)

		assertEquals(SpeechEngine.WHISPER_FALLBACK, result.result.engine)
		assertEquals(SpeechFallbackReason.AUDIO_INJECTION_UNSUPPORTED, result.fallbackReason)
		assertEquals(0, android.transcriptionCalls)
		assertEquals(1, whisper.transcriptionCalls)
	}

	@Test
	fun `retries one transient Android failure before falling back`() = runTest {
		val android = FakeEngine(
			SpeechEngine.ANDROID_ON_DEVICE,
			available = true,
			text = "",
			failures = 2,
			retryableFailure = true,
		)
		val whisper = FakeEngine(SpeechEngine.WHISPER_FALLBACK, available = true, text = "fallback")
		val result = SpeechRecognitionCoordinator(android, whisper)
			.transcribe(AudioInput(shortArrayOf(1, 2)), LearningLanguage.TAMIL)

		assertEquals(2, android.transcriptionCalls)
		assertEquals(1, whisper.transcriptionCalls)
		assertEquals(SpeechFallbackReason.ANDROID_ASR_ERROR, result.fallbackReason)
	}

	@Test
	fun `Whisper only bypasses an available Android engine`() = runTest {
		val android = FakeEngine(SpeechEngine.ANDROID_ON_DEVICE, available = true, text = "wrong Hindi")
		val whisper = FakeEngine(SpeechEngine.WHISPER_FALLBACK, available = true, text = "correct Hindi")
		val result = SpeechRecognitionCoordinator(android, whisper).transcribe(
			AudioInput(shortArrayOf(1, 2)),
			LearningLanguage.HINDI,
			RecognitionMode.WHISPER_ONLY,
		)

		assertEquals(SpeechEngine.WHISPER_FALLBACK, result.result.engine)
		assertEquals(0, android.transcriptionCalls)
		assertEquals(1, whisper.transcriptionCalls)
	}

	@Test
	fun `Android only never falls back to Whisper`() = runTest {
		val android = FakeEngine(SpeechEngine.ANDROID_ON_DEVICE, available = true, text = "", failures = 2)
		val whisper = FakeEngine(SpeechEngine.WHISPER_FALLBACK, available = true, text = "fallback")
		runCatching {
			SpeechRecognitionCoordinator(android, whisper).transcribe(
				AudioInput(shortArrayOf(1, 2)),
				LearningLanguage.HINDI,
				RecognitionMode.ANDROID_ONLY,
			)
		}

		assertEquals(0, whisper.transcriptionCalls)
	}

	private class FakeEngine(
		override val engine: SpeechEngine,
		private val available: Boolean,
		private val text: String,
		private val unavailableReason: SpeechFallbackReason = SpeechFallbackReason.LANGUAGE_UNAVAILABLE,
		private var failures: Int = 0,
		private val retryableFailure: Boolean = false,
	) : SpeechToTextEngine {
		var transcriptionCalls = 0

		override suspend fun transcribe(audio: AudioInput, language: LearningLanguage): TranscriptionResult {
			transcriptionCalls++
			if (failures-- > 0) throw SpeechEngineException(
				SpeechFallbackReason.ANDROID_ASR_ERROR,
				"temporary",
				retryable = retryableFailure,
			)
			return TranscriptionResult(
				segments = listOf(TranscriptionSegment(text, 0, audio.durationMs, language.code)),
				detectedLanguage = language.code,
				processingDurationMs = 1,
				engine = engine,
			)
		}

		override fun observePartialResults(): Flow<PartialTranscript> = emptyFlow()

		override suspend fun isAvailable(language: LearningLanguage) = SpeechEngineAvailability(
			available = available,
			languageStatus = if (available) SpeechLanguageStatus.Ready else SpeechLanguageStatus.AndroidUnsupported,
			fallbackReason = if (available) null else unavailableReason,
		)

		override suspend fun cancel() = Unit
		override fun close() = Unit
	}
}
