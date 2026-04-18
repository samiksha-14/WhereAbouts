package com.example.whereabouts.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.whereabouts.data.preferences.PreferencesKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a single sharing session.
 * [endTimeMillis] == 0L means "Until I stop" (no auto-end).
 */
data class SessionState(
    val isActive: Boolean,
    val startTimeMillis: Long,
    val endTimeMillis: Long
) {
    val hasFixedDuration: Boolean get() = endTimeMillis > 0L

    fun remainingMillis(now: Long = System.currentTimeMillis()): Long =
        if (!hasFixedDuration) -1L else maxOf(0L, endTimeMillis - now)

    fun remainingMinutes(now: Long = System.currentTimeMillis()): Long {
        val ms = remainingMillis(now)
        return if (ms < 0) -1L else ms / 60_000L
    }

    /** True if the session had a fixed duration and that duration has elapsed. */
    fun hasExpired(now: Long = System.currentTimeMillis()): Boolean =
        hasFixedDuration && now >= endTimeMillis

    companion object {
        val IDLE = SessionState(isActive = false, startTimeMillis = 0L, endTimeMillis = 0L)
    }
}

@Singleton
class SessionStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    /** Emits the current session state reactively. */
    val sessionState: Flow<SessionState> = dataStore.data.map { prefs ->
        SessionState(
            isActive = prefs[PreferencesKeys.IS_SHARING_ACTIVE] ?: false,
            startTimeMillis = prefs[PreferencesKeys.SESSION_START_TIMESTAMP] ?: 0L,
            endTimeMillis = prefs[PreferencesKeys.SESSION_END_TIMESTAMP] ?: 0L
        )
    }

    /**
     * Start a sharing session.
     * @param durationMinutes null = "Until I stop". Otherwise stores an absolute end time.
     */
    suspend fun startSession(durationMinutes: Long?) {
        val now = System.currentTimeMillis()
        val end = if (durationMinutes == null) 0L else now + durationMinutes * 60_000L
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_SHARING_ACTIVE] = true
            prefs[PreferencesKeys.SESSION_START_TIMESTAMP] = now
            prefs[PreferencesKeys.SESSION_END_TIMESTAMP] = end
        }
    }

    suspend fun stopSession() {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_SHARING_ACTIVE] = false
            prefs[PreferencesKeys.SESSION_START_TIMESTAMP] = 0L
            prefs[PreferencesKeys.SESSION_END_TIMESTAMP] = 0L
        }
    }
}
