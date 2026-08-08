package com.jacksonkasi.cliplex.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jacksonkasi.cliplex.data.local.ClipLexDatabase
import com.jacksonkasi.cliplex.ai.LocalGemmaTutor
import com.jacksonkasi.cliplex.data.repository.ModelRepository
import com.jacksonkasi.cliplex.data.repository.PreferencesRepository
import com.jacksonkasi.cliplex.data.repository.SessionRepository
import com.jacksonkasi.cliplex.domain.model.ModelResolver
import com.jacksonkasi.cliplex.speech.AndroidSpeechRecognizerEngine
import com.jacksonkasi.cliplex.speech.SpeechRecognitionCoordinator
import com.jacksonkasi.cliplex.speech.SpeechMetricsRecorder
import com.jacksonkasi.cliplex.speech.WhisperSpeechRecognizerEngine
import com.jacksonkasi.cliplex.speech.LanguageCapabilityResolver
import com.jacksonkasi.cliplex.translation.TranslationEngine
import com.jacksonkasi.cliplex.ui.viewmodel.HomeViewModel
import com.jacksonkasi.cliplex.ui.viewmodel.HistoryViewModel
import com.jacksonkasi.cliplex.ui.viewmodel.ModelManagementViewModel
import com.jacksonkasi.cliplex.whisper.WhisperEngine

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ServiceLocator private constructor(private val context: Context) {
	val preferencesRepository by lazy { PreferencesRepository(context.dataStore) }
	val modelRepository by lazy { ModelRepository(context) }
	val database by lazy {
		Room.databaseBuilder(context, ClipLexDatabase::class.java, "cliplex.db")
			.addMigrations(MIGRATION_1_2)
			.build()
	}
	val sessionMediaDirectory: java.io.File by lazy {
		context.getDir("learning_sessions", Context.MODE_PRIVATE).apply { mkdirs() }
	}
	val sessionRepository by lazy { SessionRepository(database.sessionDao(), sessionMediaDirectory) }
	val whisperEngine by lazy { WhisperEngine() }
	val translationEngine: TranslationEngine by lazy { TranslationEngine.getInstance() }
	val localGemmaTutor by lazy { LocalGemmaTutor(context) }
	val modelResolver by lazy { ModelResolver() }
	val androidSpeechRecognizerEngine by lazy { AndroidSpeechRecognizerEngine(context) }
	val whisperSpeechRecognizerEngine by lazy {
		WhisperSpeechRecognizerEngine(whisperEngine, modelRepository, modelResolver)
	}
	val speechRecognitionCoordinator by lazy {
		SpeechRecognitionCoordinator(
			androidSpeechRecognizerEngine,
			whisperSpeechRecognizerEngine,
			SpeechMetricsRecorder { metrics ->
				// Technical routing/timing only. Never include transcript or PCM content.
				Log.i("SpeechMetrics", metrics.toString())
			},
		)
	}
	val languageCapabilityResolver by lazy {
		LanguageCapabilityResolver(androidSpeechRecognizerEngine, whisperSpeechRecognizerEngine)
	}

	val modelManagementViewModelFactory: ViewModelProvider.Factory by lazy {
		SimpleFactory { ModelManagementViewModel(modelRepository, preferencesRepository, modelResolver) }
	}
	val homeViewModelFactory: ViewModelProvider.Factory by lazy {
		SimpleFactory {
			HomeViewModel(
				modelRepository = modelRepository,
				preferencesRepository = preferencesRepository,
				sessionRepository = sessionRepository,
				whisperEngine = whisperEngine,
				translationEngine = translationEngine,
				modelResolver = modelResolver,
				speechRecognitionCoordinator = speechRecognitionCoordinator,
				whisperSpeechRecognizerEngine = whisperSpeechRecognizerEngine,
				localGemmaTutor = localGemmaTutor,
			)
		}
	}
	val historyViewModelFactory: ViewModelProvider.Factory by lazy {
		SimpleFactory { HistoryViewModel(sessionRepository) }
	}

	companion object {
		private val MIGRATION_1_2 = object : Migration(1, 2) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL("ALTER TABLE sessions ADD COLUMN title TEXT NOT NULL DEFAULT 'Captured lesson'")
				db.execSQL("ALTER TABLE sessions ADD COLUMN videoPath TEXT")
				db.execSQL("ALTER TABLE sessions ADD COLUMN audioPath TEXT")
				db.execSQL("ALTER TABLE sessions ADD COLUMN segmentsJson TEXT NOT NULL DEFAULT '[]'")
				db.execSQL("ALTER TABLE sessions ADD COLUMN processingState TEXT NOT NULL DEFAULT 'READY'")
				db.execSQL("ALTER TABLE sessions ADD COLUMN captureError TEXT")
			}
		}
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
