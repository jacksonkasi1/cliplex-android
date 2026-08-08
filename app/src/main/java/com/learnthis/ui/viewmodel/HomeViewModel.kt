package com.learnthis.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.common.AppLanguage
import com.learnthis.audio.Pcm16
import com.learnthis.audio.AudioDiagnostics
import com.learnthis.data.local.SessionEntity
import com.learnthis.data.local.SessionSegmentsCodec
import com.learnthis.data.repository.ModelRepository
import com.learnthis.data.repository.PreferencesRepository
import com.learnthis.data.repository.SessionRepository
import com.learnthis.domain.model.AudioHealth
import com.learnthis.domain.model.CaptureError
import com.learnthis.domain.model.LearningLanguage
import com.learnthis.domain.model.ModelResolver
import com.learnthis.domain.model.SpeechQuality
import com.learnthis.domain.model.TranscriptionSegment
import com.learnthis.domain.model.WhisperConfiguration
import com.learnthis.service.CaptureService
import com.learnthis.translation.TranslationEngine
import com.learnthis.whisper.WhisperEngine
import com.learnthis.whisper.WhisperModelLoadResult
import com.learnthis.whisper.WhisperSegmentCallback
import com.learnthis.whisper.WhisperTranscriptionOptions
import com.learnthis.whisper.WhisperTranscriptionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class HomeViewModel(
	private val modelRepository: ModelRepository,
	private val preferencesRepository: PreferencesRepository,
	private val sessionRepository: SessionRepository,
	private val whisperEngine: WhisperEngine,
	private val translationEngine: TranslationEngine,
	private val modelResolver: ModelResolver = ModelResolver(),
) : ViewModel() {
	companion object {
		private const val TAG = "HomeViewModel"
	}

	private val _uiState = MutableStateFlow(HomeUiState())
	val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
	private var motherTongue: AppLanguage = AppLanguage.ENGLISH
	private var autoTranslate = true
	private var whisperConfiguration: WhisperConfiguration.Available? = null
	private var modelLoadJob: Job = Job().apply { complete() }
	private val initialModelSelection = CompletableDeferred<Unit>()
	private var processedSessionId = 0L
	private var latestSamples = ShortArray(0)
	private var player: AudioTrack? = null
	private var sessionObserverJob: Job? = null

	init {
		viewModelScope.launch {
			combine(
				preferencesRepository.learningLanguage,
				preferencesRepository.speechQuality,
			) { language, quality -> language to quality }
				.distinctUntilChanged()
				.collect { (language, quality) ->
				modelLoadJob.cancelAndJoin()
				val resolved = language?.let { modelResolver.resolve(it, quality) }
				whisperConfiguration = resolved as? WhisperConfiguration.Available
				when (resolved) {
					null -> {
					_uiState.value = _uiState.value.copy(isModelReady = false, modelName = null, modelError = "Select a speech model in Settings")
					}
					is WhisperConfiguration.Available -> modelLoadJob = loadModel(resolved)
					is WhisperConfiguration.Unavailable -> {
						_uiState.value = _uiState.value.copy(
							isModelReady = false,
							modelName = resolved.learningLanguage.displayName,
							modelError = "High Accuracy is not available until a model is validated on this device.",
						)
					}
				}
				if (!initialModelSelection.isCompleted) initialModelSelection.complete(Unit)
			}
		}
		viewModelScope.launch {
			preferencesRepository.motherTongue.collect { if (it != null) motherTongue = it }
		}
		viewModelScope.launch {
			preferencesRepository.autoTranslate.collect { autoTranslate = it }
		}
		viewModelScope.launch {
			preferencesRepository.captureVideo.collect { enabled ->
				_uiState.update { it.copy(captureVideo = enabled) }
			}
		}
		viewModelScope.launch {
			preferencesRepository.savedWords.collect { savedWords ->
				_uiState.update { it.copy(savedWords = savedWords) }
			}
		}
		viewModelScope.launch {
			CaptureService.captureState.collect { state ->
				_uiState.value = _uiState.value.copy(
					captureState = state,
					error = (state as? CaptureService.CaptureState.Error)?.error,
					captureErrorDetail = (state as? CaptureService.CaptureState.Error)?.detail,
				)
			}
		}
		viewModelScope.launch {
			CaptureService.capturedAudioDuration.collect { duration ->
				_uiState.value = _uiState.value.copy(captureDurationMs = duration)
			}
		}
		viewModelScope.launch {
			CaptureService.overlayStatus.collect { status ->
				_uiState.value = _uiState.value.copy(overlayStatus = status)
			}
		}
		viewModelScope.launch {
			CaptureService.latestSession.collect { session ->
				if (session != null && session.id > processedSessionId) {
					processedSessionId = session.id
					openSession(session.id)
					runCatching { processSession(session) }.onFailure { error ->
						Log.e(TAG, "Unexpected lesson processing failure for id=${session.id}", error)
						markSessionFailed(session.id, error.message ?: error.javaClass.simpleName)
						_uiState.update {
							it.copy(
								isProcessing = false,
								processingStage = null,
								error = CaptureError.ASR_EMPTY_RESULT,
								captureErrorDetail = error.message,
							)
						}
					}
				}
			}
		}
	}

	fun openSession(id: Long) {
		if (_uiState.value.activeSession?.id == id && sessionObserverJob?.isActive == true) return
		sessionObserverJob?.cancel()
		sessionObserverJob = viewModelScope.launch {
			sessionRepository.observeSessionById(id).collect { session ->
				if (session == null) {
					_uiState.update { it.copy(activeSession = null, segments = emptyList()) }
					return@collect
				}
				val persistedSegments = SessionSegmentsCodec.decode(session.segmentsJson)
				_uiState.update { current ->
					current.copy(
						activeSession = session,
						segments = when {
							persistedSegments.isNotEmpty() -> persistedSegments
							current.activeSession?.id == id -> current.segments
							else -> emptyList()
						},
					)
				}
			}
		}
	}

	fun closeSession() {
		sessionObserverJob?.cancel()
		sessionObserverJob = null
		_uiState.update { it.copy(activeSession = null, selectedWord = null) }
	}

	/** Retry after Model Management finishes downloading the currently resolved model. */
	fun refreshResolvedModel() {
		val configuration = whisperConfiguration ?: return
		modelLoadJob.cancel()
		modelLoadJob = loadModel(configuration)
	}

	private fun loadModel(configuration: WhisperConfiguration.Available): Job = viewModelScope.launch(Dispatchers.IO) {
		val type = configuration.modelType
		val displayName = configuration.displayName
		_uiState.value = _uiState.value.copy(isModelReady = false, modelName = displayName, modelError = null)
		if (!modelRepository.isModelAvailable(type)) {
			_uiState.value = _uiState.value.copy(modelError = "Selected model is missing. Choose another model in Settings.")
			return@launch
		}
		val result = whisperEngine.loadModelDetailed(modelRepository.getModelFile(type).absolutePath)
		result.fold(
			onSuccess = { loaded ->
				Log.i(
					TAG,
					"Speech model ready: ${type.name}; cacheHit=${loaded.diagnostics.cacheHit} " +
						"nativeLoadMs=${loaded.diagnostics.nativeLoadMs}",
				)
			},
			onFailure = { Log.e(TAG, "Speech model load failed: ${type.name}", it) },
		)
		_uiState.value = _uiState.value.copy(
			isModelReady = result.isSuccess,
			modelName = displayName,
			modelError = result.exceptionOrNull()?.message,
			asrDiagnostics = result.getOrNull()?.let {
				AsrDiagnosticsUiState.fromModelLoad(displayName, it)
			} ?: _uiState.value.asrDiagnostics,
		)
	}

	private suspend fun processSession(session: CaptureService.CapturedAudioSession) {
		latestSamples = session.samples
		val persistedSession = sessionRepository.getSessionById(session.id)
		_uiState.value = _uiState.value.copy(
			activeSession = persistedSession,
			isProcessing = true,
			processingStage = "Checking captured audio…",
			audioHealth = session.health,
			segments = emptyList(),
			error = session.health.error,
		)
		if (!session.health.isValid) {
			markSessionFailed(session.id, session.health.error?.name ?: "Captured audio was not usable")
			_uiState.value = _uiState.value.copy(isProcessing = false, processingStage = null)
			return
		}
		initialModelSelection.await()
		modelLoadJob.join()
		if (!_uiState.value.isModelReady) {
			Log.e(TAG, "Cannot process capture because the selected speech model is not ready")
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				processingStage = null,
				error = CaptureError.MODEL_NOT_LOADED,
			)
			markSessionFailed(session.id, "Speech model is not ready")
			return
		}
		val configuration = whisperConfiguration ?: run {
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				processingStage = null,
				error = CaptureError.MODEL_NOT_LOADED,
			)
			return
		}
		val targetLanguage = motherTongue
		val shouldTranslate = autoTranslate
		val modelType = configuration.modelType
		val modelFile = modelRepository.getModelFile(modelType)
		val firstVisibleAtNanos = AtomicLong(0L)
		val partialSegments = mutableListOf<TranscriptionSegment>()
		val partialCallback = WhisperSegmentCallback { segment ->
			firstVisibleAtNanos.compareAndSet(0L, SystemClock.elapsedRealtimeNanos())
			val snapshot = synchronized(partialSegments) {
				partialSegments.removeAll {
					it.startTimeMs == segment.startTimeMs && it.endTimeMs == segment.endTimeMs
				}
				partialSegments += segment
				partialSegments.sortedBy(TranscriptionSegment::startTimeMs)
			}
			_uiState.update { current ->
				current.copy(
					segments = snapshot,
					processingStage = "Transcribing… source text is appearing",
				)
			}
		}
		_uiState.value = _uiState.value.copy(processingStage = "Transcribing on device…", error = null)
		Log.i(
			TAG,
			"Transcribing capture id=${session.id} durationMs=${session.health.durationMs} " +
				"learningLanguage=${configuration.learningLanguage.code} quality=${configuration.speechQuality.name} " +
				"model=${configuration.modelType.fileName} requestedLanguage=${configuration.transcriptionLanguage}",
		)
		val transcription = whisperEngine.ensureModelAndTranscribe(
			modelPath = modelFile.absolutePath,
			samples = session.samples,
			options = WhisperTranscriptionOptions(
				language = configuration.transcriptionLanguage,
				nThreads = WhisperEngine.DEFAULT_N_THREADS,
				shortEnglishFastMode = configuration.learningLanguage == LearningLanguage.ENGLISH &&
					session.health.durationMs <= 10_000,
			),
			onNewSegment = partialCallback,
		)
		val transcriptionResult = transcription.getOrElse {
			Log.e(TAG, "Speech transcription failed for capture id=${session.id}", it)
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				processingStage = null,
				error = CaptureError.ASR_EMPTY_RESULT,
				asrDiagnostics = AsrDiagnosticsUiState.failed(
					source = "Captured playback",
					model = configuration.displayName,
					health = session.health,
					message = it.message,
				),
			)
			markSessionFailed(session.id, it.message ?: "Speech transcription failed")
			return
		}
		val originalSegments = transcriptionResult.segments
		val finishToSourceMs = firstVisibleAtNanos.get().takeIf { it > 0L }?.let { firstVisible ->
			(firstVisible - session.finishedAtElapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000.0
		} ?: elapsedMs(session.finishedAtElapsedRealtimeNanos)
		val diagnostics = AsrDiagnosticsUiState.fromTranscription(
			source = "Captured playback",
			model = configuration.displayName,
			health = session.health,
			result = transcriptionResult,
			finishToSourceMs = finishToSourceMs,
		)
		Log.i(TAG, "Speech transcription completed for capture id=${session.id}; segments=${originalSegments.size}")
		if (originalSegments.isEmpty()) {
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				processingStage = null,
				error = CaptureError.NO_SPEECH_DETECTED,
				asrDiagnostics = diagnostics.copy(status = "NO SPEECH"),
			)
			markSessionFailed(session.id, "Spoken dialogue was not detected")
			return
		}

		// Source text is visible before any ML Kit model download or translation.
		updatePersistedSession(
			sessionId = session.id,
			segments = originalSegments,
			targetLanguage = targetLanguage,
			processingState = if (shouldTranslate && targetLanguage.translationSupported) "TRANSLATING" else "READY",
		)
		_uiState.value = _uiState.value.copy(
			segments = originalSegments,
			processingStage = if (shouldTranslate && targetLanguage.translationSupported) {
				"Transcript ready • translating…"
			} else null,
			asrDiagnostics = diagnostics,
		)
		if (!shouldTranslate || originalSegments.all { it.language == targetLanguage.tag }) {
			updatePersistedSession(session.id, originalSegments, targetLanguage, "READY")
			_uiState.value = _uiState.value.copy(isProcessing = false, processingStage = null, error = null)
			return
		}
		if (!targetLanguage.translationSupported) {
			updatePersistedSession(
				session.id,
				originalSegments,
				targetLanguage,
				"READY",
				"Translation to ${targetLanguage.displayName} is not available on this device",
			)
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				processingStage = null,
				error = CaptureError.TRANSLATION_FAILED,
				captureErrorDetail = "${targetLanguage.displayName} is not supported by the installed on-device translator",
			)
			return
		}

		val translationStarted = SystemClock.elapsedRealtimeNanos()
		val translated = mutableListOf<TranscriptionSegment>()
		var translationFailed = false
		for (segment in originalSegments) {
			val sourceLanguage = when {
				segment.language.isNotBlank() -> segment.language
				configuration.transcriptionLanguage != "auto" -> configuration.transcriptionLanguage
				else -> null
			}
			val translatedText = if (sourceLanguage == null) {
				translationFailed = true
				null
			} else if (sourceLanguage == targetLanguage.tag) {
				null
			} else {
				translationEngine.initialize(sourceLanguage, targetLanguage.tag)
					.fold(
						onSuccess = { translationEngine.translate(segment.text).getOrNull() },
						onFailure = { null },
					)
			}
			if (sourceLanguage != null && sourceLanguage != targetLanguage.tag && translatedText == null) {
				translationFailed = true
			}
			translated += segment.copy(translatedText = translatedText)
			_uiState.value = _uiState.value.copy(segments = translated.toList())
		}
		val translationMs = elapsedMs(translationStarted)
		updatePersistedSession(
			session.id,
			translated,
			targetLanguage,
			"READY",
			if (translationFailed) "Some sentences could not be translated" else null,
		)
		_uiState.value = _uiState.value.copy(
			isProcessing = false,
			processingStage = null,
			error = if (translationFailed) CaptureError.TRANSLATION_FAILED else null,
			asrDiagnostics = diagnostics.copy(translationMs = translationMs),
		)
	}

	private suspend fun updatePersistedSession(
		sessionId: Long,
		segments: List<TranscriptionSegment>,
		targetLanguage: AppLanguage,
		processingState: String,
		captureError: String? = null,
	) = withContext(Dispatchers.IO) {
		val current = sessionRepository.getSessionById(sessionId) ?: return@withContext
		sessionRepository.updateSession(current.copy(
			title = segments.firstOrNull()?.text?.trim()?.take(56)?.takeIf { it.isNotBlank() } ?: current.title,
			sourceLanguage = segments.firstOrNull()?.language.orEmpty(),
			targetLanguage = targetLanguage.tag,
			segmentCount = segments.size,
			segmentsJson = SessionSegmentsCodec.encode(segments),
			processingState = processingState,
			captureError = captureError ?: current.captureError,
		))
	}

	private suspend fun markSessionFailed(sessionId: Long, detail: String) = withContext(Dispatchers.IO) {
		val current = sessionRepository.getSessionById(sessionId) ?: return@withContext
		sessionRepository.updateSession(current.copy(processingState = "FAILED", captureError = detail))
	}

	fun finishCapture(context: Context) {
		context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_FINISH))
	}

	fun stopLearningMode(context: Context) {
		context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
	}

	fun selectWord(word: String, exampleSentence: String) {
		val normalized = word.trim().trim { !it.isLetter() && it != '\'' }
		if (normalized.isBlank()) return
		_uiState.update {
			it.copy(selectedWord = WordDetailsUiState(
				word = normalized,
				exampleSentence = exampleSentence,
				isLoading = true,
			))
		}
		viewModelScope.launch {
			val target = motherTongue
			val meaning = when {
				target == AppLanguage.ENGLISH -> Result.success(normalized)
				!target.translationSupported -> Result.failure(
					IllegalStateException("${target.displayName} translation is not available on this device"),
				)
				else -> translationEngine.initialize("en", target.tag).fold(
					onSuccess = { translationEngine.translate(normalized) },
					onFailure = { Result.failure(it) },
				)
			}
			_uiState.update { current ->
				val selected = current.selectedWord
				if (selected == null || !selected.word.equals(normalized, ignoreCase = true)) current else current.copy(
					selectedWord = selected.copy(
						meaning = meaning.getOrNull(),
						isLoading = false,
						error = meaning.exceptionOrNull()?.message,
					),
				)
			}
		}
	}

	fun dismissWord() {
		_uiState.update { it.copy(selectedWord = null) }
	}

	fun setSelectedWordSaved(saved: Boolean) {
		val word = _uiState.value.selectedWord?.word ?: return
		viewModelScope.launch { preferencesRepository.setWordSaved(word, saved) }
	}

	fun deleteVideo(sessionId: Long) {
		viewModelScope.launch(Dispatchers.IO) { sessionRepository.deleteVideo(sessionId) }
	}

	fun deleteLesson(sessionId: Long, onDeleted: () -> Unit = {}) {
		viewModelScope.launch {
			withContext(Dispatchers.IO) { sessionRepository.deleteSession(sessionId) }
			closeSession()
			onDeleted()
		}
	}

	fun playSegment(segment: TranscriptionSegment) {
		val start = (segment.startTimeMs * CaptureService.SAMPLE_RATE_HZ / 1000).toInt().coerceIn(0, latestSamples.size)
		val end = (segment.endTimeMs * CaptureService.SAMPLE_RATE_HZ / 1000).toInt().coerceIn(start, latestSamples.size)
		if (end <= start) return
		player?.release()
		val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
		val format = AudioFormat.Builder().setSampleRate(CaptureService.SAMPLE_RATE_HZ)
			.setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
		player = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
			.setBufferSizeInBytes((end - start) * 2).setTransferMode(AudioTrack.MODE_STATIC).build().also {
				it.write(latestSamples, start, end - start); it.play()
			}
	}

	fun saveDebugWav(context: Context) {
		if (latestSamples.isEmpty()) {
			_uiState.value = _uiState.value.copy(debugMessage = "No captured audio is available to save")
			return
		}
		viewModelScope.launch(Dispatchers.IO) {
			val directory = File(context.getExternalFilesDir(null), "diagnostics").apply { mkdirs() }
			val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
			directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
			val sampleCount = minOf(latestSamples.size, CaptureService.SAMPLE_RATE_HZ * 10)
			val start = latestSamples.size - sampleCount
			val file = File(directory, "capture-${System.currentTimeMillis()}.wav")
			file.writeBytes(Pcm16.wav(latestSamples.copyOfRange(start, latestSamples.size), CaptureService.SAMPLE_RATE_HZ))
			_uiState.value = _uiState.value.copy(debugMessage = "Saved ${file.name} in app diagnostics storage")
		}
	}

	fun runKnownGoodAsrTest(context: Context) {
		if (!_uiState.value.isModelReady || _uiState.value.isProcessing) return
		val configuration = whisperConfiguration
		if (configuration?.learningLanguage != LearningLanguage.ENGLISH) {
			_uiState.value = _uiState.value.copy(
				debugMessage = "Select English in Settings before running the English known-good test",
			)
			return
		}
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				isProcessing = true,
				processingStage = "Running known-good English test…",
				debugMessage = "Validating the bundled JFK sample…",
			)
			val outcome = withContext(Dispatchers.IO) {
				runCatching<Pair<AudioHealth, WhisperTranscriptionResult>> {
					val wav = Pcm16.readWav(context.assets.open("jfk.wav").use { it.readBytes() })
					val mono = if (wav.channels == 2) Pcm16.stereoToMono(wav.samples) else wav.samples
					val samples = Pcm16.resampleLinear(mono, wav.sampleRate, CaptureService.SAMPLE_RATE_HZ)
					val health = AudioDiagnostics.analyze(samples, CaptureService.SAMPLE_RATE_HZ)
					require(health.isValid) { "Known-good PCM failed validation: ${health.error}" }
					val result = runEnglishDiagnostic(configuration, samples, health).getOrThrow()
					health to result
				}
			}
			outcome.fold(
				onSuccess = { (health, result) ->
					val text = result.segments.joinToString(" ") { it.text }.trim()
					val normalized = text.lowercase(Locale.US)
					val passed = text.isNotBlank() && "country" in normalized &&
						("ask" in normalized || "fellow" in normalized)
					_uiState.value = _uiState.value.copy(
						isProcessing = false,
						processingStage = null,
						debugMessage = if (passed) "Known-good English test: PASS" else "Known-good English test: FAIL",
						asrDiagnostics = AsrDiagnosticsUiState.fromTranscription(
							source = "Known-good JFK.wav",
							model = configuration.displayName,
							health = health,
							result = result,
							finishToSourceMs = result.diagnostics.timings.kotlinTotalMs,
						).copy(status = if (passed) "PASS" else "FAIL"),
					)
				},
				onFailure = { error ->
					_uiState.value = _uiState.value.copy(
						isProcessing = false,
						processingStage = null,
						debugMessage = "Known-good English test: FAIL (${error.message})",
						asrDiagnostics = AsrDiagnosticsUiState.failed(
							source = "Known-good JFK.wav",
							model = configuration.displayName,
							health = null,
							message = error.message,
						),
					)
				},
			)
		}
	}

	fun runLastCapturedAsrTest() {
		if (!_uiState.value.isModelReady || _uiState.value.isProcessing) return
		val configuration = whisperConfiguration
		if (configuration?.learningLanguage != LearningLanguage.ENGLISH) {
			_uiState.value = _uiState.value.copy(debugMessage = "Select English before testing captured English audio")
			return
		}
		val samples = latestSamples.copyOf()
		if (samples.isEmpty()) {
			_uiState.value = _uiState.value.copy(debugMessage = "No captured audio is available yet")
			return
		}
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(
				isProcessing = true,
				processingStage = "Testing last captured audio…",
				debugMessage = "Validating captured PCM…",
			)
			val health = withContext(Dispatchers.Default) {
				AudioDiagnostics.analyze(samples, CaptureService.SAMPLE_RATE_HZ)
			}
			if (!health.isValid) {
				_uiState.value = _uiState.value.copy(
					isProcessing = false,
					processingStage = null,
					debugMessage = "Captured-audio test: FAIL (${health.error})",
					asrDiagnostics = AsrDiagnosticsUiState.failed(
						source = "Last captured playback",
						model = configuration.displayName,
						health = health,
						message = health.error?.name,
					),
				)
				return@launch
			}
			val result = runEnglishDiagnostic(configuration, samples, health)
			result.fold(
				onSuccess = { transcription ->
					val passed = transcription.segments.isNotEmpty()
					_uiState.value = _uiState.value.copy(
						isProcessing = false,
						processingStage = null,
						segments = transcription.segments,
						audioHealth = health,
						debugMessage = "Captured-audio test: ${if (passed) "PASS" else "FAIL — no speech detected"}",
						asrDiagnostics = AsrDiagnosticsUiState.fromTranscription(
							source = "Last captured playback",
							model = configuration.displayName,
							health = health,
							result = transcription,
							finishToSourceMs = transcription.diagnostics.timings.kotlinTotalMs,
						).copy(status = if (passed) "PASS" else "NO SPEECH"),
					)
				},
				onFailure = { error ->
					_uiState.value = _uiState.value.copy(
						isProcessing = false,
						processingStage = null,
						debugMessage = "Captured-audio test: FAIL (${error.message})",
						asrDiagnostics = AsrDiagnosticsUiState.failed(
							source = "Last captured playback",
							model = configuration.displayName,
							health = health,
							message = error.message,
						),
					)
				},
			)
		}
	}

	private suspend fun runEnglishDiagnostic(
		configuration: WhisperConfiguration.Available,
		samples: ShortArray,
		health: AudioHealth,
	): Result<WhisperTranscriptionResult> = whisperEngine.ensureModelAndTranscribe(
		modelPath = modelRepository.getModelFile(configuration.modelType).absolutePath,
		samples = samples,
		options = WhisperTranscriptionOptions(
			language = "en",
			nThreads = WhisperEngine.DEFAULT_N_THREADS,
			shortEnglishFastMode = health.durationMs <= 10_000,
		),
	)

	private fun elapsedMs(startedNanos: Long): Double =
		(SystemClock.elapsedRealtimeNanos() - startedNanos).coerceAtLeast(0L) / 1_000_000.0

	override fun onCleared() {
		player?.release()
		translationEngine.close()
		super.onCleared()
	}
}

