package com.jacksonkasi.cliplex.domain.model

sealed interface WhisperConfiguration {
	val learningLanguage: LearningLanguage
	val speechQuality: SpeechQuality

	data class Available(
		override val learningLanguage: LearningLanguage,
		override val speechQuality: SpeechQuality,
		val modelType: ModelType,
		val transcriptionLanguage: String,
	) : WhisperConfiguration

	data class Unavailable(
		override val learningLanguage: LearningLanguage,
		override val speechQuality: SpeechQuality,
		val reason: Reason,
	) : WhisperConfiguration {
		enum class Reason {
			HIGH_ACCURACY_MODEL_NOT_VERIFIED,
		}
	}
}
