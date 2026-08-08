package com.jacksonkasi.cliplex.ui.screen

import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import kotlin.math.floor

internal fun segmentAt(segments: List<TranscriptionSegment>, positionMs: Long): TranscriptionSegment? {
    if (segments.isEmpty()) return null
    return segments.lastOrNull { it.startTimeMs <= positionMs } ?: segments.first()
}

internal fun activeWordIndex(segment: TranscriptionSegment, positionMs: Long): Int? {
    if (segment.words.isNotEmpty()) {
        return segment.words.indexOfLast { word ->
            word.startTimeMs?.let { it <= positionMs } == true
        }.takeIf { it >= 0 }
    }
    val wordCount = segment.text
        .trim()
        .split(Regex("\\s+"))
        .count { extractWord(it) != null }
    if (wordCount == 0) return null
    val segmentDuration = (segment.endTimeMs - segment.startTimeMs).coerceAtLeast(1L)
    val progress = ((positionMs - segment.startTimeMs).toFloat() / segmentDuration).coerceIn(0f, 0.9999f)
    return floor(progress * wordCount).toInt().coerceIn(0, wordCount - 1)
}

internal val wordRegex = Regex("[\\p{L}\\p{M}]+(?:['’\\-][\\p{L}\\p{M}]+)*")

internal fun extractWord(token: String): String? = wordRegex.find(token)?.value

internal fun languageDisplayName(tag: String): String {
    if (tag.isBlank()) return "English"
    return LearningLanguage.entries.firstOrNull { it.code == tag || it.recognitionTag.equals(tag, ignoreCase = true) }
        ?.displayName
        ?: AppLanguage.fromTag(tag)?.displayName
        ?: tag
}

internal fun readableProcessingStage(stage: String): String = when (stage.uppercase()) {
    "PREPARING" -> "Preparing your lesson…"
    "TRANSCRIBING" -> "Preparing transcript…"
    "TRANSLATING" -> "Preparing translation…"
    "CAPTURED" -> "Capture ready"
    "ERROR" -> "Lesson processing stopped"
    else -> stage.lowercase().replaceFirstChar(Char::titlecase)
}

internal fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
