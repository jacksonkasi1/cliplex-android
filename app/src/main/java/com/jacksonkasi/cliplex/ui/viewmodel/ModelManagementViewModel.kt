package com.jacksonkasi.cliplex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacksonkasi.cliplex.data.repository.ModelRepository
import com.jacksonkasi.cliplex.data.repository.PreferencesRepository
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.LearningMode
import com.jacksonkasi.cliplex.domain.model.ModelDownloadProgress
import com.jacksonkasi.cliplex.domain.model.ModelResolver
import com.jacksonkasi.cliplex.domain.model.ModelType
import com.jacksonkasi.cliplex.domain.model.RecognitionMode
import com.jacksonkasi.cliplex.domain.model.SpeechQuality
import com.jacksonkasi.cliplex.domain.model.WhisperConfiguration
import com.jacksonkasi.cliplex.domain.model.toLegacyLearningMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LearningModeItemUiState(
	val learningMode: LearningMode,
	val modelType: ModelType = learningMode.requiredWhisperModel,
	val isDownloaded: Boolean = false,
	val progress: ModelDownloadProgress = ModelDownloadProgress.Idle,
)

data class WhisperModelItemUiState(
	val modelType: ModelType,
	val isDownloaded: Boolean = false,
	val progress: ModelDownloadProgress = ModelDownloadProgress.Idle,
)

data class ModelManagementUiState(
	val modes: List<LearningModeItemUiState> = emptyList(),
	val selectedLearningMode: LearningMode? = null,
	val selectedLearningLanguage: LearningLanguage? = null,
	val selectedSpeechQuality: SpeechQuality = SpeechQuality.DEFAULT,
	val configuration: WhisperConfiguration.Available? = null,
	val requiredModelDownloaded: Boolean = false,
	val requiredModelProgress: ModelDownloadProgress = ModelDownloadProgress.Idle,
	val isChecking: Boolean = true,
	val error: String? = null,
	val captureVideo: Boolean = true,
	val recognitionMode: RecognitionMode = RecognitionMode.AUTOMATIC,
	val whisperModels: List<WhisperModelItemUiState> = emptyList(),
	val downloadedModels: Set<ModelType> = emptySet(),
) {
	val requiredModelReady: Boolean
		get() = configuration != null && requiredModelDownloaded
}

