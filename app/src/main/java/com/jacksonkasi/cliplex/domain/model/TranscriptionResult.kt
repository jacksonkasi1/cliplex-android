package com.jacksonkasi.cliplex.domain.model

import com.jacksonkasi.cliplex.speech.SpeechEngine
import com.jacksonkasi.cliplex.speech.SpeechFallbackReason

data class TranscriptionSegment(
 val text: String,
 val startTimeMs: Long,
 val endTimeMs: Long,
 val language: String,
 val confidence: Float? = null,
 val noSpeechProb: Float? = null,
 var translatedText: String? = null,
 val words: List<TranscriptWord> = emptyList(),
 )

data class TranscriptWord(
 val text: String,
 val startTimeMs: Long?,
 val confidence: Float? = null,
)

data class TranscriptionResult(
 val segments: List<TranscriptionSegment>,
 val detectedLanguage: String,
 val processingDurationMs: Long,
 val nativeErrorCode: Int? = null,
 val captureError: CaptureError? = null,
 val engine: SpeechEngine = SpeechEngine.WHISPER_FALLBACK,
 val text: String = segments.joinToString(" ") { it.text }.trim(),
 val words: List<TranscriptWord> = emptyList(),
 val durationMs: Long = segments.maxOfOrNull { it.endTimeMs } ?: 0L,
 val isFinal: Boolean = true,
 val partialResultAvailable: Boolean = false,
 val fallbackReason: SpeechFallbackReason? = null,
 )

data class TranslationResult(
 val originalText: String,
 val translatedText: String,
 val sourceLanguage: String,
 val targetLanguage: String,
 val success: Boolean,
 val error: String? = null
 )

data class AudioHealth(
 val sampleCount: Int,
 val durationMs: Long,
 val rmsLevel: Float,
 val peakAmplitude: Float,
 val dbfs: Float,
 val zeroSamplePercent: Float,
 val clippingPercent: Float,
 val nonSilentDurationMs: Long,
 val vadSpeechDurationMs: Long,
 val isValid: Boolean = true,
 val error: CaptureError? = null
 )
