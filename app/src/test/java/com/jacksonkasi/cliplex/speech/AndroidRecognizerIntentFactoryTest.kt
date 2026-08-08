package com.jacksonkasi.cliplex.speech

import android.media.AudioFormat
import android.os.Build
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidRecognizerIntentFactoryTest {
	@Test
	fun `builds offline known-language captured PCM request`() {
		val descriptors = ParcelFileDescriptor.createPipe()
		try {
			val intent = AndroidRecognizerIntentFactory().create(AndroidRecognizerRequest(
				language = LearningLanguage.HINDI,
				audioDescriptor = descriptors[0],
			))

			assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
			assertEquals("hi-IN", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
			assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
			assertFalse(intent.getBooleanExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, false))
			assertEquals(16_000, intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 0))
			assertEquals(1, intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 0))
			assertEquals(AudioFormat.ENCODING_PCM_16BIT, intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, 0))
			assertEquals(
				RecognizerIntent.EXTRA_AUDIO_SOURCE,
				intent.getStringExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION),
			)
			if (Build.VERSION.SDK_INT >= 34) {
				assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, false))
			}
		} finally {
			descriptors.forEach { it.close() }
		}
	}
}
