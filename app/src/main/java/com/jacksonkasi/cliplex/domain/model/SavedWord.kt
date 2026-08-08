package com.jacksonkasi.cliplex.domain.model

data class SavedWord(
	val word: String,
	val meaning: String? = null,
	val example: String? = null,
	val sourceLanguage: String = "en",
	val targetLanguage: String = "en",
	val savedAt: Long = System.currentTimeMillis(),
)
