package com.jacksonkasi.cliplex.domain.model

enum class SpeechQuality(
	val storageKey: String,
	val displayName: String,
) {
	FAST("fast", "Fast"),
	RECOMMENDED("recommended", "Recommended"),
	HIGH_ACCURACY("high_accuracy", "High Accuracy"),
	MAXIMUM("maximum", "Maximum");

	companion object {
		val DEFAULT: SpeechQuality = FAST

		fun fromStorageValue(value: String?): SpeechQuality? = entries.firstOrNull {
			it.storageKey == value || it.name == value
		}
	}
}
