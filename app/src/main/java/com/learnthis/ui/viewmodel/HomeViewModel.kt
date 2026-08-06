package com.learnthis.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnthis.domain.model.TranscriptionSegment
import com.learnthis.service.CaptureService
import com.learnthis.whisper.WhisperEngine
import com.learnthis.translation.TranslationEngine
import com.learnthis.vad.EnergyVad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
 private val whisperEngine: WhisperEngine,
 private val translationEngine: TranslationEngine,
) : ViewModel() {

 private val _uiState = MutableStateFlow(HomeUiState())
 val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

 private var captureService: CaptureService? = null
 private val vad = EnergyVad()
 private val audioBuffer = mutableListOf<Short>()

 fun bindService(service: CaptureService) {
 captureService = service
 viewModelScope.launch {
 service.captureState.collect { state ->
 when (state) {
 CaptureService.CaptureState.Capturing -> {
 _uiState.value = _uiState.value.copy(isCapturing = true)
 vad.reset()
 audioBuffer.clear()
 }
 CaptureService.CaptureState.Idle,
 CaptureService.CaptureState.Stopping -> {
 _uiState.value = _uiState.value.copy(isCapturing = false)
 processCollectedAudio()
 }
 CaptureService.CaptureState.RequestingPermission,
 CaptureService.CaptureState.Error -> {
 _uiState.value = _uiState.value.copy(isCapturing = false)
 }
 }
 }
 }
 }

 fun unbindService() {
 captureService = null
 }

 fun stopCapture(context: Context) {
 val intent = Intent(context, CaptureService::class.java).apply {
 action = CaptureService.ACTION_STOP
 }
 context.startService(intent)
 }

 fun translateSegment(segment: TranscriptionSegment) {
 if (segment.translatedText != null) return

 val startMs = segment.startTimeMs
 _uiState.value = _uiState.value.copy(
 translatingIds = _uiState.value.translatingIds + startMs
 )

 viewModelScope.launch {
 val result = translationEngine.translate(segment.text)
 result.onSuccess { translated ->
 val updatedSegments = _uiState.value.segments.map {
 if (it.startTimeMs == startMs) it.copy(translatedText = translated) else it
 }
 _uiState.value = _uiState.value.copy(
 segments = updatedSegments,
 translatingIds = _uiState.value.translatingIds - startMs,
 )
 }.onFailure {
 _uiState.value = _uiState.value.copy(
 translatingIds = _uiState.value.translatingIds - startMs,
 )
 }
 }
 }

 private fun processCollectedAudio() {
 val samples = audioBuffer.toShortArray()
 audioBuffer.clear()
 if (samples.isEmpty()) return

 viewModelScope.launch {
 vad.process(samples, 0, samples.size)
 val vadSegments = vad.finalize()
 if (vadSegments.isNotEmpty()) {
 val existingSegments = _uiState.value.segments.toMutableList()
 for (seg in vadSegments) {
 val startSample = seg.startSample.toInt()
 val endSample = seg.endSample.toInt()
 val segmentSamples = samples.sliceArray(startSample until minOf(endSample, samples.size))
 val text = runTranscription(segmentSamples)
 if (text.isNotBlank()) {
 existingSegments.add(
 TranscriptionSegment(
 text = text,
 startTimeMs = seg.startSample,
 endTimeMs = seg.endSample,
 language = "",
 )
 )
 }
 }
 _uiState.value = _uiState.value.copy(segments = existingSegments)
 }
 }
 }

 private suspend fun runTranscription(samples: ShortArray): String {
 return try {
 val result = whisperEngine.transcribe(samples)
 result.getOrNull()?.firstOrNull()?.text ?: ""
 } catch (e: Exception) {
 ""
 }
 }

 fun addAudioSamples(samples: ShortArray) {
 audioBuffer.addAll(samples.toList())
 }

 override fun onCleared() {
 super.onCleared()
 unbindService()
 }
}

data class HomeUiState(
 val isCapturing: Boolean = false,
 val segments: List<TranscriptionSegment> = emptyList(),
 val translatingIds: Set<Long> = emptySet(),
)
