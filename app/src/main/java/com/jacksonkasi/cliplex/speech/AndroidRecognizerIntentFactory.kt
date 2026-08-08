package com.jacksonkasi.cliplex.speech

import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import androidx.annotation.RequiresApi
import com.jacksonkasi.cliplex.domain.model.LearningLanguage

data class AndroidRecognizerRequest(
	val language: LearningLanguage,
	val languageTag: String = language.recognitionTag,
	val audioDescriptor: ParcelFileDescriptor? = null,
	val sampleRateHz: Int = 16_000,
	val channelCount: Int = 1,
	val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
	val partialResults: Boolean = true,
	val segmentedResults: Boolean = true,
	val wordTiming: Boolean = true,
)

class AndroidRecognizerIntentFactory {
	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	fun create(request: AndroidRecognizerRequest): Intent {
		require(request.language != LearningLanguage.ANY_LANGUAGE) {
			"Captured-audio recognition requires a selected language"
		}
		return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
			putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
			putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.languageTag)
			putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, request.partialResults)
			putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
			request.audioDescriptor?.let { descriptor ->
				putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptor)
				putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, request.sampleRateHz)
				putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, request.channelCount)
				putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, request.encoding)
				if (request.segmentedResults) {
					putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
				}
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && request.wordTiming) {
				putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, true)
				putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE, true)
			}
		}
	}
}
