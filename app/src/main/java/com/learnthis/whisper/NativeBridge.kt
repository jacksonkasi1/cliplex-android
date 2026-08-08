package com.learnthis.whisper

/**
 * Narrow injectable boundary around the process-wide native Whisper context.
 *
 * The older com.learnthis.util.NativeBridge remains binary compatible. New code
 * uses this detailed API so timing and model-identity information are retained.
 */
internal interface WhisperNativeApi {
	fun getNativeSystemInfo(): String
	fun whisperLoadModelDetailed(modelPath: String): String?
	fun whisperTranscribeDetailed(
		samples: ShortArray,
		language: String,
		nThreads: Int,
		shortEnglishFastMode: Boolean,
		segmentCallback: NativeWhisperSegmentCallback?,
	): String?
	fun whisperFreeModel()
}

internal fun interface NativeWhisperSegmentCallback {
	fun onSegment(
		startTimeMs: Long,
		endTimeMs: Long,
		text: String,
		language: String,
		noSpeechProbability: Float,
	)
}

internal object NativeBridge {
	init {
		System.loadLibrary("learn-this-native")
	}

	external fun getNativeSystemInfo(): String
	external fun whisperLoadModelDetailed(modelPath: String): String?
	external fun whisperTranscribeDetailed(
		samples: ShortArray,
		language: String,
		nThreads: Int,
		shortEnglishFastMode: Boolean,
		segmentCallback: NativeWhisperSegmentCallback?,
	): String?
	external fun whisperFreeModel()
}

internal object JniWhisperNativeApi : WhisperNativeApi {
	override fun getNativeSystemInfo(): String = NativeBridge.getNativeSystemInfo()

	override fun whisperLoadModelDetailed(modelPath: String): String? =
		NativeBridge.whisperLoadModelDetailed(modelPath)

	override fun whisperTranscribeDetailed(
		samples: ShortArray,
		language: String,
		nThreads: Int,
		shortEnglishFastMode: Boolean,
		segmentCallback: NativeWhisperSegmentCallback?,
	): String? = NativeBridge.whisperTranscribeDetailed(
		samples = samples,
		language = language,
		nThreads = nThreads,
		shortEnglishFastMode = shortEnglishFastMode,
		segmentCallback = segmentCallback,
	)

	override fun whisperFreeModel() = NativeBridge.whisperFreeModel()
}
