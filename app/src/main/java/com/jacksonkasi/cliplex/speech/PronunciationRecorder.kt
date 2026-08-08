package com.jacksonkasi.cliplex.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Records a short microphone sample for private, on-device pronunciation scoring. */
class PronunciationRecorder {
	@SuppressLint("MissingPermission")
	suspend fun record(durationMs: Int = 3_000): AudioInput = withContext(Dispatchers.IO) {
		val sampleRate = 16_000
		val minimumBuffer = AudioRecord.getMinBufferSize(
			sampleRate,
			AudioFormat.CHANNEL_IN_MONO,
			AudioFormat.ENCODING_PCM_16BIT,
		)
		check(minimumBuffer > 0) { "Microphone recording is unavailable" }
		val recorder = AudioRecord(
			MediaRecorder.AudioSource.VOICE_RECOGNITION,
			sampleRate,
			AudioFormat.CHANNEL_IN_MONO,
			AudioFormat.ENCODING_PCM_16BIT,
			minimumBuffer.coerceAtLeast(sampleRate / 2),
		)
		check(recorder.state == AudioRecord.STATE_INITIALIZED) {
			recorder.release()
			"Microphone recording could not start"
		}
		val samples = ShortArray(sampleRate * durationMs / 1_000)
		var written = 0
		try {
			recorder.startRecording()
			while (written < samples.size) {
				val count = recorder.read(samples, written, samples.size - written, AudioRecord.READ_BLOCKING)
				if (count <= 0) error("Microphone recording failed ($count)")
				written += count
			}
		} finally {
			runCatching { recorder.stop() }
			recorder.release()
		}
		AudioInput(samples = if (written == samples.size) samples else samples.copyOf(written))
	}
}