class ModelManagementViewModel(
	private val modelRepository: ModelRepository,
	private val preferencesRepository: PreferencesRepository,
	private val modelResolver: ModelResolver = ModelResolver(),
) : ViewModel() {
	private val downloadJobs = mutableMapOf<ModelType, Job>()
	private val _uiState = MutableStateFlow(ModelManagementUiState())
	val uiState: StateFlow<ModelManagementUiState> = _uiState.asStateFlow()

	init {
		viewModelScope.launch {
			combine(
				preferencesRepository.learningLanguage,
				preferencesRepository.speechQuality,
				preferencesRepository.whisperModel,
				preferencesRepository.recognitionMode,
			) { language, quality, model, mode -> ModelSelection(language, quality, model, mode) }
				.distinctUntilChanged()
				.collect { selection ->
					refreshResolvedModel(selection.language, selection.quality, selection.model, selection.mode)
			}
		}
		viewModelScope.launch {
			preferencesRepository.captureVideo.collect { enabled ->
				_uiState.value = _uiState.value.copy(captureVideo = enabled)
			}
		}
	}

	private suspend fun refreshResolvedModel(
		language: LearningLanguage?,
		quality: SpeechQuality,
		preferredModel: ModelType?,
		recognitionMode: RecognitionMode,
	) {
		val resolved = language?.let { modelResolver.resolve(it, quality, preferredModel) }
		val available = resolved as? WhisperConfiguration.Available
		val requiredModel = available?.modelType
		val cancelledModels = downloadJobs
			.filter { (modelType, job) -> modelType != requiredModel && job.isActive }
			.keys
			.toSet()
		cancelledModels.forEach { modelType -> downloadJobs.remove(modelType)?.cancel() }
		val modes = withContext(Dispatchers.IO) {
			LearningMode.entries.map { mode ->
				val downloaded = modelRepository.isModelAvailable(mode.requiredWhisperModel)
				LearningModeItemUiState(
					learningMode = mode,
					isDownloaded = downloaded,
					progress = if (downloaded) ModelDownloadProgress.Ready else ModelDownloadProgress.Idle,
				)
			}
		}
		val downloaded = requiredModel?.let {
			withContext(Dispatchers.IO) { modelRepository.isModelAvailable(it) }
		} == true
		val whisperModels = withContext(Dispatchers.IO) {
			language?.let { selectedLanguage ->
				ModelType.entries.filter { it.userSelectable && it.supports(selectedLanguage) }.map { model ->
					val ready = modelRepository.isModelAvailable(model)
					WhisperModelItemUiState(
						modelType = model,
						isDownloaded = ready,
						progress = if (ready) ModelDownloadProgress.Ready else ModelDownloadProgress.Idle,
					)
				}
			}.orEmpty()
		}
		val allDownloadedModels = withContext(Dispatchers.IO) {
			modelRepository.listDownloadedModels().toSet()
		}
		_uiState.value = _uiState.value.copy(
			modes = modes,
			selectedLearningMode = language?.toLegacyLearningMode(),
			selectedLearningLanguage = language,
			selectedSpeechQuality = quality,
			configuration = available,
			requiredModelDownloaded = downloaded,
			requiredModelProgress = if (downloaded) ModelDownloadProgress.Ready else ModelDownloadProgress.Idle,
			isChecking = false,
			recognitionMode = recognitionMode,
			whisperModels = whisperModels,
			downloadedModels = allDownloadedModels,
			error = when (resolved) {
				is WhisperConfiguration.Unavailable -> "High Accuracy is not available until a model is validated on this device."
				else -> null
			},
		).resetCancelledDownloads(cancelledModels)
	}

	private fun updateRequiredModel(
		modelType: ModelType,
		progress: ModelDownloadProgress,
	): ModelManagementUiState {
		val current = _uiState.value
		if (current.configuration?.modelType != modelType) return current
		return current.copy(
			requiredModelProgress = progress,
			requiredModelDownloaded = progress is ModelDownloadProgress.Ready,
			error = (progress as? ModelDownloadProgress.Error)?.message,
			whisperModels = current.whisperModels.map { item ->
				if (item.modelType == modelType) item.copy(
					progress = progress,
					isDownloaded = progress is ModelDownloadProgress.Ready,
				) else item
			},
			downloadedModels = if (progress is ModelDownloadProgress.Ready) {
				current.downloadedModels + modelType
			} else current.downloadedModels,
		).updateModel(modelType) {
			it.copy(progress = progress, isDownloaded = progress is ModelDownloadProgress.Ready)
		}
	}

	fun selectLearningMode(learningMode: LearningMode) {
		if (_uiState.value.selectedLearningMode == learningMode) return
		val cancelledModels = downloadJobs
			.filter { (modelType, job) -> modelType != learningMode.requiredWhisperModel && job.isActive }
			.keys
			.toSet()
		cancelledModels.forEach { modelType -> downloadJobs.remove(modelType)?.cancel() }
		_uiState.value = _uiState.value
			.copy(selectedLearningMode = learningMode, error = null)
			.resetCancelledDownloads(cancelledModels)
		viewModelScope.launch { preferencesRepository.setLearningMode(learningMode) }
	}

	fun downloadRequiredModel() {
		val modelType = _uiState.value.configuration?.modelType ?: return
		if (downloadJobs[modelType]?.isActive == true) return
		downloadJobs[modelType] = viewModelScope.launch {
			_uiState.value = updateRequiredModel(
				modelType,
				ModelDownloadProgress.Downloading(0, modelType.expectedByteSize),
			)
			modelRepository.getDownloadProgress(modelType).collect { progress ->
				_uiState.value = updateRequiredModel(modelType, progress)
			}
		}
	}

	fun selectSpeechQuality(quality: SpeechQuality) {
		val language = _uiState.value.selectedLearningLanguage ?: return
		val effectiveQuality = if (language == LearningLanguage.ENGLISH) SpeechQuality.FAST else quality
		if (_uiState.value.selectedSpeechQuality == effectiveQuality) return
		_uiState.value = _uiState.value.copy(selectedSpeechQuality = effectiveQuality, error = null)
		viewModelScope.launch { preferencesRepository.setSpeechQuality(effectiveQuality) }
	}

	fun selectRecognitionMode(mode: RecognitionMode) {
		if (_uiState.value.recognitionMode == mode) return
		_uiState.value = _uiState.value.copy(recognitionMode = mode, error = null)
		viewModelScope.launch { preferencesRepository.setRecognitionMode(mode) }
	}

	fun selectWhisperModel(modelType: ModelType) {
		val language = _uiState.value.selectedLearningLanguage ?: return
		if (!modelType.supports(language)) return
		viewModelScope.launch { preferencesRepository.setWhisperModel(modelType) }
	}

	fun deleteModel(modelType: ModelType) {
		downloadJobs.remove(modelType)?.cancel()
		viewModelScope.launch {
			val removed = withContext(Dispatchers.IO) { modelRepository.deleteModel(modelType) }
			_uiState.value = _uiState.value.let { current ->
				current.copy(
					downloadedModels = current.downloadedModels - modelType,
					whisperModels = current.whisperModels.map { item ->
						if (item.modelType == modelType) item.copy(
							isDownloaded = false,
							progress = ModelDownloadProgress.Idle,
						) else item
					},
					requiredModelDownloaded = if (current.configuration?.modelType == modelType) false else current.requiredModelDownloaded,
					requiredModelProgress = if (current.configuration?.modelType == modelType) ModelDownloadProgress.Idle else current.requiredModelProgress,
					error = if (!removed) "Model file was already removed" else null,
				)
			}
		}
	}

	fun setCaptureVideo(enabled: Boolean) {
		_uiState.value = _uiState.value.copy(captureVideo = enabled)
		viewModelScope.launch { preferencesRepository.setCaptureVideo(enabled) }
	}
}

private data class ModelSelection(
	val language: LearningLanguage?,
	val quality: SpeechQuality,
	val model: ModelType?,
	val mode: RecognitionMode,
)

private fun ModelManagementUiState.updateModel(
	modelType: ModelType,
	transform: (LearningModeItemUiState) -> LearningModeItemUiState,
): ModelManagementUiState = copy(
	modes = modes.map { if (it.modelType == modelType) transform(it) else it },
)

internal fun ModelManagementUiState.resetCancelledDownloads(
	modelTypes: Set<ModelType>,
): ModelManagementUiState = modelTypes.fold(this) { state, modelType ->
	state.updateModel(modelType) { item ->
		when (item.progress) {
			is ModelDownloadProgress.Downloading,
			is ModelDownloadProgress.Verifying -> item.copy(
				progress = if (item.isDownloaded) ModelDownloadProgress.Ready else ModelDownloadProgress.Idle,
			)
			else -> item
		}
	}
}
