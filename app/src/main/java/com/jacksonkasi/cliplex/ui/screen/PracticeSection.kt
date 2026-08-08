package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.common.latinPronunciation
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.domain.model.SavedWord
import com.jacksonkasi.cliplex.domain.practice.PracticeEngine
import com.jacksonkasi.cliplex.domain.practice.PronunciationResult
import com.jacksonkasi.cliplex.domain.practice.TutorMessage
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors

private enum class PracticeMode { HOME, QUIZ, SPEAK, CHAT }

@Composable
fun PracticeSection(
	sessions: List<SessionEntity>,
	savedWords: List<SavedWord>,
	motherTongue: String,
	onSpeak: (String) -> Unit,
	onRecognize: (word: String, languageTag: String, onResult: (String?) -> Unit) -> Unit,
	onAskTutor: (SessionEntity, String, String, (String?) -> Unit) -> Unit,
	smartTutorInstalled: Boolean,
) {
	var selectedSessionId by remember(sessions) { mutableStateOf(sessions.firstOrNull()?.id) }
	val session = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()
	var mode by remember { mutableStateOf(PracticeMode.HOME) }

	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		PracticeHeader(mode = mode, onBack = { mode = PracticeMode.HOME })
		if (session == null) {
			ClipLexCard(Modifier.fillMaxWidth()) {
				Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
					Text("Capture a lesson first", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
					Text("Practice uses your real sentences, translations, and saved words.", color = ClipLexColors.InkMuted)
				}
			}
			return@Column
		}

		if (mode == PracticeMode.HOME && sessions.size > 1) {
			Text("Practice a lesson", style = MaterialTheme.typography.labelLarge, color = ClipLexColors.InkMuted)
			sessions.take(4).forEach { item ->
				Surface(
					shape = RoundedCornerShape(14.dp),
					color = if (item.id == session.id) ClipLexColors.GreenSoft else ClipLexColors.Surface,
					modifier = Modifier.fillMaxWidth().clickable { selectedSessionId = item.id },
				) {
					Text(item.title, modifier = Modifier.padding(13.dp), color = ClipLexColors.Ink, maxLines = 1)
				}
			}
		}

		when (mode) {
			PracticeMode.HOME -> PracticeHome(
				session = session,
				questionCount = PracticeEngine.questionsFor(session).size,
				wordCount = savedWords.size,
				onMode = { mode = it },
			)
			PracticeMode.QUIZ -> QuizPractice(session)
			PracticeMode.SPEAK -> SpeakPractice(savedWords, session.sourceLanguage, onSpeak, onRecognize)
			PracticeMode.CHAT -> TutorChat(session, motherTongue, smartTutorInstalled, onAskTutor)
		}
	}
}

@Composable
private fun PracticeHeader(mode: PracticeMode, onBack: () -> Unit) {
	ClipLexCard(Modifier.fillMaxWidth(), containerColor = ClipLexColors.BlueSoft) {
		Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
			if (mode != PracticeMode.HOME) {
				Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to practice", tint = ClipLexColors.Blue, modifier = Modifier.size(28.dp).clickable(onClick = onBack))
				Spacer(Modifier.width(12.dp))
			}
			Icon(Icons.Default.Psychology, contentDescription = null, tint = ClipLexColors.Blue, modifier = Modifier.size(30.dp))
			Spacer(Modifier.width(10.dp))
			Column {
				Text("AI Practice", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ClipLexColors.Ink)
				Text("Private · grounded in your lessons", style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
			}
		}
	}
}

