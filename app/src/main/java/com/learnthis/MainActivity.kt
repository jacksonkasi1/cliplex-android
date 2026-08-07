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
	private var returningFromOverlaySettings = false

	private val mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK && result.data != null) {
			val serviceIntent = Intent(this, CaptureService::class.java).apply {
				action = CaptureService.ACTION_START
				putExtra(CaptureService.EXTRA_MEDIA_PROJECTION_RESULT_CODE, result.resultCode)
				putExtra(CaptureService.EXTRA_MEDIA_PROJECTION_RESULT_DATA, result.data)
			}
			ContextCompat.startForegroundService(this, serviceIntent)
		}
	}

	private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
		if (results[Manifest.permission.RECORD_AUDIO] == true ||
			ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
			requestMediaProjection()
		}
	}

	private val overlaySettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		returningFromOverlaySettings = false
		requestRuntimePermissions()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
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
							onBack = { }, onModelsReady = { }, modifier = Modifier.fillMaxSize(),
						)
					else -> {
						val homeViewModel: HomeViewModel = viewModel(factory = locator.homeViewModelFactory)
						var showHistory by remember { mutableStateOf(false) }
						if (showHistory) {
							HistoryScreen(
								onBack = { showHistory = false },
								historyViewModel = viewModel(factory = locator.historyViewModelFactory),
							)
						} else {
							HomeScreen(
								selectedLanguage = selectedLanguage,
								homeViewModel = homeViewModel,
								onStartLearning = ::beginLearningMode,
								onBeginCapture = { startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_BEGIN)) },
								onChangeLanguage = onboardingViewModel::restartOnboarding,
								onOpenHistory = { showHistory = true },
								modifier = Modifier.fillMaxSize(),
							)
						}
					}
				}
			}
		}
	}

	private fun beginLearningMode() {
		if (BuildConfig.OVERLAY_SUPPORTED && !Settings.canDrawOverlays(this)) {
			returningFromOverlaySettings = true
			overlaySettingsLauncher.launch(Intent(
				Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
				Uri.parse("package:$packageName"),
			))
		} else {
			requestRuntimePermissions()
		}
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
}
