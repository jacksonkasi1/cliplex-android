package com.jacksonkasi.cliplex.domain.model

private const val WHISPER_MODEL_REVISION = "c521a4b02f422512d734391fdf08bb08c0862f68"
private const val WHISPER_MODEL_BASE_URL =
	"https://huggingface.co/ggerganov/whisper.cpp/resolve/$WHISPER_MODEL_REVISION"
const val MAX_SELECTABLE_MODEL_BYTES: Long = 600L * 1024L * 1024L

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
	BASE_EN_Q5_1(
		metadata = WhisperModelMetadata(
			fileName = "ggml-base.en-q5_1.bin",
			technicalName = "Whisper Base English (Q5_1)",
			displaySize = "57 MB",
			expectedByteSize = 59_721_011L,
			sha256 = "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f",
		),
		userSelectable = true,
	),
	SMALL_EN_Q5_1(
		metadata = WhisperModelMetadata(
			fileName = "ggml-small.en-q5_1.bin",
			technicalName = "Whisper Small English (Q5_1)",
			displaySize = "181 MB",
			expectedByteSize = 190_098_681L,
			sha256 = "bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30",
		),
		userSelectable = true,
	),
	MEDIUM_EN_Q5_0(
		metadata = WhisperModelMetadata(
			fileName = "ggml-medium.en-q5_0.bin",
			technicalName = "Whisper Medium English (Q5_0)",
			displaySize = "514 MB",
			expectedByteSize = 539_225_533L,
			sha256 = "76733e26ad8fe1c7a5bf7531a9d41917b2adc0f20f2e4f5531688a8c6cd88eb0",
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
		userSelectable = true,
	),
	SMALL_MULTILINGUAL_Q5_1(
		metadata = WhisperModelMetadata(
			fileName = "ggml-small-q5_1.bin",
			technicalName = "Whisper Small Multilingual (Q5_1)",
			displaySize = "181 MB",
			expectedByteSize = 190_085_487L,
			sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
		),
		userSelectable = true,
	),
	MEDIUM_MULTILINGUAL_Q5_0(
		metadata = WhisperModelMetadata(
			fileName = "ggml-medium-q5_0.bin",
			technicalName = "Whisper Medium Multilingual (Q5_0)",
			displaySize = "514 MB",
			expectedByteSize = 539_212_467L,
			sha256 = "19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f",
		),
		userSelectable = true,
	);

	val fileName: String get() = metadata.fileName
	val expectedByteSize: Long get() = metadata.expectedByteSize
	val sha256: String get() = metadata.sha256
	val downloadUrl: String get() = metadata.downloadUrl
	val localAssetName: String get() = metadata.fileName
	val englishOnly: Boolean get() = name.contains("_EN_")

	fun supports(language: LearningLanguage): Boolean = when (language) {
		LearningLanguage.ENGLISH -> englishOnly
		else -> !englishOnly
	}
}
