package com.learnthis

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnthis.common.AppLanguage
import com.learnthis.ui.screen.HomeScreen
import com.learnthis.ui.screen.ModelManagementScreen
import com.learnthis.ui.screen.OnboardingScreen
import com.learnthis.ui.theme.LearnThisTheme
import com.learnthis.ui.viewmodel.ModelManagementViewModel
import com.learnthis.ui.viewmodel.OnboardingViewModel
import com.learnthis.util.NativeBridge

class MainActivity : ComponentActivity() {

	private var hasMediaProjectionPermission = false

	private val mediaProjectionLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
			hasMediaProjectionPermission = true
			val intent = Intent(this, com.learnthis.service.CaptureService::class.java).apply {
				action = com.learnthis.service.CaptureService.ACTION_START
				putExtra(com.learnthis.service.CaptureService.EXTRA_MEDIA_PROJECTION_RESULT_CODE, result.resultCode)
				putExtra(com.learnthis.service.CaptureService.EXTRA_MEDIA_PROJECTION_RESULT_DATA, result.data)
			}
			startService(intent)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		try {
			Log.i("MainActivity", "Native version: ${NativeBridge.getNativeVersion()}")
			Log.i("MainActivity", "Native ready: ${NativeBridge.isNativeReady()}")
		} catch (e: UnsatisfiedLinkError) {
			Log.e("MainActivity", "Native library failed to load", e)
		}

		setContent {
			LearnThisTheme {
				val serviceLocator = (application as com.learnthis.LearnThisApplication).serviceLocator
				val onboardingViewModel: OnboardingViewModel = viewModel(
					factory = object : androidx.lifecycle.ViewModelProvider.Factory {
						override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
							@Suppress("UNCHECKED_CAST")
							return OnboardingViewModel(serviceLocator.preferencesRepository) as T
						}
					}
				)
				val modelMgmtViewModel: ModelManagementViewModel = viewModel(
					factory = serviceLocator.modelManagementViewModelFactory
				)
				val modelUiState by modelMgmtViewModel.uiState.collectAsState()

				val isOnboardingCompleted by onboardingViewModel.isOnboardingCompleted.collectAsState(initial = false)
				val selectedLanguage by onboardingViewModel.selectedLanguage.collectAsState(initial = null)

				when {
					!isOnboardingCompleted -> {
						OnboardingScreen(
							languages = AppLanguage.entries,
							selectedLanguage = selectedLanguage,
							onLanguageSelected = { onboardingViewModel.selectLanguage(it) },
							onContinue = { onboardingViewModel.completeOnboarding() },
							modifier = Modifier.fillMaxSize()
						)
					}
					modelUiState.isChecking -> {
						ModelManagementScreen(
							onBack = { finish() },
							onModelsReady = { },
							modifier = Modifier.fillMaxSize()
						)
					}
					!modelUiState.models.any { it.progress is com.learnthis.domain.model.ModelDownloadProgress.Ready } -> {
						ModelManagementScreen(
							onBack = { finish() },
							onModelsReady = { },
							modifier = Modifier.fillMaxSize()
						)
					}
					else -> {
						HomeScreen(
							selectedLanguage = selectedLanguage,
							onStartLearning = { requestMediaProjection() },
							onChangeLanguage = {
								selectedLanguage?.let { onboardingViewModel.selectLanguage(it) }
							},
							modifier = Modifier.fillMaxSize()
						)
					}
				}
			}
		}
	}

	private fun requestMediaProjection() {
		val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
		mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
	}

	override fun onDestroy() {
		super.onDestroy()
	}
}
