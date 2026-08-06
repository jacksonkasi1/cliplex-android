package com.learnthis.translation

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * ML Kit on-device translation wrapper.
 *
 * Usage:
 * 1. TranslationEngine.initialize(context, sourceLang, targetLang)
 * 2. translate(text) -> Result<String>
 * 3. close() when done
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
 ): Result<Unit> = withContext(Dispatchers.IO) {
 try {
 if (translator != null &&
 currentSourceLang == sourceLang &&
 currentTargetLang == targetLang
 ) {
 return@withContext Result.success(Unit)
 }

 translator?.close()

 val sourceCode = languageCode(sourceLang)
 val targetCode = languageCode(targetLang)

 if (sourceCode == null || targetCode == null) {
 return@withContext Result.failure(
 IllegalArgumentException("Unsupported language pair: $sourceLang -> $targetLang")
 )
 }

 val options = TranslatorOptions.Builder()
 .setSourceLanguage(sourceCode)
 .setTargetLanguage(targetCode)
 .build()

 translator = Translation.getClient(options)
 currentSourceLang = sourceLang
 currentTargetLang = targetLang

 Result.success(Unit)
 } catch (e: Exception) {
 Result.failure(e)
 }
 }

 suspend fun translate(text: String): Result<String> = withContext(Dispatchers.IO) {
 val translator = this@TranslationEngine.translator
 if (translator == null) {
 return@withContext Result.failure(IllegalStateException("TranslationEngine not initialized"))
 }

 if (text.isBlank()) {
 return@withContext Result.success(text)
 }

 try {
 val result = translator.translate(text).await()
 Result.success(result)
 } catch (e: Exception) {
 Result.failure(e)
 }
 }

 suspend fun translateBatch(texts: List<String>): Result<List<String>> = withContext(Dispatchers.IO) {
 val translator = this@TranslationEngine.translator
 if (translator == null) {
 return@withContext Result.failure(IllegalStateException("TranslationEngine not initialized"))
 }

 val results = texts.map { text ->
 try {
 val result = translator.translate(text).await()
 Result.success(result)
 } catch (e: Exception) {
 Result.failure(e)
 }
 }

 val failures = results.filter { it.isFailure }
 if (failures.isNotEmpty()) {
 return@withContext Result.failure(failures.first().exceptionOrNull() ?: Exception("Batch translation failed"))
 }

 Result.success(results.map { it.getOrNull() ?: "" })
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
