package com.example.whereabouts.ui.screens

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.whereabouts.data.repository.SessionState
import com.example.whereabouts.data.repository.SessionStateRepository
import com.example.whereabouts.service.LocationForegroundService
import com.example.whereabouts.worker.AlertWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SharingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStateRepository: SessionStateRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionStateRepository.sessionState
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.IDLE
        )

    init {
        // Clean up a session that expired while the app was closed
        viewModelScope.launch {
            val session = sessionStateRepository.sessionState.first()
            if (session.isActive && session.hasExpired()) {
                sessionStateRepository.stopSession()
                WorkManager.getInstance(context).cancelUniqueWork(AlertWorker.WORK_NAME)
            }
        }
    }

    /**
     * @param durationMinutes null = "Until I stop", 60 = 1 hour, 480 = 8 hours
     */
    fun startSharing(durationMinutes: Long?) {
        viewModelScope.launch {
            // Guard: don't start a second session if one is already running
            if (sessionStateRepository.sessionState.first().isActive) return@launch
            sessionStateRepository.startSession(durationMinutes)
            context.startForegroundService(
                Intent(context, LocationForegroundService::class.java)
            )
            scheduleAlertWorker()
        }
    }

    fun stopSharing() {
        context.startService(
            Intent(context, LocationForegroundService::class.java).apply {
                action = LocationForegroundService.ACTION_STOP
            }
        )
        WorkManager.getInstance(context).cancelUniqueWork(AlertWorker.WORK_NAME)
    }

    // ── WorkManager ───────────────────────────────────────────────────────────

    private fun scheduleAlertWorker() {
        val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // don't restart if already scheduled
            request
        )
    }
}
