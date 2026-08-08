package com.jacksonkasi.cliplex.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PronunciationGuideTest {
	@Test
	fun devanagariWordIsShownAsSimpleLatinReadingAid() {
		assertEquals("bhai", latinPronunciation("भाई"))
	}

	@Test
	fun latinWordDoesNotRepeatItself() {
		assertNull(latinPronunciation("brother"))
	}
}