@Composable
private fun PracticeHome(session: SessionEntity, questionCount: Int, wordCount: Int, onMode: (PracticeMode) -> Unit) {
	Text("Continue with ${session.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ClipLexColors.Ink)
	PracticeAction(Icons.Default.Quiz, "Quick Quiz", "$questionCount questions from this session", ClipLexColors.Green, questionCount > 0) { onMode(PracticeMode.QUIZ) }
	PracticeAction(Icons.Default.Mic, "Speak & Match", "$wordCount saved words to pronounce", ClipLexColors.Coral, wordCount > 0) { onMode(PracticeMode.SPEAK) }
	PracticeAction(Icons.Default.Chat, "Ask your tutor", "Ask about a captured sentence in your language", ClipLexColors.Blue, true) { onMode(PracticeMode.CHAT) }
}

@Composable
private fun PracticeAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
	ClipLexCard(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
		Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
			Surface(shape = CircleShape, color = color.copy(alpha = 0.13f)) {
				Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(11.dp).size(24.dp))
			}
			Spacer(Modifier.width(13.dp))
			Column(Modifier.weight(1f)) {
				Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (enabled) ClipLexColors.Ink else ClipLexColors.InkMuted)
				Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
			}
		}
	}
}

@Composable
private fun QuizPractice(session: SessionEntity) {
	val questions = remember(session.id, session.segmentsJson) { PracticeEngine.questionsFor(session) }
	var index by remember { mutableIntStateOf(0) }
	var score by remember { mutableIntStateOf(0) }
	var selected by remember { mutableStateOf<String?>(null) }
	if (questions.isEmpty()) {
		Text("This lesson needs at least two translated sentences before a quiz can be created.", color = ClipLexColors.InkMuted)
		return
	}
	if (index >= questions.size) {
		ClipLexCard(Modifier.fillMaxWidth(), containerColor = ClipLexColors.GreenSoft) {
			Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
				Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ClipLexColors.Green, modifier = Modifier.size(46.dp))
				Text("$score / ${questions.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ClipLexColors.GreenDark)
				Text("Practice complete", color = ClipLexColors.InkMuted)
				Text("Try again", modifier = Modifier.padding(top = 14.dp).clickable { index = 0; score = 0; selected = null }, color = ClipLexColors.Blue, fontWeight = FontWeight.Bold)
			}
		}
		return
	}

	val question = questions[index]
	Text("Question ${index + 1} of ${questions.size}  ·  Score $score", style = MaterialTheme.typography.labelLarge, color = ClipLexColors.InkMuted)
	ClipLexCard(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(question.prompt, color = ClipLexColors.InkMuted)
			Text(question.sourceText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ClipLexColors.Ink)
		}
	}
	question.options.forEach { option ->
		val answered = selected != null
		val correct = option == question.correctAnswer
		val chosen = option == selected
		val background = when {
			answered && correct -> ClipLexColors.GreenSoft
			answered && chosen -> Color(0xFFFFE8E5)
			else -> ClipLexColors.Surface
		}
		Surface(
			shape = RoundedCornerShape(15.dp),
			color = background,
			border = androidx.compose.foundation.BorderStroke(1.dp, if (chosen || answered && correct) ClipLexColors.Green else ClipLexColors.Border),
			modifier = Modifier.fillMaxWidth().clickable(enabled = !answered) {
				selected = option
				if (correct) score++
			},
		) { Text(option, modifier = Modifier.padding(15.dp), color = ClipLexColors.Ink) }
	}
	selected?.let {
		Text(if (it == question.correctAnswer) "Correct · ${question.explanation}" else "Answer: ${question.correctAnswer}", color = if (it == question.correctAnswer) ClipLexColors.Green else ClipLexColors.Coral)
		Surface(shape = RoundedCornerShape(15.dp), color = ClipLexColors.Green, modifier = Modifier.fillMaxWidth().clickable { index++; selected = null }) {
			Text(if (index == questions.lastIndex) "See result" else "Next question", modifier = Modifier.padding(15.dp), color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
		}
	}
}

