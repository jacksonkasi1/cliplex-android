package com.jacksonkasi.cliplex

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.speech.tts.TextToSpeech
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.common.languageForWord
import com.jacksonkasi.cliplex.domain.model.MediaView
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.availableWhen
import com.jacksonkasi.cliplex.domain.model.defaultMediaView
import com.jacksonkasi.cliplex.service.CaptureService
import com.jacksonkasi.cliplex.speech.PronunciationRecorder
import com.jacksonkasi.cliplex.ui.screen.HomeScreen
import com.jacksonkasi.cliplex.ui.screen.HistoryScreen
import com.jacksonkasi.cliplex.ui.screen.ModelManagementScreen
import com.jacksonkasi.cliplex.ui.screen.LearningDisplayMode
import com.jacksonkasi.cliplex.ui.screen.LearningSessionScreen
import com.jacksonkasi.cliplex.ui.screen.LessonPreparingScreen
import com.jacksonkasi.cliplex.ui.screen.OnboardingScreen
import com.jacksonkasi.cliplex.ui.screen.WordMeaningUi
import com.jacksonkasi.cliplex.ui.theme.ClipLexTheme
import com.jacksonkasi.cliplex.ui.viewmodel.HomeViewModel
import com.jacksonkasi.cliplex.ui.viewmodel.OnboardingViewModel
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
	companion object {
		private const val TAG = "MainActivity"
		const val ACTION_FINISH_CAPTURE_AND_OPEN = "com.jacksonkasi.cliplex.action.FINISH_CAPTURE_AND_OPEN"
	}

	private var overlayGranted by mutableStateOf(false)
	private var setupMessage by mutableStateOf<String?>(null)
	private var requestedLessonId by mutableStateOf<Long?>(null)
	private var waitingForLesson by mutableStateOf(false)
	private var finishRequestedAfterSessionId = 0L
	private var textToSpeech: TextToSpeech? = null
	private var pendingSpokenWord: String? = null
	private var pendingPronunciationLanguage = "en"
	private var pendingPronunciationResult: ((String?) -> Unit)? = null

	private val pronunciationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if (granted) launchPronunciationRecognition(pendingPronunciationLanguage)
		else {
			pendingPronunciationResult?.invoke(null)
			pendingPronunciationResult = null
			setupMessage = "Microphone permission is required for pronunciation practice."
		}
	}

	private val mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		Log.i(TAG, "MediaProjection resultCode=${result.resultCode} hasData=${result.data != null}")
		if (result.resultCode == Activity.RESULT_OK && result.data != null) {
			setupMessage = null
			val serviceIntent = Intent(this, CaptureService::class.java).apply {
				action = CaptureService.ACTION_START
				putExtra(CaptureService.EXTRA_MEDIA_PROJECTION_RESULT_CODE, result.resultCode)
				putExtra(CaptureService.EXTRA_MEDIA_PROJECTION_RESULT_DATA, result.data)
			}
			try {
				ContextCompat.startForegroundService(this, serviceIntent)
			} catch (error: RuntimeException) {
				Log.e(TAG, "Could not start playback-capture service", error)
				setupMessage = "Playback capture could not start: ${error.message ?: error.javaClass.simpleName}"
			}
		} else setupMessage = "Screen-capture permission is required to capture playback audio."
	}

	private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
		if (results[Manifest.permission.RECORD_AUDIO] == true ||
			ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
			requestMediaProjection()
		} else setupMessage = "Audio permission is required for playback capture."
	}

	private val overlaySettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		overlayGranted = Settings.canDrawOverlays(this)
		startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_REFRESH_OVERLAY))
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		overlayGranted = Settings.canDrawOverlays(this)
		handleNavigationIntent(intent)
		setContent {
			ClipLexTheme {
				val locator = (application as ClipLexApplication).serviceLocator
				val onboardingViewModel: OnboardingViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
					override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
						@Suppress("UNCHECKED_CAST") return OnboardingViewModel(
							preferencesRepository = locator.preferencesRepository,
							androidSpeechRecognizerEngine = locator.androidSpeechRecognizerEngine,
							modelRepository = locator.modelRepository,
						) as T
					}
				})
				val onboardingState by onboardingViewModel.uiState.collectAsState()
				val latestCapturedSession by CaptureService.latestSession.collectAsState()
				LaunchedEffect(waitingForLesson, latestCapturedSession?.id) {
					val latestId = latestCapturedSession?.id ?: return@LaunchedEffect
					if (waitingForLesson && latestId > finishRequestedAfterSessionId) {
						requestedLessonId = latestId
						waitingForLesson = false
					}
				}

				when {
					!onboardingState.isOnboardingCompleted || onboardingState.learningLanguage == null -> OnboardingScreen(
						motherTongueLanguages = AppLanguage.entries.filter {
							it.tag in com.jacksonkasi.cliplex.translation.TranslationEngine.supportedLanguages()
						},
						selectedMotherTongue = onboardingState.motherTongue,
						selectedLearningLanguage = onboardingState.learningLanguage,
						selectedSpeechQuality = onboardingState.speechQuality,
						isSaving = onboardingState.isSaving,
						onMotherTongueSelected = onboardingViewModel::selectMotherTongue,
						onLearningLanguageSelected = onboardingViewModel::selectLearningLanguage,
						onSpeechQualitySelected = onboardingViewModel::selectSpeechQuality,
						onContinue = onboardingViewModel::completeOnboarding,
						speechLanguageStatus = onboardingState.speechLanguageStatus,
						fallbackSpeechReady = onboardingState.fallbackSpeechReady,
						onDownloadSpeech = onboardingViewModel::downloadSelectedSpeechLanguage,
						errorMessage = onboardingState.errorMessage,
						modifier = Modifier.fillMaxSize(),
					)
					else -> {
						val homeViewModel: HomeViewModel = viewModel(factory = locator.homeViewModelFactory)
						val homeState by homeViewModel.uiState.collectAsState()
						var showHistory by remember { mutableStateOf(false) }
						var showSettings by remember { mutableStateOf(false) }
						var displayMode by remember(requestedLessonId) { mutableStateOf(LearningDisplayMode.SENTENCE) }
						var mediaView by remember(requestedLessonId) { mutableStateOf(defaultMediaView(hasVideo = true)) }
						LaunchedEffect(requestedLessonId) {
							requestedLessonId?.let(homeViewModel::openSession)
						}
						LaunchedEffect(showSettings) {
							if (!showSettings) homeViewModel.refreshResolvedModel()
						}
						BackHandler(
							enabled = requestedLessonId != null || waitingForLesson || showHistory || showSettings,
						) {
							when {
								requestedLessonId != null -> {
									requestedLessonId = null
									homeViewModel.closeSession()
								}
								waitingForLesson -> waitingForLesson = false
								showHistory -> showHistory = false
								showSettings -> showSettings = false
							}
						}
						val requestedId = requestedLessonId
						val activeLesson = homeState.activeSession?.takeIf { it.id == requestedId }
						when {
							requestedId != null && activeLesson == null -> LessonPreparingScreen(
								onBack = {
									requestedLessonId = null
									homeViewModel.closeSession()
								},
							)
							requestedId != null && activeLesson != null -> {
								LaunchedEffect(requestedId, activeLesson.videoPath) {
								if (activeLesson.videoPath.isNullOrBlank()) mediaView = MediaView.AUDIO
								}
								val selectedWord = homeState.selectedWord
								val selectedExampleTranslation = selectedWord?.let { details ->
									homeState.segments.firstOrNull { it.text == details.exampleSentence }?.translatedText
								}
								LearningSessionScreen(
									session = activeLesson,
									segments = homeState.segments,
									processingStage = homeState.processingStage,
									displayMode = displayMode,
									mediaView = mediaView.availableWhen(!activeLesson.videoPath.isNullOrBlank()),
									selectedWord = selectedWord?.word,
									selectedMeaning = selectedWord?.takeUnless { it.isLoading }?.let { details ->
										WordMeaningUi(
											pronunciation = com.jacksonkasi.cliplex.common.latinPronunciation(details.word),
											translatedMeaning = details.meaning,
											meaningLanguage = onboardingState.motherTongue?.displayName ?: "Your language",
											definition = details.error,
											example = details.exampleSentence,
											translatedExample = selectedExampleTranslation,
										)
									},
									isSelectedWordSaved = selectedWord?.word?.lowercase(Locale.ROOT)
										?.let { it in homeState.savedWords } == true,
									onBack = {
										requestedLessonId = null
										homeViewModel.closeSession()
									},
									onDisplayModeChange = { displayMode = it },
									onMediaViewChange = { mediaView = it },
									onWordTap = { word ->
										val example = homeState.segments.firstOrNull { segment ->
											segment.text.split(Regex("\\s+")).any { token ->
												token.trim { !it.isLetter() }.equals(word, ignoreCase = true)
											}
										}?.text ?: activeLesson.title
										homeViewModel.selectWord(word, example)
									},
									onDismissWord = homeViewModel::dismissWord,
									onSaveWord = {
										homeViewModel.setSelectedWordSaved(
											it.lowercase(Locale.ROOT) !in homeState.savedWords,
										)
									},
									onPronounceWord = ::pronounceWord,
									onDeleteVideo = { homeViewModel.deleteVideo(activeLesson.id) },
									onDeleteLesson = {
										homeViewModel.deleteLesson(activeLesson.id) { requestedLessonId = null }
									},
									onReanalyze = { homeViewModel.reanalyzeSession(activeLesson.id) },
									onChangeLanguage = onboardingViewModel::restartOnboarding,
									modifier = Modifier.fillMaxSize(),
								)
							}
							waitingForLesson -> LessonPreparingScreen(onBack = {
								waitingForLesson = false
							})
							showHistory -> {
							HistoryScreen(
								onBack = { showHistory = false },
								onOpenSession = { id ->
									requestedLessonId = id
									showHistory = false
								},
								historyViewModel = viewModel(factory = locator.historyViewModelFactory),
							)
							}
							showSettings -> {
							ModelManagementScreen(
								onBack = { showSettings = false },
								showSystemSettings = true,
								overlayGranted = overlayGranted,
								onOpenOverlaySettings = ::openOverlaySettings,
								onOpenNotificationSettings = ::openNotificationSettings,
								onChangeLanguage = onboardingViewModel::restartOnboarding,
								modifier = Modifier.fillMaxSize(),
							)
							}
							else -> {
							HomeScreen(
								selectedLanguage = onboardingState.motherTongue,
								homeViewModel = homeViewModel,
								onStartLearning = ::beginLearningMode,
								onBeginCapture = { startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_BEGIN)) },
								onFinishCapture = ::finishCaptureAndOpen,
								onOpenHistory = { showHistory = true },
								onOpenSettings = { showSettings = true },
								onChangeLanguage = onboardingViewModel::restartOnboarding,
								onPronounceWord = ::pronounceWord,
								onRecognizePronunciation = ::recognizePronunciation,
								onShowFloatingControl = {
									startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_ENABLE_OVERLAY))
								},
								overlayGranted = overlayGranted,
								setupMessage = setupMessage,
								modifier = Modifier.fillMaxSize(),
							)
							}
						}
					}
				}
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handleNavigationIntent(intent)
	}

	private fun handleNavigationIntent(navigationIntent: Intent?) {
		navigationIntent ?: return
		val lessonId = navigationIntent.getLongExtra(CaptureService.EXTRA_OPEN_LESSON_ID, 0L)
		if (lessonId > 0L) {
			requestedLessonId = lessonId
			waitingForLesson = false
		}
		if (navigationIntent.action == ACTION_FINISH_CAPTURE_AND_OPEN) {
			finishCaptureAndOpen()
		}
		navigationIntent.action = null
		navigationIntent.removeExtra(CaptureService.EXTRA_OPEN_LESSON_ID)
	}

	private fun finishCaptureAndOpen() {
		finishRequestedAfterSessionId = CaptureService.latestSession.value?.id ?: 0L
		waitingForLesson = true
		startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_FINISH))
	}

	private fun pronounceWord(word: String) {
		val active = textToSpeech
		if (active != null) {
			active.language = Locale.forLanguageTag(languageForWord(word))
			active.speak(word, TextToSpeech.QUEUE_FLUSH, null, "cliplex-word")
			return
		}
		pendingSpokenWord = word
		textToSpeech = TextToSpeech(applicationContext) { status ->
			if (status == TextToSpeech.SUCCESS) {
				textToSpeech?.language = Locale.forLanguageTag(languageForWord(pendingSpokenWord.orEmpty()))
				pendingSpokenWord?.let { pending ->
					textToSpeech?.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "cliplex-word")
				}
			}
			pendingSpokenWord = null
		}
	}

	private fun recognizePronunciation(word: String, languageTag: String, onResult: (String?) -> Unit) {
		pendingPronunciationLanguage = languageTag.ifBlank { languageForWord(word) }
		pendingPronunciationResult = onResult
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
			launchPronunciationRecognition(pendingPronunciationLanguage)
		} else pronunciationPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
	}

	private fun launchPronunciationRecognition(languageTag: String) {
		lifecycleScope.launch {
			val callback = pendingPronunciationResult
			try {
				val audio = PronunciationRecorder().record()
				val language = LearningLanguage.entries.firstOrNull { candidate ->
					languageTag.startsWith(candidate.code, ignoreCase = true) ||
					languageTag.equals(candidate.displayName, ignoreCase = true)
				} ?: LearningLanguage.ENGLISH
				val locator = (application as ClipLexApplication).serviceLocator
				val transcript = locator.whisperSpeechRecognizerEngine.transcribe(audio, language).text
				callback?.invoke(transcript.takeIf { it.isNotBlank() })
			} catch (error: Throwable) {
				Log.w(TAG, "On-device pronunciation recognition failed", error)
				callback?.invoke(null)
				setupMessage = "Pronunciation needs a downloaded speech model."
			} finally {
				pendingPronunciationResult = null
			}
		}
	}

	private fun beginLearningMode() {
		setupMessage = null
		requestRuntimePermissions()
	}

	private fun openOverlaySettings() {
		overlaySettingsLauncher.launch(Intent(
				Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
				Uri.parse("package:$packageName"),
			))
	}

	private fun openNotificationSettings() {
		startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
	}

	private fun requestRuntimePermissions() {
		val permissions = buildList {
			add(Manifest.permission.RECORD_AUDIO)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
		}.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
		if (permissions.isEmpty()) requestMediaProjection() else permissionLauncher.launch(permissions.toTypedArray())
	}

	private fun requestMediaProjection() {
		val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
		// The unconfigured intent keeps Android 14+'s single-app/full-screen chooser available.
		mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
	}

	override fun onResume() {
		super.onResume()
		overlayGranted = Settings.canDrawOverlays(this)
		if (BuildConfig.OVERLAY_SUPPORTED && overlayGranted) {
			startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_REFRESH_OVERLAY))
		}
	}

	override fun onDestroy() {
		textToSpeech?.stop()
		textToSpeech?.shutdown()
		textToSpeech = null
		super.onDestroy()
	}
}
