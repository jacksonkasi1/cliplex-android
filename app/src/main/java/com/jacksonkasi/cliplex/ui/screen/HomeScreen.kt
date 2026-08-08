package com.jacksonkasi.cliplex.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.common.languageForWord
import com.jacksonkasi.cliplex.common.latinPronunciation
import com.jacksonkasi.cliplex.common.validWordTranslation
import com.jacksonkasi.cliplex.domain.model.SavedWord
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import com.jacksonkasi.cliplex.domain.model.toUserMessage
import com.jacksonkasi.cliplex.service.CaptureService
import com.jacksonkasi.cliplex.ui.components.ClipLexActionButton
import com.jacksonkasi.cliplex.ui.components.ClipLexBottomNav
import com.jacksonkasi.cliplex.ui.components.ClipLexButtonStyle
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexIconBadge
import com.jacksonkasi.cliplex.ui.components.ClipLexPill
import com.jacksonkasi.cliplex.ui.components.ClipLexProgressBar
import com.jacksonkasi.cliplex.ui.components.ClipLexSectionTitle
import com.jacksonkasi.cliplex.ui.components.LexiMascot
import com.jacksonkasi.cliplex.ui.components.LexiMood
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes
import com.jacksonkasi.cliplex.ui.viewmodel.HomeViewModel
import kotlin.math.sin

@Composable
fun HomeScreen(
    selectedLanguage: AppLanguage?,
    homeViewModel: HomeViewModel,
    onStartLearning: () -> Unit,
    onBeginCapture: () -> Unit,
    onFinishCapture: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onChangeLanguage: () -> Unit,
    onPronounceWord: (String) -> Unit,
    onRecognizePronunciation: (word: String, languageTag: String, onResult: (String?) -> Unit) -> Unit,
    onShowFloatingControl: () -> Unit,
    overlayGranted: Boolean,
    setupMessage: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by homeViewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val isListening = state.captureState == CaptureService.CaptureState.Capturing
    val isBusy = state.isProcessing || state.captureState == CaptureService.CaptureState.Preparing
    val primaryAction = when (state.captureState) {
        CaptureService.CaptureState.Idle, is CaptureService.CaptureState.Error -> onStartLearning
        CaptureService.CaptureState.Armed -> onBeginCapture
        CaptureService.CaptureState.Capturing -> onFinishCapture
        else -> ({})
    }

    Column(modifier = modifier.fillMaxSize().background(ClipLexColors.Canvas)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            HomeHeader(onOpenSettings)
            LanguageAndStreakRow(
                source = compactLanguageName(state.modelName?.substringBefore(" ·") ?: "English"),
                target = selectedLanguage?.displayName ?: "Tamil",
                onChangeLanguage = onChangeLanguage,
            )

            when (selectedTab) {
                2 -> SavedWordsSection(
                    words = state.savedWordDetails,
                    savedWordNames = state.savedWords,
                    meaningLanguage = selectedLanguage?.displayName ?: "Your language",
                    meaningLanguageTag = selectedLanguage?.tag ?: "en",
                    onRefresh = homeViewModel::refreshSavedWordMeanings,
                    onRemove = homeViewModel::removeSavedWord,
                    onPronounce = onPronounceWord,
                )

                3 -> PracticeSection(
                    sessions = state.practiceSessions,
                    savedWords = state.savedWordDetails,
                    motherTongue = selectedLanguage?.displayName ?: "Your language",
                    onSpeak = onPronounceWord,
                    onRecognize = onRecognizePronunciation,
                    onAskTutor = homeViewModel::askTutor,
                    smartTutorInstalled = homeViewModel.isSmartTutorInstalled(),
                )

                else -> {
                    CaptureDashboard(
                        isListening = isListening,
                        isBusy = isBusy,
                        isModelReady = state.isModelReady,
                        durationMs = state.captureDurationMs,
                        captureState = state.captureState,
                        primaryAction = primaryAction,
                    )

                    if (
                        com.jacksonkasi.cliplex.BuildConfig.OVERLAY_SUPPORTED &&
                        overlayGranted &&
                        state.overlayStatus == CaptureService.OverlayStatus.Disabled &&
                        (state.captureState == CaptureService.CaptureState.Armed || isListening)
                    ) {
                        ClipLexActionButton(
                            text = "Show floating control",
                            icon = Icons.Default.Mic,
                            style = ClipLexButtonStyle.GHOST,
                            onClick = onShowFloatingControl,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    val visibleMessage = setupMessage ?: state.error?.toUserMessage() ?: state.modelError
                    visibleMessage?.let {
                        ClipLexCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = ClipLexColors.CoralSoft,
                            borderColor = ClipLexColors.Coral.copy(alpha = 0.35f),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(11.dp),
                            ) {
                                ClipLexIconBadge(
                                    icon = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    background = Color.White.copy(alpha = 0.72f),
                                    contentColor = ClipLexColors.Coral,
                                    size = 38.dp,
                                )
                                Text(
                                    text = it,
                                    color = ClipLexColors.CoralDark,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    if (state.segments.isNotEmpty()) {
                        ClipLexSectionTitle(
                            title = "Recent learning",
                            actionLabel = "See all",
                            onAction = onOpenHistory,
                        )
                        state.segments.take(3).forEach { segment ->
                            SegmentCard(
                                segment = segment,
                                onPlay = { homeViewModel.playSegment(segment) },
                                onCopy = { copy(context, segment) },
                            )
                        }
                    } else {
                        FirstLessonHint()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        ClipLexBottomNav(
            items = listOf(
                Icons.Default.Home to "Home",
                Icons.Default.Book to "Learn",
                Icons.Default.Headphones to "Words",
                Icons.Default.School to "Practice",
                Icons.Default.Person to "Profile",
            ),
            selectedIndex = selectedTab,
            onSelected = { index ->
                when (index) {
                    0 -> selectedTab = 0
                    1 -> onOpenHistory()
                    2 -> {
                        selectedTab = 2
                        homeViewModel.refreshSavedWordMeanings()
                    }
                    3 -> selectedTab = 3
                    4 -> onOpenSettings()
                }
            },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}
