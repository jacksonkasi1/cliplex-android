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
	val sha256: String,
	val isDefault: Boolean = false
) {
	TINY_Q5_1(
		fileName = "ggml-tiny-q5_1.bin",
		displayName = "Whisper Tiny (Q5_1) — ~75 MB",
		fileSizeBytes = 32_152_673L,
		sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
		isDefault = true
	),
	BASE_Q5_1(
		fileName = "ggml-base-q5_1.bin",
		displayName = "Whisper Base (Q5_1) — ~150 MB",
		fileSizeBytes = 59_707_625L,
		sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
	);

	val downloadUrl: String
		get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

	val localAssetName: String
		get() = fileName
}
