package com.jacksonkasi.cliplex.audio

import com.jacksonkasi.cliplex.domain.model.AudioHealth
import com.jacksonkasi.cliplex.domain.model.CaptureError
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

object AudioDiagnostics {
	private const val SILENCE_AMPLITUDE = 128

	fun analyze(samples: ShortArray, sampleRateHz: Int = 16_000): AudioHealth {
		if (sampleRateHz <= 0) return AudioHealth(
			sampleCount = samples.size, durationMs = 0, rmsLevel = 0f, peakAmplitude = 0f,
			dbfs = Float.NEGATIVE_INFINITY, zeroSamplePercent = 100f, clippingPercent = 0f,
			nonSilentDurationMs = 0, vadSpeechDurationMs = 0, isValid = false,
			error = CaptureError.AUDIO_FORMAT_INVALID,
		)
		if (samples.isEmpty()) return emptyHealth(CaptureError.CAPTURED_SILENCE)

		var sumSquares = 0.0
		var peak = 0
		var zeroCount = 0
		var clippedCount = 0
		var nonSilentCount = 0
		for (sample in samples) {
			val value = sample.toInt()
			val magnitude = abs(value).coerceAtMost(32768)
			sumSquares += value.toDouble() * value.toDouble()
			peak = maxOf(peak, magnitude)
			if (value == 0) zeroCount++
			if (magnitude >= 32760) clippedCount++
			if (magnitude >= SILENCE_AMPLITUDE) nonSilentCount++
		}
		val rms = sqrt(sumSquares / samples.size).toFloat()
		val durationMs = samples.size * 1000L / sampleRateHz
		val nonSilentMs = nonSilentCount * 1000L / sampleRateHz
		val error = when {
			durationMs < 500 -> CaptureError.AUDIO_TOO_SHORT
			zeroCount.toFloat() / samples.size > 0.995f -> CaptureError.SOURCE_CAPTURE_BLOCKED
			rms < SILENCE_AMPLITUDE || nonSilentMs < 200 -> CaptureError.CAPTURED_SILENCE
			else -> null
		}
		return AudioHealth(
			sampleCount = samples.size,
			durationMs = durationMs,
			rmsLevel = rms,
			peakAmplitude = peak.toFloat(),
			dbfs = if (rms > 0f) (20 * log10(rms / 32768f)) else Float.NEGATIVE_INFINITY,
			zeroSamplePercent = zeroCount * 100f / samples.size,
			clippingPercent = clippedCount * 100f / samples.size,
			nonSilentDurationMs = nonSilentMs,
			vadSpeechDurationMs = 0,
			isValid = error == null,
			error = error,
		)
	}

	private fun emptyHealth(error: CaptureError) = AudioHealth(
		sampleCount = 0, durationMs = 0, rmsLevel = 0f, peakAmplitude = 0f,
		dbfs = Float.NEGATIVE_INFINITY, zeroSamplePercent = 100f, clippingPercent = 0f,
		nonSilentDurationMs = 0, vadSpeechDurationMs = 0, isValid = false, error = error,
	)
}
