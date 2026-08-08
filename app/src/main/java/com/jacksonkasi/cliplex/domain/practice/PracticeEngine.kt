package com.jacksonkasi.cliplex.domain.practice

import com.jacksonkasi.cliplex.common.latinPronunciation
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.data.local.SessionSegmentsCodec
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import java.util.Locale
import kotlin.math.max

object PracticeEngine {
	fun questionsFor(session: SessionEntity, limit: Int = 5): List<PracticeQuestion> {
		val pairs = SessionSegmentsCodec.decode(session.segmentsJson)
			.mapNotNull { segment ->
				val translation = segment.translatedText?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
				segment.takeIf { !translation.equals(segment.text.trim(), ignoreCase = true) }?.let { it to translation }
			}
		if (pairs.size < 2) return emptyList()

		val answers = pairs.map { it.second }.distinct()
		return pairs.take(limit).mapNotNull { (segment, answer) ->
			val distractors = answers.asSequence()
				.filterNot { it == answer }
				.sortedBy { stableOrder("${session.id}:${segment.startTimeMs}:$it") }
				.take(3)
				.toList()
			if (distractors.isEmpty()) return@mapNotNull null
			val options = (distractors + answer).sortedBy { stableOrder("${segment.text}:$it") }
			PracticeQuestion(
				id = "${session.id}:${segment.startTimeMs}",
				sessionId = session.id,
				prompt = "What does this mean?",
				sourceText = segment.text.trim(),
				options = options,
				correctAnswer = answer,
				explanation = "${session.targetLanguage.ifBlank { "Your language" }} meaning: $answer",
			)
		}
	}

	fun tutorReply(session: SessionEntity, question: String, motherTongue: String): String {
		val segments = SessionSegmentsCodec.decode(session.segmentsJson)
		val translated = segments.filter { !it.translatedText.isNullOrBlank() }
		if (translated.isEmpty()) return "This lesson does not have translated sentences yet. Re-analyze it first, then ask me again."

		val queryTokens = tokens(question)
		val best = translated.maxByOrNull { segment ->
			val searchable = tokens(segment.text + " " + segment.translatedText.orEmpty())
			queryTokens.count(searchable::contains)
		} ?: translated.first()
		val overlap = queryTokens.any(tokens(best.text + " " + best.translatedText.orEmpty())::contains)
		if (queryTokens.isNotEmpty() && !overlap) {
			return "I could not find that in this lesson. Try asking about one of its captured words or sentences."
		}
		return buildString {
			appendLine("$motherTongue explanation")
			appendLine(best.translatedText.orEmpty())
			appendLine()
			append("From the lesson: “${best.text.trim()}”")
		}
	}

	fun scorePronunciation(expected: String, heard: String): PronunciationResult {
		val expectedForms = listOfNotNull(normalize(expected), latinPronunciation(expected)?.let(::normalize)).filter(String::isNotBlank)
		val heardNormalized = normalize(heard)
		val heardWords = heardNormalized.split(' ').filter(String::isNotBlank)
		val bestScore = expectedForms.maxOfOrNull { expectedForm ->
			if (heardWords.contains(expectedForm) || heardNormalized == expectedForm) 100
			else similarity(expectedForm, heardNormalized)
		} ?: 0
		val feedback = when {
			bestScore >= 90 -> "Excellent match"
			bestScore >= 70 -> "Good — try once more for a cleaner match"
			bestScore >= 45 -> "Close — listen again and speak slowly"
			else -> "Not matched yet — listen, then repeat the word"
		}
		return PronunciationResult(expected, heard.trim(), bestScore, feedback)
	}

	private fun stableOrder(value: String): Int = value.hashCode() and Int.MAX_VALUE

	private fun tokens(value: String): Set<String> = normalize(value)
		.split(' ')
		.filter { it.length > 1 }
		.toSet()

	private fun normalize(value: String): String = value
		.lowercase(Locale.ROOT)
		.replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
		.trim()
		.replace(Regex("\\s+"), " ")

	private fun similarity(expected: String, actual: String): Int {
		if (expected.isBlank() || actual.isBlank()) return 0
		val distance = levenshtein(expected, actual)
		return (((max(expected.length, actual.length) - distance).coerceAtLeast(0) * 100f) /
			max(expected.length, actual.length)).toInt()
	}

	private fun levenshtein(left: String, right: String): Int {
		var previous = IntArray(right.length + 1) { it }
		for (leftIndex in left.indices) {
			val current = IntArray(right.length + 1)
			current[0] = leftIndex + 1
			for (rightIndex in right.indices) {
				val substitution = previous[rightIndex] + if (left[leftIndex] == right[rightIndex]) 0 else 1
				current[rightIndex + 1] = minOf(current[rightIndex] + 1, previous[rightIndex + 1] + 1, substitution)
			}
			previous = current
		}
		return previous[right.length]
	}
}
