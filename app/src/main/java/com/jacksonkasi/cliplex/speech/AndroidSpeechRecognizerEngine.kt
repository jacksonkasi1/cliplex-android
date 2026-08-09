package com.jacksonkasi.cliplex.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionPart
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.TranscriptWord
import com.jacksonkasi.cliplex.domain.model.TranscriptionResult
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidSpeechRecognizerEngine(
	context: Context,
	private val intentFactory: AndroidRecognizerIntentFactory = AndroidRecognizerIntentFactory(),
) : SpeechToTextEngine {
	override val engine = SpeechEngine.ANDROID_ON_DEVICE
	private val appContext = context.applicationContext
	private val operationMutex = Mutex()
	private val partialResults = MutableSharedFlow<PartialTranscript>(extraBufferCapacity = 16)
	private val languageDownloads = mutableMapOf<String, MutableStateFlow<SpeechLanguageStatus>>()
	private var recognizer: SpeechRecognizer? = null
	private var activePipe: RecognizerAudioPipe? = null
	private var writerJob: Job? = null

	override fun observePartialResults(): Flow<PartialTranscript> = partialResults.asSharedFlow()

	override suspend fun isAvailable(language: LearningLanguage): SpeechEngineAvailability {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			return unavailable(SpeechFallbackReason.API_LEVEL_UNSUPPORTED)
		}
		if (language == LearningLanguage.ANY_LANGUAGE) {
			return unavailable(SpeechFallbackReason.LANGUAGE_UNAVAILABLE)
		}
		if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
			return unavailable(SpeechFallbackReason.ON_DEVICE_RECOGNIZER_UNAVAILABLE)
		}

		return operationMutex.withLock {
			val pipe = runCatching { RecognizerAudioPipe.open() }.getOrElse {
				return@withLock unavailable(SpeechFallbackReason.AUDIO_INJECTION_UNSUPPORTED)
			}
			try {
				val intent = intentFactory.create(AndroidRecognizerRequest(
					language = language,
					audioDescriptor = pipe.readSide,
				))
				val support = checkSupport(intent)
				val resolved = support.resolveLanguage(language.recognitionTag)
				val status = resolved.status
				SpeechEngineAvailability(
					available = status is SpeechLanguageStatus.Ready,
					languageStatus = status,
					fallbackReason = when (status) {
						SpeechLanguageStatus.Ready -> null
						SpeechLanguageStatus.DownloadRequired,
						is SpeechLanguageStatus.Downloading,
						SpeechLanguageStatus.AndroidUnsupported -> SpeechFallbackReason.LANGUAGE_UNAVAILABLE
						is SpeechLanguageStatus.Error -> SpeechFallbackReason.RECOGNITION_CONFIGURATION_UNSUPPORTED
					},
					audioInjectionSupported = true,
					segmentedResultsRequested = true,
					wordTimingRequested = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
					resolvedLanguageTag = resolved.installedTag,
				)
			} catch (error: SpeechEngineException) {
				unavailable(error.reason, SpeechLanguageStatus.Error(error.message ?: "Recognition support check failed"))
			} finally {
				pipe.close()
			}
		}
	}

	suspend fun downloadLanguage(language: LearningLanguage): Flow<SpeechLanguageStatus> {
		val state = languageDownloads.getOrPut(language.recognitionTag) {
			MutableStateFlow<SpeechLanguageStatus>(SpeechLanguageStatus.Downloading(null))
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || language == LearningLanguage.ANY_LANGUAGE) {
			state.value = SpeechLanguageStatus.AndroidUnsupported
			return state
		}
		withContext(Dispatchers.Main.immediate) {
			val localRecognizer = requireRecognizer()
			val intent = intentFactory.create(AndroidRecognizerRequest(language = language))
			state.value = SpeechLanguageStatus.Downloading(null)
			try {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
					localRecognizer.triggerModelDownload(intent, appContext.mainExecutor, object : ModelDownloadListener {
						override fun onProgress(completedPercent: Int) {
							state.value = SpeechLanguageStatus.Downloading(completedPercent.coerceIn(0, 100))
						}

						override fun onSuccess() { state.value = SpeechLanguageStatus.Ready }
						override fun onScheduled() { state.value = SpeechLanguageStatus.Downloading(null) }
						override fun onError(error: Int) {
							state.value = SpeechLanguageStatus.Error("Speech support download failed ($error)")
						}
					})
				} else {
					localRecognizer.triggerModelDownload(intent)
				}
			} catch (error: Throwable) {
				state.value = SpeechLanguageStatus.Error(error.message ?: "Speech support download failed")
			}
		}
		return state
	}

	override suspend fun transcribe(audio: AudioInput, language: LearningLanguage): TranscriptionResult =
		operationMutex.withLock {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
				throw SpeechEngineException(
					SpeechFallbackReason.API_LEVEL_UNSUPPORTED,
					"Android on-device recognition requires Android 13 or newer",
				)
			}
			val availability = isAvailableWithoutMutex(language)
			if (!availability.available) {
				throw SpeechEngineException(
					availability.fallbackReason ?: SpeechFallbackReason.RECOGNITION_CONFIGURATION_UNSUPPORTED,
					"Android on-device recognition is not ready for ${language.displayName}",
				)
			}
			transcribeSupported(
				audio,
				language,
				availability.resolvedLanguageTag ?: language.recognitionTag,
			)
		}

	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	private suspend fun isAvailableWithoutMutex(language: LearningLanguage): SpeechEngineAvailability {
		if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
			return unavailable(SpeechFallbackReason.ON_DEVICE_RECOGNIZER_UNAVAILABLE)
		}
		val pipe = RecognizerAudioPipe.open()
		return try {
			val support = checkSupport(intentFactory.create(AndroidRecognizerRequest(
				language = language,
				audioDescriptor = pipe.readSide,
			)))
			val resolved = support.resolveLanguage(language.recognitionTag)
			val status = resolved.status
			SpeechEngineAvailability(
				available = status is SpeechLanguageStatus.Ready,
				languageStatus = status,
				fallbackReason = if (status is SpeechLanguageStatus.Ready) null else SpeechFallbackReason.LANGUAGE_UNAVAILABLE,
				audioInjectionSupported = true,
				segmentedResultsRequested = true,
				wordTimingRequested = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
				resolvedLanguageTag = resolved.installedTag,
			)
		} finally {
			pipe.close()
		}
	}

	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	private suspend fun checkSupport(intent: Intent): RecognitionSupport = withContext(Dispatchers.Main.immediate) {
		suspendCancellableCoroutine { continuation ->
			try {
				requireRecognizer().checkRecognitionSupport(intent, appContext.mainExecutor, object : RecognitionSupportCallback {
					override fun onSupportResult(recognitionSupport: RecognitionSupport) {
						if (continuation.isActive) continuation.resume(recognitionSupport)
					}

					override fun onError(errorCode: Int) {
						if (continuation.isActive) continuation.resumeWithException(SpeechEngineException(
							SpeechFallbackReason.RECOGNITION_CONFIGURATION_UNSUPPORTED,
							"Recognition support check failed ($errorCode)",
						))
					}
				})
			} catch (error: Throwable) {
				if (continuation.isActive) continuation.resumeWithException(error)
			}
		}
	}

	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	private suspend fun transcribeSupported(
		audio: AudioInput,
		language: LearningLanguage,
		resolvedLanguageTag: String,
	): TranscriptionResult =
		withContext(Dispatchers.Main.immediate) {
			suspendCancellableCoroutine { continuation ->
				val completed = AtomicBoolean(false)
				val started = SystemClock.elapsedRealtimeNanos()
				val segments = mutableListOf<SegmentPayload>()
				var receivedPartial = false
				val pipe = RecognizerAudioPipe.open().also { activePipe = it }
				val localRecognizer = requireRecognizer()

				fun cleanup(cancelRecognizer: Boolean) {
					writerJob?.cancel()
					writerJob = null
					activePipe?.close()
					activePipe = null
					if (cancelRecognizer) runCatching { localRecognizer.cancel() }
				}

				fun fail(error: Throwable) {
					if (!completed.compareAndSet(false, true)) return
					cleanup(cancelRecognizer = true)
					if (continuation.isActive) continuation.resumeWithException(error)
				}

				fun finish(bundle: Bundle?) {
					if (!completed.compareAndSet(false, true)) return
					val finalPayload = bundle?.toPayload()
					if (segments.isEmpty() && finalPayload != null && finalPayload.text.isNotBlank()) segments += finalPayload
					val normalized = normalizeSegments(segments, audio.durationMs, language.code)
					val words = segments.flatMap { it.words }
					cleanup(cancelRecognizer = false)
					if (normalized.isEmpty()) {
						if (continuation.isActive) continuation.resumeWithException(SpeechEngineException(
							SpeechFallbackReason.EMPTY_RESULT,
							"Android returned no usable transcript",
						))
						return
					}
					if (continuation.isActive) continuation.resume(TranscriptionResult(
						segments = normalized,
						detectedLanguage = language.code,
						processingDurationMs = elapsedMs(started),
						engine = SpeechEngine.ANDROID_ON_DEVICE,
						words = words,
						durationMs = audio.durationMs,
						partialResultAvailable = receivedPartial,
					))
				}

				localRecognizer.setRecognitionListener(object : RecognitionListener {
					override fun onReadyForSpeech(params: Bundle?) = Unit
					override fun onBeginningOfSpeech() = Unit
					override fun onRmsChanged(rmsdB: Float) = Unit
					override fun onBufferReceived(buffer: ByteArray?) = Unit
					override fun onEndOfSpeech() = Unit
					override fun onEvent(eventType: Int, params: Bundle?) = Unit

					override fun onError(error: Int) {
						fail(error.toSpeechException())
					}

					override fun onPartialResults(partialResultsBundle: Bundle?) {
						val text = partialResultsBundle.firstRecognitionText() ?: return
						receivedPartial = true
						partialResults.tryEmit(PartialTranscript(engine, language.code, text))
					}

					override fun onSegmentResults(segmentResults: Bundle) {
						segmentResults.toPayload()?.takeIf { it.text.isNotBlank() }?.let {
							segments += it
							partialResults.tryEmit(PartialTranscript(engine, language.code, it.text, isStable = true))
						}
					}

					override fun onResults(results: Bundle?) = finish(results)
					override fun onEndOfSegmentedSession() = finish(null)
				})

				continuation.invokeOnCancellation {
					if (completed.compareAndSet(false, true)) {
						appContext.mainExecutor.execute { cleanup(cancelRecognizer = true) }
					}
				}

				try {
					val intent = intentFactory.create(AndroidRecognizerRequest(
						language = language,
						languageTag = resolvedLanguageTag,
						audioDescriptor = pipe.readSide,
						sampleRateHz = audio.sampleRateHz,
						channelCount = audio.channelCount,
						encoding = audio.encoding,
					))
					localRecognizer.startListening(intent)
					writerJob = CoroutineScope(continuation.context).launch(Dispatchers.IO) {
						ensureActive()
						try {
							pipe.writePcm16(audio.samples)
						} catch (cancelled: CancellationException) {
							throw cancelled
						} catch (error: Throwable) {
							appContext.mainExecutor.execute {
								fail(SpeechEngineException(
									SpeechFallbackReason.AUDIO_INJECTION_UNSUPPORTED,
									"Captured audio could not be delivered to Android speech recognition",
									cause = error,
								))
							}
						}
					}
				} catch (error: Throwable) {
					fail(SpeechEngineException(
						SpeechFallbackReason.ANDROID_ASR_ERROR,
						"Android speech recognition could not start",
						cause = error,
					))
				}
			}
		}

	override suspend fun cancel() {
		withContext(Dispatchers.Main.immediate) {
			writerJob?.cancel()
			writerJob = null
			activePipe?.close()
			activePipe = null
			runCatching { recognizer?.cancel() }
		}
	}

	override fun close() {
		appContext.mainExecutor.execute {
			writerJob?.cancel()
			writerJob = null
			activePipe?.close()
			activePipe = null
			recognizer?.destroy()
			recognizer = null
		}
	}

	@RequiresApi(Build.VERSION_CODES.S)
	private fun requireRecognizer(): SpeechRecognizer = recognizer ?: run {
		check(SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
			"On-device SpeechRecognizer is unavailable"
		}
		SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext).also { recognizer = it }
	}

	private fun unavailable(
		reason: SpeechFallbackReason,
		status: SpeechLanguageStatus = SpeechLanguageStatus.AndroidUnsupported,
	) = SpeechEngineAvailability(false, status, reason)

	private data class SegmentPayload(val text: String, val words: List<TranscriptWord>)

	private fun Bundle?.firstRecognitionText(): String? = this
		?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
		?.firstOrNull()
		?.trim()
		?.takeIf { it.isNotBlank() }

	private fun Bundle.toPayload(): SegmentPayload? {
		val text = firstRecognitionText() ?: return null
		val words = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS, RecognitionPart::class.java)
				.orEmpty()
				.map { part ->
					TranscriptWord(
						text = part.rawText,
						startTimeMs = part.timestampMillis.takeIf { it >= 0L },
						confidence = confidence(part.confidenceLevel),
					)
				}
		} else emptyList()
		return SegmentPayload(text, words)
	}

	private fun normalizeSegments(
		payloads: List<SegmentPayload>,
		durationMs: Long,
		language: String,
	): List<TranscriptionSegment> {
		val distinct = payloads.filter { it.text.isNotBlank() }.distinctBy { it.text }
		return distinct.mapIndexed { index, payload ->
			val timedWords = payload.words.mapNotNull { it.startTimeMs }
			val start = timedWords.minOrNull() ?: durationMs * index / distinct.size.coerceAtLeast(1)
			val end = timedWords.maxOrNull()?.coerceAtLeast(start)
				?: durationMs * (index + 1) / distinct.size.coerceAtLeast(1)
			TranscriptionSegment(payload.text, start, end, language, words = payload.words)
		}
	}

	@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
	private fun confidence(level: Int): Float? = when (level) {
		RecognitionPart.CONFIDENCE_LEVEL_LOW -> .2f
		RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_LOW -> .4f
		RecognitionPart.CONFIDENCE_LEVEL_MEDIUM -> .6f
		RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_HIGH -> .8f
		RecognitionPart.CONFIDENCE_LEVEL_HIGH -> 1f
		else -> null
	}

	private data class ResolvedLanguage(
		val status: SpeechLanguageStatus,
		val installedTag: String? = null,
	)

	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	private fun RecognitionSupport.resolveLanguage(requestedTag: String): ResolvedLanguage {
		val requested = Locale.forLanguageTag(requestedTag)
		fun List<String>.match(): String? = firstOrNull { it.equals(requestedTag, ignoreCase = true) }
			?: firstOrNull { candidate ->
				val available = Locale.forLanguageTag(candidate)
				available.language.isNotBlank() && available.language == requested.language
			}
		val installed = installedOnDeviceLanguages.match()
		return when {
			installed != null -> ResolvedLanguage(SpeechLanguageStatus.Ready, installed)
			pendingOnDeviceLanguages.match() != null -> ResolvedLanguage(SpeechLanguageStatus.Downloading(null))
			supportedOnDeviceLanguages.match() != null -> ResolvedLanguage(SpeechLanguageStatus.DownloadRequired)
			else -> ResolvedLanguage(SpeechLanguageStatus.AndroidUnsupported)
		}
	}

	@RequiresApi(Build.VERSION_CODES.S)
	private fun Int.toSpeechException(): SpeechEngineException = when (this) {
		SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
		SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechEngineException(
			SpeechFallbackReason.LANGUAGE_UNAVAILABLE,
			"Selected speech language is unavailable ($this)",
		)
		SpeechRecognizer.ERROR_AUDIO -> SpeechEngineException(
			SpeechFallbackReason.AUDIO_INJECTION_UNSUPPORTED,
			"Android could not consume captured playback audio",
		)
		SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
		SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechEngineException(
			SpeechFallbackReason.ANDROID_ASR_ERROR,
			"Android speech recognition was temporarily unavailable ($this)",
			retryable = true,
		)
		SpeechRecognizer.ERROR_CLIENT,
		SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechEngineException(
			SpeechFallbackReason.ANDROID_ASR_ERROR,
			"Android speech recognition rejected the request ($this)",
		)
		SpeechRecognizer.ERROR_NO_MATCH,
		SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechEngineException(
			SpeechFallbackReason.EMPTY_RESULT,
			"Android did not find speech in the captured audio ($this)",
		)
		else -> SpeechEngineException(
			SpeechFallbackReason.ANDROID_ASR_ERROR,
			"Android speech recognition failed ($this)",
		)
	}

	private fun elapsedMs(startedNanos: Long): Long =
		(SystemClock.elapsedRealtimeNanos() - startedNanos).coerceAtLeast(0L) / 1_000_000L
}
