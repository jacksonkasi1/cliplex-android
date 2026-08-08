package com.jacksonkasi.cliplex.translation

import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translator
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TranslationEngineTest {
	@Test
	fun failedModelDownloadIsDiscardedAndSamePairCanRetry() = runTest {
		val failed = FakeTranslator(
			downloadTask = Tasks.forException(IOException("offline")),
			translation = "unused",
		)
		val recovered = FakeTranslator(
			downloadTask = Tasks.forResult(null),
			translation = "வணக்கம்",
		)
		val translators = ArrayDeque(listOf(failed, recovered))
		var factoryCalls = 0
		val engine = TranslationEngine {
			factoryCalls++
			translators.removeFirst()
		}

		val firstAttempt = async { engine.initialize("en", "ta") }
		runCurrent()
		shadowOf(Looper.getMainLooper()).idle()
		runCurrent()
		assertTrue(firstAttempt.await().isFailure)
		assertTrue(failed.closed)

		val retry = async { engine.initialize("en", "ta") }
		runCurrent()
		shadowOf(Looper.getMainLooper()).idle()
		runCurrent()
		assertTrue(retry.await().isSuccess)
		assertEquals(2, factoryCalls)

		val translation = async { engine.translate("hello") }
		runCurrent()
		shadowOf(Looper.getMainLooper()).idle()
		runCurrent()
		assertEquals("வணக்கம்", translation.await().getOrThrow())
		assertFalse(recovered.closed)
	}

	@Test
	fun pendingTranslatorIsNotTreatedAsReadyByASecondInitialization() = runTest {
		val pendingDownload = TaskCompletionSource<Void>()
		val pending = FakeTranslator(pendingDownload.task, "first")
		val replacement = FakeTranslator(Tasks.forResult(null), "second")
		val translators = ArrayDeque(listOf(pending, replacement))
		val engine = TranslationEngine { translators.removeFirst() }

		val firstAttempt = async { engine.initialize("en", "ta") }
		runCurrent()
		assertFalse(firstAttempt.isCompleted)

		val secondAttempt = async { engine.initialize("en", "ta") }
		runCurrent()
		shadowOf(Looper.getMainLooper()).idle()
		runCurrent()

		assertTrue(secondAttempt.await().isSuccess)
		assertTrue(pending.closed)
		pendingDownload.setException(IOException("superseded"))
		shadowOf(Looper.getMainLooper()).idle()
		runCurrent()
		assertTrue(firstAttempt.await().isFailure)
		assertFalse(replacement.closed)
	}

	private class FakeTranslator(
		private val downloadTask: Task<Void>,
		private val translation: String,
	) : Translator {
		var closed = false

		override fun downloadModelIfNeeded(): Task<Void> = downloadTask

		override fun downloadModelIfNeeded(conditions: DownloadConditions): Task<Void> = downloadTask

		override fun translate(text: String): Task<String> = Tasks.forResult(translation)

		override fun close() {
			closed = true
		}
	}
}
