package com.learnthis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.common.AppLanguage
import com.learnthis.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
 private val preferencesRepository: PreferencesRepository
) : ViewModel() {

 private val _selectedLanguage = MutableStateFlow<AppLanguage?>(null)
 val selectedLanguage: StateFlow<AppLanguage?> = _selectedLanguage.asStateFlow()

 private val _isOnboardingCompleted = MutableStateFlow(false)
 val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

 private val _isSaving = MutableStateFlow(false)
 val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

 init {
 viewModelScope.launch {
 preferencesRepository.motherTongue.collect { lang ->
 _selectedLanguage.value = lang
 }
 }
 viewModelScope.launch {
 preferencesRepository.isOnboardingCompleted.collect { completed ->
 _isOnboardingCompleted.value = completed
 }
 }
 }

 fun selectLanguage(language: AppLanguage) {
 _selectedLanguage.value = language
 }

 fun completeOnboarding() {
 val language = _selectedLanguage.value ?: return
 _isSaving.value = true
 viewModelScope.launch {
 preferencesRepository.setMotherTongue(language)
 preferencesRepository.setOnboardingCompleted(true)
 _isSaving.value = false
 }
 }

 fun restartOnboarding() {
 viewModelScope.launch { preferencesRepository.restartOnboarding() }
 }
}
