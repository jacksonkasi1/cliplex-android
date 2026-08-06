package com.learnthis.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.learnthis.common.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

 companion object {
 val MOTHER_TONGUE = stringPreferencesKey("mother_tongue")
 val ONBOARDING_COMPLETED = stringPreferencesKey("onboarding_completed")
 }

 val motherTongue: Flow<AppLanguage?> = dataStore.data.map { prefs ->
 AppLanguage.fromTag(prefs[MOTHER_TONGUE])
 }

 val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
 prefs[ONBOARDING_COMPLETED] == "true"
 }

 suspend fun setMotherTongue(language: AppLanguage) {
 dataStore.edit { prefs -> prefs[MOTHER_TONGUE] = language.tag }
 }

 suspend fun setOnboardingCompleted(completed: Boolean) {
 dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED] = if (completed) "true" else "false" }
 }
}
