package com.jacksonkasi.cliplex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.data.repository.PreferencesRepository
import com.jacksonkasi.cliplex.data.repository.ModelRepository
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.LearningMode
import com.jacksonkasi.cliplex.domain.model.SpeechQuality
import com.jacksonkasi.cliplex.domain.model.ModelDownloadProgress
import com.jacksonkasi.cliplex.domain.model.ModelResolver
import com.jacksonkasi.cliplex.domain.model.WhisperConfiguration
import com.jacksonkasi.cliplex.domain.model.toLegacyLearningMode
import com.jacksonkasi.cliplex.speech.AndroidSpeechRecognizerEngine
import com.jacksonkasi.cliplex.speech.SpeechLanguageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
	val motherTongue: AppLanguage? = null,
	val learningLanguage: LearningLanguage? = null,
	val speechQuality: SpeechQuality = SpeechQuality.DEFAULT,
	val isOnboardingCompleted: Boolean = false,
	val isLoaded: Boolean = false,
	val isSaving: Boolean = false,
	val errorMessage: String? = null,
	val speechLanguageStatus: SpeechLanguageStatus = SpeechLanguageStatus.AndroidUnsupported,
	val fallbackSpeechReady: Boolean = false,
) {
	val showSpeechQuality: Boolean
		get() = learningLanguage != null && learningLanguage != LearningLanguage.ENGLISH

	val canContinue: Boolean
		get() = isLoaded && motherTongue != null && learningLanguage != null && !isSaving

	val effectiveSpeechQuality: SpeechQuality
		get() = if (!showSpeechQuality) {
			SpeechQuality.DEFAULT
		} else {
			speechQuality
		}
}

