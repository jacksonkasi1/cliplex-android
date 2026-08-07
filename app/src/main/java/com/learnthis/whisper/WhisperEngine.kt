package com.learnthis.whisper

import com.learnthis.util.NativeBridge
import com.learnthis.domain.model.TranscriptionSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WhisperEngine {

 companion object {
 const val SAMPLE_RATE_HZ = 16000
 const val DEFAULT_N_THREADS = 4
 }

 @Volatile private var loadedModelPath: String? = null

 suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
 if (loadedModelPath == modelPath) return@withContext Result.success(Unit)
 try {
 val ok = NativeBridge.whisperLoadModel(modelPath)
 if (ok) {
 loadedModelPath = modelPath
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
 language: String = "auto",
 nThreads: Int = DEFAULT_N_THREADS,
 ): Result<List<TranscriptionSegment>> = withContext(Dispatchers.IO) {
 if (loadedModelPath == null) {
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
 if (loadedModelPath != null) {
 NativeBridge.whisperFreeModel()
 loadedModelPath = null
 }
 }

 private fun parseResult(json: String): List<TranscriptionSegment> {
 val root = JSONObject(json)
 val detectedLanguage = root.optString("language")
 val values = root.optJSONArray("segments") ?: return emptyList()
 val segments = mutableListOf<TranscriptionSegment>()
 for (index in 0 until values.length()) {
 val value = values.getJSONObject(index)
 val startMs = value.optLong("start")
 val endMs = value.optLong("end")
 val text = value.optString("text")
 if (text.isNotBlank()) {
 segments.add(TranscriptionSegment(
 text = text.trim(),
 startTimeMs = startMs,
 endTimeMs = endMs,
 language = detectedLanguage,
 noSpeechProb = value.optDouble("noSpeechProb", Double.NaN).takeUnless { it.isNaN() }?.toFloat(),
 ))
 }
 }
 return segments
 }
}
