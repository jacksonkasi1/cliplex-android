package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes

@Composable
fun LessonPreparingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(ClipLexColors.Canvas)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ClipLexColors.Ink)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                Text("Clip", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                Text("Lex", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Accent)
            }
            Spacer(Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = ClipLexColors.AccentSoft,
                modifier = Modifier.size(150.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LexiMascot(modifier = Modifier.size(124.dp), mood = LexiMood.THINKING)
                }
            }
            Text(
                "Turning this moment into a lesson",
                style = MaterialTheme.typography.headlineMedium,
                color = ClipLexColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                "ClipLex is transcribing and translating on this phone. You can leave this screen while it continues.",
                style = MaterialTheme.typography.bodyMedium,
                color = ClipLexColors.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 9.dp),
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(4.dp)
                    .clip(ClipLexShapes.Pill),
                color = ClipLexColors.Accent,
                trackColor = ClipLexColors.SurfaceMuted,
            )

            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                shape = ClipLexShapes.Card,
                color = ClipLexColors.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, ClipLexColors.Border),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    PreparationStep(
                        icon = Icons.Default.Check,
                        title = "Clip saved",
                        subtitle = "Media is stored privately",
                        state = PreparationState.COMPLETE,
                    )
                    PreparationStep(
                        icon = Icons.Default.GraphicEq,
                        title = "Listening for speech",
                        subtitle = "On-device transcription is running",
                        state = PreparationState.ACTIVE,
                    )
                    PreparationStep(
                        icon = Icons.Default.Translate,
                        title = "Preparing learning tools",
                        subtitle = "Translation and practice come next",
                        state = PreparationState.PENDING,
                    )
                }
            }
        }
    }
}

private enum class PreparationState { COMPLETE, ACTIVE, PENDING }

@Composable
private fun PreparationStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    state: PreparationState,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ClipLexIconBadge(
            icon = icon,
            contentDescription = null,
            background = when (state) {
                PreparationState.COMPLETE -> ClipLexColors.Accent
                PreparationState.ACTIVE -> ClipLexColors.AccentSoft
                PreparationState.PENDING -> ClipLexColors.SurfaceMuted
            },
            contentColor = when (state) {
                PreparationState.COMPLETE -> Color.White
                PreparationState.ACTIVE -> ClipLexColors.Accent
                PreparationState.PENDING -> ClipLexColors.InkFaint
            },
            size = 42.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ClipLexColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
        }
        Box(
            modifier = Modifier
                .size(if (state == PreparationState.ACTIVE) 9.dp else 7.dp)
                .background(
                    when (state) {
                        PreparationState.COMPLETE -> ClipLexColors.Accent
                        PreparationState.ACTIVE -> ClipLexColors.Warm
                        PreparationState.PENDING -> ClipLexColors.BorderStrong
                    },
                    CircleShape,
                ),
        )
    }
}
