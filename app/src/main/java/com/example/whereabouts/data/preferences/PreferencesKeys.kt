package com.example.whereabouts.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    val IS_SHARING_ACTIVE = booleanPreferencesKey("is_sharing_active")
    val SESSION_START_TIMESTAMP = longPreferencesKey("session_start_timestamp")
    val SESSION_END_TIMESTAMP = longPreferencesKey("session_end_timestamp")
    val ALERT_ENABLED = booleanPreferencesKey("alert_enabled")
    val ALERT_THRESHOLD_MINUTES = longPreferencesKey("alert_threshold_minutes")
    val PERMISSION_DENIED_ONCE = booleanPreferencesKey("permission_denied_once")
}
