package com.jacksonkasi.cliplex.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.domain.model.SavedWord
import com.jacksonkasi.cliplex.common.latinPronunciation
import com.jacksonkasi.cliplex.common.languageForWord
import com.jacksonkasi.cliplex.common.validWordTranslation
import com.jacksonkasi.cliplex.domain.model.TranscriptionSegment
import com.jacksonkasi.cliplex.domain.model.toUserMessage
import com.jacksonkasi.cliplex.service.CaptureService
import com.jacksonkasi.cliplex.ui.components.ClipLexBottomNav
import com.jacksonkasi.cliplex.ui.components.ClipLexCard
import com.jacksonkasi.cliplex.ui.components.ClipLexPill
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
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
			modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			Spacer(Modifier.height(2.dp))
			HomeHeader(onOpenSettings)
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
				ClipLexPill(
					text = "${compactLanguageName(state.modelName?.substringBefore(" ·") ?: "English")}  →  ${selectedLanguage?.displayName ?: "Tamil"}",
					icon = Icons.Default.Translate,
					onClick = onChangeLanguage,
				)
				ClipLexPill(text = "7 🔥", contentColor = ClipLexColors.Warm)
			}
			if (selectedTab == 2) {
				SavedWordsSection(
					words = state.savedWordDetails,
					savedWordNames = state.savedWords,
					meaningLanguage = selectedLanguage?.displayName ?: "Your language",
					meaningLanguageTag = selectedLanguage?.tag ?: "en",
					onRefresh = homeViewModel::refreshSavedWordMeanings,
					onRemove = homeViewModel::removeSavedWord,
					onPronounce = onPronounceWord,
				)
			} else if (selectedTab == 3) {
				PracticeSection(
					sessions = state.practiceSessions,
					savedWords = state.savedWordDetails,
					motherTongue = selectedLanguage?.displayName ?: "Your language",
					onSpeak = onPronounceWord,
					onRecognize = onRecognizePronunciation,
					onAskTutor = homeViewModel::askTutor,
					smartTutorInstalled = homeViewModel.isSmartTutorInstalled(),
				)
			} else {
			if (
				com.jacksonkasi.cliplex.BuildConfig.OVERLAY_SUPPORTED && overlayGranted &&
				state.overlayStatus == CaptureService.OverlayStatus.Disabled &&
				(state.captureState == CaptureService.CaptureState.Armed || state.captureState == CaptureService.CaptureState.Capturing)
			) {
				ClipLexPill(
					text = "Show float",
					icon = Icons.Default.Mic,
					onClick = onShowFloatingControl,
					modifier = Modifier.align(Alignment.Start),
				)
			}

			ListeningStatusCard(isListening, state.isProcessing, state.captureDurationMs)

			Surface(shape = RoundedCornerShape(13.dp), color = ClipLexColors.GreenSoft, modifier = Modifier.align(Alignment.CenterHorizontally)) {
				Text(
					text = if (isListening) "Tap Finish when done" else "Tap to listen ✨",
					style = MaterialTheme.typography.bodySmall,
					color = ClipLexColors.Ink,
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
				)
			}

			ListeningOrb(
				active = isListening,
				busy = isBusy,
				enabled = state.isModelReady && !isBusy,
				onClick = primaryAction,
			)

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceEvenly,
				verticalAlignment = Alignment.CenterVertically,
			) {
				RoundSupportAction(
					icon = Icons.Default.Stop,
					label = if (isListening) "Finish" else "Stop",
					color = if (isListening) ClipLexColors.Coral else ClipLexColors.Blue,
					enabled = isListening,
					onClick = onFinishCapture,
				)
				RoundSupportAction(
					icon = Icons.Default.Refresh,
					label = "Again",
					color = ClipLexColors.Green,
					enabled = state.captureState == CaptureService.CaptureState.Armed,
					onClick = onBeginCapture,
				)
			}

			val visibleMessage = setupMessage ?: state.error?.toUserMessage() ?: state.modelError
			visibleMessage?.let {
				ClipLexCard(Modifier.fillMaxWidth(), containerColor = Color(0xFFFFF4F2)) {
					Text(it, color = Color(0xFFB42318), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(15.dp))
				}
			}

			if (state.segments.isNotEmpty()) {
				Text("Recent", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
				state.segments.take(3).forEach { segment ->
					SegmentCard(segment, onPlay = { homeViewModel.playSegment(segment) }, onCopy = { copy(context, segment) })
				}
			}
			}
			Spacer(Modifier.height(6.dp))
		}

		ClipLexBottomNav(
			items = listOf(Icons.Default.Home to "Home", Icons.Default.Book to "Learn", Icons.Default.Headphones to "Words", Icons.Default.School to "Practice", Icons.Default.Person to "Profile"),
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
			modifier = Modifier.navigationBarsPadding().shadow(12.dp),
		)
	}
}

