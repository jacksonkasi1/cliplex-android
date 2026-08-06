package com.learnthis.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.learnthis.data.model.PreferencesKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class PreferencesRepository(
 private val dataStore: androidx.datastore.core.DataStore<Preferences>
 ) {
 suspend fun setMotherTongue(languageCode: String) {
 dataStore.edit { prefs ->
 prefs[PreferencesKeys.MOTHER_TONGUE] = languageCode
 }
 }

 fun getMotherTongue(): Flow<String?> = dataStore.data.map { prefs ->
 prefs[PreferencesKeys.MOTHER_TONGUE]
 }

 suspend fun setOnboardingCompleted(completed: Boolean) {
 dataStore.edit { prefs ->
 prefs[PreferencesKeys.ONBOARDING_COMPLETED] = if (completed) "true" else "false"
 }
 }

 fun isOnboardingCompleted(): Flow<Boolean> = dataStore.data.map { prefs ->
 prefs[PreferencesKeys.ONBOARDING_COMPLETED] == "true"
 }
}