data class AsrDiagnosticsUiState(
	val source: String,
	val model: String,
	val modelFile: String? = null,
	val modelLoaded: Boolean,
	val modelAlreadyLoaded: Boolean? = null,
	val modelLoadMs: Double? = null,
	val nativeBackend: String? = null,
	val threadCount: Int? = null,
	val fastMode: Boolean? = null,
	val audioContextOverride: Int? = null,
	val callbackSegments: Int? = null,
	val callbackFailed: Boolean? = null,
	val audioDurationMs: Long? = null,
	val sampleCount: Int? = null,
	val rms: Float? = null,
	val peak: Float? = null,
	val jniConversionMs: Double? = null,
	val melAndPreEncodeMs: Double? = null,
	val encodeMs: Double? = null,
	val decodeMs: Double? = null,
	val nativeTotalMs: Double? = null,
	val jsonParseMs: Double? = null,
	val inferenceMs: Double? = null,
	val finishToSourceMs: Double? = null,
	val translationMs: Double? = null,
	val resultPreview: String? = null,
	val status: String,
	val errorMessage: String? = null,
) {
	fun asText(): String = buildString {
		appendLine("ASR_DIAGNOSTICS")
		appendLine("status = $status")
		appendLine("source = $source")
		appendLine("model = $model${modelFile?.let { " ($it)" }.orEmpty()}")
		appendLine("modelLoaded = $modelLoaded")
		modelAlreadyLoaded?.let { appendLine("modelAlreadyLoaded = $it") }
		modelLoadMs?.let { appendLine("modelLoadMs = ${formatMs(it)}") }
		threadCount?.let { appendLine("threads = $it") }
		fastMode?.let { appendLine("shortEnglishFastMode = $it") }
		audioContextOverride?.takeIf { it > 0 }?.let { appendLine("audioContext = $it") }
		callbackSegments?.let { appendLine("partialSegments = $it") }
		callbackFailed?.let { appendLine("partialCallbackFailed = $it") }
		audioDurationMs?.let { appendLine("audioDurationMs = $it") }
		sampleCount?.let { appendLine("pcmSamples = $it") }
		if (sampleCount != null) appendLine("pcmSampleRate = ${WhisperEngine.SAMPLE_RATE_HZ}")
		rms?.let { appendLine("rms = ${"%.1f".format(Locale.US, it)}") }
		peak?.let { appendLine("peak = ${"%.0f".format(Locale.US, it)}") }
		jniConversionMs?.let { appendLine("jniConversionMs = ${formatMs(it)}") }
		melAndPreEncodeMs?.let { appendLine("melAndPreEncodeMs = ${formatMs(it)}") }
		encodeMs?.let { appendLine("encodeMsPerRun = ${formatMs(it)}") }
		decodeMs?.let { appendLine("decodeMsPerRun = ${formatMs(it)}") }
		nativeTotalMs?.let { appendLine("nativeTotalMs = ${formatMs(it)}") }
		jsonParseMs?.let { appendLine("jsonParseMs = ${formatMs(it)}") }
		inferenceMs?.let { appendLine("warmInferenceMs = ${formatMs(it)}") }
		finishToSourceMs?.let { appendLine("finishToVisibleSourceMs = ${formatMs(it)}") }
		translationMs?.let { appendLine("translationMs = ${formatMs(it)}") }
		nativeBackend?.let { appendLine("nativeBackend = $it") }
		resultPreview?.let { appendLine("result = $it") }
		errorMessage?.let { appendLine("error = $it") }
	}.trimEnd()

	companion object {
		fun fromModelLoad(model: String, result: WhisperModelLoadResult): AsrDiagnosticsUiState =
			AsrDiagnosticsUiState(
				source = "Model preload",
				model = model,
				modelFile = result.runtime.model?.fileName,
				modelLoaded = true,
				modelAlreadyLoaded = result.diagnostics.cacheHit,
				modelLoadMs = result.diagnostics.nativeLoadMs,
				nativeBackend = result.runtime.systemInfo,
				status = "READY",
			)

		fun fromTranscription(
			source: String,
			model: String,
			health: AudioHealth,
			result: WhisperTranscriptionResult,
			finishToSourceMs: Double,
		): AsrDiagnosticsUiState {
			val timings = result.diagnostics.timings
			return AsrDiagnosticsUiState(
				source = source,
				model = model,
				modelFile = result.runtime.model?.fileName,
				modelLoaded = true,
				modelAlreadyLoaded = result.modelLoadDiagnostics?.cacheHit ?: result.diagnostics.modelWasWarm,
				modelLoadMs = result.modelLoadDiagnostics?.nativeLoadMs,
				nativeBackend = result.runtime.systemInfo,
				threadCount = result.diagnostics.threadCount,
				fastMode = result.diagnostics.fastModeApplied,
				audioContextOverride = result.diagnostics.audioContextOverride,
				callbackSegments = result.diagnostics.callbackSegmentsEmitted,
				callbackFailed = result.diagnostics.segmentCallbackFailed,
				audioDurationMs = health.durationMs,
				sampleCount = health.sampleCount,
				rms = health.rmsLevel,
				peak = health.peakAmplitude,
				jniConversionMs = timings.jniPcmConversionMs,
				melAndPreEncodeMs = timings.whisperMelAndPreEncodeMs,
				encodeMs = timings.whisperEncodeMsPerRun,
				decodeMs = timings.whisperDecodeMsPerRun,
				nativeTotalMs = timings.nativeTotalMs,
				jsonParseMs = timings.jsonParseMs,
				inferenceMs = timings.whisperInferenceMs,
				finishToSourceMs = finishToSourceMs,
				resultPreview = result.segments.joinToString(" ") { it.text }.trim().ifBlank { null },
				status = if (result.segments.isEmpty()) "NO SPEECH" else "PASS",
			)
		}

		fun failed(
			source: String,
			model: String,
			health: AudioHealth?,
			message: String?,
		): AsrDiagnosticsUiState = AsrDiagnosticsUiState(
			source = source,
			model = model,
			modelLoaded = false,
			audioDurationMs = health?.durationMs,
			sampleCount = health?.sampleCount,
			rms = health?.rmsLevel,
			peak = health?.peakAmplitude,
			status = "FAIL",
			errorMessage = message,
		)

		private fun formatMs(value: Double): String = "%.2f ms".format(Locale.US, value)
	}
}

