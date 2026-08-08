package com.learnthis.ui.screen

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.learnthis.data.local.SessionEntity
import com.learnthis.domain.model.TranscriptionSegment
import java.io.File
import kotlin.math.floor
import kotlinx.coroutines.delay

enum class LearningDisplayMode(val label: String) {
	WORD_BY_WORD("Word by Word"),
	SENTENCE("Sentence"),
	TAMIL_VIEW("Tamil View"),
}

/** Presentation data for the selected word. A null value means lookup is still in progress. */
data class WordMeaningUi(
	val pronunciation: String? = null,
	val partOfSpeech: String? = null,
	val translatedMeaning: String? = null,
	val definition: String? = null,
	val example: String? = null,
	val translatedExample: String? = null,
)

/**
 * Video-first learning surface. Persistence, translation, vocabulary lookup, and deletion are
 * deliberately delegated to the caller; this composable owns only transient media playback state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningSessionScreen(
	session: SessionEntity,
	segments: List<TranscriptionSegment>,
	processingStage: String?,
	displayMode: LearningDisplayMode,
	selectedWord: String?,
	selectedMeaning: WordMeaningUi?,
	isSelectedWordSaved: Boolean,
	onBack: () -> Unit,
	onDisplayModeChange: (LearningDisplayMode) -> Unit,
	onWordTap: (String) -> Unit,
	onDismissWord: () -> Unit,
	onSaveWord: (String) -> Unit,
	onPronounceWord: (String) -> Unit,
	onDeleteVideo: () -> Unit,
	onDeleteLesson: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val videoSource = session.videoPath?.takeIf(String::isNotBlank)
	val audioSource = session.audioPath?.takeIf(String::isNotBlank)
	val sourceKey = videoSource ?: audioSource
	val playerState = remember(sourceKey, session.durationMs) {
		LessonPlayerState(fallbackDurationMs = session.durationMs)
	}
	val lifecycleOwner = LocalLifecycleOwner.current
	val context = LocalContext.current
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

	DisposableEffect(playerState, videoSource, audioSource) {
		if (videoSource == null && audioSource != null) {
			playerState.bindAudio(context, audioSource)
		}
		onDispose { }
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
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			TopAppBar(
				title = { Text("Learn This", fontWeight = FontWeight.SemiBold) },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
					}
				},
				actions = {
					Box {
						IconButton(onClick = { optionsExpanded = true }) {
							Icon(Icons.Default.MoreVert, contentDescription = "Lesson options")
						}
						DropdownMenu(
							expanded = optionsExpanded,
							onDismissRequest = { optionsExpanded = false },
						) {
							if (videoSource != null) {
								DropdownMenuItem(
									text = { Text("Delete video") },
									onClick = {
										optionsExpanded = false
										onDeleteVideo()
									},
									leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
								)
							}
							DropdownMenuItem(
								text = { Text("Delete lesson") },
								onClick = {
									optionsExpanded = false
									showDeleteLessonConfirmation = true
								},
								leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
							)
						}
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
			)
		},
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
		) {
			item(key = "media") {
				if (videoSource != null) {
					VideoHero(
						videoSource = videoSource,
						playerState = playerState,
						currentSegment = currentSegment,
						displayPositionMs = displayPositionMs,
						displayMode = displayMode,
						processingStage = visibleStage,
						onDisplayModeChange = onDisplayModeChange,
						onWordTap = onWordTap,
					)
				} else {
					AudioHero(
						currentSegment = currentSegment,
						displayPositionMs = displayPositionMs,
						displayMode = displayMode,
						processingStage = visibleStage,
						playbackError = if (audioSource == null) "No playable media is stored for this lesson." else playerState.playbackError,
						onDisplayModeChange = onDisplayModeChange,
						onWordTap = onWordTap,
					)
				}
			}

			item(key = "controls") {
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
				)
			}

			if (session.captureError != null || (videoSource != null && playerState.playbackError != null)) {
				item(key = "media_error") {
					Text(
						text = playerState.playbackError ?: session.captureError.orEmpty(),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.error,
						modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
					)
				}
			}

			item(key = "lesson_title") {
				Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
					Text(
						text = session.title.ifBlank { "Captured lesson" },
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold,
					)
					Text(
						text = if (videoSource == null) "Audio lesson" else "Captured video lesson",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}
	}

	if (selectedWord != null) {
		val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
		ModalBottomSheet(
			onDismissRequest = onDismissWord,
			sheetState = sheetState,
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
			icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
			title = { Text("Delete this lesson?") },
			text = { Text("The captured media, transcript, and translations for this lesson will be permanently deleted.") },
			confirmButton = {
				TextButton(
					onClick = {
						showDeleteLessonConfirmation = false
						onDeleteLesson()
					},
				) { Text("Delete", color = MaterialTheme.colorScheme.error) }
			},
			dismissButton = {
				TextButton(onClick = { showDeleteLessonConfirmation = false }) { Text("Cancel") }
			},
		)
	}
}

@Composable
private fun VideoHero(
	videoSource: String,
	playerState: LessonPlayerState,
	currentSegment: TranscriptionSegment?,
	displayPositionMs: Long,
	displayMode: LearningDisplayMode,
	processingStage: String?,
	onDisplayModeChange: (LearningDisplayMode) -> Unit,
	onWordTap: (String) -> Unit,
) {
	BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
		val aspectRatio = (playerState.videoAspectRatio ?: (9f / 16f)).coerceIn(9f / 16f, 16f / 9f)
		val heroHeight = (maxWidth / aspectRatio).coerceIn(240.dp, 560.dp)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(heroHeight)
				.background(Color.Black),
		) {
			AndroidView(
				factory = { context ->
					VideoView(context).also { playerState.bindVideo(it, videoSource) }
				},
				update = { playerState.bindVideo(it, videoSource) },
				modifier = Modifier.fillMaxSize(),
			)

			ModeMenu(
				selectedMode = displayMode,
				onModeSelected = onDisplayModeChange,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(12.dp),
			)

			if (processingStage != null) {
				ProcessingPill(
					text = processingStage,
					modifier = Modifier
						.align(Alignment.TopStart)
						.padding(12.dp),
				)
			}

			SubtitleSurface(
				segment = currentSegment,
				positionMs = displayPositionMs,
				mode = displayMode,
				onWordTap = onWordTap,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.fillMaxWidth()
					.padding(12.dp),
			)
		}
	}
}

@Composable
private fun AudioHero(
	currentSegment: TranscriptionSegment?,
	displayPositionMs: Long,
	displayMode: LearningDisplayMode,
	processingStage: String?,
	playbackError: String?,
	onDisplayModeChange: (LearningDisplayMode) -> Unit,
	onWordTap: (String) -> Unit,
) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(320.dp)
			.background(MaterialTheme.colorScheme.surfaceVariant),
	) {
		Column(
			modifier = Modifier
				.align(Alignment.TopCenter)
				.padding(top = 32.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
				Icon(
					imageVector = Icons.Default.GraphicEq,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onPrimaryContainer,
					modifier = Modifier.padding(18.dp).size(32.dp),
				)
			}
			Spacer(Modifier.height(10.dp))
			Text("Audio lesson", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
			if (playbackError != null) {
				Text(
					text = playbackError,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
				)
			}
		}

		ModeMenu(
			selectedMode = displayMode,
			onModeSelected = onDisplayModeChange,
			modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
		)

		if (processingStage != null) {
			ProcessingPill(
				text = processingStage,
				modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
				darkSurface = false,
			)
		}

		SubtitleSurface(
			segment = currentSegment,
			positionMs = displayPositionMs,
			mode = displayMode,
			onWordTap = onWordTap,
			darkSurface = false,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth()
				.padding(12.dp),
		)
	}
}

@Composable
private fun PlaybackControls(
	state: LessonPlayerState,
	isScrubbing: Boolean,
	scrubPositionMs: Float,
	onScrubChange: (Float) -> Unit,
	onScrubFinished: () -> Unit,
) {
	val duration = state.durationMs.coerceAtLeast(1L)
	val displayedPosition = if (isScrubbing) scrubPositionMs else state.positionMs.toFloat()
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)
			.padding(horizontal = 16.dp, vertical = 8.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceEvenly,
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = state::replay, enabled = state.isPrepared) {
				Icon(Icons.Default.Replay, contentDescription = "Replay")
			}
			IconButton(onClick = { state.seekBy(-10_000L) }, enabled = state.isPrepared) {
				Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds")
			}
			Surface(
				shape = CircleShape,
				color = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.onPrimary,
			) {
				IconButton(onClick = state::togglePlayback, enabled = state.isPrepared) {
					Icon(
						imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
						contentDescription = if (state.isPlaying) "Pause" else "Play",
					)
				}
			}
			IconButton(onClick = { state.seekBy(10_000L) }, enabled = state.isPrepared) {
				Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds")
			}
		}

		Slider(
			value = displayedPosition.coerceIn(0f, duration.toFloat()),
			onValueChange = onScrubChange,
			onValueChangeFinished = onScrubFinished,
			enabled = state.isPrepared && state.durationMs > 0L,
			valueRange = 0f..duration.toFloat(),
			modifier = Modifier.fillMaxWidth(),
		)
		Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
			Text(formatDuration(displayedPosition.toLong()), style = MaterialTheme.typography.labelSmall)
			Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall)
		}
	}
}

@Composable
private fun ModeMenu(
	selectedMode: LearningDisplayMode,
	onModeSelected: (LearningDisplayMode) -> Unit,
	modifier: Modifier = Modifier,
) {
	var expanded by remember { mutableStateOf(false) }
	Box(modifier) {
		Surface(
			shape = RoundedCornerShape(20.dp),
			color = Color.Black.copy(alpha = 0.68f),
			contentColor = Color.White,
			modifier = Modifier.clickable { expanded = true },
		) {
			Row(
				modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
				verticalAlignment = Alignment.CenterVertically,
		) {
				Text(selectedMode.label, style = MaterialTheme.typography.labelLarge)
				Spacer(Modifier.width(2.dp))
				Icon(Icons.Default.ExpandMore, contentDescription = "Change learning mode", modifier = Modifier.size(18.dp))
			}
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			LearningDisplayMode.entries.forEach { mode ->
				DropdownMenuItem(
					text = { Text(mode.label) },
					onClick = {
						expanded = false
						onModeSelected(mode)
					},
				)
			}
		}
	}
}

@Composable
private fun ProcessingPill(
	text: String,
	modifier: Modifier = Modifier,
	darkSurface: Boolean = true,
) {
	Surface(
		shape = RoundedCornerShape(20.dp),
		color = if (darkSurface) Color.Black.copy(alpha = 0.68f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
		contentColor = if (darkSurface) Color.White else MaterialTheme.colorScheme.onSurface,
		modifier = modifier,
	) {
		Row(
			modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			CircularProgressIndicator(
				modifier = Modifier.size(14.dp),
				strokeWidth = 2.dp,
				color = if (darkSurface) Color.White else MaterialTheme.colorScheme.primary,
			)
			Spacer(Modifier.width(7.dp))
			Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 2)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleSurface(
	segment: TranscriptionSegment?,
	positionMs: Long,
	mode: LearningDisplayMode,
	onWordTap: (String) -> Unit,
	modifier: Modifier = Modifier,
	darkSurface: Boolean = true,
) {
	if (segment == null) return
	val background = if (darkSurface) Color.Black.copy(alpha = 0.74f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
	val primaryText = if (darkSurface) Color.White else MaterialTheme.colorScheme.onSurface
	val secondaryText = if (darkSurface) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant
	Surface(
		modifier = modifier,
		shape = RoundedCornerShape(14.dp),
		color = background,
		contentColor = primaryText,
	) {
		Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
			when (mode) {
				LearningDisplayMode.WORD_BY_WORD -> {
					TappableWordLine(
						text = segment.text,
						activeWordIndex = activeWordIndex(segment, positionMs),
						onWordTap = onWordTap,
						textColor = primaryText,
					)
				}

				LearningDisplayMode.SENTENCE -> {
					TappableWordLine(
						text = segment.text,
						activeWordIndex = null,
						onWordTap = onWordTap,
						textColor = primaryText,
					)
					TranslationLine(
						translation = segment.translatedText,
						missingText = "Translation is being prepared…",
						color = secondaryText,
					)
				}

				LearningDisplayMode.TAMIL_VIEW -> {
					val translation = segment.translatedText
					Text(
						text = translation ?: "Tamil translation is being prepared…",
						style = MaterialTheme.typography.bodyLarge,
						fontWeight = if (translation == null) FontWeight.Normal else FontWeight.SemiBold,
						fontStyle = if (translation == null) FontStyle.Italic else FontStyle.Normal,
						color = if (translation == null) secondaryText else primaryText,
					)
					Spacer(Modifier.height(5.dp))
					TappableWordLine(
						text = segment.text,
						activeWordIndex = null,
						onWordTap = onWordTap,
						textColor = secondaryText,
						compact = true,
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TappableWordLine(
	text: String,
	activeWordIndex: Int?,
	onWordTap: (String) -> Unit,
	textColor: Color,
	compact: Boolean = false,
) {
	val tokens = remember(text) { text.trim().split(Regex("\\s+")).filter(String::isNotBlank) }
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		var wordIndex = 0
		tokens.forEach { token ->
			val word = extractEnglishWord(token)
			val thisWordIndex = if (word != null) wordIndex++ else null
			val isActive = thisWordIndex != null && thisWordIndex == activeWordIndex
			Text(
				text = token,
				style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
				fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
				color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else textColor,
				modifier = Modifier
					.clip(RoundedCornerShape(5.dp))
					.background(if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
					.clickable(enabled = word != null) { word?.let(onWordTap) }
					.padding(horizontal = 3.dp, vertical = 1.dp),
			)
		}
	}
}

@Composable
private fun TranslationLine(translation: String?, missingText: String, color: Color) {
	Spacer(Modifier.height(5.dp))
	Text(
		text = translation ?: missingText,
		style = MaterialTheme.typography.bodySmall,
		fontStyle = if (translation == null) FontStyle.Italic else FontStyle.Normal,
		color = color,
	)
}

@Composable
private fun WordMeaningSheet(
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
			.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
	) {
		Text(word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
		meaning?.pronunciation?.takeIf(String::isNotBlank)?.let {
			Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		meaning?.partOfSpeech?.takeIf(String::isNotBlank)?.let {
			Text(
				text = it,
				style = MaterialTheme.typography.labelLarge,
				fontStyle = FontStyle.Italic,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier.padding(top = 5.dp),
			)
		}

		if (meaning == null) {
			Row(
				modifier = Modifier.padding(vertical = 24.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
				Spacer(Modifier.width(10.dp))
				Text("Finding meaning…", color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		} else {
			meaning.translatedMeaning?.takeIf(String::isNotBlank)?.let {
				Text(
					text = it,
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.padding(top = 14.dp),
				)
			}
			meaning.definition?.takeIf(String::isNotBlank)?.let {
				Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
			}
		}

		Row(
			modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			OutlinedButton(onClick = onPronounce, modifier = Modifier.weight(1f)) {
				Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(7.dp))
				Text("Pronounce")
			}
			Button(onClick = onSave, enabled = !isSaved, modifier = Modifier.weight(1f)) {
				Icon(
					imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
					contentDescription = null,
					modifier = Modifier.size(18.dp),
				)
				Spacer(Modifier.width(7.dp))
				Text(if (isSaved) "Saved" else "Save word")
			}
		}

		if (meaning?.example?.isNotBlank() == true) {
			HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
			Text("Example", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
			Text(meaning.example, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 7.dp))
			meaning.translatedExample?.takeIf(String::isNotBlank)?.let {
				Text(
					text = it,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 5.dp),
				)
			}
		}
	}
}

@Stable
private class LessonPlayerState(fallbackDurationMs: Long) {
	var positionMs by mutableLongStateOf(0L)
		private set
	var durationMs by mutableLongStateOf(fallbackDurationMs.coerceAtLeast(0L))
		private set
	var isPlaying by mutableStateOf(false)
		private set
	var isPrepared by mutableStateOf(false)
		private set
	var playbackError by mutableStateOf<String?>(null)
		private set
	var videoAspectRatio by mutableStateOf<Float?>(null)
		private set

	private var videoView: VideoView? = null
	private var audioPlayer: MediaPlayer? = null
	private var boundVideoSource: String? = null
	private var boundAudioSource: String? = null

	fun bindVideo(view: VideoView, source: String) {
		if (videoView === view && boundVideoSource == source) return
		releaseAudio()
		if (videoView !== view) videoView?.stopPlayback()
		videoView = view
		boundVideoSource = source
		isPrepared = false
		isPlaying = false
		playbackError = null
		view.setOnPreparedListener { mediaPlayer ->
			durationMs = mediaPlayer.duration.toLong().takeIf { it > 0L } ?: durationMs
			if (mediaPlayer.videoWidth > 0 && mediaPlayer.videoHeight > 0) {
				videoAspectRatio = mediaPlayer.videoWidth.toFloat() / mediaPlayer.videoHeight.toFloat()
			}
			isPrepared = true
			view.seekTo(1)
		}
		view.setOnCompletionListener {
			isPlaying = false
			positionMs = durationMs
		}
		view.setOnErrorListener { _, _, _ ->
			isPrepared = false
			isPlaying = false
			playbackError = "The captured video could not be opened."
			true
		}
		runCatching { view.setVideoURI(mediaUri(source)) }
			.onFailure {
				playbackError = "The captured video could not be opened."
				isPrepared = false
			}
	}

	fun bindAudio(context: Context, source: String) {
		if (boundAudioSource == source && audioPlayer != null) return
		videoView?.stopPlayback()
		videoView = null
		boundVideoSource = null
		releaseAudio()
		boundAudioSource = source
		isPrepared = false
		isPlaying = false
		playbackError = null
		val player = MediaPlayer()
		audioPlayer = player
		player.setAudioAttributes(
			AudioAttributes.Builder()
				.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.build()
		)
		player.setOnPreparedListener {
			durationMs = it.duration.toLong().takeIf { value -> value > 0L } ?: durationMs
			isPrepared = true
		}
		player.setOnCompletionListener {
			isPlaying = false
			positionMs = durationMs
		}
		player.setOnErrorListener { _, _, _ ->
			isPrepared = false
			isPlaying = false
			playbackError = "The captured audio could not be opened."
			true
		}
		runCatching {
			val uri = mediaUri(source)
			if (uri.scheme.equals("file", ignoreCase = true)) {
				player.setDataSource(requireNotNull(uri.path))
			} else {
				player.setDataSource(context, uri)
			}
			player.prepareAsync()
		}.onFailure {
			playbackError = "The captured audio could not be opened."
			isPrepared = false
		}
	}

	fun togglePlayback() {
		if (!isPrepared) return
		if (isPlaying) pause() else play()
	}

	fun play() {
		if (!isPrepared) return
		runCatching {
			if (positionMs >= durationMs && durationMs > 0L) seekTo(0L)
			videoView?.start() ?: audioPlayer?.start()
			isPlaying = true
		}.onFailure {
			isPlaying = false
			playbackError = "Playback could not start."
		}
	}

	fun pause() {
		runCatching {
			videoView?.takeIf(VideoView::isPlaying)?.pause()
			audioPlayer?.takeIf(MediaPlayer::isPlaying)?.pause()
		}
		isPlaying = false
		refreshPosition()
	}

	fun replay() {
		seekTo(0L)
		play()
	}

	fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

	fun seekTo(targetMs: Long) {
		if (!isPrepared) return
		val clamped = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
		runCatching {
			videoView?.seekTo(clamped.toInt())
			audioPlayer?.seekTo(clamped, MediaPlayer.SEEK_CLOSEST)
			positionMs = clamped
		}.onFailure { playbackError = "Could not move to that point in the lesson." }
	}

	fun refreshPosition() {
		runCatching {
			when {
				videoView != null -> {
					positionMs = videoView?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: positionMs
					isPlaying = videoView?.isPlaying == true
				}
				audioPlayer != null && isPrepared -> {
					positionMs = audioPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: positionMs
					isPlaying = audioPlayer?.isPlaying == true
				}
			}
		}
	}

	fun release() {
		runCatching { videoView?.stopPlayback() }
		videoView = null
		boundVideoSource = null
		releaseAudio()
		isPrepared = false
		isPlaying = false
	}

	private fun releaseAudio() {
		runCatching { audioPlayer?.release() }
		audioPlayer = null
		boundAudioSource = null
	}
}

private fun mediaUri(source: String): Uri {
	val parsed = Uri.parse(source)
	return if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(source)) else parsed
}

private fun segmentAt(segments: List<TranscriptionSegment>, positionMs: Long): TranscriptionSegment? {
	if (segments.isEmpty()) return null
	return segments.lastOrNull { it.startTimeMs <= positionMs } ?: segments.first()
}

private fun activeWordIndex(segment: TranscriptionSegment, positionMs: Long): Int? {
	val wordCount = segment.text
		.trim()
		.split(Regex("\\s+"))
		.count { extractEnglishWord(it) != null }
	if (wordCount == 0) return null
	val segmentDuration = (segment.endTimeMs - segment.startTimeMs).coerceAtLeast(1L)
	val progress = ((positionMs - segment.startTimeMs).toFloat() / segmentDuration).coerceIn(0f, 0.9999f)
	return floor(progress * wordCount).toInt().coerceIn(0, wordCount - 1)
}

private val wordRegex = Regex("[\\p{L}\\p{M}]+(?:['’\\-][\\p{L}\\p{M}]+)*")

private fun extractEnglishWord(token: String): String? = wordRegex.find(token)?.value

private fun readableProcessingStage(stage: String): String = when (stage.uppercase()) {
	"PREPARING" -> "Preparing your lesson…"
	"TRANSCRIBING" -> "Preparing transcript…"
	"TRANSLATING" -> "Preparing translation…"
	"CAPTURED" -> "Capture ready"
	"ERROR" -> "Lesson processing stopped"
	else -> stage.lowercase().replaceFirstChar(Char::titlecase)
}

private fun formatDuration(milliseconds: Long): String {
	val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
	val minutes = totalSeconds / 60L
	val seconds = totalSeconds % 60L
	return "%d:%02d".format(minutes, seconds)
}