class OnboardingViewModel(
	private val preferencesRepository: PreferencesRepository,
	coroutineScopeOverride: CoroutineScope? = null,
	private val androidSpeechRecognizerEngine: AndroidSpeechRecognizerEngine? = null,
	private val modelRepository: ModelRepository? = null,
	private val modelResolver: ModelResolver = ModelResolver(),
) : ViewModel() {
	private val workScope = coroutineScopeOverride ?: viewModelScope

	private val _uiState = MutableStateFlow(OnboardingUiState())
	val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

	val selectedMotherTongue: StateFlow<AppLanguage?> = uiState
		.map { it.motherTongue }
		.distinctUntilChanged()
		.stateIn(workScope, SharingStarted.Eagerly, null)

	val selectedLearningLanguage: StateFlow<LearningLanguage?> = uiState
		.map { it.learningLanguage }
		.distinctUntilChanged()
		.stateIn(workScope, SharingStarted.Eagerly, null)

	val selectedSpeechQuality: StateFlow<SpeechQuality> = uiState
		.map { it.speechQuality }
		.distinctUntilChanged()
		.stateIn(workScope, SharingStarted.Eagerly, SpeechQuality.DEFAULT)

	val isOnboardingCompleted: StateFlow<Boolean> = uiState
		.map { it.isOnboardingCompleted }
		.distinctUntilChanged()
		.stateIn(workScope, SharingStarted.Eagerly, false)

	val isSaving: StateFlow<Boolean> = uiState
		.map { it.isSaving }
		.distinctUntilChanged()
		.stateIn(workScope, SharingStarted.Eagerly, false)

	/* Source-compatible aliases for callers migrating from the original onboarding contract. */
	val selectedLanguage: StateFlow<AppLanguage?> = selectedMotherTongue
	val selectedLearningMode: StateFlow<LearningMode?> = uiState
		.map { it.learningLanguage?.toLegacyLearningMode() }
		.distinctUntilChanged()
		.stateIn(workScope, SharingStarted.Eagerly, null)

	init {
		workScope.launch {
			combine(
				preferencesRepository.motherTongue,
				preferencesRepository.learningLanguage,
				preferencesRepository.speechQuality,
				preferencesRepository.isOnboardingCompleted,
			) { motherTongue, learningLanguage, speechQuality, isCompleted ->
				OnboardingUiState(
					motherTongue = motherTongue,
					learningLanguage = learningLanguage,
					speechQuality = speechQuality,
					isOnboardingCompleted = isCompleted,
					isLoaded = true,
				)
			}.collect { persistedState ->
				_uiState.update { current ->
					persistedState.copy(
						isSaving = current.isSaving,
						errorMessage = current.errorMessage,
						speechLanguageStatus = current.speechLanguageStatus,
						fallbackSpeechReady = current.fallbackSpeechReady,
					)
				}
			}
		}
		if (androidSpeechRecognizerEngine != null) {
			workScope.launch {
				selectedLearningLanguage.filterNotNull().distinctUntilChanged().collect { language ->
					val availability = androidSpeechRecognizerEngine.isAvailable(language)
					val fallbackReady = fallbackReady(language)
					_uiState.update {
						it.copy(
							speechLanguageStatus = availability.languageStatus,
							fallbackSpeechReady = fallbackReady,
						)
					}
				}
			}
		}
	}

	fun downloadSelectedSpeechLanguage() {
		val language = _uiState.value.learningLanguage ?: return
		workScope.launch {
			if (_uiState.value.speechLanguageStatus is SpeechLanguageStatus.DownloadRequired) {
				val engine = androidSpeechRecognizerEngine ?: return@launch
				val terminal = engine.downloadLanguage(language)
					.onEach { status -> _uiState.update { it.copy(speechLanguageStatus = status) } }
					.first { it is SpeechLanguageStatus.Ready || it is SpeechLanguageStatus.Error }
				if (terminal is SpeechLanguageStatus.Ready) completeOnboarding()
			} else {
				downloadFallbackSpeech(language)
			}
		}
	}

	private fun fallbackReady(language: LearningLanguage): Boolean {
		val repository = modelRepository ?: return false
		val configuration = modelResolver.resolve(language, _uiState.value.effectiveSpeechQuality)
			as? WhisperConfiguration.Available ?: return false
		return repository.isModelAvailable(configuration.modelType)
	}

	private suspend fun downloadFallbackSpeech(language: LearningLanguage) {
		val repository = modelRepository ?: return
		val configuration = modelResolver.resolve(language, _uiState.value.effectiveSpeechQuality)
			as? WhisperConfiguration.Available ?: return
		val terminal = repository.getDownloadProgress(configuration.modelType)
			.onEach { progress ->
				val status = when (progress) {
					ModelDownloadProgress.Idle,
					is ModelDownloadProgress.Verifying -> SpeechLanguageStatus.Downloading(null)
					is ModelDownloadProgress.Downloading -> SpeechLanguageStatus.Downloading(
						if (progress.totalBytes > 0L) {
							(progress.bytesDownloaded * 100L / progress.totalBytes).toInt().coerceIn(0, 100)
						} else null,
					)
					ModelDownloadProgress.Ready -> SpeechLanguageStatus.Ready
					is ModelDownloadProgress.Error -> SpeechLanguageStatus.Error(progress.message)
				}
				_uiState.update {
					it.copy(
						speechLanguageStatus = status,
						fallbackSpeechReady = progress is ModelDownloadProgress.Ready,
					)
				}
			}
			.first { it is ModelDownloadProgress.Ready || it is ModelDownloadProgress.Error }
		if (terminal is ModelDownloadProgress.Ready) completeOnboarding()
	}

	fun selectMotherTongue(language: AppLanguage) {
		_uiState.update { it.copy(motherTongue = language, errorMessage = null) }
	}

	fun selectLearningLanguage(language: LearningLanguage) {
		_uiState.update { current ->
			current.copy(
				learningLanguage = language,
				speechQuality = if (language == LearningLanguage.ENGLISH) {
					SpeechQuality.DEFAULT
				} else {
					current.speechQuality
				},
				errorMessage = null,
			)
		}
	}

	fun selectSpeechQuality(quality: SpeechQuality) {
		if (!_uiState.value.showSpeechQuality) return
		_uiState.update { it.copy(speechQuality = quality, errorMessage = null) }
	}

	fun completeOnboarding() {
		val state = _uiState.value
		val motherTongue = state.motherTongue ?: return
		val learningLanguage = state.learningLanguage ?: return
		if (state.isSaving) return

		_uiState.update { it.copy(isSaving = true, errorMessage = null) }
		workScope.launch {
			runCatching {
				preferencesRepository.completeOnboarding(
					motherTongue = motherTongue,
					learningLanguage = learningLanguage,
					speechQuality = state.effectiveSpeechQuality,
				)
			}.onFailure {
				_uiState.update { current ->
					current.copy(errorMessage = "Unable to save your setup. Try again.")
				}
			}
			_uiState.update { it.copy(isSaving = false) }
		}
	}

	fun restartOnboarding() {
		workScope.launch {
			runCatching { preferencesRepository.restartOnboarding() }
				.onFailure {
					_uiState.update { current ->
						current.copy(errorMessage = "Unable to restart setup. Try again.")
					}
				}
		}
	}

	fun selectLanguage(language: AppLanguage) = selectMotherTongue(language)

	fun selectLearningMode(learningMode: LearningMode) {
		selectLearningLanguage(LearningLanguage.fromLegacyLearningMode(learningMode) ?: LearningLanguage.ANY_LANGUAGE)
	}
}
