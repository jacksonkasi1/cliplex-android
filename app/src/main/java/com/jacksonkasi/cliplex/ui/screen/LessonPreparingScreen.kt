package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.components.ClipLexProgressBar
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors

@Composable
fun LessonPreparingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "lesson-preparation")
    val progress by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(1_700), RepeatMode.Reverse),
        label = "lesson-preparation-progress",
    )

    Column(modifier = modifier.fillMaxSize().background(ClipLexColors.Canvas)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClipLexColors.Surface)
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ClipLexColors.Ink)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                Text("Clip", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                Text("Lex", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Green)
            }
            Spacer(Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ClipLexCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = ClipLexColors.GreenWash,
                borderColor = ClipLexColors.Green.copy(alpha = 0.22f),
                depth = 4.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LexiMascot(modifier = Modifier.size(170.dp), mood = LexiMood.THINKING)
                    Text(
                        "Making this clip teachable…",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ClipLexColors.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Your private audio stays on this phone while ClipLex prepares the transcript, translation and practice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ClipLexColors.InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                    ClipLexProgressBar(
                        progress = progress,
                        modifier = Modifier.padding(top = 22.dp),
                        height = 12.dp,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        PreparationStep(
                            icon = Icons.Default.Check,
                            title = "Clip captured",
                            subtitle = "Media saved privately",
                            complete = true,
                        )
                        PreparationStep(
                            icon = Icons.Default.GraphicEq,
                            title = "Listening for clear speech",
                            subtitle = "On-device transcription",
                            complete = false,
                        )
                        PreparationStep(
                            icon = Icons.Default.Translate,
                            title = "Building your lesson",
                            subtitle = "Translation and practice follow",
                            complete = false,
                        )
                    }
                }
            }
            Text(
                "You can leave this screen; processing will continue.",
                style = MaterialTheme.typography.labelSmall,
                color = ClipLexColors.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun PreparationStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    complete: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ClipLexIconBadge(
            icon = icon,
            contentDescription = null,
            background = if (complete) ClipLexColors.Green else ClipLexColors.BlueSoft,
            contentColor = if (complete) Color.White else ClipLexColors.Blue,
            size = 42.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ClipLexColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (complete) ClipLexColors.Green else ClipLexColors.Blue, CircleShape),
        )
    }
}
