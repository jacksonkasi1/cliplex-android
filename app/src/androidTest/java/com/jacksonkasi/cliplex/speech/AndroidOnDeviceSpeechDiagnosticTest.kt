package com.jacksonkasi.cliplex.speech

import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.jacksonkasi.cliplex.audio.Pcm16
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

/**
 * Real-device diagnostic: verifies that the installed on-device RecognitionService accepts the
 * exact microphone-free EXTRA_AUDIO_SOURCE configuration. Transcript accuracy still requires a
 * known-good language PCM fixture on the target device.
 */
@RunWith(AndroidJUnit4::class)
class AndroidOnDeviceSpeechDiagnosticTest {
	companion object { private const val TAG = "ClipLexAsrDeviceTest" }
	@Test
	fun selectedLanguageCapturedAudioConfigurationsCanBeQueried() = runBlocking {
		assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
		val engine = AndroidSpeechRecognizerEngine(ApplicationProvider.getApplicationContext())
		try {
			listOf(LearningLanguage.ENGLISH, LearningLanguage.HINDI, LearningLanguage.TAMIL).forEach { language ->
				val availability = engine.isAvailable(language)
				Log.i(TAG, "${language.displayName} availability=$availability")
				// A completed query is useful on every device; readiness depends on the installed service/model.
				check(availability.languageStatus !is SpeechLanguageStatus.Error) {
					(availability.languageStatus as SpeechLanguageStatus.Error).reason
				}
			}
		} finally {
			engine.close()
		}
	}

	@Test
	fun knownGoodEnglishPcmUsesInjectedAudio() = runBlocking {
		assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
		val context = ApplicationProvider.getApplicationContext<android.content.Context>()
		val engine = AndroidSpeechRecognizerEngine(context)
		try {
			val availability = engine.isAvailable(LearningLanguage.ENGLISH)
			assumeTrue(availability.available && availability.audioInjectionSupported)
			val wav = InstrumentationRegistry.getInstrumentation().targetContext.assets
				.open("jfk.wav").use { Pcm16.readWav(it.readBytes()) }
			val mono = if (wav.channels == 2) Pcm16.stereoToMono(wav.samples) else wav.samples
			val samples = Pcm16.resampleLinear(mono, wav.sampleRate, 16_000)
			val result = engine.transcribe(AudioInput(samples), LearningLanguage.ENGLISH)
			Log.i(
				TAG,
				"JFK engine=${result.engine} processingMs=${result.processingDurationMs} " +
					"wordTimings=${result.words.count { it.startTimeMs != null }} text=${result.text}",
			)
			val normalized = result.text.lowercase()
			assertTrue("Expected a transcript from injected PCM", normalized.isNotBlank())
			assertTrue(
				"Injected PCM transcript did not match the known-good fixture",
				"country" in normalized || "fellow" in normalized || "ask" in normalized,
			)
		} finally {
			engine.close()
		}
	}
}
