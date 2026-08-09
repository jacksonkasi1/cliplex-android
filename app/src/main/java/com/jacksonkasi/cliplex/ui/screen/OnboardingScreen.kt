package com.jacksonkasi.cliplex.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.LearningMode
import com.jacksonkasi.cliplex.domain.model.SpeechQuality
import com.jacksonkasi.cliplex.speech.SpeechLanguageStatus
import com.jacksonkasi.cliplex.ui.components.ClipLexActionButton
import com.jacksonkasi.cliplex.ui.components.ClipLexButtonStyle
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.components.ClipLexProgressBar
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes

/**
 * First-run setup as a focused learning journey. Each decision gets its own screen so the user is
 * never confronted by one long settings form.
 */
@Composable
fun OnboardingScreen(
    motherTongueLanguages: List<AppLanguage>,
    selectedMotherTongue: AppLanguage?,
    selectedLearningLanguage: LearningLanguage?,
    selectedSpeechQuality: SpeechQuality,
    isSaving: Boolean,
    onMotherTongueSelected: (AppLanguage) -> Unit,
    onLearningLanguageSelected: (LearningLanguage) -> Unit,
    onSpeechQualitySelected: (SpeechQuality) -> Unit,
    onContinue: () -> Unit,
    speechLanguageStatus: SpeechLanguageStatus = SpeechLanguageStatus.AndroidUnsupported,
    fallbackSpeechReady: Boolean = false,
    onDownloadSpeech: () -> Unit = {},
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val needsQualityStep = selectedLearningLanguage != null && selectedLearningLanguage != LearningLanguage.ENGLISH
    val totalSteps = if (needsQualityStep) 4 else 3
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val lastStep = totalSteps - 1
    val speechDownloadRequired = speechLanguageStatus is SpeechLanguageStatus.DownloadRequired ||
        (!fallbackSpeechReady && (
            speechLanguageStatus is SpeechLanguageStatus.AndroidUnsupported ||
                speechLanguageStatus is SpeechLanguageStatus.Error
            ))
    val speechDownloading = speechLanguageStatus is SpeechLanguageStatus.Downloading

    LaunchedEffect(totalSteps) {
        currentStep = currentStep.coerceAtMost(totalSteps - 1)
    }

    BackHandler(enabled = currentStep > 0) { currentStep-- }

    Column(modifier = modifier.fillMaxSize().background(ClipLexColors.Canvas)) {
        OnboardingTopBar(
            step = currentStep,
            totalSteps = totalSteps,
            onBack = { if (currentStep > 0) currentStep-- },
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero_$currentStep") {
                OnboardingHero(
                    title = when (currentStep) {
                        0 -> "What do you want to learn?"
                        1 -> "What language helps you understand?"
                        2 -> if (needsQualityStep) "Choose your speech quality" else "Your learning plan is ready"
                        else -> "Your learning plan is ready"
                    },
                    subtitle = when (currentStep) {
                        0 -> "ClipLex listens for this language in the videos you watch."
                        1 -> "Translations, meanings and explanations will appear in this language."
                        2 -> if (needsQualityStep) {
                            "Start fast or choose a larger offline model for stronger recognition."
                        } else {
                            "Everything stays on your phone. You can change these choices later."
                        }
                        else -> "Everything stays on your phone. You can change these choices later."
                    },
                    mood = if (currentStep == lastStep) LexiMood.CELEBRATING else LexiMood.READY,
                )
            }

            when (currentStep) {
                0 -> items(LearningLanguage.entries, key = { "learn_${it.name}" }) { language ->
                    LanguageChoiceCard(
                        title = language.displayName,
                        subtitle = learningLanguageDescription(language),
                        emoji = flagForTag(language.code),
                        selected = selectedLearningLanguage == language,
                        onClick = { onLearningLanguageSelected(language) },
                    )
                }

                1 -> items(motherTongueLanguages, key = { "mother_${it.tag}" }) { language ->
                    LanguageChoiceCard(
                        title = language.displayName,
                        subtitle = "Explain lessons in ${language.displayName}",
                        emoji = flagForTag(language.tag),
                        selected = selectedMotherTongue == language,
                        onClick = { onMotherTongueSelected(language) },
                    )
                }

                2 -> if (needsQualityStep) {
                    item(key = "quality_fast") {
                        SpeechQualityChoice(
                            quality = SpeechQuality.FAST,
                            description = "Starts quickly and uses the least storage",
                            badge = "Recommended to start",
                            selected = selectedSpeechQuality == SpeechQuality.FAST,
                            onClick = { onSpeechQualitySelected(SpeechQuality.FAST) },
                        )
                    }
                    item(key = "quality_recommended") {
                        SpeechQualityChoice(
                            quality = SpeechQuality.RECOMMENDED,
                            description = "A balanced offline model with stronger recognition",
                            badge = "Balanced",
                            selected = selectedSpeechQuality == SpeechQuality.RECOMMENDED,
                            onClick = { onSpeechQualitySelected(SpeechQuality.RECOMMENDED) },
                        )
                    }
                    item(key = "quality_high") {
                        SpeechQualityChoice(
                            quality = SpeechQuality.HIGH_ACCURACY,
                            description = "Largest download and slowest processing, for difficult speech",
                            badge = "Highest accuracy",
                            selected = selectedSpeechQuality == SpeechQuality.HIGH_ACCURACY,
                            onClick = { onSpeechQualitySelected(SpeechQuality.HIGH_ACCURACY) },
                        )
                    }
                } else {
                    item(key = "review") {
                        SetupReviewCard(
                            selectedLearningLanguage = selectedLearningLanguage,
                            selectedMotherTongue = selectedMotherTongue,
                            selectedSpeechQuality = selectedSpeechQuality,
                            speechLanguageStatus = speechLanguageStatus,
                            fallbackSpeechReady = fallbackSpeechReady,
                        )
                    }
                }

                else -> item(key = "review") {
                    SetupReviewCard(
                        selectedLearningLanguage = selectedLearningLanguage,
                        selectedMotherTongue = selectedMotherTongue,
                        selectedSpeechQuality = selectedSpeechQuality,
                        speechLanguageStatus = speechLanguageStatus,
                        fallbackSpeechReady = fallbackSpeechReady,
                    )
                }
            }

            if (errorMessage != null) {
                item(key = "error") {
                    ClipLexCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = ClipLexColors.CoralSoft,
                        borderColor = ClipLexColors.Coral.copy(alpha = 0.28f),
                        depth = 2.dp,
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ClipLexColors.CoralDark,
                            modifier = Modifier.padding(15.dp),
                        )
                    }
                }
            }
        }

        val stepReady = when (currentStep) {
            0 -> selectedLearningLanguage != null
            1 -> selectedMotherTongue != null
            2 -> if (needsQualityStep) true else selectedLearningLanguage != null && selectedMotherTongue != null
            else -> selectedLearningLanguage != null && selectedMotherTongue != null
        }
        val atReview = currentStep == lastStep
        val buttonText = when {
            isSaving -> "Saving your plan…"
            speechDownloading && atReview -> "Preparing speech support…"
            atReview && speechDownloadRequired -> "Download & Continue"
            atReview -> "Start learning"
            else -> "Continue"
        }
        val buttonIcon = when {
            atReview && speechDownloadRequired -> Icons.Default.Download
            atReview -> Icons.Default.School
            else -> null
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClipLexColors.Surface)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            ClipLexActionButton(
                text = buttonText,
                icon = buttonIcon,
                enabled = stepReady && !isSaving && !speechDownloading,
                onClick = {
                    if (!atReview) {
                        currentStep++
                    } else if (speechDownloadRequired) {
                        onDownloadSpeech()
                    } else {
                        onContinue()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OnboardingTopBar(step: Int, totalSteps: Int, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClipLexColors.Surface)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous step", tint = ClipLexColors.Ink)
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                Text("Clip", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                Text("Lex", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Green)
            }
            Text(
                "${step + 1}/$totalSteps",
                style = MaterialTheme.typography.labelLarge,
                color = ClipLexColors.InkMuted,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center,
            )
        }
        ClipLexProgressBar(
            progress = (step + 1f) / totalSteps,
            modifier = Modifier.padding(horizontal = 8.dp),
            height = 8.dp,
        )
    }
}

