package com.learnthis.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.common.AppLanguage
import com.learnthis.audio.Pcm16
import com.learnthis.data.local.SessionEntity
import com.learnthis.data.repository.ModelRepository
import com.learnthis.data.repository.PreferencesRepository
import com.learnthis.data.repository.SessionRepository
import com.learnthis.domain.model.AudioHealth
import com.learnthis.domain.model.CaptureError
import com.learnthis.domain.model.ModelType
import com.learnthis.domain.model.TranscriptionSegment
import com.learnthis.service.CaptureService
import com.learnthis.translation.TranslationEngine
import com.learnthis.whisper.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeViewModel(
	private val modelRepository: ModelRepository,
	private val preferencesRepository: PreferencesRepository,
	private val sessionRepository: SessionRepository,
	private val whisperEngine: WhisperEngine,
	private val translationEngine: TranslationEngine,
) : ViewModel() {
	private val _uiState = MutableStateFlow(HomeUiState())
	val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
	private var motherTongue: AppLanguage = AppLanguage.ENGLISH
	private var modelLoadJob: Job = Job().apply { complete() }
	private var processedSessionId = 0L
	private var latestSamples = ShortArray(0)
	private var player: AudioTrack? = null

	init {
		viewModelScope.launch {
			preferencesRepository.activeModel.distinctUntilChanged().collect { type ->
				modelLoadJob.cancelAndJoin()
				if (type == null) {
					_uiState.value = _uiState.value.copy(isModelReady = false, modelName = null, modelError = "Select a speech model in Settings")
				} else {
					modelLoadJob = loadModel(type)
				}
			}
		}
		viewModelScope.launch {
			preferencesRepository.motherTongue.collect { if (it != null) motherTongue = it }
		}
		viewModelScope.launch {
			CaptureService.captureState.collect { state ->
				_uiState.value = _uiState.value.copy(
					captureState = state,
					error = (state as? CaptureService.CaptureState.Error)?.error,
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
					processSession(session)
				}
			}
		}
	}

	private fun loadModel(type: ModelType): Job = viewModelScope.launch(Dispatchers.IO) {
		_uiState.value = _uiState.value.copy(isModelReady = false, modelName = type.displayName, modelError = null)
		if (!modelRepository.isModelAvailable(type)) {
			_uiState.value = _uiState.value.copy(modelError = "Selected model is missing. Choose another model in Settings.")
			return@launch
		}
		val result = whisperEngine.loadModel(modelRepository.getModelFile(type).absolutePath)
		_uiState.value = _uiState.value.copy(
			isModelReady = result.isSuccess,
			modelName = type.displayName,
			modelError = result.exceptionOrNull()?.message,
		)
	}

	private suspend fun processSession(session: CaptureService.CapturedAudioSession) {
		latestSamples = session.samples
		_uiState.value = _uiState.value.copy(
			isProcessing = true, audioHealth = session.health, segments = emptyList(),
			error = session.health.error,
		)
		if (!session.health.isValid) {
			_uiState.value = _uiState.value.copy(isProcessing = false)
			return
		}
		modelLoadJob.join()
		if (!_uiState.value.isModelReady) {
			_uiState.value = _uiState.value.copy(isProcessing = false, error = CaptureError.MODEL_NOT_LOADED)
			return
		}
		val transcription = whisperEngine.transcribe(session.samples, language = "auto")
		val originalSegments = transcription.getOrElse {
			_uiState.value = _uiState.value.copy(isProcessing = false, error = CaptureError.ASR_EMPTY_RESULT)
			return
		}
		if (originalSegments.isEmpty()) {
			_uiState.value = _uiState.value.copy(isProcessing = false, error = CaptureError.NO_SPEECH_DETECTED)
			return
		}

		val translated = mutableListOf<TranscriptionSegment>()
		for (segment in originalSegments) {
			val translatedText = if (segment.language == motherTongue.tag) {
				segment.text
			} else {
				translationEngine.initialize(segment.language.ifBlank { "en" }, motherTongue.tag)
					.fold(
						onSuccess = { translationEngine.translate(segment.text).getOrNull() },
						onFailure = { null },
					)
			}
			translated += segment.copy(translatedText = translatedText)
			_uiState.value = _uiState.value.copy(segments = translated.toList())
		}
		withContext(Dispatchers.IO) {
			sessionRepository.insertSession(SessionEntity(
				sourceLanguage = originalSegments.first().language,
				targetLanguage = motherTongue.tag,
				durationMs = session.health.durationMs,
				segmentCount = originalSegments.size,
			))
		}
		_uiState.value = _uiState.value.copy(
			isProcessing = false,
			error = if (translated.any { it.translatedText == null }) CaptureError.TRANSLATION_FAILED else null,
		)
	}

	fun finishCapture(context: Context) {
		context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_FINISH))
	}

	fun stopLearningMode(context: Context) {
		context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
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
		if (latestSamples.isEmpty()) return
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
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isProcessing = true, debugMessage = "Running known-good JFK sample…")
			val result = withContext(Dispatchers.IO) {
				runCatching {
					val wav = Pcm16.readWav(context.assets.open("jfk.wav").use { it.readBytes() })
					val mono = if (wav.channels == 2) Pcm16.stereoToMono(wav.samples) else wav.samples
					val samples = Pcm16.resampleLinear(mono, wav.sampleRate, CaptureService.SAMPLE_RATE_HZ)
					whisperEngine.transcribe(samples, "en").getOrThrow().joinToString(" ") { it.text }
				}
			}
			_uiState.value = _uiState.value.copy(
				isProcessing = false,
				debugMessage = result.fold(
					onSuccess = { if (it.isBlank()) "Known-good ASR returned no text" else "Known-good ASR: $it" },
					onFailure = { "Known-good ASR failed: ${it.message}" },
				),
			)
		}
	}

	override fun onCleared() {
		player?.release()
		translationEngine.close()
		super.onCleared()
	}
}

data class HomeUiState(
	val captureState: CaptureService.CaptureState = CaptureService.CaptureState.Idle,
	val captureDurationMs: Long = 0,
	val isProcessing: Boolean = false,
	val isModelReady: Boolean = false,
	val modelName: String? = null,
	val modelError: String? = null,
	val segments: List<TranscriptionSegment> = emptyList(),
	val audioHealth: AudioHealth? = null,
	val error: CaptureError? = null,
	val debugMessage: String? = null,
	val overlayStatus: CaptureService.OverlayStatus = CaptureService.OverlayStatus.Unavailable,
)
