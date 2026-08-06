package com.learnthis

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.learnthis.common.AppLanguage
import com.learnthis.ui.screen.HomeScreen
import com.learnthis.ui.screen.ModelManagementScreen
import com.learnthis.ui.screen.OnboardingScreen
import com.learnthis.ui.theme.LearnThisTheme
import com.learnthis.ui.viewmodel.ModelManagementViewModel
import com.learnthis.ui.viewmodel.OnboardingViewModel
import com.learnthis.util.NativeBridge
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Verify native library loads
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
						// Checking models — splash handled by ModelManagementScreen
						ModelManagementScreen(
							onBack = { finish() },
							onModelsReady = { /* proceed */ },
							modifier = Modifier.fillMaxSize()
						)
					}
					!modelUiState.models.any { it.progress is com.learnthis.domain.model.ModelDownloadProgress.Ready } -> {
						ModelManagementScreen(
							onBack = { finish() },
							onModelsReady = { /* proceed */ },
							modifier = Modifier.fillMaxSize()
						)
					}
					else -> {
						HomeScreen(
							selectedLanguage = selectedLanguage,
							onStartLearning = { /* Phase 04 */ },
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
}
