package com.learnthis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnthis.common.AppLanguage
import com.learnthis.domain.model.ModelDownloadProgress
import com.learnthis.service.CaptureService
import com.learnthis.ui.screen.HomeScreen
import com.learnthis.ui.screen.HistoryScreen
import com.learnthis.ui.screen.ModelManagementScreen
import com.learnthis.ui.screen.OnboardingScreen
import com.learnthis.ui.theme.LearnThisTheme
import com.learnthis.ui.viewmodel.HomeViewModel
import com.learnthis.ui.viewmodel.ModelManagementViewModel
import com.learnthis.ui.viewmodel.OnboardingViewModel

class MainActivity : ComponentActivity() {
	private var overlayGranted by mutableStateOf(false)
	private var setupMessage by mutableStateOf<String?>(null)

	private val mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK && result.data != null) {
			setupMessage = null
			val serviceIntent = Intent(this, CaptureService::class.java).apply {
				action = CaptureService.ACTION_START
				putExtra(CaptureService.EXTRA_MEDIA_PROJECTION_RESULT_CODE, result.resultCode)
				putExtra(CaptureService.EXTRA_MEDIA_PROJECTION_RESULT_DATA, result.data)
			}
			ContextCompat.startForegroundService(this, serviceIntent)
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
		setContent {
			LearnThisTheme {
				val locator = (application as LearnThisApplication).serviceLocator
				val onboardingViewModel: OnboardingViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
					override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
						@Suppress("UNCHECKED_CAST") return OnboardingViewModel(locator.preferencesRepository) as T
					}
				})
				val modelViewModel: ModelManagementViewModel = viewModel(factory = locator.modelManagementViewModelFactory)
				val onboardingComplete by onboardingViewModel.isOnboardingCompleted.collectAsState()
				val selectedLanguage by onboardingViewModel.selectedLanguage.collectAsState()
				val modelState by modelViewModel.uiState.collectAsState()

				when {
					!onboardingComplete -> OnboardingScreen(
						languages = AppLanguage.onboardingLanguages,
						selectedLanguage = selectedLanguage,
						onLanguageSelected = onboardingViewModel::selectLanguage,
						onContinue = onboardingViewModel::completeOnboarding,
						modifier = Modifier.fillMaxSize(),
					)
					modelState.isChecking || modelState.models.none { it.progress is ModelDownloadProgress.Ready } ->
						ModelManagementScreen(
							onBack = { }, showBackButton = false,
							modifier = Modifier.fillMaxSize(),
						)
					else -> {
						val homeViewModel: HomeViewModel = viewModel(factory = locator.homeViewModelFactory)
						var showHistory by remember { mutableStateOf(false) }
						var showSettings by remember { mutableStateOf(false) }
						if (showHistory) {
							HistoryScreen(
								onBack = { showHistory = false },
								historyViewModel = viewModel(factory = locator.historyViewModelFactory),
							)
						} else if (showSettings) {
							ModelManagementScreen(
								onBack = { showSettings = false },
								showSystemSettings = true,
								overlayGranted = overlayGranted,
								onOpenOverlaySettings = ::openOverlaySettings,
								onOpenNotificationSettings = ::openNotificationSettings,
								onChangeLanguage = onboardingViewModel::restartOnboarding,
								modifier = Modifier.fillMaxSize(),
							)
						} else {
							HomeScreen(
								selectedLanguage = selectedLanguage,
								homeViewModel = homeViewModel,
								onStartLearning = ::beginLearningMode,
								onBeginCapture = { startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_BEGIN)) },
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

	private fun beginLearningMode() {
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
		mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
	}

	override fun onResume() {
		super.onResume()
		overlayGranted = Settings.canDrawOverlays(this)
	}
}
