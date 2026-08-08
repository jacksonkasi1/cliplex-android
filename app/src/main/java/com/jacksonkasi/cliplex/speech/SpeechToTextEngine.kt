package com.jacksonkasi.cliplex.speech

import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.TranscriptionResult
import kotlinx.coroutines.flow.Flow

interface SpeechToTextEngine {
	val engine: SpeechEngine

	suspend fun transcribe(
		audio: AudioInput,
		language: LearningLanguage,
	): TranscriptionResult

	fun observePartialResults(): Flow<PartialTranscript>

	suspend fun isAvailable(language: LearningLanguage): SpeechEngineAvailability

	suspend fun cancel()

	fun close()
}
