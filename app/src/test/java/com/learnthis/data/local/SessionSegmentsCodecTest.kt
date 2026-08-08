package com.learnthis.data.local

import com.learnthis.domain.model.TranscriptionSegment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class SessionSegmentsCodecTest {
	@Test fun roundTrip_preservesTimedSourceAndTranslation() {
		val expected = listOf(
			TranscriptionSegment("Good morning", 120, 1_480, "en", translatedText = "காலை வணக்கம்"),
			TranscriptionSegment("everyone", 1_480, 2_050, "en", null),
		)

		assertEquals(expected, SessionSegmentsCodec.decode(SessionSegmentsCodec.encode(expected)))
	}

	@Test fun malformedJson_degradesToEmptyLesson() {
		assertEquals(emptyList<TranscriptionSegment>(), SessionSegmentsCodec.decode("not-json"))
	}
}
