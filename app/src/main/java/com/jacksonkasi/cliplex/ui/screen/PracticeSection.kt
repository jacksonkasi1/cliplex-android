package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.common.latinPronunciation
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.domain.model.SavedWord
import com.jacksonkasi.cliplex.domain.practice.PracticeEngine
import com.jacksonkasi.cliplex.domain.practice.PronunciationResult
import com.jacksonkasi.cliplex.domain.practice.TutorMessage
import com.jacksonkasi.cliplex.ui.components.ClipLexActionButton
import com.jacksonkasi.cliplex.ui.components.ClipLexButtonStyle
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.components.ClipLexProgressBar
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes

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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LexiMascot(modifier = Modifier.size(130.dp), mood = LexiMood.READY)
                    Text("Capture a lesson first", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                    Text(
                        "Practice is built from your own sentences, translations and saved words.",
                        color = ClipLexColors.InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
            return@Column
        }

        if (mode == PracticeMode.HOME && sessions.size > 1) {
            Text("Choose a lesson", style = MaterialTheme.typography.labelLarge, color = ClipLexColors.InkMuted)
            sessions.take(5).forEach { item ->
                ClipLexCard(
                    modifier = Modifier.fillMaxWidth().clickable { selectedSessionId = item.id },
                    containerColor = if (item.id == session.id) ClipLexColors.GreenSoft else ClipLexColors.Surface,
                    borderColor = if (item.id == session.id) ClipLexColors.Green else ClipLexColors.Border,
                    depth = if (item.id == session.id) 3.dp else 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.title.ifBlank { "Captured lesson" },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = ClipLexColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.id == session.id) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = ClipLexColors.Green)
                        }
                    }
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
    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = ClipLexColors.PurpleSoft,
        borderColor = ClipLexColors.Purple.copy(alpha = 0.22f),
        depth = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mode != PracticeMode.HOME) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to practice", tint = ClipLexColors.Purple)
                }
            } else {
                LexiMascot(modifier = Modifier.size(82.dp), mood = LexiMood.CELEBRATING)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when (mode) {
                        PracticeMode.HOME -> "Practice lab"
                        PracticeMode.QUIZ -> "Quick quiz"
                        PracticeMode.SPEAK -> "Speak & match"
                        PracticeMode.CHAT -> "Ask your tutor"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = ClipLexColors.Ink,
                )
                Text(
                    when (mode) {
                        PracticeMode.HOME -> "Your clips become personalised exercises"
                        PracticeMode.QUIZ -> "Choose the best translation"
                        PracticeMode.SPEAK -> "Listen, repeat and improve"
                        PracticeMode.CHAT -> "Grounded only in this lesson"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ClipLexColors.InkMuted,
                )
            }
            if (mode == PracticeMode.HOME) {
                ClipLexIconBadge(
                    icon = Icons.Default.Psychology,
                    contentDescription = null,
                    background = Color.White.copy(alpha = 0.72f),
                    contentColor = ClipLexColors.Purple,
                )
            }
        }
    }
}