private val WhisperConfiguration.Available.displayName: String
	get() = if (learningLanguage == LearningLanguage.ENGLISH) {
		"English learning · Fast & optimized"
	} else {
		"${learningLanguage.displayName} · ${speechQuality.displayName}"
	}

data class HomeUiState(
	val captureState: CaptureService.CaptureState = CaptureService.CaptureState.Idle,
	val captureDurationMs: Long = 0,
	val isProcessing: Boolean = false,
	val processingStage: String? = null,
	val isModelReady: Boolean = false,
	val modelName: String? = null,
	val modelError: String? = null,
	val segments: List<TranscriptionSegment> = emptyList(),
	val audioHealth: AudioHealth? = null,
	val error: CaptureError? = null,
	val captureErrorDetail: String? = null,
	val debugMessage: String? = null,
	val asrDiagnostics: AsrDiagnosticsUiState? = null,
	val overlayStatus: CaptureService.OverlayStatus = CaptureService.OverlayStatus.Unavailable,
	val activeSession: SessionEntity? = null,
	val selectedWord: WordDetailsUiState? = null,
	val savedWords: Set<String> = emptySet(),
	val captureVideo: Boolean = true,
)

data class WordDetailsUiState(
	val word: String,
	val exampleSentence: String,
	val meaning: String? = null,
	val isLoading: Boolean = false,
	val error: String? = null,
)
