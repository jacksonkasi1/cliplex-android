package com.jacksonkasi.cliplex.domain.model

enum class LearningLanguage(
	val code: String,
	val displayName: String,
	val recognitionTag: String,
) {
	ENGLISH("en", "English", "en-IN"),
	HINDI("hi", "Hindi", "hi-IN"),
	TAMIL("ta", "Tamil", "ta-IN"),
	TELUGU("te", "Telugu", "te-IN"),
	MALAYALAM("ml", "Malayalam", "ml-IN"),
	KANNADA("kn", "Kannada", "kn-IN"),
	BENGALI("bn", "Bengali", "bn-IN"),
	MARATHI("mr", "Marathi", "mr-IN"),
	ANY_LANGUAGE("auto", "Any Language", "");

	val transcriptionLanguage: String get() = code

	companion object {
		fun fromStorageValue(value: String?): LearningLanguage? = entries.firstOrNull {
			it.code == value || it.name == value
		}

		fun fromLegacyLearningMode(mode: LearningMode?): LearningLanguage? = when (mode) {
			LearningMode.ENGLISH_ONLY -> ENGLISH
			LearningMode.MULTILINGUAL -> ANY_LANGUAGE
			null -> null
		}
	}
}

fun LearningLanguage.toLegacyLearningMode(): LearningMode = when (this) {
	LearningLanguage.ENGLISH -> LearningMode.ENGLISH_ONLY
	else -> LearningMode.MULTILINGUAL
}
