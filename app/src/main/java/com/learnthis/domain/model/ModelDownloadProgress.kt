package com.learnthis.domain.model

private const val WHISPER_MODEL_REVISION = "c521a4b02f422512d734391fdf08bb08c0862f68"
private const val WHISPER_MODEL_BASE_URL =
	"https://huggingface.co/ggerganov/whisper.cpp/resolve/$WHISPER_MODEL_REVISION"

sealed interface ModelDownloadProgress {
	data object Idle : ModelDownloadProgress
	data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadProgress
	data class Verifying(val message: String = "Verifying model integrity...") : ModelDownloadProgress
	data object Ready : ModelDownloadProgress
	data class Error(val message: String) : ModelDownloadProgress
}

data class WhisperModelMetadata(
	val fileName: String,
	val technicalName: String,
	val displaySize: String,
	val expectedByteSize: Long,
	val sha256: String,
	val downloadUrl: String = "$WHISPER_MODEL_BASE_URL/$fileName",
)

enum class ModelType(
	val metadata: WhisperModelMetadata,
	val userSelectable: Boolean,
) {
	TINY_EN_Q5_1(
		metadata = WhisperModelMetadata(
			fileName = "ggml-tiny.en-q5_1.bin",
			technicalName = "Whisper Tiny English (Q5_1)",
			displaySize = "31 MB",
			expectedByteSize = 32_166_155L,
			sha256 = "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b",
		),
		userSelectable = true,
	),
	TINY_MULTILINGUAL_Q5_1(
		metadata = WhisperModelMetadata(
			fileName = "ggml-tiny-q5_1.bin",
			technicalName = "Whisper Tiny Multilingual (Q5_1)",
			displaySize = "31 MB",
			expectedByteSize = 32_152_673L,
			sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
		),
		userSelectable = true,
	),
	BASE_MULTILINGUAL_Q5_1(
		metadata = WhisperModelMetadata(
			fileName = "ggml-base-q5_1.bin",
			technicalName = "Whisper Base Multilingual (Q5_1)",
			displaySize = "57 MB",
			expectedByteSize = 59_707_625L,
			sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
		),
		userSelectable = false,
	);

	val fileName: String get() = metadata.fileName
	val expectedByteSize: Long get() = metadata.expectedByteSize
	val sha256: String get() = metadata.sha256
	val downloadUrl: String get() = metadata.downloadUrl
	val localAssetName: String get() = metadata.fileName
}
