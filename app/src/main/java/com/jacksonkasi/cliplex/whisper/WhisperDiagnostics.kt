package com.jacksonkasi.cliplex.whisper

import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import org.json.JSONObject

data class WhisperModelInfo(
	val fileName: String,
	val modelType: String,
	val isMultilingual: Boolean,
	val vocabularySize: Int,
	val audioContextSize: Int,
	val textContextSize: Int,
	val melBins: Int,
	val quantizationType: Int,
	val quantization: String,
	val fileSizeBytes: Long = 0L,
)

data class ArmBackendDiagnostics(
	val abi: String,
	val arm64: Boolean,
	val neon: Boolean,
	val dotProd: Boolean,
	val i8mm: Boolean,
	val kleidiAiIntegrationEnabled: Boolean,
	val kleidiAiSourcesIncluded: Boolean,
	val kleidiAiKernelSelectionObserved: Boolean,
	val modelEligibleForKleidiAi: Boolean,
	val selectedComputePath: String,
	val fallbackReason: String,
	val modelQuantization: String,
)

data class WhisperRuntimeInfo(
	val systemInfo: String,
	val backend: ArmBackendDiagnostics,
	val model: WhisperModelInfo?,
)

data class WhisperModelLoadDiagnostics(
	val cacheHit: Boolean,
	val nativeLoadMs: Double,
	val bridgeCallMs: Double,
	val jsonParseMs: Double,
)

data class WhisperModelLoadResult(
	val runtime: WhisperRuntimeInfo,
	val diagnostics: WhisperModelLoadDiagnostics,
)

/**
 * whisper.cpp's public timing API reports sample/encode/decode as milliseconds
 * per run. The mel field is wall time from whisper_full() entry to the first
 * encoder callback, so it also includes small pre-encode setup costs.
 */
data class WhisperInferenceTimings(
	val jniPcmConversionMs: Double,
	val whisperMelAndPreEncodeMs: Double,
	val whisperSampleMsPerRun: Double,
	val whisperEncodeMsPerRun: Double,
	val whisperDecodeMsPerRun: Double,
	val whisperBatchDecodeMsPerRun: Double,
	val whisperPromptMsPerRun: Double,
	val whisperInferenceMs: Double,
	val nativeTotalMs: Double,
	val jsonParseMs: Double = 0.0,
	val kotlinTotalMs: Double = 0.0,
)

data class WhisperInferenceDiagnostics(
	val sampleCount: Int,
	val audioDurationMs: Long,
	val threadCount: Int,
	val fastModeRequested: Boolean,
	val fastModeApplied: Boolean,
	val audioContextOverride: Int,
	val modelWasWarm: Boolean,
	val inferenceIndex: Long,
	val modelLoadMs: Double,
	val segmentCallbackRequested: Boolean,
	val segmentCallbackFailed: Boolean,
	val callbackSegmentsEmitted: Int,
	val loadedModelFile: String,
	val modelIsMultilingual: Boolean,
	val requestedLanguage: String,
	val translationEnabled: Boolean,
	/** whisper.cpp's detect-only flag; `auto` still detects then transcribes while this remains false. */
	val detectLanguageEnabled: Boolean,
	val timings: WhisperInferenceTimings,
)

/**
 * Receives newly finalized source segments on the native inference thread.
 * Implementations must return quickly, must not call WhisperEngine recursively,
 * and should marshal UI work to their own StateFlow/coroutine. Final JSON remains
 * authoritative if a listener fails or Whisper later revises its output.
 */
fun interface WhisperSegmentCallback {
	fun onSegment(segment: TranscriptionSegment)
}

data class WhisperTranscriptionResult(
	val segments: List<TranscriptionSegment>,
	val detectedLanguage: String,
	val runtime: WhisperRuntimeInfo,
	val diagnostics: WhisperInferenceDiagnostics,
	/** Present when ensureModelAndTranscribe performed the model check/load. */
	val modelLoadDiagnostics: WhisperModelLoadDiagnostics? = null,
)

