package com.learnthis.data.repository

import android.content.Context
import android.util.Log
import com.learnthis.domain.model.ModelDownloadProgress
import com.learnthis.domain.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

class ModelRepository(
	private val context: Context,
) {

	companion object {
		const val MODEL_DIR = "whisper_models"
		private const val TAG = "ModelRepository"
	}

	private val client = OkHttpClient()

	fun getDownloadProgress(modelType: ModelType): Flow<ModelDownloadProgress> = flow {
		emit(ModelDownloadProgress.Idle)

		val modelFile = getModelFile(modelType)
		if (isModelAvailable(modelType)) {
			emit(ModelDownloadProgress.Ready)
			return@flow
		}

		val partialFile = File(modelFile.parentFile, "${modelFile.name}.part")
		if (partialFile.length() > modelType.fileSizeBytes) partialFile.delete()
		var existingBytes = partialFile.length().coerceAtMost(modelType.fileSizeBytes)
		emit(ModelDownloadProgress.Downloading(existingBytes, modelType.fileSizeBytes))

		try {
		val request = Request.Builder()
			.url(modelType.downloadUrl)
			.apply { if (existingBytes > 0) header("Range", "bytes=$existingBytes-") }
			.build()

		Log.i(TAG, "Downloading: ${modelType.downloadUrl}")

		client.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				Log.e(TAG, "HTTP ${response.code}")
				emit(ModelDownloadProgress.Error("Server error: HTTP ${response.code}"))
				return@flow
			}

			val body = response.body
				?: run {
					emit(ModelDownloadProgress.Error("Empty response from server"))
					return@flow
				}

			if (response.code != 206) existingBytes = 0L
			modelFile.parentFile?.mkdirs()

			body.byteStream().use { input ->
				RandomAccessFile(partialFile, "rw").use { output ->
					if (existingBytes == 0L) output.setLength(0)
					output.seek(existingBytes)
					val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
					var read: Int
					var downloadedBytes = existingBytes
					var lastProgressBytes = existingBytes
					while (input.read(buffer).also { read = it } != -1) {
						output.write(buffer, 0, read)
						downloadedBytes += read
						if (downloadedBytes - lastProgressBytes >= 256 * 1024 || downloadedBytes == modelType.fileSizeBytes) {
							emit(ModelDownloadProgress.Downloading(downloadedBytes, modelType.fileSizeBytes))
							lastProgressBytes = downloadedBytes
						}
					}
				}
			}
		}

		if (partialFile.length() != modelType.fileSizeBytes) {
			emit(ModelDownloadProgress.Error("Download incomplete; retry to resume"))
			return@flow
		}
		emit(ModelDownloadProgress.Verifying())
		if (sha256(partialFile) != modelType.sha256) {
			partialFile.delete()
			emit(ModelDownloadProgress.Error("Model checksum verification failed"))
			return@flow
		}
		if (modelFile.exists()) modelFile.delete()
		if (!partialFile.renameTo(modelFile)) {
			emit(ModelDownloadProgress.Error("Could not finalize model file"))
			return@flow
		}
		emit(ModelDownloadProgress.Ready)
		} catch (e: IOException) {
			Log.e(TAG, "Download failed", e)
			emit(ModelDownloadProgress.Error("Network error: ${e.message ?: "Connection failed"}"))
		}
	}.flowOn(Dispatchers.IO)

	fun isModelAvailable(modelType: ModelType): Boolean {
		return getModelFile(modelType).run {
			exists() && length() == modelType.fileSizeBytes && sha256(this) == modelType.sha256
		}
	}

	fun deleteModel(modelType: ModelType): Boolean {
		return getModelFile(modelType).delete()
	}

	fun getModelFile(modelType: ModelType): File {
		return File(context.getDir(MODEL_DIR, Context.MODE_PRIVATE), modelType.fileName)
	}

	fun listDownloadedModels(): List<ModelType> {
		return ModelType.entries.filter { isModelAvailable(it) }
	}

	private fun sha256(file: File): String {
		val digest = MessageDigest.getInstance("SHA-256")
		file.inputStream().buffered().use { input ->
			val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
			while (true) {
				val read = input.read(buffer)
				if (read < 0) break
				digest.update(buffer, 0, read)
			}
		}
		return digest.digest().joinToString("") { "%02x".format(it) }
	}
}
