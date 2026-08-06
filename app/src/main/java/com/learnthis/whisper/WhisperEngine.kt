package com.learnthis.whisper

import com.learnthis.util.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperEngine {

 companion object {
 const val SAMPLE_RATE_HZ = 16000
 const val DEFAULT_N_THREADS = 4
 }

 private var isModelLoaded = false

 suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
 try {
 val ok = NativeBridge.whisperLoadModel(modelPath)
 if (ok) {
 isModelLoaded = true
 Result.success(Unit)
 } else {
 Result.failure(IllegalStateException("Failed to load model: $modelPath"))
 }
 } catch (e: UnsatisfiedLinkError) {
 Result.failure(IllegalStateException("Native library not loaded", e))
 } catch (e: Exception) {
 Result.failure(e)
 }
 }

 suspend fun transcribe(
 samples: ShortArray,
 language: String = "en",
 nThreads: Int = DEFAULT_N_THREADS,
 ): Result<List<TranscriptionSegment>> = withContext(Dispatchers.IO) {
 if (!isModelLoaded) {
 return@withContext Result.failure(IllegalStateException("Model not loaded"))
 }

 try {
 val json = NativeBridge.whisperTranscribe(samples, language, nThreads)
 if (json != null) {
 Result.success(parseResult(json))
 } else {
 Result.failure(IllegalStateException("Transcription returned null"))
 }
 } catch (e: UnsatisfiedLinkError) {
 Result.failure(IllegalStateException("Native library not loaded", e))
 } catch (e: Exception) {
 Result.failure(e)
 }
 }

 fun release() {
 if (isModelLoaded) {
 NativeBridge.whisperFreeModel()
 isModelLoaded = false
 }
 }

 private fun parseResult(json: String): List<TranscriptionSegment> {
 val segments = mutableListOf<TranscriptionSegment>()
 val regex = Regex(""""start":(\d+),"end":(\d+),"text":"([^"]*)"""")
 val matches = regex.findAll(json)
 for (match in matches) {
 val startMs = match.groupValues[1].toLongOrNull() ?: 0L
 val endMs = match.groupValues[2].toLongOrNull() ?: 0L
 val text = match.groupValues[3]
 if (text.isNotBlank()) {
 segments.add(TranscriptionSegment(text = text.trim(), startTimeMs = startMs, endTimeMs = endMs, language = ""))
 }
 }
 if (segments.isEmpty() && json.isNotBlank()) {
 segments.add(TranscriptionSegment(text = json.trim(), startTimeMs = 0, endTimeMs = 0, language = ""))
 }
 return segments
 }
}

data class TranscriptionSegment(
 val text: String,
 val startTimeMs: Long,
 val endTimeMs: Long,
 val language: String,
 val confidence: Float? = null,
 val noSpeechProb: Float? = null,
 var translatedText: String? = null,
)
