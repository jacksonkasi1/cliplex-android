package com.jacksonkasi.cliplex.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jacksonkasi.cliplex.common.AppLanguage
import com.jacksonkasi.cliplex.domain.model.LearningLanguage
import com.jacksonkasi.cliplex.domain.model.LearningMode
import com.jacksonkasi.cliplex.domain.model.SpeechQuality
import com.jacksonkasi.cliplex.domain.model.ModelType
import com.jacksonkasi.cliplex.domain.model.RecognitionMode
import com.jacksonkasi.cliplex.domain.model.SavedWord
import com.jacksonkasi.cliplex.domain.model.toLegacyLearningMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

 companion object {
 val MOTHER_TONGUE = stringPreferencesKey("mother_tongue")
 val ONBOARDING_COMPLETED = stringPreferencesKey("onboarding_completed")
 val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
 val AUTO_TRANSLATE = booleanPreferencesKey("auto_translate")
 val LEARNING_LANGUAGE = stringPreferencesKey("learning_language")
 val SPEECH_QUALITY = stringPreferencesKey("speech_quality")
 val LEARNING_MODE = stringPreferencesKey("learning_mode")
 val CAPTURE_VIDEO = booleanPreferencesKey("capture_video")
 val SAVED_WORDS = stringSetPreferencesKey("saved_words")
 val RECOGNITION_MODE = stringPreferencesKey("recognition_mode")
 val WHISPER_MODEL = stringPreferencesKey("whisper_model")
 val SAVED_WORD_DETAILS = stringPreferencesKey("saved_word_details")
 // Kept only to migrate installations created before LearningLanguage became the source of truth.
 val ACTIVE_MODEL = stringPreferencesKey("active_model")

 internal fun learningModeFromStoredValues(
 explicitMode: String?,
 legacyActiveModel: String?,
 ): LearningMode? = LearningMode.fromStorageValue(explicitMode)
 ?: when (legacyActiveModel) {
 "TINY_EN_Q5_1", "BASE_EN_Q5_1" -> LearningMode.ENGLISH_ONLY
 "TINY_Q5_1", "BASE_Q5_1", "TINY_MULTILINGUAL_Q5_1", "BASE_MULTILINGUAL_Q5_1" ->
 LearningMode.MULTILINGUAL
 else -> null
 }

 internal fun learningLanguageFromStoredValues(
 explicitLanguage: String?,
 legacyLearningMode: String?,
 legacyActiveModel: String?,
 ): LearningLanguage? = LearningLanguage.fromStorageValue(explicitLanguage)
 ?: LearningLanguage.fromLegacyLearningMode(
 learningModeFromStoredValues(legacyLearningMode, legacyActiveModel)
 )
 }

 val motherTongue: Flow<AppLanguage?> = dataStore.data.map { prefs ->
 AppLanguage.fromTag(prefs[MOTHER_TONGUE] ?: prefs[SELECTED_LANGUAGE])
 }

 val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
 prefs[ONBOARDING_COMPLETED] == "true"
 }

 val selectedLanguage: Flow<AppLanguage?> = dataStore.data.map { prefs ->
 prefs[SELECTED_LANGUAGE]?.let { AppLanguage.fromTag(it) }
 }

 val autoTranslate: Flow<Boolean> = dataStore.data.map { prefs ->
 prefs[AUTO_TRANSLATE] ?: true
 }

 val learningLanguage: Flow<LearningLanguage?> = dataStore.data.map { prefs ->
 learningLanguageFromStoredValues(
 explicitLanguage = prefs[LEARNING_LANGUAGE],
 legacyLearningMode = prefs[LEARNING_MODE],
 legacyActiveModel = prefs[ACTIVE_MODEL],
 )
 }

 val speechQuality: Flow<SpeechQuality> = dataStore.data.map { prefs ->
 SpeechQuality.fromStorageValue(prefs[SPEECH_QUALITY]) ?: SpeechQuality.DEFAULT
 }

 val recognitionMode: Flow<RecognitionMode> = dataStore.data.map { prefs ->
 RecognitionMode.fromStorageValue(prefs[RECOGNITION_MODE])
 }

 val whisperModel: Flow<ModelType?> = dataStore.data.map { prefs ->
 prefs[WHISPER_MODEL]?.let { stored -> ModelType.entries.firstOrNull { it.name == stored } }
 }

 val learningMode: Flow<LearningMode?> = dataStore.data.map { prefs ->
 learningLanguageFromStoredValues(
 explicitLanguage = prefs[LEARNING_LANGUAGE],
 legacyLearningMode = prefs[LEARNING_MODE],
 legacyActiveModel = prefs[ACTIVE_MODEL],
 )?.toLegacyLearningMode()
 }

 val captureVideo: Flow<Boolean> = dataStore.data.map { prefs ->
 prefs[CAPTURE_VIDEO] ?: true
 }

 val savedWords: Flow<Set<String>> = dataStore.data.map { prefs ->
 prefs[SAVED_WORDS].orEmpty()
 }

 val savedWordDetails: Flow<List<SavedWord>> = dataStore.data.map { prefs ->
 decodeSavedWords(prefs[SAVED_WORD_DETAILS])
 }

 suspend fun setMotherTongue(language: AppLanguage) {
 dataStore.edit { prefs ->
 prefs[MOTHER_TONGUE] = language.tag
 prefs.remove(SELECTED_LANGUAGE)
 }
 }

 suspend fun setOnboardingCompleted(completed: Boolean) {
 dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED] = if (completed) "true" else "false" }
 }

 suspend fun completeOnboarding(
 motherTongue: AppLanguage,
 learningLanguage: LearningLanguage,
 speechQuality: SpeechQuality = SpeechQuality.DEFAULT,
 ) {
 dataStore.edit { prefs ->
 prefs[MOTHER_TONGUE] = motherTongue.tag
 prefs[LEARNING_LANGUAGE] = learningLanguage.code
 prefs[SPEECH_QUALITY] = speechQuality.storageKey
 prefs[ONBOARDING_COMPLETED] = "true"
 prefs.remove(ACTIVE_MODEL)
 prefs.remove(LEARNING_MODE)
 prefs.remove(SELECTED_LANGUAGE)
 }
 }

 suspend fun completeOnboarding(language: AppLanguage, learningMode: LearningMode) {
 completeOnboarding(
 motherTongue = language,
 learningLanguage = LearningLanguage.fromLegacyLearningMode(learningMode)
 ?: LearningLanguage.ANY_LANGUAGE,
 speechQuality = SpeechQuality.DEFAULT,
 )
 }

 suspend fun restartOnboarding() = setOnboardingCompleted(false)

 suspend fun saveSelectedLanguage(language: AppLanguage) {
 setMotherTongue(language)
 }

 suspend fun saveAutoTranslate(enabled: Boolean) {
 dataStore.edit { prefs -> prefs[AUTO_TRANSLATE] = enabled }
 }

 suspend fun setLearningLanguage(learningLanguage: LearningLanguage) {
 dataStore.edit { prefs ->
 prefs[LEARNING_LANGUAGE] = learningLanguage.code
 prefs.remove(LEARNING_MODE)
 prefs.remove(ACTIVE_MODEL)
 }
 }

 suspend fun setSpeechQuality(speechQuality: SpeechQuality) {
 dataStore.edit { prefs -> prefs[SPEECH_QUALITY] = speechQuality.storageKey }
 }

 suspend fun setRecognitionMode(mode: RecognitionMode) {
 dataStore.edit { prefs -> prefs[RECOGNITION_MODE] = mode.storageKey }
 }

 suspend fun setWhisperModel(modelType: ModelType) {
 dataStore.edit { prefs -> prefs[WHISPER_MODEL] = modelType.name }
 }

 suspend fun setLearningMode(learningMode: LearningMode) {
 setLearningLanguage(
 LearningLanguage.fromLegacyLearningMode(learningMode) ?: LearningLanguage.ANY_LANGUAGE
 )
 }

 suspend fun setCaptureVideo(enabled: Boolean) {
 dataStore.edit { prefs -> prefs[CAPTURE_VIDEO] = enabled }
 }

 suspend fun setWordSaved(word: String, saved: Boolean) {
 val normalized = word.trim().lowercase()
 if (normalized.isBlank()) return
 dataStore.edit { prefs ->
 val updated = prefs[SAVED_WORDS].orEmpty().toMutableSet()
 if (saved) updated += normalized else updated -= normalized
 prefs[SAVED_WORDS] = updated
 if (!saved) {
 val details = decodeSavedWords(prefs[SAVED_WORD_DETAILS]).filterNot { it.word == normalized }
 prefs[SAVED_WORD_DETAILS] = encodeSavedWords(details)
 }
 }
 }

 suspend fun saveWord(word: SavedWord) {
 val normalized = word.word.trim().lowercase()
 if (normalized.isBlank()) return
 dataStore.edit { prefs ->
 val words = prefs[SAVED_WORDS].orEmpty().toMutableSet().apply { add(normalized) }
 prefs[SAVED_WORDS] = words
 val details = decodeSavedWords(prefs[SAVED_WORD_DETAILS]).filterNot { it.word == normalized } +
 word.copy(word = normalized)
 prefs[SAVED_WORD_DETAILS] = encodeSavedWords(details)
 }
 }

 private fun decodeSavedWords(raw: String?): List<SavedWord> = runCatching {
 val array = JSONArray(raw ?: "[]")
 List(array.length()) { index ->
 val item = array.getJSONObject(index)
 SavedWord(
 word = item.getString("word"),
 meaning = item.optString("meaning").takeUnless { it.isBlank() || it == "null" },
 example = item.optString("example").takeUnless { it.isBlank() || it == "null" },
 sourceLanguage = item.optString("sourceLanguage", "en"),
 targetLanguage = item.optString("targetLanguage", "en"),
 savedAt = item.optLong("savedAt", 0L),
 )
 }
 }.getOrDefault(emptyList())

 private fun encodeSavedWords(words: List<SavedWord>): String = JSONArray().apply {
 words.forEach { word ->
 put(JSONObject().apply {
 put("word", word.word)
 put("meaning", word.meaning)
 put("example", word.example)
 put("sourceLanguage", word.sourceLanguage)
 put("targetLanguage", word.targetLanguage)
 put("savedAt", word.savedAt)
 })
 }
 }.toString()

}
