package com.jacksonkasi.cliplex.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jacksonkasi.cliplex.data.local.SessionEntity
import com.jacksonkasi.cliplex.domain.model.MediaView
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import com.jacksonkasi.cliplex.domain.model.availableWhen
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes
import kotlinx.coroutines.delay

enum class LearningDisplayMode(val label: String) {
    WORD_BY_WORD("Word by Word"),
    SENTENCE("Sentence"),
    TAMIL_VIEW("Translation First"),
}

/** Presentation data for the selected word. A null value means lookup is still in progress. */
data class WordMeaningUi(
    val pronunciation: String? = null,
    val partOfSpeech: String? = null,
    val translatedMeaning: String? = null,
    val meaningLanguage: String = "Your language",
    val definition: String? = null,
    val example: String? = null,
    val translatedExample: String? = null,
)

/**
 * Media-first lesson UI. Captions are deliberately bounded: video uses a moving, compact word
 * window, while audio uses an internally scrollable caption card. Full text lives in the transcript
 * section, so a long ASR segment can never cover the player or create an unbounded page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningSessionScreen(
    session: SessionEntity,
    segments: List<TranscriptionSegment>,
    processingStage: String?,
    displayMode: LearningDisplayMode,
    mediaView: MediaView,
    selectedWord: String?,
    selectedMeaning: WordMeaningUi?,
    isSelectedWordSaved: Boolean,
    onBack: () -> Unit,
    onDisplayModeChange: (LearningDisplayMode) -> Unit,
    onMediaViewChange: (MediaView) -> Unit,
    onWordTap: (String) -> Unit,
    onDismissWord: () -> Unit,
    onSaveWord: (String) -> Unit,
    onPronounceWord: (String) -> Unit,
    onDeleteVideo: () -> Unit,
    onDeleteLesson: () -> Unit,
    onReanalyze: () -> Unit,
    onChangeLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val videoSource = session.videoPath?.takeIf(String::isNotBlank)
    val audioSource = session.audioPath?.takeIf(String::isNotBlank)
    val effectiveMediaView = mediaView.availableWhen(videoSource != null)
    val sourceKey = session.id
    val playerState = remember(sourceKey, session.durationMs) {
        LessonPlayerState(fallbackDurationMs = session.durationMs)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val sortedSegments = remember(segments) { segments.sortedBy(TranscriptionSegment::startTimeMs) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var showDeleteLessonConfirmation by remember { mutableStateOf(false) }
    var isScrubbing by remember(sourceKey) { mutableStateOf(false) }
    var scrubPositionMs by remember(sourceKey) { mutableFloatStateOf(0f) }

    DisposableEffect(playerState) {
        onDispose(playerState::release)
    }

    DisposableEffect(lifecycleOwner, playerState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playerState.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(playerState, effectiveMediaView, videoSource, audioSource) {
        if (effectiveMediaView == MediaView.AUDIO && audioSource != null) {
            playerState.bindAudio(context, audioSource)
        }
        onDispose { playerState.prepareForMediaSwitch() }
    }

    LaunchedEffect(playerState) {
        while (true) {
            playerState.refreshPosition()
            delay(100)
        }
    }

    val displayPositionMs = if (isScrubbing) scrubPositionMs.toLong() else playerState.positionMs
    val currentSegment = remember(sortedSegments, displayPositionMs) {
        segmentAt(sortedSegments, displayPositionMs)
    }
    val visibleStage = processingStage
        ?.takeIf(String::isNotBlank)
        ?: session.processingState
            .takeUnless { it.equals("READY", ignoreCase = true) || it.equals("COMPLETE", ignoreCase = true) }
            ?.let(::readableProcessingStage)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ClipLexColors.Canvas,
        topBar = {
            LearningTopBar(
                session = session,
                playerState = playerState,
                onBack = onBack,
                onReanalyze = onReanalyze,
                onChangeLanguage = onChangeLanguage,
                processing = processingStage != null,
                optionsExpanded = optionsExpanded,
                onOptionsExpandedChange = { optionsExpanded = it },
                hasVideo = videoSource != null,
                onDeleteVideo = onDeleteVideo,
                onDeleteLesson = { showDeleteLessonConfirmation = true },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "selectors") {
                LessonSelectors(
                    selectedMediaView = effectiveMediaView,
                    hasVideo = videoSource != null,
                    selectedMode = displayMode,
                    onMediaViewSelected = {
                        playerState.prepareForMediaSwitch()
                        onMediaViewChange(it)
                    },
                    onModeSelected = onDisplayModeChange,
                )
            }

            item(key = "media") {
                if (effectiveMediaView == MediaView.VIDEO && videoSource != null) {
                    VideoHero(
                        videoSource = videoSource,
                        playerState = playerState,
                        currentSegment = currentSegment,
                        displayPositionMs = displayPositionMs,
                        displayMode = displayMode,
                        processingStage = visibleStage,
                        onWordTap = onWordTap,
                    )
                } else {
                    AudioHero(
                        state = playerState,
                        title = session.title.ifBlank { "Captured lesson" },
                        currentSegment = currentSegment,
                        displayPositionMs = displayPositionMs,
                        displayMode = displayMode,
                        processingStage = visibleStage,
                        playbackError = if (audioSource == null) {
                            "No playable media is stored for this lesson."
                        } else {
                            playerState.playbackError
                        },
                        onWordTap = onWordTap,
                        isScrubbing = isScrubbing,
                        scrubPositionMs = scrubPositionMs,
                        onScrubChange = {
                            isScrubbing = true
                            scrubPositionMs = it
                        },
                        onScrubFinished = {
                            playerState.seekTo(scrubPositionMs.toLong())
                            isScrubbing = false
                        },
                    )
                }
            }

            if (effectiveMediaView == MediaView.VIDEO && videoSource != null) {
                item(key = "video_controls") {
                    PlaybackControls(
                        state = playerState,
                        isScrubbing = isScrubbing,
                        scrubPositionMs = scrubPositionMs,
                        onScrubChange = {
                            isScrubbing = true
                            scrubPositionMs = it
                        },
                        onScrubFinished = {
                            playerState.seekTo(scrubPositionMs.toLong())
                            isScrubbing = false
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            if (session.captureError != null || playerState.playbackError != null) {
                item(key = "media_error") {
                    ClipLexCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        containerColor = ClipLexColors.CoralSoft,
                        borderColor = ClipLexColors.Coral.copy(alpha = 0.28f),
                        depth = 2.dp,
                    ) {
                        Text(
                            text = playerState.playbackError ?: session.captureError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = ClipLexColors.CoralDark,
                            modifier = Modifier.padding(15.dp),
                        )
                    }
                }
            }

            item(key = "lesson_overview") {
                LessonOverviewCard(session, effectiveMediaView)
            }

            item(key = "transcript_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Lesson transcript", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
                        Text(
                            "Tap a word to learn it · expand only when needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClipLexColors.InkMuted,
                        )
                    }
                    Text("${sortedSegments.size}", style = MaterialTheme.typography.labelLarge, color = ClipLexColors.GreenDark)
                }
            }

            if (sortedSegments.isEmpty()) {
                item(key = "empty_transcript") {
                    ClipLexCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        containerColor = ClipLexColors.BlueSoft,
                        borderColor = ClipLexColors.Blue.copy(alpha = 0.18f),
                    ) {
                        Text(
                            visibleStage ?: "Transcript will appear here when processing is complete.",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ClipLexColors.InkMuted,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = sortedSegments,
                    key = { index, segment -> "${index}_${segment.startTimeMs}_${segment.text.hashCode()}" },
                ) { index, segment ->
                    TranscriptSegmentCard(
                        index = index,
                        segment = segment,
                        isCurrent = segment === currentSegment || (
                            segment.startTimeMs == currentSegment?.startTimeMs && segment.text == currentSegment?.text
                        ),
                        onWordTap = onWordTap,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (selectedWord != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissWord,
            sheetState = sheetState,
            shape = ClipLexShapes.Sheet,
            containerColor = ClipLexColors.Surface,
        ) {
            WordMeaningSheet(
                word = selectedWord,
                meaning = selectedMeaning,
                isSaved = isSelectedWordSaved,
                onSave = { onSaveWord(selectedWord) },
                onPronounce = { onPronounceWord(selectedWord) },
            )
        }
    }

    if (showDeleteLessonConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteLessonConfirmation = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ClipLexColors.Coral) },
            title = { Text("Delete this lesson?") },
            text = { Text("The captured media, transcript and translations will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteLessonConfirmation = false
                        onDeleteLesson()
                    },
                ) { Text("Delete", color = ClipLexColors.Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLessonConfirmation = false }) { Text("Keep lesson") }
            },
        )
    }
}
