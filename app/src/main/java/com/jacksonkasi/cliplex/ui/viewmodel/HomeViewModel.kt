package com.jacksonkasi.cliplex.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.common.languageForWord
import com.jacksonkasi.cliplex.common.validWordTranslation
import com.jacksonkasi.cliplex.audio.Pcm16
import com.jacksonkasi.cliplex.audio.AudioDiagnostics
import com.jacksonkasi.cliplex.ai.LocalGemmaTutor
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.data.local.SessionSegmentsCodec
import com.jacksonkasi.cliplex.data.repository.ModelRepository
import com.jacksonkasi.cliplex.data.repository.PreferencesRepository
import com.jacksonkasi.cliplex.data.repository.SessionRepository
import com.jacksonkasi.cliplex.domain.model.AudioHealth
import com.jacksonkasi.cliplex.domain.model.CaptureError
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.ModelResolver
import com.jacksonkasi.cliplex.domain.model.ModelType
import com.jacksonkasi.cliplex.domain.model.RecognitionMode
import com.jacksonkasi.cliplex.domain.model.SavedWord
import com.jacksonkasi.cliplex.domain.model.SpeechQuality
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import com.jacksonkasi.cliplex.domain.model.TranscriptionResult
import com.jacksonkasi.cliplex.domain.model.WhisperConfiguration
import com.jacksonkasi.cliplex.service.CaptureService
import com.jacksonkasi.cliplex.speech.AudioInput
import com.jacksonkasi.cliplex.speech.SpeechEngine
import com.jacksonkasi.cliplex.speech.SpeechLanguageStatus
import com.jacksonkasi.cliplex.speech.SpeechRecognitionCoordinator
import com.jacksonkasi.cliplex.speech.WhisperSpeechRecognizerEngine
import com.jacksonkasi.cliplex.translation.TranslationEngine
import com.jacksonkasi.cliplex.whisper.WhisperEngine
import com.jacksonkasi.cliplex.whisper.WhisperModelLoadResult
import com.jacksonkasi.cliplex.whisper.WhisperTranscriptionOptions
import com.jacksonkasi.cliplex.whisper.WhisperTranscriptionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
	private val speechRecognitionCoordinator: SpeechRecognitionCoordinator,
	private val whisperSpeechRecognizerEngine: WhisperSpeechRecognizerEngine,
	private val localGemmaTutor: LocalGemmaTutor,
) : ViewModel() {
	companion object {
		private const val TAG = "HomeViewModel"
	}

	private val _uiState = MutableStateFlow(HomeUiState())
	val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
	private var motherTongue: AppLanguage = AppLanguage.ENGLISH
	private var autoTranslate = true
	private var whisperConfiguration: WhisperConfiguration.Available? = null
	private var learningLanguage: LearningLanguage? = null
	private var recognitionMode: RecognitionMode = RecognitionMode.AUTOMATIC
	private var selectedWhisperModel: ModelType? = null
	private val initialModelSelection = CompletableDeferred<Unit>()
	private var processedSessionId = 0L
	private var latestSamples = ShortArray(0)
	private var player: AudioTrack? = null
	private var sessionObserverJob: Job? = null
	private var retranslationJob: Job? = null

	fun askTutor(
		session: SessionEntity,
		question: String,
		motherTongue: String,
		onResult: (String?) -> Unit,
	) {
		viewModelScope.launch {
			onResult(localGemmaTutor.answer(session, question, motherTongue))
		}
	}

	fun isSmartTutorInstalled(): Boolean = localGemmaTutor.isInstalled()
	private var analysisJob: Job? = null
	private var savedWordMeaningJob: Job? = null
	private val liveStableSegments = mutableListOf<TranscriptionSegment>()
	private val firstVisibleAtNanos = AtomicLong(0L)
	private val translationMutex = Mutex()
	private var activePartialTranslationTarget: AppLanguage? = null

	init {
		viewModelScope.launch {
			combine(
				preferencesRepository.learningLanguage,
				preferencesRepository.speechQuality,
				preferencesRepository.whisperModel,
			) { language, quality, model -> Triple(language, quality, model) }
				.distinctUntilChanged()
				.collect { (language, quality, model) ->
				learningLanguage = language
				selectedWhisperModel = model
				val resolved = language?.let { modelResolver.resolve(it, quality, model) }
				whisperConfiguration = resolved as? WhisperConfiguration.Available
				whisperSpeechRecognizerEngine.setSpeechQuality(quality)
				whisperSpeechRecognizerEngine.setModelSelection(model)
				_uiState.update {
					it.copy(selectedWhisperModel = whisperConfiguration?.modelType)
				}
				if (language == null) {
					_uiState.update { it.copy(isModelReady = false, modelName = null, modelError = "Choose a learning language") }
				} else {
					refreshSpeechAvailability(language)
				}
				if (!initialModelSelection.isCompleted) initialModelSelection.complete(Unit)
			}
		}
		viewModelScope.launch {
			preferencesRepository.recognitionMode.collect { mode ->
				recognitionMode = mode
				_uiState.update { it.copy(recognitionMode = mode) }
				learningLanguage?.let { refreshSpeechAvailability(it) }
			}
		}
		viewModelScope.launch {
			speechRecognitionCoordinator.observePartialResults().collect { partial ->
				if (!_uiState.value.isProcessing) return@collect
				firstVisibleAtNanos.compareAndSet(0L, SystemClock.elapsedRealtimeNanos())
				val duration = _uiState.value.captureDurationMs.coerceAtLeast(1L)
				val segment = TranscriptionSegment(
					text = partial.text,
					startTimeMs = duration * liveStableSegments.size / (liveStableSegments.size + 1),
					endTimeMs = duration,
					language = partial.language,
				)
				if (partial.isStable) {
					liveStableSegments.removeAll { it.text == segment.text }
					liveStableSegments += segment
				}
				_uiState.update { current ->
					current.copy(
						segments = if (partial.isStable) liveStableSegments.toList() else liveStableSegments + segment,
						processingStage = "Source text is appearing…",
					)
				}
				if (partial.isStable) translateStablePartial(segment)
			}
		}
		viewModelScope.launch {
			preferencesRepository.motherTongue.filterNotNull().distinctUntilChanged().collect { language ->
				val changed = language != motherTongue
				motherTongue = language
				if (changed) {
					val current = _uiState.value
					current.activeSession?.let { session ->
						if (current.segments.isNotEmpty() && session.targetLanguage != language.tag) {
							scheduleRetranslation(session, current.segments, language)
						}
					}
				}
			}
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
			preferencesRepository.savedWordDetails.collect { details ->
				_uiState.update { it.copy(savedWordDetails = details.sortedByDescending(SavedWord::savedAt)) }
			}
		}
		viewModelScope.launch {
			sessionRepository.getAllSessions().collect { sessions ->
				_uiState.update { it.copy(practiceSessions = sessions.filter { session -> session.processingState in setOf("READY", "COMPLETE") }) }
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
			CaptureService.latestSession.collectLatest { session ->
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
				if (persistedSegments.isNotEmpty() && session.targetLanguage != motherTongue.tag) {
					scheduleRetranslation(session, persistedSegments, motherTongue)
				}
			}
		}
	}

	fun closeSession() {
		if (_uiState.value.isProcessing) viewModelScope.launch { speechRecognitionCoordinator.cancel() }
		analysisJob?.cancel()
		analysisJob = null
		retranslationJob?.cancel()
		retranslationJob = null
		sessionObserverJob?.cancel()
		sessionObserverJob = null
		_uiState.update {
			it.copy(
				activeSession = null,
				selectedWord = null,
				isProcessing = false,
				processingStage = null,
			)
		}
	}

	private fun scheduleRetranslation(
		session: SessionEntity,
		segments: List<TranscriptionSegment>,
		target: AppLanguage,
	) {
		retranslationJob?.cancel()
		retranslationJob = viewModelScope.launch {
			retranslateExistingSession(session, segments, target)
		}
	}

	private suspend fun retranslateExistingSession(
		session: SessionEntity,
		segments: List<TranscriptionSegment>,
		target: AppLanguage,
	) {
		if (segments.isEmpty()) return
		_uiState.update {
			it.copy(isProcessing = true, processingStage = "Translating to ${target.displayName}…", error = null)
		}
		val translationSupported = target.tag in TranslationEngine.supportedLanguages()
		var failed = !translationSupported
		val translated = mutableListOf<TranscriptionSegment>()
		for (segment in segments) {
			val sourceLanguage = segment.language.ifBlank { session.sourceLanguage }
			val translatedText = when {
				!translationSupported -> null
				sourceLanguage == target.tag -> null
				sourceLanguage.isBlank() -> null
				else -> translationMutex.withLock {
					translationEngine.initialize(sourceLanguage, target.tag).fold(
						onSuccess = { translationEngine.translate(segment.text).getOrNull() },
						onFailure = { null },
					)
				}
			}
			if (translationSupported && sourceLanguage.isNotBlank() && sourceLanguage != target.tag && translatedText == null) {
				failed = true
			}
			translated += segment.copy(translatedText = translatedText)
			_uiState.update { it.copy(segments = translated + segments.drop(translated.size)) }
		}
		withContext(Dispatchers.IO) {
			val current = sessionRepository.getSessionById(session.id) ?: return@withContext
			sessionRepository.updateSession(current.copy(
				targetLanguage = target.tag,
				segmentsJson = SessionSegmentsCodec.encode(translated),
				processingState = "READY",
				captureError = if (failed) "Some sentences could not be translated to ${target.displayName}" else null,
			))
		}
		_uiState.update {
			it.copy(
				segments = translated,
				isProcessing = false,
				processingStage = null,
				error = if (failed) CaptureError.TRANSLATION_FAILED else null,
				captureErrorDetail = if (failed) "Some sentences could not be translated to ${target.displayName}" else null,
			)
		}
	}

	/** Retry after Model Management finishes downloading the currently resolved model. */
	fun refreshResolvedModel() {
		val language = learningLanguage ?: return
		viewModelScope.launch { refreshSpeechAvailability(language) }
	}

	private suspend fun refreshSpeechAvailability(language: LearningLanguage) {
		val primary = speechRecognitionCoordinator.primaryAvailability(language)
		val fallback = speechRecognitionCoordinator.fallbackAvailability(language)
		val ready = when (recognitionMode) {
			RecognitionMode.AUTOMATIC -> primary.available || fallback.available
			RecognitionMode.ANDROID_ONLY -> primary.available
			RecognitionMode.WHISPER_ONLY -> fallback.available
		}
		val friendlyStatus = if (recognitionMode == RecognitionMode.WHISPER_ONLY && !fallback.available) {
			"Download the selected Whisper model in Advanced Settings"
		} else when (primary.languageStatus) {
			SpeechLanguageStatus.Ready -> null
			SpeechLanguageStatus.DownloadRequired -> "Download ${language.displayName} speech support in setup"
			is SpeechLanguageStatus.Downloading -> "${language.displayName} speech support is downloading"
			SpeechLanguageStatus.AndroidUnsupported -> if (fallback.available) null else "Additional speech support required"
			is SpeechLanguageStatus.Error -> if (fallback.available) null else "Additional speech support required"
		}
		_uiState.update {
			it.copy(
				isModelReady = ready,
				modelName = language.displayName,
				modelError = friendlyStatus,
				speechLanguageStatus = primary.languageStatus,
				fallbackReady = fallback.available,
			)
		}
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

	private suspend fun processSession(
		session: CaptureService.CapturedAudioSession,
		languageOverride: LearningLanguage? = null,
		preserveExistingOnFailure: Boolean = false,
	) {
		latestSamples = session.samples
		val persistedSession = sessionRepository.getSessionById(session.id)
		val existingSegments = _uiState.value.segments
		_uiState.value = _uiState.value.copy(
			activeSession = persistedSession,
			isProcessing = true,
			processingStage = "Checking captured audio…",
			audioHealth = session.health,
			segments = if (preserveExistingOnFailure) existingSegments else emptyList(),
			error = session.health.error,
			captureErrorDetail = null,
		)
		if (!session.health.isValid) {
			val detail = reanalysisAudioError(session.health.error)
			recordProcessingFailure(session.id, detail, preserveExistingOnFailure)
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				processingStage = null,
				captureErrorDetail = detail,
				debugMessage = detail,
			)
			return
		}
		initialModelSelection.await()
		val selectedLearningLanguage = languageOverride ?: learningLanguage ?: run {
			val detail = "Choose the lesson language before re-analyzing"
			recordProcessingFailure(session.id, detail, preserveExistingOnFailure)
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				processingStage = null,
				error = CaptureError.MODEL_NOT_LOADED,
				captureErrorDetail = detail,
			)
			return
		}
		val targetLanguage = motherTongue
		val shouldTranslate = autoTranslate
		val translationSupported = targetLanguage.tag in TranslationEngine.supportedLanguages()
		activePartialTranslationTarget = targetLanguage.takeIf {
			shouldTranslate && translationSupported && it.tag != selectedLearningLanguage.code
		}
		liveStableSegments.clear()
		firstVisibleAtNanos.set(0L)
		_uiState.value = _uiState.value.copy(processingStage = "Transcribing captured audio…", error = null)
		Log.i(
			TAG,
			"Transcribing capture id=${session.id} durationMs=${session.health.durationMs} " +
				"learningLanguage=${selectedLearningLanguage.code}",
		)
		val coordinated = runCatching {
				speechRecognitionCoordinator.transcribe(
					audio = AudioInput(session.samples, CaptureService.SAMPLE_RATE_HZ),
					language = selectedLearningLanguage,
					mode = recognitionMode,
				)
		}.getOrElse {
			activePartialTranslationTarget = null
			Log.e(TAG, "Speech transcription failed for capture id=${session.id}", it)
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				processingStage = null,
				error = CaptureError.ASR_EMPTY_RESULT,
				asrDiagnostics = AsrDiagnosticsUiState.failed(
					source = "Captured playback",
					model = "On-device speech",
					health = session.health,
					message = it.message,
				),
			)
			recordProcessingFailure(
				session.id,
				it.message ?: "Speech transcription failed",
				preserveExistingOnFailure,
			)
			return
		}
		val transcriptionResult = coordinated.result
		activePartialTranslationTarget = null
		val originalSegments = transcriptionResult.segments
		val finishToSourceMs = firstVisibleAtNanos.get().takeIf { it > 0L }?.let { firstVisible ->
			(firstVisible - session.finishedAtElapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000.0
		} ?: elapsedMs(session.finishedAtElapsedRealtimeNanos)
		val diagnostics = AsrDiagnosticsUiState.fromCoordinatedTranscription(
			source = "Captured playback",
			health = session.health,
			result = transcriptionResult,
			finishToSourceMs = finishToSourceMs,
			fallbackReason = coordinated.fallbackReason?.name,
			selectedLanguage = selectedLearningLanguage.recognitionTag.ifBlank { selectedLearningLanguage.code },
			androidSpeechModel = _uiState.value.speechLanguageStatus.diagnosticName(),
			audioInjection = when {
				transcriptionResult.engine == SpeechEngine.ANDROID_ON_DEVICE -> "Supported"
				coordinated.fallbackReason == com.jacksonkasi.cliplex.speech.SpeechFallbackReason.AUDIO_INJECTION_UNSUPPORTED -> "Failed"
				else -> "Unverified"
			},
		)
		Log.i(TAG, "Speech transcription completed for capture id=${session.id}; segments=${originalSegments.size}")
		if (originalSegments.isEmpty()) {
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				processingStage = null,
				error = CaptureError.NO_SPEECH_DETECTED,
				asrDiagnostics = diagnostics.copy(status = "NO SPEECH"),
			)
			recordProcessingFailure(session.id, "Spoken dialogue was not detected", preserveExistingOnFailure)
			return
		}

		// Source text is visible before any ML Kit model download or translation.
		updatePersistedSession(
			sessionId = session.id,
			segments = originalSegments,
			targetLanguage = targetLanguage,
			processingState = if (shouldTranslate && translationSupported) "TRANSLATING" else "READY",
			clearCaptureError = preserveExistingOnFailure,
		)
		_uiState.value = _uiState.value.copy(
			segments = originalSegments,
			processingStage = if (shouldTranslate && translationSupported) {
				"Transcript ready • translating…"
			} else null,
			asrDiagnostics = diagnostics,
		)
		if (!shouldTranslate || originalSegments.all { it.language == targetLanguage.tag }) {
			updatePersistedSession(session.id, originalSegments, targetLanguage, "READY")
			_uiState.value = _uiState.value.copy(isProcessing = false, processingStage = null, error = null)
			return
		}
		if (!translationSupported) {
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
				selectedLearningLanguage != LearningLanguage.ANY_LANGUAGE -> selectedLearningLanguage.code
				else -> null
			}
			val translatedText = if (sourceLanguage == null) {
				translationFailed = true
				null
			} else if (sourceLanguage == targetLanguage.tag) {
				null
			} else {
				translationMutex.withLock {
					translationEngine.initialize(sourceLanguage, targetLanguage.tag)
						.fold(
							onSuccess = { translationEngine.translate(segment.text).getOrNull() },
							onFailure = { null },
						)
				}
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

	private fun translateStablePartial(segment: TranscriptionSegment) {
		val target = activePartialTranslationTarget ?: return
		if (segment.language == target.tag || segment.text.isBlank()) return
		viewModelScope.launch {
			val translated = translationMutex.withLock {
				translationEngine.initialize(segment.language, target.tag).fold(
					onSuccess = { translationEngine.translate(segment.text).getOrNull() },
					onFailure = { null },
				)
			} ?: return@launch
			_uiState.update { current ->
				current.copy(segments = current.segments.map {
					if (it.text == segment.text) it.copy(translatedText = translated) else it
				})
			}
		}
	}

	private suspend fun updatePersistedSession(
		sessionId: Long,
		segments: List<TranscriptionSegment>,
		targetLanguage: AppLanguage,
		processingState: String,
		captureError: String? = null,
		clearCaptureError: Boolean = false,
	) = withContext(Dispatchers.IO) {
		val current = sessionRepository.getSessionById(sessionId) ?: return@withContext
		sessionRepository.updateSession(current.copy(
			title = segments.firstOrNull()?.text?.trim()?.take(56)?.takeIf { it.isNotBlank() } ?: current.title,
			sourceLanguage = segments.firstOrNull()?.language.orEmpty(),
			targetLanguage = targetLanguage.tag,
			segmentCount = segments.size,
			segmentsJson = SessionSegmentsCodec.encode(segments),
			processingState = processingState,
			captureError = when {
				captureError != null -> captureError
				clearCaptureError -> null
				else -> current.captureError
			},
		))
	}

	private suspend fun recordProcessingFailure(sessionId: Long, detail: String, preserveExisting: Boolean) {
		if (!preserveExisting) {
			markSessionFailed(sessionId, detail)
			return
		}
		withContext(Dispatchers.IO) {
			val current = sessionRepository.getSessionById(sessionId) ?: return@withContext
			sessionRepository.updateSession(current.copy(processingState = "READY", captureError = detail))
		}
	}

	private fun reanalysisAudioError(error: CaptureError?): String = when (error) {
		CaptureError.CAPTURED_SILENCE, CaptureError.SOURCE_CAPTURE_BLOCKED ->
			"The saved audio is silent. Re-analysis cannot recover audio that was not captured."
		CaptureError.AUDIO_TOO_SHORT -> "The saved audio is too short to re-analyze."
		CaptureError.AUDIO_FORMAT_INVALID -> "The saved audio format is invalid."
		else -> "The saved audio could not be re-analyzed."
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
		val segmentLanguage = _uiState.value.segments.firstOrNull { it.text == exampleSentence }?.language
		val sourceLanguage = languageForWord(
			normalized,
			segmentLanguage ?: _uiState.value.activeSession?.sourceLanguage ?: learningLanguage?.code,
		)
		_uiState.update {
			it.copy(selectedWord = WordDetailsUiState(
				word = normalized,
				exampleSentence = exampleSentence,
				sourceLanguage = sourceLanguage,
				isLoading = true,
			))
		}
		viewModelScope.launch {
			val target = motherTongue
			val rawMeaning = when {
				sourceLanguage == target.tag -> Result.success(normalized)
				target.tag !in TranslationEngine.supportedLanguages() -> Result.failure(
					IllegalStateException("${target.displayName} translation is not available on this device"),
				)
				else -> translationMutex.withLock {
					translationEngine.initialize(sourceLanguage, target.tag).fold(
						onSuccess = { translationEngine.translate(normalized) },
						onFailure = { Result.failure(it) },
					)
				}
			}
			val meaning = rawMeaning.mapCatching { translated ->
				validWordTranslation(normalized, translated, sourceLanguage, target.tag)
					?: error("No ${target.displayName} meaning was found for this word")
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
		val selected = _uiState.value.selectedWord ?: return
		viewModelScope.launch {
			if (saved) {
				preferencesRepository.saveWord(SavedWord(
					word = selected.word,
					meaning = selected.meaning,
					example = selected.exampleSentence,
					sourceLanguage = selected.sourceLanguage,
					targetLanguage = motherTongue.tag,
				))
			} else {
				preferencesRepository.setWordSaved(selected.word, false)
			}
		}
	}

	fun refreshSavedWordMeanings() {
		if (savedWordMeaningJob?.isActive == true) return
		savedWordMeaningJob = viewModelScope.launch {
			val current = _uiState.value
			val existing = current.savedWordDetails.associateBy(SavedWord::word)
			val target = motherTongue.tag
			for (word in current.savedWords.sorted()) {
				val saved = existing[word]
				val source = languageForWord(
					word,
					saved?.sourceLanguage ?: learningLanguage?.code,
				)
				val reusableMeaning = saved?.meaning
					?.takeIf { saved.targetLanguage == target }
					?.let { validWordTranslation(word, it, source, target) }
				if (reusableMeaning != null && saved.sourceLanguage == source) continue
				val translated = reusableMeaning ?: if (source == target) word else translationMutex.withLock {
					translationEngine.initialize(source, target).fold(
						onSuccess = { translationEngine.translate(word).getOrNull() },
						onFailure = { null },
					)
				}
				val meaning = validWordTranslation(word, translated, source, target)
				preferencesRepository.saveWord(SavedWord(
					word = word,
					meaning = meaning,
					example = saved?.example,
					sourceLanguage = source,
					targetLanguage = target,
					savedAt = saved?.savedAt?.takeIf { it > 0L } ?: System.currentTimeMillis(),
				))
			}
		}
	}

	fun removeSavedWord(word: String) {
		viewModelScope.launch { preferencesRepository.setWordSaved(word, false) }
	}

	fun deleteVideo(sessionId: Long) {
		viewModelScope.launch(Dispatchers.IO) { sessionRepository.deleteVideo(sessionId) }
	}

	/** Re-run recognition and translation from a lesson's persisted WAV without recapturing media. */
	fun reanalyzeSession(sessionId: Long) {
		if (_uiState.value.isProcessing) return
		analysisJob?.cancel()
		analysisJob = viewModelScope.launch {
			val persisted = withContext(Dispatchers.IO) { sessionRepository.getSessionById(sessionId) }
			if (persisted == null) {
				_uiState.update { it.copy(debugMessage = "This lesson is no longer available") }
				return@launch
			}
			_uiState.update {
				it.copy(isProcessing = true, processingStage = "Loading saved lesson audio…", error = null, captureErrorDetail = null)
			}
			val prepared = runCatching {
				withContext(Dispatchers.IO) {
					val path = persisted.audioPath?.takeIf(String::isNotBlank)
						requireNotNull(path) { "No saved audio is available for this lesson" }
					val file = File(path)
					require(file.isFile && file.length() > 44L) { "The saved lesson audio is missing or empty" }
					val wav = Pcm16.readWav(file.readBytes())
					val mono = when (wav.channels) {
						1 -> wav.samples
						2 -> Pcm16.stereoToMono(wav.samples)
						else -> error("Unsupported saved audio channel count: ${wav.channels}")
					}
					Pcm16.resampleLinear(mono, wav.sampleRate, CaptureService.SAMPLE_RATE_HZ)
				}
			}
			val samples = prepared.getOrElse { failure ->
				val detail = failure.message ?: "The saved lesson audio could not be read"
				recordProcessingFailure(sessionId, detail, preserveExisting = true)
				_uiState.update {
					it.copy(
						isProcessing = false,
						processingStage = null,
						error = CaptureError.AUDIO_FORMAT_INVALID,
						captureErrorDetail = detail,
						debugMessage = detail,
					)
				}
				return@launch
			}
			val sourceTag = persisted.sourceLanguage
			val sourceLanguage = LearningLanguage.entries.firstOrNull { candidate ->
				candidate != LearningLanguage.ANY_LANGUAGE && (
					candidate.code.equals(sourceTag.substringBefore('-'), ignoreCase = true) ||
					candidate.recognitionTag.equals(sourceTag, ignoreCase = true)
				)
			} ?: learningLanguage
			val health = AudioDiagnostics.analyze(samples, CaptureService.SAMPLE_RATE_HZ)
			processSession(
				session = CaptureService.CapturedAudioSession(
					id = persisted.id,
					samples = samples,
					health = health,
					videoPath = persisted.videoPath,
					audioPath = persisted.audioPath,
					finishedAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
				),
				languageOverride = sourceLanguage,
				preserveExistingOnFailure = true,
			)
		}
	}

	fun deleteLesson(sessionId: Long, onDeleted: () -> Unit = {}) {
		viewModelScope.launch {
			speechRecognitionCoordinator.cancel()
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
		speechRecognitionCoordinator.close()
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
	val engineUsed: String? = null,
	val fallbackReason: String? = null,
	val partialResultAvailable: Boolean? = null,
	val wordTimingAvailable: Boolean? = null,
	val selectedLanguage: String? = null,
	val androidSpeechModel: String? = null,
	val audioInjection: String? = null,
	val segmentResultsAvailable: Boolean? = null,
) {
	fun asText(): String = buildString {
		appendLine("ASR_DIAGNOSTICS")
		appendLine("status = $status")
		appendLine("androidApi = ${android.os.Build.VERSION.SDK_INT}")
		engineUsed?.let { appendLine("engineUsed = $it") }
		fallbackReason?.let { appendLine("fallbackReason = $it") }
		partialResultAvailable?.let { appendLine("partialResultAvailable = $it") }
		wordTimingAvailable?.let { appendLine("wordTimingAvailable = $it") }
		selectedLanguage?.let { appendLine("selectedLanguage = $it") }
		androidSpeechModel?.let { appendLine("androidSpeechModel = $it") }
		audioInjection?.let { appendLine("audioInjection = $it") }
		segmentResultsAvailable?.let { appendLine("segmentResultsAvailable = $it") }
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
		fun fromCoordinatedTranscription(
			source: String,
			health: AudioHealth,
			result: TranscriptionResult,
			finishToSourceMs: Double,
			fallbackReason: String?,
			selectedLanguage: String,
			androidSpeechModel: String,
			audioInjection: String,
		): AsrDiagnosticsUiState = AsrDiagnosticsUiState(
			source = source,
			model = if (result.engine == SpeechEngine.ANDROID_ON_DEVICE) "Android on-device" else "Local fallback",
			modelLoaded = true,
			audioDurationMs = health.durationMs,
			sampleCount = health.sampleCount,
			rms = health.rmsLevel,
			peak = health.peakAmplitude,
			inferenceMs = result.processingDurationMs.toDouble(),
			finishToSourceMs = finishToSourceMs,
			resultPreview = result.text.ifBlank { null },
			status = if (result.text.isBlank()) "NO SPEECH" else "PASS",
			engineUsed = result.engine.name,
			fallbackReason = fallbackReason,
			partialResultAvailable = result.partialResultAvailable,
			wordTimingAvailable = result.words.any { it.startTimeMs != null },
			selectedLanguage = selectedLanguage,
			androidSpeechModel = androidSpeechModel,
			audioInjection = audioInjection,
			segmentResultsAvailable = result.segments.size > 1,
		)

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

private fun SpeechLanguageStatus.diagnosticName(): String = when (this) {
	SpeechLanguageStatus.Ready -> "Installed"
	SpeechLanguageStatus.DownloadRequired -> "Downloadable"
	is SpeechLanguageStatus.Downloading -> "Pending${progress?.let { " ($it%)" }.orEmpty()}"
	SpeechLanguageStatus.AndroidUnsupported -> "Unsupported"
	is SpeechLanguageStatus.Error -> "Error: $reason"
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
	val savedWordDetails: List<SavedWord> = emptyList(),
	val practiceSessions: List<SessionEntity> = emptyList(),
	val captureVideo: Boolean = true,
	val speechLanguageStatus: SpeechLanguageStatus = SpeechLanguageStatus.AndroidUnsupported,
	val fallbackReady: Boolean = false,
	val recognitionMode: RecognitionMode = RecognitionMode.AUTOMATIC,
	val selectedWhisperModel: ModelType? = null,
)

data class WordDetailsUiState(
	val word: String,
	val exampleSentence: String,
	val sourceLanguage: String = "en",
	val meaning: String? = null,
	val isLoading: Boolean = false,
	val error: String? = null,
)
