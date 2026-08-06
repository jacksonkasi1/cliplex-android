package com.learnthis.domain.model

sealed interface ModelDownloadProgress {
	data object Idle : ModelDownloadProgress
	data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadProgress
	data class Verifying(val message: String = "Verifying model integrity...") : ModelDownloadProgress
	data object Ready : ModelDownloadProgress
	data class Error(val message: String) : ModelDownloadProgress
}

enum class ModelType(
	val fileName: String,
	val displayName: String,
	val fileSizeBytes: Long,
	val isDefault: Boolean = false
) {
	TINY_Q5_1(
		fileName = "ggml-tiny-q5_1.bin",
		displayName = "Whisper Tiny (Q5_1) — ~75 MB",
		fileSizeBytes = 75L * 1024 * 1024,
		isDefault = true
	),
	BASE_Q5_1(
		fileName = "ggml-base-q5_1.bin",
		displayName = "Whisper Base (Q5_1) — ~150 MB",
		fileSizeBytes = 150L * 1024 * 1024
	),
	BASE_Q8_0(
		fileName = "ggml-base-q8_0.bin",
		displayName = "Whisper Base (Q8_0) — ~250 MB",
		fileSizeBytes = 250L * 1024 * 1024
	);

	val downloadUrl: String
		get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

	val localAssetName: String
		get() = fileName
}