@Composable
private fun OnboardingHero(title: String, subtitle: String, mood: LexiMood) {
    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = ClipLexColors.GreenWash,
        borderColor = ClipLexColors.Green.copy(alpha = 0.2f),
        depth = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LexiMascot(modifier = Modifier.size(104.dp), mood = mood)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = ClipLexColors.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
            }
        }
    }
}

@Composable
private fun LanguageChoiceCard(
    title: String,
    subtitle: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ClipLexCard(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick),
        containerColor = if (selected) ClipLexColors.GreenSoft else ClipLexColors.Surface,
        borderColor = if (selected) ClipLexColors.Green else ClipLexColors.Border,
        depth = if (selected) 4.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, ClipLexColors.Border),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(ClipLexColors.Green),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            } else {
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(ClipLexColors.SurfaceMuted),
                )
            }
        }
    }
}

@Composable
private fun SpeechQualityChoice(
    quality: SpeechQuality,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String,
) {
    ClipLexCard(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick),
        containerColor = if (selected) ClipLexColors.BlueSoft else ClipLexColors.Surface,
        borderColor = if (selected) ClipLexColors.Blue else ClipLexColors.Border,
        depth = if (selected) 4.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            ClipLexIconBadge(
                icon = Icons.Default.RecordVoiceOver,
                contentDescription = null,
                background = if (selected) Color.White.copy(alpha = 0.8f) else ClipLexColors.BlueSoft,
                contentColor = ClipLexColors.Blue,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(quality.displayName, style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink, modifier = Modifier.weight(1f))
                    Surface(shape = ClipLexShapes.Pill, color = ClipLexColors.WarmSoft, contentColor = ClipLexColors.WarmDark) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = ClipLexColors.Blue, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SetupReviewCard(
    selectedLearningLanguage: LearningLanguage?,
    selectedMotherTongue: AppLanguage?,
    selectedSpeechQuality: SpeechQuality,
    speechLanguageStatus: SpeechLanguageStatus,
    fallbackSpeechReady: Boolean,
) {
    val statusText = when (val status = speechLanguageStatus) {
        SpeechLanguageStatus.Ready -> "Speech support is ready offline"
        SpeechLanguageStatus.DownloadRequired -> "One speech download is required"
        is SpeechLanguageStatus.Downloading -> status.progress?.let { "Downloading speech support · $it%" }
            ?: "Downloading speech support…"
        SpeechLanguageStatus.AndroidUnsupported -> if (fallbackSpeechReady) {
            "Offline Whisper fallback is ready"
        } else {
            "Additional speech support is required"
        }
        is SpeechLanguageStatus.Error -> if (fallbackSpeechReady) {
            "Offline Whisper fallback is ready"
        } else {
            "Speech support needs attention"
        }
    }
    val ready = speechLanguageStatus is SpeechLanguageStatus.Ready || fallbackSpeechReady

    ClipLexCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = ClipLexColors.Surface,
        borderColor = if (ready) ClipLexColors.Green.copy(alpha = 0.32f) else ClipLexColors.Warm.copy(alpha = 0.32f),
        depth = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SetupSummaryRow(
                icon = Icons.Default.School,
                label = "Learning",
                value = selectedLearningLanguage?.displayName ?: "Not selected",
                color = ClipLexColors.Green,
            )
            SetupSummaryRow(
                icon = Icons.Default.Language,
                label = "Explanations",
                value = selectedMotherTongue?.displayName ?: "Not selected",
                color = ClipLexColors.Blue,
            )
            SetupSummaryRow(
                icon = Icons.Default.RecordVoiceOver,
                label = "Speech quality",
                value = selectedSpeechQuality.displayName,
                color = ClipLexColors.Purple,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (ready) ClipLexColors.GreenSoft else ClipLexColors.WarmSoft, ClipLexShapes.Control)
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (speechLanguageStatus is SpeechLanguageStatus.Downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ClipLexColors.Blue)
                } else {
                    Icon(
                        if (ready) Icons.Default.Check else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (ready) ClipLexColors.GreenDark else ClipLexColors.WarmDark,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) ClipLexColors.GreenDark else ClipLexColors.WarmDark,
                )
            }
            Text(
                "Captured audio, transcripts and learning history stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = ClipLexColors.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SetupSummaryRow(icon: ImageVector, label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ClipLexIconBadge(
            icon = icon,
            contentDescription = null,
            background = color.copy(alpha = 0.10f),
            contentColor = color,
            size = 42.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
            Text(value, style = MaterialTheme.typography.titleMedium, color = ClipLexColors.Ink)
        }
        Icon(Icons.Default.Check, contentDescription = null, tint = ClipLexColors.Green, modifier = Modifier.size(20.dp))
    }
}

