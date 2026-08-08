package com.jacksonkasi.cliplex.domain.model

class ModelResolver {
	fun resolve(
		learningLanguage: LearningLanguage,
		speechQuality: SpeechQuality = SpeechQuality.DEFAULT,
		preferredModel: ModelType? = null,
	): WhisperConfiguration {
		val modelType = preferredModel?.takeIf { it.supports(learningLanguage) } ?: when {
			learningLanguage == LearningLanguage.ENGLISH && speechQuality == SpeechQuality.MAXIMUM -> ModelType.MEDIUM_EN_Q5_0
			learningLanguage == LearningLanguage.ENGLISH && speechQuality == SpeechQuality.HIGH_ACCURACY -> ModelType.SMALL_EN_Q5_1
			learningLanguage == LearningLanguage.ENGLISH && speechQuality == SpeechQuality.RECOMMENDED -> ModelType.BASE_EN_Q5_1
			learningLanguage == LearningLanguage.ENGLISH -> ModelType.TINY_EN_Q5_1
			speechQuality == SpeechQuality.MAXIMUM -> ModelType.MEDIUM_MULTILINGUAL_Q5_0
			speechQuality == SpeechQuality.HIGH_ACCURACY -> ModelType.SMALL_MULTILINGUAL_Q5_1
			speechQuality == SpeechQuality.RECOMMENDED -> ModelType.BASE_MULTILINGUAL_Q5_1
			else -> ModelType.TINY_MULTILINGUAL_Q5_1
		}
		return available(learningLanguage, speechQuality, modelType)
	}

	private fun available(
		learningLanguage: LearningLanguage,
		speechQuality: SpeechQuality,
		modelType: ModelType,
	): WhisperConfiguration.Available = WhisperConfiguration.Available(
		learningLanguage = learningLanguage,
		speechQuality = speechQuality,
		modelType = modelType,
		transcriptionLanguage = learningLanguage.transcriptionLanguage,
	)
}