@Composable
private fun PracticeHome(
    session: SessionEntity,
    questionCount: Int,
    wordCount: Int,
    onMode: (PracticeMode) -> Unit,
) {
    Text(
        "Continue with ${session.title.ifBlank { "your latest lesson" }}",
        style = MaterialTheme.typography.titleMedium,
        color = ClipLexColors.Ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    PracticeAction(
        icon = Icons.Default.Quiz,
        title = "Quick Quiz",
        subtitle = "$questionCount questions from this lesson",
        color = ClipLexColors.Green,
        enabled = questionCount > 0,
    ) { onMode(PracticeMode.QUIZ) }
    PracticeAction(
        icon = Icons.Default.Mic,
        title = "Speak & Match",
        subtitle = "$wordCount saved words ready to pronounce",
        color = ClipLexColors.Coral,
        enabled = wordCount > 0,
    ) { onMode(PracticeMode.SPEAK) }
    PracticeAction(
        icon = Icons.Default.Chat,
        title = "Ask your tutor",
        subtitle = "Explain a captured word or sentence privately",
        color = ClipLexColors.Blue,
        enabled = true,
    ) { onMode(PracticeMode.CHAT) }
}

@Composable
private fun PracticeAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ClipLexCard(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        borderColor = if (enabled) color.copy(alpha = 0.24f) else ClipLexColors.Border,
        depth = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            ClipLexIconBadge(
                icon = icon,
                contentDescription = null,
                background = color.copy(alpha = 0.12f),
                contentColor = if (enabled) color else ClipLexColors.InkFaint,
                size = 50.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) ClipLexColors.Ink else ClipLexColors.InkMuted)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
            }
            Surface(shape = CircleShape, color = if (enabled) color else ClipLexColors.SurfaceMuted, contentColor = Color.White) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start $title", modifier = Modifier.padding(9.dp).size(20.dp))
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
        ClipLexCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = ClipLexColors.GreenSoft,
            borderColor = ClipLexColors.Green.copy(alpha = 0.3f),
            depth = 4.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LexiMascot(modifier = Modifier.size(150.dp), mood = LexiMood.CELEBRATING)
                Text("$score / ${questions.size}", style = MaterialTheme.typography.headlineLarge, color = ClipLexColors.GreenDark)
                Text("Practice complete!", style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
                ClipLexProgressBar(score / questions.size.toFloat(), modifier = Modifier.padding(top = 14.dp), height = 12.dp)
                ClipLexActionButton(
                    text = "Practice again",
                    onClick = { index = 0; score = 0; selected = null },
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                )
            }
        }
        return
    }

    val question = questions[index]
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Question ${index + 1} of ${questions.size}", style = MaterialTheme.typography.labelLarge, color = ClipLexColors.InkMuted)
        Text("$score correct", style = MaterialTheme.typography.labelLarge, color = ClipLexColors.GreenDark)
    }
    ClipLexProgressBar((index + 1f) / questions.size, height = 10.dp)

    ClipLexCard(Modifier.fillMaxWidth(), depth = 3.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(question.prompt.uppercase(), style = MaterialTheme.typography.labelSmall, color = ClipLexColors.Purple)
            Text(question.sourceText, style = MaterialTheme.typography.headlineSmall, color = ClipLexColors.Ink)
        }
    }

    question.options.forEach { option ->
        val answered = selected != null
        val correct = option == question.correctAnswer
        val chosen = option == selected
        val background = when {
            answered && correct -> ClipLexColors.GreenSoft
            answered && chosen -> ClipLexColors.CoralSoft
            else -> ClipLexColors.Surface
        }
        val border = when {
            answered && correct -> ClipLexColors.Green
            answered && chosen -> ClipLexColors.Coral
            else -> ClipLexColors.Border
        }
        ClipLexCard(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !answered) {
                selected = option
                if (correct) score++
            },
            containerColor = background,
            borderColor = border,
            depth = if (chosen || answered && correct) 3.dp else 2.dp,
        ) {
            Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(option, modifier = Modifier.weight(1f), color = ClipLexColors.Ink, style = MaterialTheme.typography.bodyLarge)
                if (answered && correct) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Correct answer", tint = ClipLexColors.Green)
                } else if (answered && chosen) {
                    Icon(Icons.Default.Close, contentDescription = "Incorrect answer", tint = ClipLexColors.Coral)
                }
            }
        }
    }

    selected?.let {
        ClipLexCard(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            containerColor = if (it == question.correctAnswer) ClipLexColors.GreenSoft else ClipLexColors.CoralSoft,
            borderColor = if (it == question.correctAnswer) ClipLexColors.Green.copy(alpha = 0.3f) else ClipLexColors.Coral.copy(alpha = 0.3f),
            depth = 2.dp,
        ) {
            Text(
                if (it == question.correctAnswer) "Correct! ${question.explanation}" else "The answer is ${question.correctAnswer}",
                color = if (it == question.correctAnswer) ClipLexColors.GreenDark else ClipLexColors.CoralDark,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(15.dp),
            )
        }
        ClipLexActionButton(
            text = if (index == questions.lastIndex) "See result" else "Next question",
            onClick = { index++; selected = null },
            modifier = Modifier.fillMaxWidth(),
        )
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

    if (words.isEmpty()) {
        Text("Save at least one word before starting pronunciation practice.", color = ClipLexColors.InkMuted)
        return
    }

    val item = words[index % words.size]
    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = ClipLexColors.BlueSoft,
        borderColor = ClipLexColors.Blue.copy(alpha = 0.24f),
        depth = 4.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LexiMascot(
                modifier = Modifier.size(135.dp),
                mood = if (listening) LexiMood.LISTENING else LexiMood.READY,
            )
            Text("LISTEN · THEN SPEAK", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.Coral)
            Text(item.word, style = MaterialTheme.typography.headlineLarge, color = ClipLexColors.Ink, modifier = Modifier.padding(top = 8.dp))
            latinPronunciation(item.word)?.let {
                Text("Say it: $it", color = ClipLexColors.GreenDark, style = MaterialTheme.typography.titleSmall)
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ClipLexIconBadge(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Listen to ${item.word}",
                    background = Color.White,
                    contentColor = ClipLexColors.Green,
                    size = 60.dp,
                    onClick = { onSpeak(item.word) },
                )
                ClipLexIconBadge(
                    icon = Icons.Default.Mic,
                    contentDescription = "Speak ${item.word}",
                    background = ClipLexColors.Coral,
                    contentColor = Color.White,
                    size = 60.dp,
                    onClick = if (listening) null else {
                        {
                            listening = true
                            onRecognize(item.word, item.sourceLanguage.ifBlank { fallbackLanguage }) { heard ->
                                listening = false
                                result = heard?.let { PracticeEngine.scorePronunciation(item.word, it) }
                            }
                        }
                    },
                )
            }
            Text(
                if (listening) "Listening…" else "Tap the microphone and repeat",
                color = ClipLexColors.InkMuted,
                modifier = Modifier.padding(top = 11.dp),
            )
        }
    }

    result?.let { match ->
        ClipLexCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (match.score >= 70) ClipLexColors.GreenSoft else ClipLexColors.WarmSoft,
            borderColor = if (match.score >= 70) ClipLexColors.Green.copy(alpha = 0.3f) else ClipLexColors.Warm.copy(alpha = 0.3f),
            depth = 3.dp,
        ) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${match.score}% match", style = MaterialTheme.typography.titleLarge, color = if (match.score >= 70) ClipLexColors.GreenDark else ClipLexColors.WarmDark)
                ClipLexProgressBar(match.score / 100f, progressColor = if (match.score >= 70) ClipLexColors.Green else ClipLexColors.Warm, height = 10.dp)
                Text(match.feedback, color = ClipLexColors.Ink)
                Text("Heard: ${match.heard}", color = ClipLexColors.InkMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (words.size > 1) {
        ClipLexActionButton(
            text = "Next word",
            style = ClipLexButtonStyle.GHOST,
            onClick = { index++; result = null },
            modifier = Modifier.fillMaxWidth(),
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
    val messages = remember(session.id) {
        mutableStateListOf(
            TutorMessage(
                false,
                "Ask me about a word or sentence from this lesson. I will stay grounded in what was captured.",
            ),
        )
    }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }

    ClipLexCard(Modifier.fillMaxWidth(), depth = 2.dp) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            messages.takeLast(6).forEach { message ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
                ) {
                    Text(
                        message.text,
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .background(
                                if (message.fromUser) ClipLexColors.BlueSoft else ClipLexColors.GreenSoft,
                                ClipLexShapes.Control,
                            )
                            .padding(13.dp),
                        color = ClipLexColors.Ink,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.fillMaxWidth(),
        shape = ClipLexShapes.Control,
        placeholder = { Text("Explain a sentence…") },
        trailingIcon = {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Ask tutor",
                tint = if (input.isNotBlank() && !thinking) ClipLexColors.Blue else ClipLexColors.InkFaint,
                modifier = Modifier.clickable(enabled = input.isNotBlank() && !thinking) {
                    val question = input.trim()
                    messages += TutorMessage(true, question)
                    input = ""
                    thinking = true
                    onAskTutor(session, question, motherTongue) { smartAnswer ->
                        messages += TutorMessage(false, smartAnswer ?: PracticeEngine.tutorReply(session, question, motherTongue))
                        thinking = false
                    }
                },
            )
        },
    )

    if (thinking) Text("Your on-device tutor is thinking…", color = ClipLexColors.Blue, style = MaterialTheme.typography.bodySmall)
    Text(
        if (smartTutorInstalled) "Gemma 3 · on-device · grounded in this lesson" else "Offline grounded mode · smart model optional",
        style = MaterialTheme.typography.labelSmall,
        color = ClipLexColors.InkMuted,
    )
}
