package com.learnthis.translation

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit on-device translation wrapper using callback-based suspend.
 */
class TranslationEngine private constructor() {

 companion object {
 @Volatile private var instance: TranslationEngine? = null
 fun getInstance(): TranslationEngine =
 instance ?: synchronized(this) {
 instance ?: TranslationEngine().also { instance = it }
 }
 }

 private var translator: Translator? = null
 private var currentSourceLang: String? = null
 private var currentTargetLang: String? = null

 suspend fun initialize(
 context: Context,
 sourceLang: String,
 targetLang: String,
 ): Result<Unit> = suspendCancellableCoroutine { cont ->
 try {
 if (translator != null &&
 currentSourceLang == sourceLang &&
 currentTargetLang == targetLang
 ) {
 cont.resume(Result.success(Unit))
 return@suspendCancellableCoroutine
 }

 translator?.close()

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

 translator = Translation.getClient(options)
 currentSourceLang = sourceLang
 currentTargetLang = targetLang

 cont.resume(Result.success(Unit))
 } catch (e: Exception) {
 cont.resumeWithException(e)
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
 .addOnSuccessListener { result -> cont.resume(Result.success(result)) }
 .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
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
 try {
 translator?.close()
 } catch (e: Exception) { }
 translator = null
 currentSourceLang = null
 currentTargetLang = null
 }

 private fun languageCode(language: String): String? {
 return when (language.lowercase()) {
 "english" -> TranslateLanguage.ENGLISH
 "spanish" -> TranslateLanguage.SPANISH
 "french" -> TranslateLanguage.FRENCH
 "german" -> TranslateLanguage.GERMAN
 "italian" -> TranslateLanguage.ITALIAN
 "portuguese" -> TranslateLanguage.PORTUGUESE
 "dutch" -> TranslateLanguage.DUTCH
 "russian" -> TranslateLanguage.RUSSIAN
 "japanese" -> TranslateLanguage.JAPANESE
 "korean" -> TranslateLanguage.KOREAN
 "chinese" -> TranslateLanguage.CHINESE
 "arabic" -> TranslateLanguage.ARABIC
 "hindi" -> TranslateLanguage.HINDI
 "bengali" -> TranslateLanguage.BENGALI
 "turkish" -> TranslateLanguage.TURKISH
 "vietnamese" -> TranslateLanguage.VIETNAMESE
 "thai" -> TranslateLanguage.THAI
 "indonesian" -> TranslateLanguage.INDONESIAN
 "malay" -> TranslateLanguage.MALAY
 "tamil" -> TranslateLanguage.TAMIL
 "telugu" -> TranslateLanguage.TELUGU
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
