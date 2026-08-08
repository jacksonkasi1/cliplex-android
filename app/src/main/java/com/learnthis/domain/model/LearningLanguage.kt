package com.learnthis.domain.model

enum class LearningLanguage(
	val code: String,
	val displayName: String,
) {
	ENGLISH("en", "English"),
	HINDI("hi", "Hindi"),
	TAMIL("ta", "Tamil"),
	TELUGU("te", "Telugu"),
	MALAYALAM("ml", "Malayalam"),
	KANNADA("kn", "Kannada"),
	BENGALI("bn", "Bengali"),
	MARATHI("mr", "Marathi"),
	ANY_LANGUAGE("auto", "Any Language");

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
