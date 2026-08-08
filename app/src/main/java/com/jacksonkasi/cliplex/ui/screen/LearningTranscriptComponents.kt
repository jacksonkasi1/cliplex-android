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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
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
    val scrollState = rememberScrollState()
    val maxHeight = if (compactVideo) 112.dp else 148.dp

    Surface(
        modifier = modifier.heightIn(max = maxHeight),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = if (compactVideo) 0.78f else 0.38f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (compactVideo) {
                        Modifier
                    } else {
                        Modifier.verticalScroll(scrollState)
                    },
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        missingText = "Translation is being prepared…",
                        maxLines = if (compactVideo) 2 else Int.MAX_VALUE,
                    )
                }

                LearningDisplayMode.TAMIL_VIEW -> {
                    Text(
                        text = segment.translatedText ?: "Translation is being prepared…",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (segment.translatedText == null) FontWeight.Normal else FontWeight.Bold,
                        fontStyle = if (segment.translatedText == null) FontStyle.Italic else FontStyle.Normal,
                        color = Color.White,
                        maxLines = if (compactVideo) 2 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = compactCaptionText(segment.text, activeIndex, compactVideo),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = if (compactVideo) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
        if (compactWindow) captionWindow(models, activeWordIndex, 16) else models
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
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isActive) ClipLexColors.Ink else Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
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
        color = Color.White.copy(alpha = 0.78f),
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

    ClipLexCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = if (isCurrent) ClipLexColors.GreenWash else ClipLexColors.Surface,
        borderColor = if (isCurrent) ClipLexColors.Green.copy(alpha = 0.38f) else ClipLexColors.Border,
        depth = if (isCurrent) 3.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (isCurrent) ClipLexColors.Green else ClipLexColors.SurfaceMuted,
                    contentColor = if (isCurrent) Color.White else ClipLexColors.InkMuted,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    if (isCurrent) "NOW PLAYING" else formatDuration(segment.startTimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) ClipLexColors.GreenDark else ClipLexColors.InkMuted,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse transcript" else "Expand transcript",
                    tint = ClipLexColors.InkMuted,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(5.dp),
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClipLexColors.GreenSoft, ClipLexShapes.Small)
                        .padding(12.dp),
                ) {
                    Text("TRANSLATION", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.GreenDark)
                    if (expanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .padding(top = 5.dp)
                                .verticalScroll(translationScroll),
                        ) {
                            Text(translation, style = MaterialTheme.typography.bodyMedium, color = ClipLexColors.GreenDark)
                        }
                    } else {
                        Text(
                            translation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ClipLexColors.GreenDark,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }

            if (!expanded && (segment.text.length > 150 || (segment.translatedText?.length ?: 0) > 100)) {
                Text(
                    "Read full transcript",
                    style = MaterialTheme.typography.labelLarge,
                    color = ClipLexColors.Blue,
                    modifier = Modifier.clickable { expanded = true }.padding(vertical = 3.dp),
                )
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
    return captionWindow(captionTokenModels(text), activeWordIndex, 16).joinToString(" ") { it.raw }
}