data class WhisperTranscriptionOptions(
	/** `auto` and non-English codes require a multilingual model; `.en` models require `en`. */
	val language: String = "auto",
	val nThreads: Int = WhisperEngine.DEFAULT_N_THREADS,
	/**
	 * Enables the 512-frame audio-context path only when native validation also
	 * confirms English input between 100 ms and 10 seconds. Sentence timestamps
	 * and segmentation remain enabled for synchronized lesson playback.
	 */
	val shortEnglishFastMode: Boolean = false,
)

class WhisperNativeException(
	val errorCode: String,
	message: String,
	val runtime: WhisperRuntimeInfo? = null,
	val modelLoadDiagnostics: WhisperModelLoadDiagnostics? = null,
	val inferenceDiagnostics: WhisperInferenceDiagnostics? = null,
	cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal object WhisperJsonParser {
	fun parseModelLoad(json: String): WhisperModelLoadResult {
		val root = JSONObject(json)
		val runtime = parseRuntime(root)
		val diagnostics = WhisperModelLoadDiagnostics(
			cacheHit = root.optBoolean("cacheHit"),
			nativeLoadMs = root.optDouble("nativeLoadMs", 0.0),
			bridgeCallMs = 0.0,
			jsonParseMs = 0.0,
		)
		throwIfFailure(
			root = root,
			runtime = runtime,
			modelLoadDiagnostics = diagnostics,
		)
		return WhisperModelLoadResult(
			runtime = runtime,
			diagnostics = diagnostics,
		)
	}

	fun parseTranscription(json: String): WhisperTranscriptionResult {
		val root = JSONObject(json)
		val runtime = parseRuntime(root)
		val diagnosticsJson = root.optJSONObject("diagnostics")
		val parsedDiagnostics = diagnosticsJson?.let(::parseInferenceDiagnostics)
		throwIfFailure(
			root = root,
			runtime = runtime,
			inferenceDiagnostics = parsedDiagnostics,
		)
		val inferenceDiagnostics = parsedDiagnostics
			?: parseInferenceDiagnostics(root.getJSONObject("diagnostics"))
		val detectedLanguage = root.optString("language")
		val values = root.optJSONArray("segments")
		val segments = buildList {
			if (values != null) {
				for (index in 0 until values.length()) {
					val value = values.getJSONObject(index)
					val text = value.optString("text").trim()
					if (text.isNotBlank()) {
						add(TranscriptionSegment(
							text = text,
							startTimeMs = value.optLong("start"),
							endTimeMs = value.optLong("end"),
							language = detectedLanguage,
							noSpeechProb = value.optDouble("noSpeechProb", Double.NaN)
								.takeUnless { it.isNaN() }
								?.toFloat(),
						))
					}
				}
			}
		}
		return WhisperTranscriptionResult(
			segments = segments,
			detectedLanguage = detectedLanguage,
			runtime = runtime,
			diagnostics = inferenceDiagnostics,
		)
	}

	private fun parseInferenceDiagnostics(diagnostics: JSONObject): WhisperInferenceDiagnostics =
		WhisperInferenceDiagnostics(
			sampleCount = diagnostics.optInt("sampleCount"),
			audioDurationMs = diagnostics.optLong("audioDurationMs"),
			threadCount = diagnostics.optInt("threadCount"),
			fastModeRequested = diagnostics.optBoolean("fastModeRequested"),
			fastModeApplied = diagnostics.optBoolean("fastModeApplied"),
			audioContextOverride = diagnostics.optInt("audioContextOverride"),
			modelWasWarm = diagnostics.optBoolean("modelWasWarm"),
			inferenceIndex = diagnostics.optLong("inferenceIndex"),
			modelLoadMs = diagnostics.optDouble("modelLoadMs", 0.0),
			segmentCallbackRequested = diagnostics.optBoolean("segmentCallbackRequested"),
			segmentCallbackFailed = diagnostics.optBoolean("segmentCallbackFailed"),
			callbackSegmentsEmitted = diagnostics.optInt("callbackSegmentsEmitted"),
			loadedModelFile = diagnostics.optString("loadedModelFile"),
			modelIsMultilingual = diagnostics.optBoolean("modelIsMultilingual"),
			requestedLanguage = diagnostics.optString("requestedLanguage"),
			translationEnabled = diagnostics.optBoolean("translationEnabled"),
			detectLanguageEnabled = diagnostics.optBoolean("detectLanguageEnabled"),
			timings = WhisperInferenceTimings(
				jniPcmConversionMs = diagnostics.optDouble("jniPcmConversionMs", 0.0),
				whisperMelAndPreEncodeMs = diagnostics.optDouble("whisperMelAndPreEncodeMs", 0.0),
				whisperSampleMsPerRun = diagnostics.optDouble("whisperSampleMsPerRun", 0.0),
				whisperEncodeMsPerRun = diagnostics.optDouble("whisperEncodeMsPerRun", 0.0),
				whisperDecodeMsPerRun = diagnostics.optDouble("whisperDecodeMsPerRun", 0.0),
				whisperBatchDecodeMsPerRun = diagnostics.optDouble("whisperBatchDecodeMsPerRun", 0.0),
				whisperPromptMsPerRun = diagnostics.optDouble("whisperPromptMsPerRun", 0.0),
				whisperInferenceMs = diagnostics.optDouble("whisperInferenceMs", 0.0),
				nativeTotalMs = diagnostics.optDouble("nativeTotalMs", 0.0),
			),
		)

	private fun parseRuntime(root: JSONObject): WhisperRuntimeInfo {
		val modelJson = root.optJSONObject("model")
		val backendJson = root.optJSONObject("backend") ?: JSONObject()
		return WhisperRuntimeInfo(
			systemInfo = root.optString("systemInfo"),
			backend = ArmBackendDiagnostics(
				abi = backendJson.optString("abi", "unknown"),
				arm64 = backendJson.optBoolean("arm64"),
				neon = backendJson.optBoolean("neon"),
				dotProd = backendJson.optBoolean("dotProd"),
				i8mm = backendJson.optBoolean("i8mm"),
				kleidiAiIntegrationEnabled = backendJson.optBoolean("kleidiAiIntegrationEnabled"),
				kleidiAiSourcesIncluded = backendJson.optBoolean("kleidiAiSourcesIncluded"),
				kleidiAiKernelSelectionObserved = backendJson.optBoolean("kleidiAiKernelSelectionObserved"),
				modelEligibleForKleidiAi = backendJson.optBoolean("modelEligibleForKleidiAi"),
				selectedComputePath = backendJson.optString("selectedComputePath", "unknown"),
				fallbackReason = backendJson.optString("fallbackReason"),
				modelQuantization = backendJson.optString("modelQuantization", "unknown"),
			),
			model = modelJson?.let {
				WhisperModelInfo(
					fileName = it.optString("fileName"),
					modelType = it.optString("type"),
					isMultilingual = it.optBoolean("isMultilingual"),
					vocabularySize = it.optInt("vocabularySize"),
					audioContextSize = it.optInt("audioContextSize"),
					textContextSize = it.optInt("textContextSize"),
					melBins = it.optInt("melBins"),
					quantizationType = it.optInt("quantizationType"),
					quantization = it.optString("quantization", "unknown"),
				)
			},
		)
	}

	private fun throwIfFailure(
		root: JSONObject,
		runtime: WhisperRuntimeInfo,
		modelLoadDiagnostics: WhisperModelLoadDiagnostics? = null,
		inferenceDiagnostics: WhisperInferenceDiagnostics? = null,
	) {
		if (root.optBoolean("success", false)) return
		val code = root.optString("errorCode").ifBlank { "NATIVE_WHISPER_ERROR" }
		val message = root.optString("errorMessage").ifBlank { "Native Whisper operation failed" }
		throw WhisperNativeException(
			errorCode = code,
			message = message,
			runtime = runtime,
			modelLoadDiagnostics = modelLoadDiagnostics,
			inferenceDiagnostics = inferenceDiagnostics,
		)
	}
}
