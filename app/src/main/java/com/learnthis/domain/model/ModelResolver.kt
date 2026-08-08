package com.learnthis.domain.model

class ModelResolver(
	private val modelProvider: ModelProvider = ModelProvider.NONE,
) {
	fun resolve(
		learningLanguage: LearningLanguage,
		speechQuality: SpeechQuality = SpeechQuality.DEFAULT,
	): WhisperConfiguration {
		if (speechQuality == SpeechQuality.HIGH_ACCURACY) {
			val verifiedModel = modelProvider.verifiedModelFor(learningLanguage, speechQuality)
				?: return WhisperConfiguration.Unavailable(
					learningLanguage = learningLanguage,
					speechQuality = speechQuality,
					reason = WhisperConfiguration.Unavailable.Reason.HIGH_ACCURACY_MODEL_NOT_VERIFIED,
				)
			return available(learningLanguage, speechQuality, verifiedModel)
		}

		val modelType = when {
			learningLanguage == LearningLanguage.ENGLISH -> ModelType.TINY_EN_Q5_1
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
