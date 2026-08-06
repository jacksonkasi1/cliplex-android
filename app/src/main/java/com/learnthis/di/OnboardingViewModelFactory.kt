package com.learnthis.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.learnthis.data.repository.PreferencesRepository
import com.learnthis.ui.viewmodel.OnboardingViewModel

class OnboardingViewModelFactory(
 private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
 override fun <T : ViewModel> create(modelClass: Class<T>): T {
 if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
 @Suppress("UNCHECKED_CAST")
 return OnboardingViewModel(preferencesRepository) as T
 }
 throw IllegalArgumentException("Unknown ViewModel class")
 }
}
