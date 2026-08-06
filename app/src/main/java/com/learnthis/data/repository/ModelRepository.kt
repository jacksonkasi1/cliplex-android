package com.learnthis.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.learnthis.domain.model.ModelDownloadProgress
import com.learnthis.domain.model.ModelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ModelRepository(
	private val context: Context,
	private val dataStore: DataStore<Preferences>
) {

	companion object {
		const val MODEL_DIR = "whisper_models"
	}

	private val client = OkHttpClient()

	fun getDownloadProgress(modelType: ModelType): Flow<ModelDownloadProgress> = flow {
		emit(ModelDownloadProgress.Idle)

		val modelFile = getModelFile(modelType)
		if (modelFile.exists() && modelFile.length() == modelType.fileSizeBytes) {
			emit(ModelDownloadProgress.Ready)
			return@flow
		}

		emit(ModelDownloadProgress.Downloading(0, modelType.fileSizeBytes))

		try {
			val request = Request.Builder()
				.url(modelType.downloadUrl)
				.build()

			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					emit(ModelDownloadProgress.Error("Download failed: HTTP ${response.code}"))
					return@flow
				}

				val body = response.body ?: run {
					emit(ModelDownloadProgress.Error("Empty response body"))
					return@flow
				}

				val totalBytes = body.contentLength().takeIf { it > 0 }
					?: modelType.fileSizeBytes
				var downloadedBytes = 0L

				modelFile.parentFile?.mkdirs()

				body.byteStream().use { input ->
					modelFile.outputStream().use { output ->
						val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
						var read: Int
						while (input.read(buffer).also { read = it } != -1) {
							output.write(buffer, 0, read)
							downloadedBytes += read
							emit(
								ModelDownloadProgress.Downloading(
									bytesDownloaded = downloadedBytes,
									totalBytes = totalBytes
								)
							)
						}
					}
				}
			}

			emit(ModelDownloadProgress.Verifying())

			if (modelFile.length() == modelType.fileSizeBytes) {
				emit(ModelDownloadProgress.Ready)
			} else {
				modelFile.delete()
				emit(ModelDownloadProgress.Error("File size mismatch after download"))
			}
		} catch (e: Exception) {
			modelFile.delete()
			emit(ModelDownloadProgress.Error("Download error: ${e.message ?: "Unknown error"}"))
		}
	}

	fun isModelAvailable(modelType: ModelType): Boolean {
		return getModelFile(modelType).run {
			exists() && length() == modelType.fileSizeBytes
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
}
