package com.learnthis.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.learnthis.data.repository.PreferencesRepository
import com.learnthis.data.repository.ModelRepository
import com.learnthis.ui.viewmodel.OnboardingViewModel
import com.learnthis.ui.viewmodel.ModelManagementViewModel

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ServiceLocator private constructor(private val context: Context) {
	val preferencesRepository: PreferencesRepository by lazy {
		PreferencesRepository(context.dataStore)
	}

	val modelRepository: ModelRepository by lazy {
		ModelRepository(context, context.dataStore)
	}

	val modelManagementViewModelFactory: ViewModelProvider.Factory by lazy {
		ModelManagementViewModelFactory(modelRepository)
	}

	companion object {
		@Volatile
		private var INSTANCE: ServiceLocator? = null

		fun getInstance(application: Context): ServiceLocator {
			return INSTANCE ?: synchronized(this) {
				INSTANCE ?: ServiceLocator(application.applicationContext).also { INSTANCE = it }
			}
		}
	}
}

class ModelManagementViewModelFactory(
	private val modelRepository: ModelRepository
) : ViewModelProvider.Factory {
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		if (modelClass.isAssignableFrom(ModelManagementViewModel::class.java)) {
			@Suppress("UNCHECKED_CAST")
			return ModelManagementViewModel(modelRepository) as T
		}
		throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
	}
}
