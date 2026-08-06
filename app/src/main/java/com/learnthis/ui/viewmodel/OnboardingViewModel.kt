package com.learnthis.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.common.AppLanguage
import com.learnthis.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingUiState(
 val selectedLanguage: AppLanguage? = null,
 val isOnboardingCompleted: Boolean = false,
 val isSaving: Boolean = false,
)

class OnboardingViewModel(
 private val preferencesRepository: PreferencesRepository
) : ViewModel() {
 var uiState by mutableStateOf(OnboardingUiState())
 private set

 val selectedLanguage: StateFlow<AppLanguage?> = preferencesRepository.motherTongue
 .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

 val isOnboardingCompleted: StateFlow<Boolean> = preferencesRepository.isOnboardingCompleted
 .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

 init {
 viewModelScope.launch {
 selectedLanguage.collect { lang ->
 uiState = uiState.copy(selectedLanguage = lang)
 }
 }
 viewModelScope.launch {
 isOnboardingCompleted.collect { completed ->
 uiState = uiState.copy(isOnboardingCompleted = completed)
 }
 }
 }

 fun selectLanguage(language: AppLanguage) {
 uiState = uiState.copy(selectedLanguage = language)
 }

 fun completeOnboarding() {
 val language = uiState.selectedLanguage ?: return
 uiState = uiState.copy(isSaving = true)
 viewModelScope.launch {
 preferencesRepository.setMotherTongue(language)
 preferencesRepository.setOnboardingCompleted(true)
 uiState = uiState.copy(isSaving = false)
 }
 }
}
