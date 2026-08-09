package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.domain.model.toUserMessage
import com.jacksonkasi.cliplex.service.CaptureService
import com.jacksonkasi.cliplex.ui.components.ClipLexActionButton
import com.jacksonkasi.cliplex.ui.components.ClipLexBottomNav
import com.jacksonkasi.cliplex.ui.components.ClipLexButtonStyle
import com.jacksonkasi.cliplex.ui.components.ClipLexSectionTitle
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes
import com.jacksonkasi.cliplex.ui.viewmodel.HomeViewModel

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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(4.dp))
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
                    visibleMessage?.let { message ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ClipLexShapes.Card,
                            color = ClipLexColors.CoralSoft,
                            contentColor = ClipLexColors.CoralDark,
                        ) {
                            Row(
                                modifier = Modifier.padding(15.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null)
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    if (state.segments.isNotEmpty()) {
                        ClipLexSectionTitle(
                            title = "Recent learning",
                            actionLabel = "View lessons",
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
            Spacer(Modifier.height(10.dp))
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