@Composable
private fun SavedWordsSection(
	words: List<SavedWord>,
	savedWordNames: Set<String>,
	meaningLanguage: String,
	meaningLanguageTag: String,
	onRefresh: () -> Unit,
	onRemove: (String) -> Unit,
	onPronounce: (String) -> Unit,
) {
	val indexed = words.associateBy(SavedWord::word)
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		ClipLexCard(Modifier.fillMaxWidth(), containerColor = ClipLexColors.GreenSoft) {
			Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
				Text("My Words", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ClipLexColors.GreenDark)
				Text("Words you saved 🌱", color = ClipLexColors.Ink)
			}
		}
		if (savedWordNames.isEmpty()) {
			ClipLexCard(Modifier.fillMaxWidth()) {
				Text("Save a word in a lesson. Its meaning appears here.", modifier = Modifier.padding(20.dp), color = ClipLexColors.InkMuted)
			}
		} else {
			savedWordNames.sorted().forEach { word ->
				val saved = indexed[word]
				val sourceLanguage = languageForWord(word, saved?.sourceLanguage)
				val displayedMeaning = validWordTranslation(word, saved?.meaning, sourceLanguage, meaningLanguageTag)
				ClipLexCard(Modifier.fillMaxWidth()) {
							Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
						Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
											Column(Modifier.weight(1f)) {
								Text("${languageName(sourceLanguage)} word", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
												Text(word.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ClipLexColors.Ink)
												latinPronunciation(word)?.let { guide ->
													Text("Say it: $guide", style = MaterialTheme.typography.bodyMedium, color = ClipLexColors.GreenDark)
												}
							}
							Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
								Surface(
									shape = CircleShape,
									color = ClipLexColors.GreenSoft,
									modifier = Modifier.size(40.dp).clickable { onPronounce(word) },
								) {
									Icon(
										Icons.AutoMirrored.Filled.VolumeUp,
										contentDescription = "Hear $word",
										tint = ClipLexColors.Green,
										modifier = Modifier.padding(9.dp),
									)
								}
								Text("Remove", modifier = Modifier.clickable { onRemove(word) }.padding(6.dp), style = MaterialTheme.typography.labelMedium, color = ClipLexColors.Coral)
							}
						}
						Text("$meaningLanguage meaning", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
						Text(
							displayedMeaning ?: "$meaningLanguage translation unavailable — tap Refresh",
							style = MaterialTheme.typography.bodyLarge,
							color = if (displayedMeaning == null) ClipLexColors.Coral else ClipLexColors.GreenDark,
						)
						saved?.example?.takeIf(String::isNotBlank)?.let { example ->
							Text("From the ${languageName(sourceLanguage)} sentence", style = MaterialTheme.typography.labelSmall, color = ClipLexColors.InkMuted)
							Text(example, style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
						}
					}
				}
			}
			Text("Refresh", modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onRefresh).padding(10.dp), color = ClipLexColors.Blue, style = MaterialTheme.typography.labelLarge)
		}
	}
}

private fun languageName(tag: String?): String = tag
	?.takeIf(String::isNotBlank)
	?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH) }
	?.takeIf(String::isNotBlank)
	?: "Original"

private fun compactLanguageName(name: String): String = if (name == "Any Language") "Any" else name

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
		Spacer(Modifier.size(38.dp))
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text("Clip", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
			Text("Lex", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Green)
		}
		Surface(shape = CircleShape, color = ClipLexColors.GreenSoft, modifier = Modifier.size(38.dp).clickable(onClick = onOpenSettings)) {
			Icon(Icons.Default.Person, contentDescription = "Profile and settings", tint = ClipLexColors.Green, modifier = Modifier.padding(8.dp))
		}
	}
}

@Composable
private fun ListeningStatusCard(active: Boolean, processing: Boolean, durationMs: Long) {
	ClipLexCard(Modifier.fillMaxWidth()) {
		Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
			Surface(shape = CircleShape, color = ClipLexColors.GreenSoft, modifier = Modifier.size(68.dp)) {
				Icon(Icons.Default.Headphones, contentDescription = null, tint = ClipLexColors.Green, modifier = Modifier.padding(16.dp))
			}
			Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
				Text(if (processing) "Creating lesson…" else if (active) "Listening…" else "Ready", style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Green)
				Text(if (active) "${durationMs / 1000}s" else "Play a video 😊", style = MaterialTheme.typography.bodyMedium, color = ClipLexColors.InkMuted)
				Waveform(active, Modifier.fillMaxWidth().height(42.dp).padding(top = 6.dp))
			}
		}
	}
}

