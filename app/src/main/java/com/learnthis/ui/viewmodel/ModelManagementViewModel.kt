package com.learnthis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.data.repository.ModelRepository
import com.learnthis.data.repository.PreferencesRepository
import com.learnthis.domain.model.LearningLanguage
import com.learnthis.domain.model.LearningMode
import com.learnthis.domain.model.ModelDownloadProgress
import com.learnthis.domain.model.ModelResolver
import com.learnthis.domain.model.ModelType
import com.learnthis.domain.model.SpeechQuality
import com.learnthis.domain.model.WhisperConfiguration
import com.learnthis.domain.model.toLegacyLearningMode
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
			) { language, quality -> language to quality }
				.distinctUntilChanged()
				.collect { (language, quality) ->
					refreshResolvedModel(language, quality)
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
	) {
		val resolved = language?.let { modelResolver.resolve(it, quality) }
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
		_uiState.value = _uiState.value.copy(
			modes = modes,
			selectedLearningMode = language?.toLegacyLearningMode(),
			selectedLearningLanguage = language,
			selectedSpeechQuality = quality,
			configuration = available,
			requiredModelDownloaded = downloaded,
			requiredModelProgress = if (downloaded) ModelDownloadProgress.Ready else ModelDownloadProgress.Idle,
			isChecking = false,
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

	fun setCaptureVideo(enabled: Boolean) {
		_uiState.value = _uiState.value.copy(captureVideo = enabled)
		viewModelScope.launch { preferencesRepository.setCaptureVideo(enabled) }
	}
}

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
