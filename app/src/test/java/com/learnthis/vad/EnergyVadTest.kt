package com.learnthis.vad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyVadTest {
	@Test fun detectsAudibleSpeechRegion() {
		val vad = EnergyVad(energyThreshold = 500f)
		val audio = ShortArray(16_000) { if (it in 3_200 until 12_800) 2_000 else 0 }
		val detection = vad.process(audio, 0, audio.size)
		val segments = vad.finalize()
		assertTrue(detection.isSpeech)
		assertEquals(1, segments.size)
		assertTrue(segments.single().durationMs >= 500)
	}
}
