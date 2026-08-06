package com.learnthis.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.learnthis.data.repository.PreferencesRepository

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ServiceLocator private constructor(private val context: Context) {
 val preferencesRepository: PreferencesRepository by lazy {
 PreferencesRepository(context.dataStore)
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
