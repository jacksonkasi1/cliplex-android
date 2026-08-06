package com.learnthis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.common.AppLanguage
import com.learnthis.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
 val selectedLanguage: AppLanguage = AppLanguage.ENGLISH,
 val autoTranslate: Boolean = true,
)

class SettingsViewModel(
 private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

 private val _uiState = MutableStateFlow(SettingsUiState())
 val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

 init {
 viewModelScope.launch {
 preferencesRepository.selectedLanguage.collect { lang ->
 _uiState.value = _uiState.value.copy(selectedLanguage = lang ?: AppLanguage.ENGLISH)
 }
 }
 viewModelScope.launch {
 preferencesRepository.autoTranslate.collect { auto ->
 _uiState.value = _uiState.value.copy(autoTranslate = auto)
 }
 }
 }

 fun selectLanguage(language: AppLanguage) {
 _uiState.value = _uiState.value.copy(selectedLanguage = language)
 viewModelScope.launch {
 preferencesRepository.saveSelectedLanguage(language)
 }
 }

 fun setAutoTranslate(enabled: Boolean) {
 _uiState.value = _uiState.value.copy(autoTranslate = enabled)
 viewModelScope.launch {
 preferencesRepository.saveAutoTranslate(enabled)
 }
 }
}