@Composable
private fun SpeakPractice(
	words: List<SavedWord>,
	fallbackLanguage: String,
	onSpeak: (String) -> Unit,
	onRecognize: (String, String, (String?) -> Unit) -> Unit,
) {
	var index by remember { mutableIntStateOf(0) }
	var result by remember { mutableStateOf<PronunciationResult?>(null) }
	var listening by remember { mutableStateOf(false) }
	if (words.isEmpty()) return
	val item = words[index % words.size]
	ClipLexCard(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
			Text("LISTEN · THEN SPEAK", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.Coral)
			Text(item.word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ClipLexColors.Ink, modifier = Modifier.padding(top = 8.dp))
			latinPronunciation(item.word)?.let { Text("Say it: $it", color = ClipLexColors.GreenDark) }
			Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
				Surface(shape = CircleShape, color = ClipLexColors.GreenSoft, modifier = Modifier.size(58.dp).clickable { onSpeak(item.word) }) {
					Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Listen", tint = ClipLexColors.Green, modifier = Modifier.padding(15.dp))
				}
				Surface(shape = CircleShape, color = ClipLexColors.Coral, modifier = Modifier.size(58.dp).clickable(enabled = !listening) {
					listening = true
					onRecognize(item.word, item.sourceLanguage.ifBlank { fallbackLanguage }) { heard ->
						listening = false
						result = heard?.let { PracticeEngine.scorePronunciation(item.word, it) }
					}
				}) {
					Icon(Icons.Default.Mic, contentDescription = "Speak now", tint = Color.White, modifier = Modifier.padding(15.dp))
				}
			}
			Text(if (listening) "Listening…" else "Tap the microphone and repeat", color = ClipLexColors.InkMuted, modifier = Modifier.padding(top = 10.dp))
		}
	}
	result?.let { match ->
		ClipLexCard(Modifier.fillMaxWidth(), containerColor = if (match.score >= 70) ClipLexColors.GreenSoft else Color(0xFFFFF4F2)) {
			Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
				Text("${match.score}% match", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (match.score >= 70) ClipLexColors.GreenDark else ClipLexColors.Coral)
				Text(match.feedback, color = ClipLexColors.Ink)
				Text("Heard: ${match.heard}", color = ClipLexColors.InkMuted)
			}
		}
	}
	if (words.size > 1) {
		Text(
			"Next word",
			modifier = Modifier.fillMaxWidth().clickable { index++; result = null }.padding(12.dp),
			color = ClipLexColors.Blue,
			fontWeight = FontWeight.Bold,
			textAlign = androidx.compose.ui.text.style.TextAlign.Center,
		)
	}
}

@Composable
private fun TutorChat(
	session: SessionEntity,
	motherTongue: String,
	smartTutorInstalled: Boolean,
	onAskTutor: (SessionEntity, String, String, (String?) -> Unit) -> Unit,
) {
	val messages = remember(session.id) { mutableStateListOf(TutorMessage(false, "Ask me about a word or sentence from this lesson. I will stay grounded in what was captured.")) }
	var input by remember { mutableStateOf("") }
	var thinking by remember { mutableStateOf(false) }
	messages.forEach { message ->
		Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
			Text(
				message.text,
				modifier = Modifier.fillMaxWidth(0.88f).background(if (message.fromUser) ClipLexColors.BlueSoft else ClipLexColors.GreenSoft, RoundedCornerShape(16.dp)).padding(13.dp),
				color = ClipLexColors.Ink,
			)
		}
	}
	OutlinedTextField(
		value = input,
		onValueChange = { input = it },
		modifier = Modifier.fillMaxWidth(),
		placeholder = { Text("Explain a sentence…") },
		trailingIcon = {
			Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask", tint = ClipLexColors.Blue, modifier = Modifier.clickable(enabled = input.isNotBlank() && !thinking) {
				val question = input.trim()
				messages += TutorMessage(true, question)
				input = ""
				thinking = true
				onAskTutor(session, question, motherTongue) { smartAnswer ->
					messages += TutorMessage(false, smartAnswer ?: PracticeEngine.tutorReply(session, question, motherTongue))
					thinking = false
				}
			})
		},
	)
	if (thinking) Text("Gemma is thinking on your phone…", color = ClipLexColors.Blue)
	Text(
		if (smartTutorInstalled) "Gemma 3 · on-device · grounded in this lesson" else "Offline grounded mode · smart model optional",
		style = MaterialTheme.typography.labelSmall,
		color = ClipLexColors.InkMuted,
	)
}
