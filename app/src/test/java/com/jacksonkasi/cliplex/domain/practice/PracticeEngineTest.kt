package com.jacksonkasi.cliplex.domain.practice

import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.data.local.SessionSegmentsCodec
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PracticeEngineTest {
	private val session = SessionEntity(
		id = 7,
		targetLanguage = "ta",
		segmentsJson = SessionSegmentsCodec.encode(listOf(
			TranscriptionSegment("hello", 0, 1_000, "en", translatedText = "வணக்கம்"),
			TranscriptionSegment("brother", 1_000, 2_000, "en", translatedText = "சகோதரன்"),
			TranscriptionSegment("thank you", 2_000, 3_000, "en", translatedText = "நன்றி"),
		)),
	)

	@Test
	fun quizContainsCorrectMeaningAndDistractors() {
		val question = PracticeEngine.questionsFor(session).first()
		assertTrue(question.options.contains(question.correctAnswer))
		assertTrue(question.options.size >= 2)
	}

	@Test
	fun pronunciationSupportsRomanizedIndicWord() {
		assertEquals(100, PracticeEngine.scorePronunciation("भाई", "bhai").score)
	}

	@Test
	fun tutorIsGroundedInCapturedTranslation() {
		assertTrue(PracticeEngine.tutorReply(session, "explain brother", "Tamil").contains("சகோதரன்"))
	}
}
