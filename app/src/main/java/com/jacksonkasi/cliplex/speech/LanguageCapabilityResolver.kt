package com.jacksonkasi.cliplex.speech

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class LanguageCapabilityResolver(
	private val androidEngine: AndroidSpeechRecognizerEngine,
	private val fallbackEngine: SpeechToTextEngine,
	private val remoteModelManager: RemoteModelManager = RemoteModelManager.getInstance(),
) {
	private val supportedTranslationLanguages: Set<String>
		get() = TranslateLanguage.getAllLanguages().toSet()

	suspend fun resolve(
		learningLanguage: LearningLanguage,
		translationLanguage: String,
	): LanguageCapabilities {
		val speech = androidEngine.isAvailable(learningLanguage)
		val translationSupported = translationLanguage in supportedTranslationLanguages
		val translationDownloaded = if (!translationSupported) false else {
			val model = TranslateRemoteModel.Builder(translationLanguage).build()
			remoteModelManager.isModelDownloaded(model).awaitResult().getOrDefault(false)
		}
		return LanguageCapabilities(
			language = learningLanguage,
			androidSpeechStatus = speech.languageStatus,
			translationSupported = translationSupported,
			translationModelDownloaded = translationDownloaded,
			fallbackAvailable = fallbackEngine.isAvailable(learningLanguage).available,
		)
	}

	fun translationLanguages(): Set<String> = supportedTranslationLanguages
}

private suspend fun <T> Task<T>.awaitResult(): Result<T> = suspendCancellableCoroutine { continuation ->
	addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(Result.success(value)) }
	addOnFailureListener { error -> if (continuation.isActive) continuation.resume(Result.failure(error)) }
}
