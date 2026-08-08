package com.learnthis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.common.AppLanguage
import com.learnthis.data.repository.PreferencesRepository
import com.learnthis.domain.model.LearningLanguage
import com.learnthis.domain.model.LearningMode
import com.learnthis.domain.model.SpeechQuality
import com.learnthis.domain.model.toLegacyLearningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
) {
	val showSpeechQuality: Boolean
		get() = learningLanguage != null && learningLanguage != LearningLanguage.ENGLISH

	val canContinue: Boolean
		get() = isLoaded && motherTongue != null && learningLanguage != null && !isSaving

	val effectiveSpeechQuality: SpeechQuality
		get() = if (!showSpeechQuality || speechQuality == SpeechQuality.HIGH_ACCURACY) {
			SpeechQuality.DEFAULT
		} else {
			speechQuality
		}
}

class OnboardingViewModel(
	private val preferencesRepository: PreferencesRepository,
	coroutineScopeOverride: CoroutineScope? = null,
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
					speechQuality = speechQuality.availableOnOnboarding(),
					isOnboardingCompleted = isCompleted,
					isLoaded = true,
				)
			}.collect { persistedState ->
				_uiState.update { current ->
					persistedState.copy(
						isSaving = current.isSaving,
						errorMessage = current.errorMessage,
					)
				}
			}
		}
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
					current.speechQuality.availableOnOnboarding()
				},
				errorMessage = null,
			)
		}
	}

	fun selectSpeechQuality(quality: SpeechQuality) {
		if (!_uiState.value.showSpeechQuality || quality == SpeechQuality.HIGH_ACCURACY) return
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

private fun SpeechQuality.availableOnOnboarding(): SpeechQuality =
	if (this == SpeechQuality.HIGH_ACCURACY) SpeechQuality.DEFAULT else this
