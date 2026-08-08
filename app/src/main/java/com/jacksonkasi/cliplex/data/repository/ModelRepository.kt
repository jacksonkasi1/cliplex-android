package com.jacksonkasi.cliplex.data.repository

import android.content.Context
import android.util.Log
import com.jacksonkasi.cliplex.domain.model.ModelDownloadProgress
import com.jacksonkasi.cliplex.domain.model.ModelType
import com.jacksonkasi.cliplex.domain.model.WhisperModelMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ModelRepository internal constructor(
	private val modelDirectory: File,
	private val client: OkHttpClient,
) {
	constructor(context: Context) : this(
		modelDirectory = context.getDir(MODEL_DIR, Context.MODE_PRIVATE),
		client = OkHttpClient(),
	)

	companion object {
		const val MODEL_DIR = "whisper_models"
		private const val TAG = "ModelRepository"
		private const val PROGRESS_INTERVAL_BYTES = 256 * 1024
	}

	fun getDownloadProgress(modelType: ModelType): Flow<ModelDownloadProgress> =
		getDownloadProgress(modelType.metadata)

	internal fun getDownloadProgress(metadata: WhisperModelMetadata): Flow<ModelDownloadProgress> = flow {
		emit(ModelDownloadProgress.Idle)
		modelDirectory.mkdirs()
		val modelFile = getModelFile(metadata)
		val partialFile = getPartialFile(metadata)
		if (verifyModelFile(modelFile, metadata)) {
			emit(ModelDownloadProgress.Ready)
			return@flow
		}

		if (partialFile.isFile && partialFile.length() == metadata.expectedByteSize) {
			emit(ModelDownloadProgress.Verifying())
			if (verifyModelFile(partialFile, metadata)) {
				if (replaceAtomically(partialFile, modelFile) && verifyModelFile(modelFile, metadata)) {
					emit(ModelDownloadProgress.Ready)
					return@flow
				}
				partialFile.delete()
				emit(ModelDownloadProgress.Error("Could not finalize the verified model"))
				return@flow
			}
			partialFile.delete()
		}

		if (partialFile.exists() && (partialFile.length() <= 0L || partialFile.length() > metadata.expectedByteSize)) {
			partialFile.delete()
		}
		var existingBytes = partialFile.takeIf { it.isFile }?.length() ?: 0L
		emit(ModelDownloadProgress.Downloading(existingBytes, metadata.expectedByteSize))

		try {
			val request = Request.Builder()
				.url(metadata.downloadUrl)
				.apply { if (existingBytes > 0L) header("Range", "bytes=$existingBytes-") }
				.build()
			Log.i(TAG, "Downloading ${metadata.fileName}; resumeBytes=$existingBytes")

			client.newCall(request).await().use { response ->
				if (!response.isSuccessful) {
					partialFile.delete()
					emit(ModelDownloadProgress.Error("Server error: HTTP ${response.code}"))
					return@flow
				}
				val body = response.body ?: run {
					partialFile.delete()
					emit(ModelDownloadProgress.Error("The model server returned an empty response"))
					return@flow
				}
				val contentRange = parseContentRange(response.header("Content-Range"))
				when {
					existingBytes > 0L && response.code == 206 -> {
						if (contentRange?.start != existingBytes) {
							partialFile.delete()
							emit(ModelDownloadProgress.Error("The model server returned an invalid resume range"))
							return@flow
						}
					}
					response.code == 200 -> existingBytes = 0L
					response.code == 206 && contentRange?.start == 0L -> existingBytes = 0L
					else -> {
						partialFile.delete()
						emit(ModelDownloadProgress.Error("The model server returned an unsupported response"))
						return@flow
					}
				}

				val reportedTotal = contentRange?.total
					?: body.contentLength().takeIf { response.code == 200 && it >= 0L }
				if (reportedTotal != null && reportedTotal != metadata.expectedByteSize) {
					partialFile.delete()
					emit(ModelDownloadProgress.Error("Model download size did not match the server metadata"))
					return@flow
				}
				if (response.code == 206 && body.contentLength() >= 0L &&
					body.contentLength() != metadata.expectedByteSize - existingBytes) {
					partialFile.delete()
					emit(ModelDownloadProgress.Error("Model resume length did not match the server metadata"))
					return@flow
				}

				body.byteStream().use { input ->
					RandomAccessFile(partialFile, "rw").use { output ->
						if (existingBytes == 0L) output.setLength(0L)
						output.seek(existingBytes)
						val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
						var downloadedBytes = existingBytes
						var lastProgressBytes = existingBytes
						while (true) {
							val read = input.read(buffer)
							if (read < 0) break
							output.write(buffer, 0, read)
							downloadedBytes += read
							if (downloadedBytes > metadata.expectedByteSize) {
								throw IOException("Model response exceeded the expected byte count")
							}
							if (downloadedBytes - lastProgressBytes >= PROGRESS_INTERVAL_BYTES ||
								downloadedBytes == metadata.expectedByteSize) {
								emit(ModelDownloadProgress.Downloading(downloadedBytes, metadata.expectedByteSize))
								lastProgressBytes = downloadedBytes
							}
						}
						output.fd.sync()
					}
				}
			}

			if (partialFile.length() != metadata.expectedByteSize) {
				partialFile.delete()
				emit(ModelDownloadProgress.Error("Model download was incomplete"))
				return@flow
			}
			emit(ModelDownloadProgress.Verifying())
			if (!verifyModelFile(partialFile, metadata)) {
				partialFile.delete()
				emit(ModelDownloadProgress.Error("Model checksum verification failed"))
				return@flow
			}
			if (!replaceAtomically(partialFile, modelFile) || !verifyModelFile(modelFile, metadata)) {
				partialFile.delete()
				emit(ModelDownloadProgress.Error("Could not finalize the verified model"))
				return@flow
			}
			emit(ModelDownloadProgress.Ready)
		} catch (cancelled: CancellationException) {
			// A mode switch or process stop is not corruption. Keep a bounded partial so the
			// next explicit download can continue with a validated HTTP Range request.
			if (partialFile.length() <= 0L || partialFile.length() >= metadata.expectedByteSize) {
				partialFile.delete()
			}
			throw cancelled
		} catch (error: Exception) {
			if (partialFile.length() <= 0L || partialFile.length() >= metadata.expectedByteSize) {
				partialFile.delete()
			}
			Log.e(TAG, "Download failed for ${metadata.fileName}", error)
			emit(ModelDownloadProgress.Error("Download failed: ${error.message ?: "Connection failed"}"))
		}
	}.flowOn(Dispatchers.IO)

	fun isModelAvailable(modelType: ModelType): Boolean =
		verifyModelFile(getModelFile(modelType), modelType.metadata)

	internal fun verifyModelFile(file: File, metadata: WhisperModelMetadata): Boolean =
		file.isFile && file.length() == metadata.expectedByteSize && sha256(file) == metadata.sha256

	fun deleteModel(modelType: ModelType): Boolean {
		getPartialFile(modelType.metadata).delete()
		return getModelFile(modelType).delete()
	}

	fun getModelFile(modelType: ModelType): File = getModelFile(modelType.metadata)

	internal fun getModelFile(metadata: WhisperModelMetadata): File = File(modelDirectory, metadata.fileName)

	fun listDownloadedModels(): List<ModelType> = ModelType.entries.filter(::isModelAvailable)

	private fun getPartialFile(metadata: WhisperModelMetadata): File =
		File(modelDirectory, "${metadata.fileName}.part")

	private fun replaceAtomically(source: File, destination: File): Boolean = try {
		Files.move(
			source.toPath(),
			destination.toPath(),
			StandardCopyOption.ATOMIC_MOVE,
			StandardCopyOption.REPLACE_EXISTING,
		)
		true
	} catch (_: AtomicMoveNotSupportedException) {
		try {
			Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
			true
		} catch (_: IOException) {
			false
		}
	} catch (_: IOException) {
		false
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

internal data class ContentRange(val start: Long, val endInclusive: Long, val total: Long?)

internal fun parseContentRange(value: String?): ContentRange? {
	val match = value?.let { Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(it.trim()) } ?: return null
	val start = match.groupValues[1].toLongOrNull() ?: return null
	val end = match.groupValues[2].toLongOrNull() ?: return null
	if (end < start) return null
	val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
	return ContentRange(start, end, total)
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
	continuation.invokeOnCancellation { cancel() }
	enqueue(object : Callback {
		override fun onFailure(call: Call, error: IOException) {
			continuation.resumeWithException(error)
		}

		override fun onResponse(call: Call, response: Response) {
			continuation.resume(response) { _, value, _ -> value.close() }
		}
	})
}
