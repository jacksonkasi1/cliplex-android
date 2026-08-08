package com.jacksonkasi.cliplex.domain.model

enum class LearningMode(
	val storageKey: String,
	val displayName: String,
	val description: String,
	val requiredWhisperModel: ModelType,
	val transcriptionLanguage: String,
) {
	ENGLISH_ONLY(
		storageKey = "english_only",
		displayName = "English Only",
		description = "Best performance for English videos",
		requiredWhisperModel = ModelType.TINY_EN_Q5_1,
		transcriptionLanguage = "en",
	),
	MULTILINGUAL(
		storageKey = "multilingual",
		displayName = "Multiple Languages",
		description = "Understand videos in different languages",
		requiredWhisperModel = ModelType.TINY_MULTILINGUAL_Q5_1,
		transcriptionLanguage = "auto",
	);

	companion object {
		fun fromStorageValue(value: String?): LearningMode? =
			entries.firstOrNull { it.storageKey == value || it.name == value }
	}
}
