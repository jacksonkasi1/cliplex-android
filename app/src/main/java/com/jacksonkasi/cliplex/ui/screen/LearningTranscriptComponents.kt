package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubtitleSurface(
    segment: TranscriptionSegment?,
    positionMs: Long,
    mode: LearningDisplayMode,
    onWordTap: (String) -> Unit,
    compactVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    if (segment == null) return
    val activeIndex = activeWordIndex(segment, positionMs)

    if (compactVideo) {
        Column(
            modifier = modifier
                .heightIn(max = 124.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                    ),
                )
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CaptionContent(
                segment = segment,
                mode = mode,
                activeIndex = activeIndex,
                onWordTap = onWordTap,
                compactVideo = true,
            )
        }
    } else {
        val scrollState = rememberScrollState()
        Surface(
            modifier = modifier.heightIn(max = 148.dp),
            shape = ClipLexShapes.Control,
            color = Color.White.copy(alpha = 0.07f),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CaptionContent(
                    segment = segment,
                    mode = mode,
                    activeIndex = activeIndex,
                    onWordTap = onWordTap,
                    compactVideo = false,
                )
            }
        }
    }
}

@Composable
private fun CaptionContent(
    segment: TranscriptionSegment,
    mode: LearningDisplayMode,
    activeIndex: Int?,
    onWordTap: (String) -> Unit,
    compactVideo: Boolean,
) {
    when (mode) {
        LearningDisplayMode.WORD_BY_WORD -> CaptionWordLine(
            text = segment.text,
            activeWordIndex = activeIndex,
            onWordTap = onWordTap,
            compactWindow = compactVideo,
        )

        LearningDisplayMode.SENTENCE -> {
            CaptionWordLine(
                text = segment.text,
                activeWordIndex = activeIndex,
                onWordTap = onWordTap,
                compactWindow = compactVideo,
            )
            TranslationLine(
                translation = segment.translatedText,
                missingText = "Preparing translation...",
                maxLines = if (compactVideo) 2 else Int.MAX_VALUE,
            )
        }

        LearningDisplayMode.TAMIL_VIEW -> {
            Text(
                text = segment.translatedText ?: "Preparing translation...",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (segment.translatedText == null) FontWeight.Normal else FontWeight.SemiBold,
                fontStyle = if (segment.translatedText == null) FontStyle.Italic else FontStyle.Normal,
                color = Color.White,
                maxLines = if (compactVideo) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = compactCaptionText(segment.text, activeIndex, compactVideo),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.70f),
                maxLines = if (compactVideo) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaptionWordLine(
    text: String,
    activeWordIndex: Int?,
    onWordTap: (String) -> Unit,
    compactWindow: Boolean,
) {
    val models = remember(text) { captionTokenModels(text) }
    val visibleModels = remember(models, activeWordIndex, compactWindow) {
        if (compactWindow) captionWindow(models, activeWordIndex, 14) else models
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        visibleModels.forEach { token ->
            val isActive = token.wordIndex != null && token.wordIndex == activeWordIndex
            Text(
                text = token.raw,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isActive) ClipLexColors.Ink else Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isActive) ClipLexColors.Warm else Color.Transparent)
                    .clickable(enabled = token.word != null) { token.word?.let(onWordTap) }
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
internal fun TranslationLine(translation: String?, missingText: String, maxLines: Int) {
    Text(
        text = translation ?: missingText,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = if (translation == null) FontStyle.Italic else FontStyle.Normal,
        color = Color.White.copy(alpha = 0.76f),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun TranscriptSegmentCard(
    index: Int,
    segment: TranscriptionSegment,
    isCurrent: Boolean,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(segment.text, segment.translatedText) { mutableStateOf(false) }
    val sourceScroll = rememberScrollState()
    val translationScroll = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ClipLexShapes.Card,
        color = if (isCurrent) ClipLexColors.AccentWash else ClipLexColors.Surface,
        contentColor = ClipLexColors.Ink,
        border = BorderStroke(
            1.dp,
            if (isCurrent) ClipLexColors.Accent.copy(alpha = 0.42f) else ClipLexColors.Border,
        ),
        shadowElevation = if (isCurrent) 2.dp else 0.dp,
    ) {
        Row {
            Box(
                Modifier
                    .width(4.dp)
                    .heightIn(min = 126.dp)
                    .background(if (isCurrent) ClipLexColors.Accent else Color.Transparent),
            )
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isCurrent) "Playing now" else formatDuration(segment.startTimeMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrent) ClipLexColors.AccentStrong else ClipLexColors.InkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ClipLexColors.InkFaint,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse transcript" else "Expand transcript",
                        tint = ClipLexColors.InkMuted,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(4.dp),
                    )
                }

                if (expanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                            .verticalScroll(sourceScroll),
                    ) {
                        TappableTranscriptText(segment.text, onWordTap)
                    }
                } else {
                    Text(
                        segment.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ClipLexColors.Ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = true },
                    )
                }

                segment.translatedText?.takeIf(String::isNotBlank)?.let { translation ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .heightIn(min = 38.dp)
                                .background(ClipLexColors.Accent.copy(alpha = 0.45f)),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Translation", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.AccentStrong)
                            if (expanded) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .verticalScroll(translationScroll),
                                ) {
                                    Text(translation, style = MaterialTheme.typography.bodyMedium, color = ClipLexColors.InkSoft)
                                }
                            } else {
                                Text(
                                    translation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ClipLexColors.InkSoft,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (!expanded && (segment.text.length > 150 || (segment.translatedText?.length ?: 0) > 100)) {
                    Text(
                        "Show full text",
                        style = MaterialTheme.typography.labelLarge,
                        color = ClipLexColors.Accent,
                        modifier = Modifier.clickable { expanded = true }.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TappableTranscriptText(text: String, onWordTap: (String) -> Unit) {
    val models = remember(text) { captionTokenModels(text) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        models.forEach { token ->
            Text(
                text = token.raw,
                style = MaterialTheme.typography.bodyLarge,
                color = ClipLexColors.Ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .clickable(enabled = token.word != null) { token.word?.let(onWordTap) }
                    .padding(horizontal = 2.dp, vertical = 1.dp),
            )
        }
    }
}

internal data class CaptionToken(
    val raw: String,
    val word: String?,
    val wordIndex: Int?,
)

internal fun captionTokenModels(text: String): List<CaptionToken> {
    var nextWordIndex = 0
    return text.trim().split(Regex("\\s+")).filter(String::isNotBlank).map { raw ->
        val word = extractWord(raw)
        CaptionToken(
            raw = raw,
            word = word,
            wordIndex = if (word == null) null else nextWordIndex++,
        )
    }
}

internal fun captionWindow(tokens: List<CaptionToken>, activeWordIndex: Int?, maxTokens: Int): List<CaptionToken> {
    if (tokens.size <= maxTokens) return tokens
    val center = activeWordIndex
        ?.let { active -> tokens.indexOfFirst { it.wordIndex == active }.takeIf { it >= 0 } }
        ?: 0
    val start = (center - maxTokens / 2).coerceIn(0, tokens.size - maxTokens)
    return tokens.subList(start, start + maxTokens)
}

internal fun compactCaptionText(text: String, activeWordIndex: Int?, compact: Boolean): String {
    if (!compact) return text
    return captionWindow(captionTokenModels(text), activeWordIndex, 14).joinToString(" ") { it.raw }
}
