package com.jacksonkasi.cliplex.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ML Kit on-device translation wrapper using callback-based suspend.
 */
class TranslationEngine internal constructor(
 private val translatorFactory: (TranslatorOptions) -> Translator = Translation::getClient,
) {

 companion object {
 @Volatile private var instance: TranslationEngine? = null
 fun getInstance(): TranslationEngine =
 instance ?: synchronized(this) {
 instance ?: TranslationEngine().also { instance = it }
 }
	fun supportedLanguages(): Set<String> = TranslateLanguage.getAllLanguages().toSet()
 }

 private var translator: Translator? = null
 private var currentSourceLang: String? = null
 private var currentTargetLang: String? = null
 private var translatorReady = false

 suspend fun initialize(
 sourceLang: String,
 targetLang: String,
 ): Result<Unit> = suspendCancellableCoroutine { cont ->
 try {
 if (translatorReady && translator != null &&
 currentSourceLang == sourceLang &&
 currentTargetLang == targetLang
 ) {
 cont.resume(Result.success(Unit))
 return@suspendCancellableCoroutine
 }

 closeTranslator()

 val sourceCode = languageCode(sourceLang)
 val targetCode = languageCode(targetLang)

 if (sourceCode == null || targetCode == null) {
 cont.resume(Result.failure(
 IllegalArgumentException("Unsupported language pair: $sourceLang -> $targetLang")
 ))
 return@suspendCancellableCoroutine
 }

 val options = TranslatorOptions.Builder()
 .setSourceLanguage(sourceCode)
 .setTargetLanguage(targetCode)
 .build()

 val createdTranslator = translatorFactory(options)
 translator = createdTranslator
 currentSourceLang = sourceLang
 currentTargetLang = targetLang
 translatorReady = false
 cont.invokeOnCancellation { discardTranslator(createdTranslator) }
 val conditions = DownloadConditions.Builder().build()
 createdTranslator.downloadModelIfNeeded(conditions)
 .addOnSuccessListener {
 if (translator === createdTranslator && cont.isActive) {
 translatorReady = true
 cont.resume(Result.success(Unit))
 } else {
 discardTranslator(createdTranslator)
 if (cont.isActive) cont.resume(Result.failure(
 IllegalStateException("Translation model initialization was superseded")
 ))
 }
 }
 .addOnFailureListener { error ->
 discardTranslator(createdTranslator)
 if (cont.isActive) cont.resume(Result.failure(error))
 }
 } catch (e: Exception) {
 closeTranslator()
 if (cont.isActive) cont.resume(Result.failure(e))
 }
 }

 suspend fun translate(text: String): Result<String> = suspendCancellableCoroutine { cont ->
 val translator = this@TranslationEngine.translator
 if (translator == null) {
 cont.resume(Result.failure(IllegalStateException("TranslationEngine not initialized")))
 return@suspendCancellableCoroutine
 }

 if (text.isBlank()) {
 cont.resume(Result.success(text))
 return@suspendCancellableCoroutine
 }

 translator.translate(text)
 .addOnSuccessListener { result -> if (cont.isActive) cont.resume(Result.success(result)) }
 .addOnFailureListener { e -> if (cont.isActive) cont.resume(Result.failure(e)) }
 }

 suspend fun translateBatch(texts: List<String>): Result<List<String>> = suspendCancellableCoroutine { cont ->
 val translator = this@TranslationEngine.translator
 if (translator == null) {
 cont.resume(Result.failure(IllegalStateException("TranslationEngine not initialized")))
 return@suspendCancellableCoroutine
 }

 val results = mutableListOf<String>()
 var pending = texts.size
 var failed = false

 if (texts.isEmpty()) {
 cont.resume(Result.success(emptyList()))
 return@suspendCancellableCoroutine
 }

 for (text in texts) {
 translator.translate(text)
 .addOnSuccessListener { result ->
 results.add(result)
 pending--
 if (pending == 0 && !failed) {
 cont.resume(Result.success(results))
 }
 }
 .addOnFailureListener { e ->
 failed = true
 pending--
 if (pending == 0) {
 cont.resume(Result.failure(e))
 }
 }
 }
 }

 fun close() {
 closeTranslator()
 }

 private fun closeTranslator() {
 val previous = translator
 translator = null
 currentSourceLang = null
 currentTargetLang = null
 translatorReady = false
 try {
 previous?.close()
 } catch (_: Exception) { }
 }

 private fun discardTranslator(candidate: Translator) {
 if (translator === candidate) closeTranslator()
 else try {
 candidate.close()
 } catch (_: Exception) { }
 }

 private fun languageCode(language: String): String? {
 TranslateLanguage.fromLanguageTag(language.lowercase())?.let { return it }
 return when (language.lowercase()) {
 "en", "english" -> TranslateLanguage.ENGLISH
 "es", "spanish" -> TranslateLanguage.SPANISH
 "fr", "french" -> TranslateLanguage.FRENCH
 "de", "german" -> TranslateLanguage.GERMAN
 "it", "italian" -> TranslateLanguage.ITALIAN
 "pt", "portuguese" -> TranslateLanguage.PORTUGUESE
 "nl", "dutch" -> TranslateLanguage.DUTCH
 "ru", "russian" -> TranslateLanguage.RUSSIAN
 "ja", "japanese" -> TranslateLanguage.JAPANESE
 "ko", "korean" -> TranslateLanguage.KOREAN
 "zh", "chinese" -> TranslateLanguage.CHINESE
 "ar", "arabic" -> TranslateLanguage.ARABIC
 "hi", "hindi" -> TranslateLanguage.HINDI
 "bn", "bengali" -> TranslateLanguage.BENGALI
 "turkish" -> TranslateLanguage.TURKISH
 "vietnamese" -> TranslateLanguage.VIETNAMESE
 "thai" -> TranslateLanguage.THAI
 "indonesian" -> TranslateLanguage.INDONESIAN
 "malay" -> TranslateLanguage.MALAY
 "ta", "tamil" -> TranslateLanguage.TAMIL
 "te", "telugu" -> TranslateLanguage.TELUGU
 "kn", "kannada" -> TranslateLanguage.KANNADA
 "mr", "marathi" -> TranslateLanguage.MARATHI
 "urdu" -> TranslateLanguage.URDU
 "persian" -> TranslateLanguage.PERSIAN
 "polish" -> TranslateLanguage.POLISH
 "czech" -> TranslateLanguage.CZECH
 "greek" -> TranslateLanguage.GREEK
 "hebrew" -> TranslateLanguage.HEBREW
 "romanian" -> TranslateLanguage.ROMANIAN
 "hungarian" -> TranslateLanguage.HUNGARIAN
 "ukrainian" -> TranslateLanguage.UKRAINIAN
 "swedish" -> TranslateLanguage.SWEDISH
 "danish" -> TranslateLanguage.DANISH
 "norwegian" -> TranslateLanguage.NORWEGIAN
 "finnish" -> TranslateLanguage.FINNISH
 else -> null
 }
 }
}
