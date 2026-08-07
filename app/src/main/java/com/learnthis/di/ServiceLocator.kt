package com.learnthis.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.learnthis.data.local.LearnThisDatabase
import com.learnthis.data.repository.ModelRepository
import com.learnthis.data.repository.PreferencesRepository
import com.learnthis.data.repository.SessionRepository
import com.learnthis.translation.TranslationEngine
import com.learnthis.ui.viewmodel.HomeViewModel
import com.learnthis.ui.viewmodel.HistoryViewModel
import com.learnthis.ui.viewmodel.ModelManagementViewModel
import com.learnthis.whisper.WhisperEngine

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ServiceLocator private constructor(private val context: Context) {
	val preferencesRepository by lazy { PreferencesRepository(context.dataStore) }
	val modelRepository by lazy { ModelRepository(context) }
	val database by lazy {
		Room.databaseBuilder(context, LearnThisDatabase::class.java, "learn-this.db").build()
	}
	val sessionRepository by lazy { SessionRepository(database.sessionDao()) }
	val whisperEngine by lazy { WhisperEngine() }
	val translationEngine: TranslationEngine by lazy { TranslationEngine.getInstance() }

	val modelManagementViewModelFactory: ViewModelProvider.Factory by lazy {
		SimpleFactory { ModelManagementViewModel(modelRepository) }
	}
	val homeViewModelFactory: ViewModelProvider.Factory by lazy {
		SimpleFactory {
			HomeViewModel(
				modelRepository = modelRepository,
				preferencesRepository = preferencesRepository,
				sessionRepository = sessionRepository,
				whisperEngine = whisperEngine,
				translationEngine = translationEngine,
			)
		}
	}
	val historyViewModelFactory: ViewModelProvider.Factory by lazy {
		SimpleFactory { HistoryViewModel(sessionRepository) }
	}

	companion object {
		@Volatile private var instance: ServiceLocator? = null
		fun getInstance(application: Context): ServiceLocator = instance ?: synchronized(this) {
			instance ?: ServiceLocator(application.applicationContext).also { instance = it }
		}
	}
}

private class SimpleFactory(private val createViewModel: () -> ViewModel) : ViewModelProvider.Factory {
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		@Suppress("UNCHECKED_CAST")
		return createViewModel() as T
	}
}
