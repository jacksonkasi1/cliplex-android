package com.learnthis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.data.repository.ModelRepository
import com.learnthis.domain.model.ModelDownloadProgress
import com.learnthis.domain.model.ModelType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelItemUiState(
	val modelType: ModelType,
	val isDownloaded: Boolean = false,
	val progress: ModelDownloadProgress = ModelDownloadProgress.Idle,
	val isDeleting: Boolean = false
)

data class ModelManagementUiState(
	val models: List<ModelItemUiState> = emptyList(),
	val isChecking: Boolean = true,
	val error: String? = null
)

class ModelManagementViewModel(
	private val modelRepository: ModelRepository
) : ViewModel() {

	private val _uiState = MutableStateFlow(ModelManagementUiState())
	val uiState: StateFlow<ModelManagementUiState> = _uiState.asStateFlow()

	init {
		checkDownloadedModels()
	}

	private fun checkDownloadedModels() {
		viewModelScope.launch {
			val updatedModels = ModelType.entries.map { type ->
				ModelItemUiState(
					modelType = type,
					isDownloaded = modelRepository.isModelAvailable(type)
				)
			}
			_uiState.value = _uiState.value.copy(
				models = updatedModels,
				isChecking = false
			)
		}
	}

	fun downloadModel(modelType: ModelType) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.updateModel(modelType) {
				it.copy(progress = ModelDownloadProgress.Downloading(0, modelType.fileSizeBytes))
			}

			modelRepository.getDownloadProgress(modelType).collect { progress ->
				_uiState.value = _uiState.value.updateModel(modelType) {
					it.copy(progress = progress)
				}
			}
		}
	}

	fun deleteModel(modelType: ModelType) {
		viewModelScope.launch {
			_uiState.value = _uiState.value.updateModel(modelType) {
				it.copy(isDeleting = true)
			}

			val deleted = modelRepository.deleteModel(modelType)

			_uiState.value = _uiState.value.updateModel(modelType) {
				ModelItemUiState(
					modelType = modelType,
					isDownloaded = !deleted,
					progress = ModelDownloadProgress.Idle,
					isDeleting = false
				)
			}
		}
	}

	fun dismissError() {
		_uiState.value = _uiState.value.copy(error = null)
	}
}

private fun ModelManagementUiState.updateModel(
	modelType: ModelType,
	transform: (ModelItemUiState) -> ModelItemUiState
): ModelManagementUiState {
	return copy(models = models.map { model ->
		if (model.modelType == modelType) transform(model) else model
	})
}
