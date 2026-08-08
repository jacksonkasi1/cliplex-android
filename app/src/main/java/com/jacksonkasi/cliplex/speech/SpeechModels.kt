package com.jacksonkasi.cliplex.speech

import android.media.AudioFormat
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.TranscriptionResult

enum class SpeechEngine {
	ANDROID_ON_DEVICE,
	WHISPER_FALLBACK,
}

enum class SpeechFallbackReason {
	ON_DEVICE_RECOGNIZER_UNAVAILABLE,
	API_LEVEL_UNSUPPORTED,
	LANGUAGE_UNAVAILABLE,
	MODEL_DOWNLOAD_FAILED,
	AUDIO_INJECTION_UNSUPPORTED,
	RECOGNITION_CONFIGURATION_UNSUPPORTED,
	ANDROID_ASR_ERROR,
	EMPTY_RESULT,
	UNKNOWN,
}

sealed interface SpeechLanguageStatus {
	data object Ready : SpeechLanguageStatus
	data object DownloadRequired : SpeechLanguageStatus
	data class Downloading(val progress: Int?) : SpeechLanguageStatus
	data object AndroidUnsupported : SpeechLanguageStatus
	data class Error(val reason: String) : SpeechLanguageStatus
}

data class SpeechEngineAvailability(
	val available: Boolean,
	val languageStatus: SpeechLanguageStatus,
	val fallbackReason: SpeechFallbackReason? = null,
	val audioInjectionSupported: Boolean = false,
	val segmentedResultsRequested: Boolean = false,
	val wordTimingRequested: Boolean = false,
	val resolvedLanguageTag: String? = null,
)

data class AudioInput(
	val samples: ShortArray,
	val sampleRateHz: Int = 16_000,
	val channelCount: Int = 1,
	val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
) {
	init {
		require(sampleRateHz > 0) { "Sample rate must be positive" }
		require(channelCount > 0) { "Channel count must be positive" }
		require(encoding == AudioFormat.ENCODING_PCM_16BIT) { "Only PCM16 is supported" }
	}

	val durationMs: Long
		get() = samples.size.toLong() * 1_000L / sampleRateHz / channelCount
}

data class PartialTranscript(
	val engine: SpeechEngine,
	val language: String,
	val text: String,
	val isStable: Boolean = false,
)

data class LanguageCapabilities(
	val language: LearningLanguage,
	val androidSpeechStatus: SpeechLanguageStatus,
	val translationSupported: Boolean,
	val translationModelDownloaded: Boolean,
	val fallbackAvailable: Boolean,
)

data class SpeechRecognitionMetrics(
	val engine: SpeechEngine,
	val language: String,
	val audioDurationMs: Long,
	val processingDurationMs: Long,
	val fallbackReason: SpeechFallbackReason?,
	val partialResultAvailable: Boolean,
	val wordTimingAvailable: Boolean,
	val success: Boolean,
)

fun interface SpeechMetricsRecorder {
	fun record(metrics: SpeechRecognitionMetrics)
}

class SpeechEngineException(
	val reason: SpeechFallbackReason,
	message: String,
	val retryable: Boolean = false,
	cause: Throwable? = null,
) : Exception(message, cause)

data class CoordinatedTranscription(
	val result: TranscriptionResult,
	val fallbackReason: SpeechFallbackReason? = null,
)

internal fun AppLanguage.isTranslationSupported(supportedTags: Set<String>): Boolean =
	tag in supportedTags
