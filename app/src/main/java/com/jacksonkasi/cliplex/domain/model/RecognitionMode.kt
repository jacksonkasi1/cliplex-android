package com.jacksonkasi.cliplex.domain.model

enum class RecognitionMode(val storageKey: String, val displayName: String) {
	AUTOMATIC("automatic", "Automatic"),
	ANDROID_ONLY("android_only", "Android only"),
	WHISPER_ONLY("whisper_only", "Whisper only");

	companion object {
		fun fromStorageValue(value: String?): RecognitionMode = entries.firstOrNull {
			it.storageKey == value || it.name == value
		} ?: AUTOMATIC
	}
}
