package com.learnthis.data.model

import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
 val MOTHER_TONGUE = stringPreferencesKey("mother_tongue")
 val SPEECH_MODEL_STATUS = stringPreferencesKey("speech_model_status")
 val TRANSLATION_MODEL_STATUS = stringPreferencesKey("translation_model_status")
 val ONBOARDING_COMPLETED = stringPreferencesKey("onboarding_completed")
}
