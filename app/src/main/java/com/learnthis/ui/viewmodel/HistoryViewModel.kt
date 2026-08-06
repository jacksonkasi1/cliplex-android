package com.learnthis.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.data.local.SessionEntity
import com.learnthis.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
 val sessions: List<SessionEntity> = emptyList(),
)

class HistoryViewModel(
 private val sessionRepository: SessionRepository,
) : ViewModel() {

 private val _uiState = MutableStateFlow(HistoryUiState())
 val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

 init {
 viewModelScope.launch {
 sessionRepository.getAllSessions().collect { sessions ->
 _uiState.value = HistoryUiState(sessions = sessions)
 }
 }
 }

 fun deleteSession(id: Long) {
 viewModelScope.launch {
 sessionRepository.deleteSession(id)
 }
 }
}
