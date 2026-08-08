package com.jacksonkasi.cliplex.audio

import com.jacksonkasi.cliplex.domain.model.CaptureError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDiagnosticsTest {
	@Test fun allZeroCaptureIsReportedAsBlocked() {
		val result = AudioDiagnostics.analyze(ShortArray(16_000))
		assertEquals(CaptureError.SOURCE_CAPTURE_BLOCKED, result.error)
		assertEquals(100f, result.zeroSamplePercent, 0.001f)
	}

	@Test fun quietNonZeroCaptureIsReportedAsSilence() {
		val result = AudioDiagnostics.analyze(ShortArray(16_000) { 20 })
		assertEquals(CaptureError.CAPTURED_SILENCE, result.error)
	}

	@Test fun audibleCaptureProducesHealthMetrics() {
		val result = AudioDiagnostics.analyze(ShortArray(16_000) { if (it % 2 == 0) 2_000 else -2_000 })
		assertTrue(result.isValid)
		assertEquals(2_000f, result.rmsLevel, 0.1f)
		assertEquals(2_000f, result.peakAmplitude, 0.1f)
	}
}
