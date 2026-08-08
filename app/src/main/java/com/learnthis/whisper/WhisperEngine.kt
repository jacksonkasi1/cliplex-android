package com.learnthis.whisper

import com.learnthis.domain.model.TranscriptionSegment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WhisperEngine internal constructor(
	private val nativeApi: WhisperNativeApi = JniWhisperNativeApi,
) {
	companion object {
		const val SAMPLE_RATE_HZ = 16000
		const val DEFAULT_N_THREADS = 6

		/* The JNI context is process-global, so this lock must be process-global too. */
		private val nativeMutex = Mutex()
		private var activeNativeApi: WhisperNativeApi? = null
		private var loadedModelPath: String? = null
	}

	/** Backward-compatible load API. Prefer [loadModelDetailed] for diagnostics. */
	suspend fun loadModel(modelPath: String): Result<Unit> =
		loadModelDetailed(modelPath).map { Unit }

	suspend fun loadModelDetailed(modelPath: String): Result<WhisperModelLoadResult> =
		withContext(Dispatchers.IO) {
			runCatching {
				nativeMutex.withLock {
					activateApiLocked()
					loadModelLocked(modelPath)
				}
			}
		}

	/**
	 * Backward-compatible transcription API. It serializes each native call, but
	 * cannot prove which earlier standalone load the caller intended. New callers
	 * that know a model path should use [ensureModelAndTranscribe].
	 */
	suspend fun transcribe(
		samples: ShortArray,
		language: String = "auto",
		nThreads: Int = DEFAULT_N_THREADS,
	): Result<List<TranscriptionSegment>> = transcribeDetailed(
		samples = samples,
		options = WhisperTranscriptionOptions(language = language, nThreads = nThreads),
	).map { it.segments }

	suspend fun transcribeDetailed(
		samples: ShortArray,
		options: WhisperTranscriptionOptions = WhisperTranscriptionOptions(),
		onNewSegment: WhisperSegmentCallback? = null,
	): Result<WhisperTranscriptionResult> = withContext(Dispatchers.IO) {
		runCatching {
			nativeMutex.withLock {
				activateApiLocked()
				transcribeLocked(samples, options, onNewSegment)
			}
		}
	}

	/**
	 * Atomically ensures [modelPath] is the loaded native context and transcribes
	 * before any other coroutine or WhisperEngine instance can switch models.
	 */
	suspend fun ensureModelAndTranscribe(
		modelPath: String,
		samples: ShortArray,
		options: WhisperTranscriptionOptions = WhisperTranscriptionOptions(),
		onNewSegment: WhisperSegmentCallback? = null,
	): Result<WhisperTranscriptionResult> = withContext(Dispatchers.IO) {
		runCatching {
			nativeMutex.withLock {
				activateApiLocked()
				val loadResult = loadModelLocked(modelPath)
				try {
					transcribeLocked(samples, options, onNewSegment).copy(
						modelLoadDiagnostics = loadResult.diagnostics,
					)
				} catch (error: WhisperNativeException) {
					throw error.enriched(modelLoadDiagnostics = loadResult.diagnostics)
				}
			}
		}
	}

	suspend fun getSystemInfo(): Result<String> = withContext(Dispatchers.IO) {
		runCatching {
			nativeMutex.withLock {
				activateApiLocked()
				nativeApi.getNativeSystemInfo()
			}
		}
	}

	suspend fun releaseModel(): Result<Unit> = withContext(Dispatchers.IO) {
		runCatching {
			nativeMutex.withLock {
				activateApiLocked()
				nativeApi.whisperFreeModel()
				loadedModelPath = null
			}
		}
	}

	/** Backward-compatible synchronous release. */
	fun release() {
		runBlocking(Dispatchers.IO) { releaseModel() }
	}

	private fun activateApiLocked() {
		if (activeNativeApi !== nativeApi) {
			activeNativeApi = nativeApi
			loadedModelPath = null
		}
	}

	private fun loadModelLocked(modelPath: String): WhisperModelLoadResult {
		require(modelPath.isNotBlank()) { "Model path is blank" }
		val bridgeStarted = System.nanoTime()
		val json = nativeApi.whisperLoadModelDetailed(modelPath)
			?: throw IllegalStateException("Native model load returned null")
		val bridgeCallMs = elapsedMs(bridgeStarted)
		val fileSizeBytes = File(modelPath).takeIf { it.isFile }?.length() ?: 0L

		val parseStarted = System.nanoTime()
		val parsed = try {
			WhisperJsonParser.parseModelLoad(json)
		} catch (error: WhisperNativeException) {
			throw error.enriched(
				runtime = error.runtime?.withModelFileSize(fileSizeBytes, File(modelPath).name),
				modelLoadDiagnostics = error.modelLoadDiagnostics?.copy(
					bridgeCallMs = bridgeCallMs,
					jsonParseMs = elapsedMs(parseStarted),
				),
			)
		}
		val jsonParseMs = elapsedMs(parseStarted)
		val result = parsed.copy(
			runtime = parsed.runtime.withModelFileSize(fileSizeBytes, File(modelPath).name),
			diagnostics = parsed.diagnostics.copy(
				bridgeCallMs = bridgeCallMs,
				jsonParseMs = jsonParseMs,
			),
		)
		loadedModelPath = modelPath
		return result
	}

	private fun transcribeLocked(
		samples: ShortArray,
		options: WhisperTranscriptionOptions,
		onNewSegment: WhisperSegmentCallback?,
	): WhisperTranscriptionResult {
		val modelPath = loadedModelPath
			?: throw IllegalStateException("Model not loaded")
		val normalizedLanguage = options.language.trim().ifBlank { "auto" }
		val nativeSegmentCallback = onNewSegment?.let { listener ->
			NativeWhisperSegmentCallback { startTimeMs, endTimeMs, text, language, noSpeechProbability ->
				val normalizedText = text.trim()
				if (normalizedText.isNotBlank()) {
					listener.onSegment(TranscriptionSegment(
						text = normalizedText,
						startTimeMs = startTimeMs,
						endTimeMs = endTimeMs,
						language = language,
						noSpeechProb = noSpeechProbability,
					))
				}
			}
		}
		val totalStarted = System.nanoTime()
		val json = nativeApi.whisperTranscribeDetailed(
			samples = samples,
			language = normalizedLanguage,
			nThreads = options.nThreads.coerceAtLeast(1),
			shortEnglishFastMode = options.shortEnglishFastMode,
			segmentCallback = nativeSegmentCallback,
		) ?: throw IllegalStateException("Native transcription returned null")

		val parseStarted = System.nanoTime()
		val fileSizeBytes = File(modelPath).takeIf { it.isFile }?.length() ?: 0L
		val parsed = try {
			WhisperJsonParser.parseTranscription(json)
		} catch (error: WhisperNativeException) {
			throw error.enriched(
				runtime = error.runtime?.withModelFileSize(fileSizeBytes, File(modelPath).name),
				inferenceDiagnostics = error.inferenceDiagnostics?.let { diagnostics ->
					diagnostics.copy(
						timings = diagnostics.timings.copy(
							jsonParseMs = elapsedMs(parseStarted),
							kotlinTotalMs = elapsedMs(totalStarted),
						),
					)
				},
			)
		}
		val jsonParseMs = elapsedMs(parseStarted)
		val kotlinTotalMs = elapsedMs(totalStarted)
		return parsed.copy(
			runtime = parsed.runtime.withModelFileSize(fileSizeBytes, File(modelPath).name),
			diagnostics = parsed.diagnostics.copy(
				timings = parsed.diagnostics.timings.copy(
					jsonParseMs = jsonParseMs,
					kotlinTotalMs = kotlinTotalMs,
				),
			),
		)
	}

	private fun elapsedMs(startedNanos: Long): Double =
		(System.nanoTime() - startedNanos) / 1_000_000.0

	private fun WhisperRuntimeInfo.withModelFileSize(
		fileSizeBytes: Long,
		expectedFileName: String,
	): WhisperRuntimeInfo = copy(
		model = model?.let { info ->
			if (info.fileName == expectedFileName) info.copy(fileSizeBytes = fileSizeBytes) else info
		},
	)

	private fun WhisperNativeException.enriched(
		runtime: WhisperRuntimeInfo? = this.runtime,
		modelLoadDiagnostics: WhisperModelLoadDiagnostics? = this.modelLoadDiagnostics,
		inferenceDiagnostics: WhisperInferenceDiagnostics? = this.inferenceDiagnostics,
	): WhisperNativeException = WhisperNativeException(
		errorCode = errorCode,
		message = message ?: "Native Whisper operation failed",
		runtime = runtime,
		modelLoadDiagnostics = modelLoadDiagnostics,
		inferenceDiagnostics = inferenceDiagnostics,
		cause = this,
	)
}
