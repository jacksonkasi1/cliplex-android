package com.jacksonkasi.cliplex.data.repository

import com.jacksonkasi.cliplex.domain.model.ModelDownloadProgress
import com.jacksonkasi.cliplex.domain.model.WhisperModelMetadata
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ModelRepositoryTest {

	@get:Rule
	val temporaryFolder = TemporaryFolder()

	@Test
	fun verifiedFinalFileSkipsNetwork() = runBlocking {
		val bytes = "already verified".toByteArray()
		val metadata = metadata(bytes)
		val directory = temporaryFolder.newFolder("verified")
		File(directory, metadata.fileName).writeBytes(bytes)
		val interceptor = RecordingInterceptor { request ->
			error("Unexpected network request: ${request.url}")
		}
		val repository = repository(directory, interceptor)

		val progress = repository.getDownloadProgress(metadata).toList()

		assertEquals(listOf(ModelDownloadProgress.Idle, ModelDownloadProgress.Ready), progress)
		assertTrue(repository.verifyModelFile(File(directory, metadata.fileName), metadata))
		assertTrue(interceptor.requests.isEmpty())
	}

	@Test
	fun completedPartialIsVerifiedAndFinalizedWithoutNetwork() = runBlocking {
		val bytes = ByteArray(3_000) { index -> (index % 211).toByte() }
		val metadata = metadata(bytes)
		val directory = temporaryFolder.newFolder("completed-partial")
		val partial = File(directory, "${metadata.fileName}.part").apply { writeBytes(bytes) }
		val interceptor = RecordingInterceptor { request ->
			error("Unexpected network request: ${request.url}")
		}
		val repository = repository(directory, interceptor)

		val progress = repository.getDownloadProgress(metadata).toList()

		assertEquals(
			listOf(ModelDownloadProgress.Idle, ModelDownloadProgress.Verifying(), ModelDownloadProgress.Ready),
			progress,
		)
		assertFalse(partial.exists())
		assertArrayEquals(bytes, repository.getModelFile(metadata).readBytes())
		assertTrue(interceptor.requests.isEmpty())
	}

	@Test
	fun freshDownloadIsVerifiedAndFinalized() = runBlocking {
		val bytes = ByteArray(4_096) { index -> (index % 251).toByte() }
		val metadata = metadata(bytes)
		val directory = temporaryFolder.newFolder("fresh")
		val interceptor = RecordingInterceptor { request -> response(request, 200, bytes) }
		val repository = repository(directory, interceptor)

		val progress = repository.getDownloadProgress(metadata).toList()

		assertTrue(progress.last() is ModelDownloadProgress.Ready)
		assertTrue(progress.any { it is ModelDownloadProgress.Verifying })
		assertNull(interceptor.requests.single().header("Range"))
		assertArrayEquals(bytes, repository.getModelFile(metadata).readBytes())
		assertFalse(File(directory, "${metadata.fileName}.part").exists())
	}

	@Test
	fun partialDownloadResumesAtExactOffset() = runBlocking {
		val bytes = ByteArray(8_192) { index -> (index % 193).toByte() }
		val prefixLength = 1_537
		val metadata = metadata(bytes)
		val directory = temporaryFolder.newFolder("resume")
		File(directory, "${metadata.fileName}.part").writeBytes(bytes.copyOfRange(0, prefixLength))
		val interceptor = RecordingInterceptor { request ->
			response(
				request = request,
				code = 206,
				body = bytes.copyOfRange(prefixLength, bytes.size),
				contentRange = "bytes $prefixLength-${bytes.lastIndex}/${bytes.size}",
			)
		}
		val repository = repository(directory, interceptor)

		val progress = repository.getDownloadProgress(metadata).toList()

		assertTrue(progress.last() is ModelDownloadProgress.Ready)
		assertEquals("bytes=$prefixLength-", interceptor.requests.single().header("Range"))
		assertArrayEquals(bytes, repository.getModelFile(metadata).readBytes())
	}

	@Test
	fun fullResponseToRangeRequestRestartsInsteadOfAppending() = runBlocking {
		val bytes = ByteArray(2_048) { index -> (index % 127).toByte() }
		val metadata = metadata(bytes)
		val directory = temporaryFolder.newFolder("range-ignored")
		File(directory, "${metadata.fileName}.part").writeBytes(byteArrayOf(9, 8, 7, 6))
		val interceptor = RecordingInterceptor { request -> response(request, 200, bytes) }
		val repository = repository(directory, interceptor)

		val progress = repository.getDownloadProgress(metadata).toList()

		assertTrue(progress.last() is ModelDownloadProgress.Ready)
		assertEquals("bytes=4-", interceptor.requests.single().header("Range"))
		assertArrayEquals(bytes, repository.getModelFile(metadata).readBytes())
	}

	@Test
	fun invalidResumeRangeIsRejectedAndPartialIsRemoved() = runBlocking {
		val bytes = ByteArray(1_024) { 4 }
		val metadata = metadata(bytes)
		val directory = temporaryFolder.newFolder("invalid-range")
		val partial = File(directory, "${metadata.fileName}.part").apply {
			writeBytes(bytes.copyOfRange(0, 100))
		}
		val interceptor = RecordingInterceptor { request ->
			response(
				request = request,
				code = 206,
				body = bytes.copyOfRange(100, bytes.size),
				contentRange = "bytes 101-${bytes.lastIndex}/${bytes.size}",
			)
		}
		val repository = repository(directory, interceptor)

		val progress = repository.getDownloadProgress(metadata).toList()

		val error = progress.last() as ModelDownloadProgress.Error
		assertTrue(error.message.contains("invalid resume range"))
		assertFalse(partial.exists())
		assertFalse(repository.getModelFile(metadata).exists())
	}

	@Test
	fun checksumMismatchDoesNotPublishFinalModel() = runBlocking {
		val bytes = ByteArray(1_024) { 3 }
		val metadata = metadata(bytes).copy(sha256 = "0".repeat(64))
		val directory = temporaryFolder.newFolder("bad-checksum")
		val repository = repository(
			directory,
			RecordingInterceptor { request -> response(request, 200, bytes) },
		)

		val progress = repository.getDownloadProgress(metadata).toList()

		assertEquals("Model checksum verification failed", (progress.last() as ModelDownloadProgress.Error).message)
		assertFalse(repository.getModelFile(metadata).exists())
		assertFalse(File(directory, "${metadata.fileName}.part").exists())
	}

	@Test
	fun contentRangeParserAcceptsValidRangeAndRejectsMalformedValues() {
		assertEquals(ContentRange(100, 199, 1_000), parseContentRange("bytes 100-199/1000"))
		assertEquals(ContentRange(0, 99, null), parseContentRange("bytes 0-99/*"))
		assertNull(parseContentRange("items 0-99/1000"))
		assertNull(parseContentRange("bytes 100-99/1000"))
		assertNull(parseContentRange(null))
	}

	private fun repository(directory: File, interceptor: Interceptor): ModelRepository =
		ModelRepository(directory, OkHttpClient.Builder().addInterceptor(interceptor).build())

	private fun metadata(bytes: ByteArray): WhisperModelMetadata = WhisperModelMetadata(
		fileName = "test-model.bin",
		technicalName = "Test model",
		displaySize = "${bytes.size} B",
		expectedByteSize = bytes.size.toLong(),
		sha256 = sha256(bytes),
		downloadUrl = "https://models.example.test/test-model.bin",
	)

	private fun response(
		request: Request,
		code: Int,
		body: ByteArray,
		contentRange: String? = null,
	): Response = Response.Builder()
		.request(request)
		.protocol(Protocol.HTTP_1_1)
		.code(code)
		.message(if (code in 200..299) "OK" else "Error")
		.apply { if (contentRange != null) header("Content-Range", contentRange) }
		.body(body.toResponseBody("application/octet-stream".toMediaType()))
		.build()

	private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
		.digest(bytes)
		.joinToString("") { "%02x".format(it) }

	private class RecordingInterceptor(
		private val responder: (Request) -> Response,
	) : Interceptor {
		val requests = mutableListOf<Request>()

		override fun intercept(chain: Interceptor.Chain): Response = chain.request().let { request ->
			requests += request
			responder(request)
		}
	}
}