private fun learningLanguageDescription(language: LearningLanguage): String = when (language) {
    LearningLanguage.ENGLISH -> "Optimized English recognition and pronunciation"
    LearningLanguage.ANY_LANGUAGE -> "Automatically detect supported languages"
    else -> "Learn ${language.displayName} from real clips"
}

private fun flagForTag(tag: String): String = when (tag.substringBefore('-').lowercase()) {
    "en" -> "🇬🇧"
    "es" -> "🇪🇸"
    "fr" -> "🇫🇷"
    "de" -> "🇩🇪"
    "it" -> "🇮🇹"
    "pt" -> "🇵🇹"
    "hi", "ta", "te", "ml", "kn", "bn", "mr", "gu", "pa" -> "🇮🇳"
    "ja" -> "🇯🇵"
    "ko" -> "🇰🇷"
    "zh" -> "🇨🇳"
    "ar" -> "🇸🇦"
    "ru" -> "🇷🇺"
    else -> "🌐"
}

/** Temporary source-compatible bridge while the activity migrates to the explicit language API. */
@Deprecated("Use the LearningLanguage and SpeechQuality overload")
@Composable
fun OnboardingScreen(
    languages: List<AppLanguage>,
    selectedLanguage: AppLanguage?,
    selectedLearningMode: LearningMode?,
    onLanguageSelected: (AppLanguage) -> Unit,
    onLearningModeSelected: (LearningMode) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var learningLanguage by remember(selectedLearningMode) {
        mutableStateOf(
            when (selectedLearningMode) {
                LearningMode.ENGLISH_ONLY -> LearningLanguage.ENGLISH
                LearningMode.MULTILINGUAL -> LearningLanguage.ANY_LANGUAGE
                null -> null
            },
        )
    }
    var speechQuality by remember { mutableStateOf(SpeechQuality.FAST) }
    OnboardingScreen(
        motherTongueLanguages = languages,
        selectedMotherTongue = selectedLanguage,
        selectedLearningLanguage = learningLanguage,
        selectedSpeechQuality = speechQuality,
        isSaving = false,
        onMotherTongueSelected = onLanguageSelected,
        onLearningLanguageSelected = { selected ->
            learningLanguage = selected
            onLearningModeSelected(
                if (selected == LearningLanguage.ENGLISH) LearningMode.ENGLISH_ONLY else LearningMode.MULTILINGUAL,
            )
        },
        onSpeechQualitySelected = { speechQuality = it },
        onContinue = onContinue,
        fallbackSpeechReady = true,
        modifier = modifier,
    )
}
