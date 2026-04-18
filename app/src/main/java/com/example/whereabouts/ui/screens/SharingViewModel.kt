package com.example.whereabouts.ui.screens

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whereabouts.data.repository.SessionState
import com.example.whereabouts.data.repository.SessionStateRepository
import com.example.whereabouts.service.LocationForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStateRepository: SessionStateRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionStateRepository.sessionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.IDLE
        )

    /**
     * @param durationMinutes null = "Until I stop", 60 = 1 hour, 480 = 8 hours
     */
    fun startSharing(durationMinutes: Long?) {
        viewModelScope.launch {
            sessionStateRepository.startSession(durationMinutes)
            val intent = Intent(context, LocationForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }

    fun stopSharing() {
        val intent = Intent(context, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }
}
