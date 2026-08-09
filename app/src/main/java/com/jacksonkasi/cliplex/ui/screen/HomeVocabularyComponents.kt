package com.jacksonkasi.cliplex.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.common.languageForWord
import com.jacksonkasi.cliplex.common.latinPronunciation
import com.jacksonkasi.cliplex.common.validWordTranslation
import com.jacksonkasi.cliplex.domain.model.SavedWord
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import com.jacksonkasi.cliplex.ui.components.ClipLexActionButton
import com.jacksonkasi.cliplex.ui.components.ClipLexButtonStyle
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes

@Composable
internal fun SavedWordsSection(
    words: List<SavedWord>,
    savedWordNames: Set<String>,
    meaningLanguage: String,
    meaningLanguageTag: String,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    onPronounce: (String) -> Unit,
) {
    val indexed = words.associateBy(SavedWord::word)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Saved words", style = MaterialTheme.typography.headlineMedium, color = ClipLexColors.Ink)
            Text(
                if (savedWordNames.isEmpty()) "Build a personal dictionary from your lessons."
                else "${savedWordNames.size} words ready to review in $meaningLanguage.",
                style = MaterialTheme.typography.bodyMedium,
                color = ClipLexColors.InkMuted,
            )
        }

        if (savedWordNames.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ClipLexShapes.Hero,
                color = ClipLexColors.AccentWash,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LexiMascot(modifier = Modifier.size(118.dp), mood = LexiMood.READY)
                    Text("Save your first word", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                    Text(
                        "Tap a word in any lesson to keep its meaning, pronunciation and source sentence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ClipLexColors.InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
        } else {
            savedWordNames.sorted().forEach { word ->
                val saved = indexed[word]
                val sourceLanguage = languageForWord(word, saved?.sourceLanguage)
                val displayedMeaning = validWordTranslation(word, saved?.meaning, sourceLanguage, meaningLanguageTag)
                SavedWordCard(
                    word = word,
                    saved = saved,
                    displayedMeaning = displayedMeaning,
                    sourceLanguage = sourceLanguage,
                    meaningLanguage = meaningLanguage,
                    onRemove = { onRemove(word) },
                    onPronounce = { onPronounce(word) },
                )
            }
            ClipLexActionButton(
                text = "Refresh meanings",
                icon = Icons.Default.Refresh,
                style = ClipLexButtonStyle.GHOST,
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SavedWordCard(
    word: String,
    saved: SavedWord?,
    displayedMeaning: String?,
    sourceLanguage: String?,
    meaningLanguage: String,
    onRemove: () -> Unit,
    onPronounce: () -> Unit,
) {
    var exampleExpanded by remember(word) { mutableStateOf(false) }
    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        depth = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 16.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(languageName(sourceLanguage), style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
                    Text(word.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall, color = ClipLexColors.Ink)
                    latinPronunciation(word)?.let { guide ->
                        Text("Pronounce: $guide", style = MaterialTheme.typography.bodySmall, color = ClipLexColors.AccentStrong)
                    }
                }
                ClipLexIconBadge(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Hear $word",
                    background = ClipLexColors.AccentSoft,
                    contentColor = ClipLexColors.Accent,
                    onClick = onPronounce,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Meaning in $meaningLanguage", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
                Text(
                    text = displayedMeaning ?: "Translation unavailable. Refresh to try again.",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (displayedMeaning == null) ClipLexColors.CoralDark else ClipLexColors.AccentStrong,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            saved?.example?.takeIf(String::isNotBlank)?.let { example ->
                HorizontalDivider(color = ClipLexColors.Border)
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { exampleExpanded = !exampleExpanded },
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Source sentence",
                            style = MaterialTheme.typography.labelSmall,
                            color = ClipLexColors.InkMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (exampleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (exampleExpanded) "Collapse example" else "Expand example",
                            tint = ClipLexColors.InkMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (exampleExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .padding(top = 7.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(example, style = MaterialTheme.typography.bodyMedium, color = ClipLexColors.InkSoft)
                        }
                    } else {
                        Text(
                            example,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ClipLexColors.InkSoft,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 7.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "Remove word",
                    modifier = Modifier.clickable(onClick = onRemove).padding(horizontal = 4.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = ClipLexColors.Coral,
                )
            }
        }
    }
}

@Composable
internal fun SegmentCard(segment: TranscriptionSegment, onPlay: () -> Unit, onCopy: () -> Unit) {
    var expanded by remember(segment.text, segment.translatedText) { mutableStateOf(false) }
    ClipLexCard(Modifier.fillMaxWidth(), depth = 0.dp) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ClipLexIconBadge(
                    icon = Icons.Default.Headphones,
                    contentDescription = null,
                    background = ClipLexColors.SurfaceMuted,
                    contentColor = ClipLexColors.Accent,
                    size = 38.dp,
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    if (expanded) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 190.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = segment.text,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ClipLexColors.Ink,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                segment.translatedText?.takeIf(String::isNotBlank)?.let {
                                    Text(it, color = ClipLexColors.AccentStrong, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = segment.text,
                            fontWeight = FontWeight.SemiBold,
                            color = ClipLexColors.Ink,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        segment.translatedText?.takeIf(String::isNotBlank)?.let {
                            Text(
                                text = it,
                                color = ClipLexColors.AccentStrong,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) "Show less" else "Read more",
                    style = MaterialTheme.typography.labelMedium,
                    color = ClipLexColors.Accent,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(9.dp),
                )
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Replay sentence",
                    tint = ClipLexColors.Accent,
                    modifier = Modifier.clickable(onClick = onPlay).padding(9.dp),
                )
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy sentence",
                    tint = ClipLexColors.InkMuted,
                    modifier = Modifier.clickable(onClick = onCopy).padding(9.dp),
                )
            }
        }
    }
}

internal fun languageName(tag: String?): String = tag
    ?.takeIf(String::isNotBlank)
    ?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH) }
    ?.takeIf(String::isNotBlank)
    ?: "Original"

internal fun copy(context: Context, segment: TranscriptionSegment) = copyText(
    context,
    "ClipLex sentence",
    listOfNotNull(segment.text, segment.translatedText).joinToString("\n"),
)

internal fun copyText(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(label, text))
}