@Composable
private fun Waveform(active: Boolean, modifier: Modifier = Modifier) {
	val transition = rememberInfiniteTransition(label = "listening-wave")
	val phase by transition.animateFloat(0f, 6.28f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "wave-phase")
	Canvas(modifier) {
		val count = 25
		val gap = size.width / count
		repeat(count) { index ->
			val animated = if (active) sin(phase + index * 0.7f) * 0.5f + 0.5f else sin(index * 1.13f) * 0.25f + 0.42f
			val height = size.height * (0.18f + animated * 0.75f)
			drawLine(
				color = if (index % 5 == 0) ClipLexColors.GreenDark else ClipLexColors.Green.copy(alpha = 0.62f),
				start = Offset(gap * index + gap / 2, (size.height - height) / 2),
				end = Offset(gap * index + gap / 2, (size.height + height) / 2),
				strokeWidth = gap * 0.38f,
				cap = StrokeCap.Round,
			)
		}
	}
}

@Composable
private fun ListeningOrb(active: Boolean, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
	Box(Modifier.fillMaxWidth().height(212.dp), contentAlignment = Alignment.Center) {
		Box(Modifier.size(206.dp).background(ClipLexColors.BlueSoft.copy(alpha = 0.7f), CircleShape))
		Box(Modifier.size(170.dp).background(ClipLexColors.GreenSoft.copy(alpha = 0.82f), CircleShape))
		Box(
			modifier = Modifier
				.size(136.dp)
				.shadow(18.dp, CircleShape, spotColor = ClipLexColors.Green.copy(alpha = 0.32f))
				.clip(CircleShape)
				.background(Brush.radialGradient(listOf(Color(0xFF41D77D), ClipLexColors.GreenDark)))
				.clickable(enabled = enabled, onClick = onClick),
			contentAlignment = Alignment.Center,
		) {
			if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp)
			else Icon(if (active) Icons.Default.Stop else Icons.Default.Mic, contentDescription = if (active) "Finish capture" else "Start listening", tint = Color.White, modifier = Modifier.size(62.dp))
		}
	}
}

@Composable
private fun RoundSupportAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
	Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Surface(shape = CircleShape, color = if (enabled) color.copy(alpha = 0.12f) else ClipLexColors.Border.copy(alpha = 0.6f), modifier = Modifier.size(48.dp).clickable(enabled = enabled, onClick = onClick)) {
			Icon(icon, contentDescription = label, tint = if (enabled) color else Color(0xFFB0B8C4), modifier = Modifier.padding(13.dp))
		}
		Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) ClipLexColors.Ink else ClipLexColors.InkMuted)
	}
}

@Composable
private fun DiagnosticsCard(state: com.jacksonkasi.cliplex.ui.viewmodel.HomeUiState, expanded: Boolean, onToggle: () -> Unit, onCopy: () -> Unit) {
	ClipLexCard(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(if (expanded) "Hide technical diagnostics" else "Technical diagnostics", modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle), style = MaterialTheme.typography.labelLarge, color = ClipLexColors.Blue)
			if (expanded) {
				Text(state.debugMessage ?: "ASR diagnostics are ready after a capture.", style = MaterialTheme.typography.bodySmall, color = ClipLexColors.InkMuted)
				state.asrDiagnostics?.let { Text("${it.status} · ${it.model} · ${it.resultPreview.orEmpty()}", style = MaterialTheme.typography.bodySmall) }
				Text("Copy diagnostics", modifier = Modifier.clickable(onClick = onCopy), color = ClipLexColors.Blue, style = MaterialTheme.typography.labelLarge)
			}
		}
	}
}

@Composable
private fun SegmentCard(segment: TranscriptionSegment, onPlay: () -> Unit, onCopy: () -> Unit) {
	ClipLexCard(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(segment.text, fontWeight = FontWeight.SemiBold, color = ClipLexColors.Ink)
			segment.translatedText?.let { Text(it, color = ClipLexColors.Green) }
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
				Icon(Icons.Default.PlayArrow, "Replay sentence", tint = ClipLexColors.Blue, modifier = Modifier.clickable(onClick = onPlay).padding(8.dp))
				Spacer(Modifier.width(6.dp))
				Icon(Icons.Default.ContentCopy, "Copy sentence", tint = ClipLexColors.InkMuted, modifier = Modifier.clickable(onClick = onCopy).padding(8.dp))
			}
		}
	}
}

private fun copy(context: Context, segment: TranscriptionSegment) = copyText(context, "ClipLex sentence", listOfNotNull(segment.text, segment.translatedText).joinToString("\n"))

private fun copyText(context: Context, label: String, text: String) {
	(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}
