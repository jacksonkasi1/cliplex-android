package com.jacksonkasi.cliplex.domain.practice

data class PracticeQuestion(
	val id: String,
	val sessionId: Long,
	val prompt: String,
	val sourceText: String,
	val options: List<String>,
	val correctAnswer: String,
	val explanation: String,
)

data class PronunciationResult(
	val expected: String,
	val heard: String,
	val score: Int,
	val feedback: String,
)

data class TutorMessage(val fromUser: Boolean, val text: String)
