package com.jacksonkasi.cliplex.speech

import com.jacksonkasi.cliplex.data.repository.ModelRepository
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.ModelResolver
import com.jacksonkasi.cliplex.domain.model.SpeechQuality
import com.jacksonkasi.cliplex.domain.model.ModelType
import com.jacksonkasi.cliplex.domain.model.TranscriptionResult
import com.jacksonkasi.cliplex.domain.model.WhisperConfiguration
import com.jacksonkasi.cliplex.whisper.WhisperEngine
import com.jacksonkasi.cliplex.whisper.WhisperSegmentCallback
import com.jacksonkasi.cliplex.whisper.WhisperTranscriptionOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WhisperSpeechRecognizerEngine(
	private val whisper: WhisperEngine,
	private val modelRepository: ModelRepository,
	private val modelResolver: ModelResolver = ModelResolver(),
	private val qualityProvider: () -> SpeechQuality = { selectedQuality },
) : SpeechToTextEngine {
	companion object {
		@Volatile private var selectedQuality: SpeechQuality = SpeechQuality.DEFAULT
		@Volatile private var selectedModel: ModelType? = null
	}
	override val engine = SpeechEngine.WHISPER_FALLBACK
	private val partialResults = MutableSharedFlow<PartialTranscript>(extraBufferCapacity = 16)

	fun setSpeechQuality(quality: SpeechQuality) {
		selectedQuality = quality
	}

	fun setModelSelection(modelType: ModelType?) {
		selectedModel = modelType
	}

	override fun observePartialResults(): Flow<PartialTranscript> = partialResults.asSharedFlow()

	override suspend fun isAvailable(language: LearningLanguage): SpeechEngineAvailability {
		val configuration = modelResolver.resolve(language, qualityProvider(), selectedModel) as? WhisperConfiguration.Available
			?: return SpeechEngineAvailability(
				available = false,
				languageStatus = SpeechLanguageStatus.AndroidUnsupported,
				fallbackReason = SpeechFallbackReason.LANGUAGE_UNAVAILABLE,
			)
		return SpeechEngineAvailability(
			available = modelRepository.isModelAvailable(configuration.modelType),
			languageStatus = SpeechLanguageStatus.AndroidUnsupported,
		)
	}

	override suspend fun transcribe(audio: AudioInput, language: LearningLanguage): TranscriptionResult {
		val configuration = modelResolver.resolve(language, qualityProvider(), selectedModel) as? WhisperConfiguration.Available
			?: throw SpeechEngineException(
				SpeechFallbackReason.LANGUAGE_UNAVAILABLE,
				"No local fallback is configured for ${language.displayName}",
			)
		val modelFile = modelRepository.getModelFile(configuration.modelType)
		if (!modelRepository.isModelAvailable(configuration.modelType)) {
			throw SpeechEngineException(
				SpeechFallbackReason.UNKNOWN,
				"Additional speech support is not installed",
			)
		}

		val started = System.nanoTime()
		val callback = WhisperSegmentCallback { segment ->
			partialResults.tryEmit(PartialTranscript(
				engine = engine,
				language = configuration.transcriptionLanguage,
				text = segment.text,
				isStable = true,
			))
		}
		val result = whisper.ensureModelAndTranscribe(
			modelPath = modelFile.absolutePath,
			samples = audio.samples,
			options = WhisperTranscriptionOptions(
				// Known learning languages are always forced. "auto" is reserved for the explicit Any Language mode.
				language = configuration.transcriptionLanguage,
				nThreads = WhisperEngine.DEFAULT_N_THREADS,
				shortEnglishFastMode = language == LearningLanguage.ENGLISH && audio.durationMs <= 10_000,
			),
			onNewSegment = callback,
		).getOrElse { error ->
			throw SpeechEngineException(
				SpeechFallbackReason.UNKNOWN,
				"Local fallback transcription failed",
				cause = error,
			)
		}
		return TranscriptionResult(
			segments = result.segments,
			detectedLanguage = result.detectedLanguage,
			processingDurationMs = elapsedMs(started),
			engine = engine,
			durationMs = audio.durationMs,
			partialResultAvailable = result.diagnostics.callbackSegmentsEmitted > 0,
		)
	}

	override suspend fun cancel() = Unit

	override fun close() = Unit

	private fun elapsedMs(startedNanos: Long): Long =
		(System.nanoTime() - startedNanos).coerceAtLeast(0L) / 1_000_000L
}
