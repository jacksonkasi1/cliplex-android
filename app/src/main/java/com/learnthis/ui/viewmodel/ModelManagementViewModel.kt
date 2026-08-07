package com.learnthis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.data.repository.ModelRepository
import com.learnthis.data.repository.PreferencesRepository
import com.learnthis.domain.model.ModelDownloadProgress
import com.learnthis.domain.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelItemUiState(
	val modelType: ModelType,
	val isDownloaded: Boolean = false,
	val progress: ModelDownloadProgress = ModelDownloadProgress.Idle,
	val isDeleting: Boolean = false,
)

data class ModelManagementUiState(
	val models: List<ModelItemUiState> = emptyList(),
	val activeModel: ModelType? = null,
	val isChecking: Boolean = true,
	val error: String? = null,
)

class ModelManagementViewModel(
	private val modelRepository: ModelRepository,
	private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
	private val downloadJobs = mutableMapOf<ModelType, Job>()
	private val _uiState = MutableStateFlow(ModelManagementUiState())
	val uiState: StateFlow<ModelManagementUiState> = _uiState.asStateFlow()

	init {
		checkDownloadedModels()
		viewModelScope.launch {
			preferencesRepository.activeModel.collect { selected ->
				_uiState.value = _uiState.value.copy(activeModel = selected)
			}
		}
	}

	private fun checkDownloadedModels() {
		viewModelScope.launch {
			val models = withContext(Dispatchers.IO) {
				ModelType.entries.map { type ->
					val downloaded = modelRepository.isModelAvailable(type)
					ModelItemUiState(
						modelType = type,
						isDownloaded = downloaded,
						progress = if (downloaded) ModelDownloadProgress.Ready else ModelDownloadProgress.Idle,
					)
				}
			}
			val stored = preferencesRepository.activeModel.first()
			val fallback = models.firstOrNull { it.modelType == ModelType.TINY_Q5_1 && it.isDownloaded }?.modelType
				?: models.firstOrNull { it.isDownloaded }?.modelType
			val active = stored?.takeIf { selected -> models.any { it.modelType == selected && it.isDownloaded } } ?: fallback
			if (active != stored) preferencesRepository.setActiveModel(active)
			_uiState.value = _uiState.value.copy(models = models, activeModel = active, isChecking = false)
		}
	}

	fun downloadModel(modelType: ModelType) {
		if (downloadJobs[modelType]?.isActive == true) return
		downloadJobs[modelType] = viewModelScope.launch {
			_uiState.value = _uiState.value.updateModel(modelType) {
				it.copy(progress = ModelDownloadProgress.Downloading(0, modelType.fileSizeBytes))
			}
			modelRepository.getDownloadProgress(modelType).collect { progress ->
				_uiState.value = _uiState.value.updateModel(modelType) {
					it.copy(progress = progress, isDownloaded = progress is ModelDownloadProgress.Ready)
				}
				if (progress is ModelDownloadProgress.Ready && _uiState.value.activeModel == null) {
					selectModel(modelType)
				}
			}
		}
	}

	fun selectModel(modelType: ModelType) {
		if (_uiState.value.models.none { it.modelType == modelType && it.isDownloaded }) return
		_uiState.value = _uiState.value.copy(activeModel = modelType)
		viewModelScope.launch { preferencesRepository.setActiveModel(modelType) }
	}

	fun deleteModel(modelType: ModelType) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.updateModel(modelType) { it.copy(isDeleting = true) }
			withContext(Dispatchers.IO) { modelRepository.deleteModel(modelType) }
			val remaining = _uiState.value.models.map {
				if (it.modelType == modelType) ModelItemUiState(modelType) else it
			}
			val nextActive = if (_uiState.value.activeModel == modelType) {
				remaining.firstOrNull { it.isDownloaded }?.modelType
			} else _uiState.value.activeModel
			_uiState.value = _uiState.value.copy(models = remaining, activeModel = nextActive)
			preferencesRepository.setActiveModel(nextActive)
		}
	}
}

private fun ModelManagementUiState.updateModel(
	modelType: ModelType,
	transform: (ModelItemUiState) -> ModelItemUiState,
): ModelManagementUiState = copy(models = models.map { if (it.modelType == modelType) transform(it) else it })
