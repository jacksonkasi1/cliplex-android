package com.jacksonkasi.cliplex.speech

import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.RecognitionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

class SpeechRecognitionCoordinator(
	private val primary: SpeechToTextEngine,
	private val fallback: SpeechToTextEngine,
	private val metricsRecorder: SpeechMetricsRecorder = SpeechMetricsRecorder { },
) {
	suspend fun primaryAvailability(language: LearningLanguage): SpeechEngineAvailability =
		primary.isAvailable(language)

	suspend fun fallbackAvailability(language: LearningLanguage): SpeechEngineAvailability =
		fallback.isAvailable(language)

	fun observePartialResults(): Flow<PartialTranscript> = merge(
		primary.observePartialResults(),
		fallback.observePartialResults(),
	)

	suspend fun transcribe(
		audio: AudioInput,
		language: LearningLanguage,
		mode: RecognitionMode = RecognitionMode.AUTOMATIC,
	): CoordinatedTranscription {
		if (mode == RecognitionMode.WHISPER_ONLY) return fallbackOnly(audio, language)
		val availability = primary.isAvailable(language)
		var fallbackReason = availability.fallbackReason
		if (availability.available) {
			val primaryStarted = System.nanoTime()
			try {
				return primaryAttempt(audio, language, retry = true)
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: SpeechEngineException) {
				fallbackReason = error.reason
			} catch (_: Throwable) {
				fallbackReason = SpeechFallbackReason.UNKNOWN
			}
			metricsRecorder.record(SpeechRecognitionMetrics(
				engine = SpeechEngine.ANDROID_ON_DEVICE,
				language = language.code,
				audioDurationMs = audio.durationMs,
				processingDurationMs = elapsedMs(primaryStarted),
				fallbackReason = fallbackReason,
				partialResultAvailable = false,
				wordTimingAvailable = false,
				success = false,
			))
		}
		if (mode == RecognitionMode.ANDROID_ONLY) {
			throw SpeechEngineException(
				reason = fallbackReason ?: SpeechFallbackReason.ON_DEVICE_RECOGNIZER_UNAVAILABLE,
				message = "Android on-device recognition could not transcribe this audio",
			)
		}

		val fallbackAvailability = fallback.isAvailable(language)
		if (!fallbackAvailability.available) {
			throw SpeechEngineException(
				reason = fallbackReason ?: SpeechFallbackReason.UNKNOWN,
				message = "On-device speech recognition and its local fallback are unavailable",
			)
		}

		val started = System.nanoTime()
		return try {
			val result = fallback.transcribe(audio, language).copy(fallbackReason = fallbackReason)
			metricsRecorder.record(result.toMetrics(audio, fallbackReason, success = true))
			CoordinatedTranscription(result, fallbackReason)
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (error: Throwable) {
			metricsRecorder.record(SpeechRecognitionMetrics(
				engine = SpeechEngine.WHISPER_FALLBACK,
				language = language.code,
				audioDurationMs = audio.durationMs,
				processingDurationMs = elapsedMs(started),
				fallbackReason = fallbackReason,
				partialResultAvailable = false,
				wordTimingAvailable = false,
				success = false,
			))
			throw error
		}
	}

	private suspend fun fallbackOnly(audio: AudioInput, language: LearningLanguage): CoordinatedTranscription {
		val availability = fallback.isAvailable(language)
		if (!availability.available) throw SpeechEngineException(
			SpeechFallbackReason.MODEL_DOWNLOAD_FAILED,
			"The selected Whisper model is not downloaded",
		)
		val started = System.nanoTime()
		return try {
			val result = fallback.transcribe(audio, language)
			metricsRecorder.record(result.toMetrics(audio, null, success = true))
			CoordinatedTranscription(result)
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (error: Throwable) {
			metricsRecorder.record(SpeechRecognitionMetrics(
				engine = SpeechEngine.WHISPER_FALLBACK,
				language = language.code,
				audioDurationMs = audio.durationMs,
				processingDurationMs = elapsedMs(started),
				fallbackReason = null,
				partialResultAvailable = false,
				wordTimingAvailable = false,
				success = false,
			))
			throw error
		}
	}

	private suspend fun primaryAttempt(
		audio: AudioInput,
		language: LearningLanguage,
		retry: Boolean,
	): CoordinatedTranscription {
		return try {
			val result = primary.transcribe(audio, language)
			if (result.text.isBlank()) {
				throw SpeechEngineException(SpeechFallbackReason.EMPTY_RESULT, "Android returned no usable transcript")
			}
			metricsRecorder.record(result.toMetrics(audio, null, success = true))
			CoordinatedTranscription(result)
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (error: SpeechEngineException) {
			if (retry && error.retryable) primaryAttempt(audio, language, retry = false) else throw error
		}
	}

	suspend fun cancel() {
		primary.cancel()
		fallback.cancel()
	}

	fun close() {
		primary.close()
		fallback.close()
	}

	private fun com.jacksonkasi.cliplex.domain.model.TranscriptionResult.toMetrics(
		audio: AudioInput,
		reason: SpeechFallbackReason?,
		success: Boolean,
	) = SpeechRecognitionMetrics(
		engine = engine,
		language = detectedLanguage,
		audioDurationMs = audio.durationMs,
		processingDurationMs = processingDurationMs,
		fallbackReason = reason,
		partialResultAvailable = partialResultAvailable,
		wordTimingAvailable = words.any { it.startTimeMs != null },
		success = success,
	)

	private fun elapsedMs(startedNanos: Long): Long =
		(System.nanoTime() - startedNanos).coerceAtLeast(0L) / 1_000_000L
}
