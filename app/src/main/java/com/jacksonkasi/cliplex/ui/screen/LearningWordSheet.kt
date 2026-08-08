package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.ui.components.ClipLexActionButton
import com.jacksonkasi.cliplex.ui.components.ClipLexButtonStyle
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors

@Composable
internal fun WordMeaningSheet(
    word: String,
    meaning: WordMeaningUi?,
    isSaved: Boolean,
    onSave: () -> Unit,
    onPronounce: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WORD", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.Green)
                Text(word.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.headlineMedium, color = ClipLexColors.Ink)
            }
            ClipLexIconBadge(
                icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (isSaved) "Saved word" else "Save word",
                background = if (isSaved) ClipLexColors.WarmSoft else ClipLexColors.SurfaceMuted,
                contentColor = ClipLexColors.Warm,
                onClick = onSave,
            )
        }

        ClipLexActionButton(
            text = "Listen to pronunciation",
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            style = ClipLexButtonStyle.GHOST,
            onClick = onPronounce,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        meaning?.pronunciation?.takeIf(String::isNotBlank)?.let {
            Text(
                "Say it: $it",
                style = MaterialTheme.typography.titleMedium,
                color = ClipLexColors.GreenDark,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        meaning?.partOfSpeech?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, fontStyle = FontStyle.Italic, color = ClipLexColors.Green, modifier = Modifier.padding(top = 5.dp))
        }

        if (meaning == null) {
            Row(modifier = Modifier.padding(vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(21.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Finding meaning…", color = ClipLexColors.InkMuted)
            }
        } else {
            val translatedMeaning = meaning.translatedMeaning?.takeIf(String::isNotBlank)
            ClipLexCard(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                containerColor = if (translatedMeaning == null) ClipLexColors.CoralSoft else ClipLexColors.GreenWash,
                borderColor = if (translatedMeaning == null) ClipLexColors.Coral.copy(alpha = 0.25f) else ClipLexColors.Green.copy(alpha = 0.2f),
                depth = 2.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("${meaning.meaningLanguage} meaning", style = MaterialTheme.typography.labelMedium, color = ClipLexColors.InkMuted)
                    Text(
                        translatedMeaning ?: meaning.definition?.takeIf(String::isNotBlank) ?: "Translation unavailable for this word",
                        style = if (translatedMeaning == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium,
                        color = if (translatedMeaning == null) ClipLexColors.CoralDark else ClipLexColors.GreenDark,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (translatedMeaning != null) {
                        meaning.definition?.takeIf(String::isNotBlank)?.let { definition ->
                            Text(definition, style = MaterialTheme.typography.bodyMedium, color = ClipLexColors.InkMuted, modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }
        }

        if (meaning?.example?.isNotBlank() == true) {
            ClipLexCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), depth = 2.dp) {
                Column(Modifier.padding(15.dp)) {
                    Text("IN A SENTENCE", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.BlueDark)
                    Text(meaning.example, style = MaterialTheme.typography.bodyLarge, color = ClipLexColors.Ink, modifier = Modifier.padding(top = 7.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    meaning.translatedExample?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = ClipLexColors.GreenDark, modifier = Modifier.padding(top = 6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        ClipLexActionButton(
            text = if (isSaved) "Saved to My Words" else "Save Word",
            icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        )
    }
}
