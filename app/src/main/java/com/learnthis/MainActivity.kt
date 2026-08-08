package com.learnthis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.speech.tts.TextToSpeech
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.learnthis.common.AppLanguage
import com.learnthis.service.CaptureService
import com.learnthis.ui.screen.HomeScreen
import com.learnthis.ui.screen.HistoryScreen
import com.learnthis.ui.screen.ModelManagementScreen
import com.learnthis.ui.screen.LearningDisplayMode
import com.learnthis.ui.screen.LearningSessionScreen
import com.learnthis.ui.screen.LessonPreparingScreen
import com.learnthis.ui.screen.OnboardingScreen
import com.learnthis.ui.screen.WordMeaningUi
import com.learnthis.ui.theme.LearnThisTheme
import com.learnthis.ui.viewmodel.HomeViewModel
import com.learnthis.ui.viewmodel.ModelManagementViewModel
import com.learnthis.ui.viewmodel.OnboardingViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
	companion object {
		private const val TAG = "MainActivity"
		const val ACTION_FINISH_CAPTURE_AND_OPEN = "com.learnthis.action.FINISH_CAPTURE_AND_OPEN"
	}

	private var overlayGranted by mutableStateOf(false)
	private var setupMessage by mutableStateOf<String?>(null)
	private var requestedLessonId by mutableStateOf<Long?>(null)
	private var waitingForLesson by mutableStateOf(false)
	private var finishRequestedAfterSessionId = 0L
	private var textToSpeech: TextToSpeech? = null
	private var pendingSpokenWord: String? = null

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
		overlayGranted = Settings.canDrawOverlays(this)
		handleNavigationIntent(intent)
		setContent {
			LearnThisTheme {
				val locator = (application as LearnThisApplication).serviceLocator
				val onboardingViewModel: OnboardingViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
					override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
						@Suppress("UNCHECKED_CAST") return OnboardingViewModel(locator.preferencesRepository) as T
					}
				})
				val modelViewModel: ModelManagementViewModel = viewModel(factory = locator.modelManagementViewModelFactory)
				val onboardingState by onboardingViewModel.uiState.collectAsState()
				val modelState by modelViewModel.uiState.collectAsState()
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
						motherTongueLanguages = AppLanguage.onboardingLanguages,
						selectedMotherTongue = onboardingState.motherTongue,
						selectedLearningLanguage = onboardingState.learningLanguage,
						selectedSpeechQuality = onboardingState.speechQuality,
						isSaving = onboardingState.isSaving,
						onMotherTongueSelected = onboardingViewModel::selectMotherTongue,
						onLearningLanguageSelected = onboardingViewModel::selectLearningLanguage,
						onSpeechQualitySelected = onboardingViewModel::selectSpeechQuality,
						onContinue = onboardingViewModel::completeOnboarding,
						errorMessage = onboardingState.errorMessage,
						modifier = Modifier.fillMaxSize(),
					)
					modelState.isChecking || !modelState.requiredModelReady ->
						ModelManagementScreen(
							onBack = { }, showBackButton = false,
							modifier = Modifier.fillMaxSize(),
						)
					else -> {
						val homeViewModel: HomeViewModel = viewModel(factory = locator.homeViewModelFactory)
						val homeState by homeViewModel.uiState.collectAsState()
						LaunchedEffect(modelState.requiredModelReady, modelState.configuration?.modelType) {
							if (modelState.requiredModelReady) homeViewModel.refreshResolvedModel()
						}
						var showHistory by remember { mutableStateOf(false) }
						var showSettings by remember { mutableStateOf(false) }
						var displayMode by remember(requestedLessonId) { mutableStateOf(LearningDisplayMode.SENTENCE) }
						LaunchedEffect(requestedLessonId) {
							requestedLessonId?.let(homeViewModel::openSession)
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
								val selectedWord = homeState.selectedWord
								val selectedExampleTranslation = selectedWord?.let { details ->
									homeState.segments.firstOrNull { it.text == details.exampleSentence }?.translatedText
								}
								LearningSessionScreen(
									session = activeLesson,
									segments = homeState.segments,
									processingStage = homeState.processingStage,
									displayMode = displayMode,
									selectedWord = selectedWord?.word,
									selectedMeaning = selectedWord?.takeUnless { it.isLoading }?.let { details ->
										WordMeaningUi(
											translatedMeaning = details.meaning,
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
			active.speak(word, TextToSpeech.QUEUE_FLUSH, null, "learn-this-word")
			return
		}
		pendingSpokenWord = word
		textToSpeech = TextToSpeech(applicationContext) { status ->
			if (status == TextToSpeech.SUCCESS) {
				textToSpeech?.language = Locale.US
				pendingSpokenWord?.let { pending ->
					textToSpeech?.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "learn-this-word")
				}
			}
			pendingSpokenWord = null
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
		val consentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
		} else {
			manager.createScreenCaptureIntent()
		}
		mediaProjectionLauncher.launch(consentIntent)
	}

	override fun onResume() {
		super.onResume()
		overlayGranted = Settings.canDrawOverlays(this)
	}

	override fun onDestroy() {
		textToSpeech?.stop()
		textToSpeech?.shutdown()
		textToSpeech = null
		super.onDestroy()
	}
}
