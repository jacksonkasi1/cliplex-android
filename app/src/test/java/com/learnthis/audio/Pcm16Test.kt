package com.learnthis.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Pcm16Test {
	@Test fun convertsLittleEndianBytes() {
		assertArrayEquals(shortArrayOf(1, -2, 32767), Pcm16.littleEndianBytesToShorts(byteArrayOf(1, 0, -2, -1, -1, 127)))
	}

	@Test fun normalizesSignedPcm() {
		val values = Pcm16.normalize(shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE))
		assertEquals(-1f, values[0], 0f); assertEquals(0f, values[1], 0f); assertEquals(0.9999695f, values[2], 0.000001f)
	}

	@Test fun downmixesStereoWithoutOverflow() {
		assertArrayEquals(shortArrayOf(0, 32767), Pcm16.stereoToMono(shortArrayOf(32767, -32768, 32767, 32767)))
	}

	@Test fun resamplesToExpectedLength() {
		assertEquals(16_000, Pcm16.resampleLinear(ShortArray(48_000), 48_000, 16_000).size)
	}

	@Test fun createsValidWavHeader() {
		val wav = Pcm16.wav(shortArrayOf(1, -1), 16_000)
		assertEquals("RIFF", String(wav, 0, 4)); assertEquals("WAVE", String(wav, 8, 4))
		assertEquals(4, ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int)
		assertEquals(48, wav.size)
	}

	@Test fun readsGeneratedWav() {
		val expected = shortArrayOf(-3, 4, 5)
		val decoded = Pcm16.readWav(Pcm16.wav(expected, 16_000))
		assertArrayEquals(expected, decoded.samples)
		assertEquals(16_000, decoded.sampleRate)
		assertEquals(1, decoded.channels)
	}
}
