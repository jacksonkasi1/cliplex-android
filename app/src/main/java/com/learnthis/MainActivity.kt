package com.learnthis

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.learnthis.ui.screen.HomeScreen
import com.learnthis.ui.screen.OnboardingScreen
import com.learnthis.ui.theme.LearnThisTheme
import com.learnthis.ui.viewmodel.OnboardingViewModel
import com.learnthis.util.NativeBridge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.learnthis.common.AppLanguage

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
				val viewModel: OnboardingViewModel = viewModel(
					factory = com.learnthis.di.OnboardingViewModelFactory(
						(application as com.learnthis.LearnThisApplication).serviceLocator.preferencesRepository
					)
				)
				val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState(initial = false)
				val selectedLanguage by viewModel.selectedLanguage.collectAsState(initial = null)

				if (!isOnboardingCompleted) {
					OnboardingScreen(
						languages = AppLanguage.entries,
						selectedLanguage = selectedLanguage,
						onLanguageSelected = { viewModel.selectLanguage(it) },
						onContinue = { viewModel.completeOnboarding() },
						modifier = Modifier.fillMaxSize()
					)
				} else {
					HomeScreen(
						selectedLanguage = selectedLanguage,
						onStartLearning = { /* Phase 04 */ },
						onChangeLanguage = {
							selectedLanguage?.let { viewModel.selectLanguage(it) }
						},
						modifier = Modifier.fillMaxSize()
					)
				}
			}
		}
	}
}
