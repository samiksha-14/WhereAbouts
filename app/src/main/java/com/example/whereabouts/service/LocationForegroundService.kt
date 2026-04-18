package com.example.whereabouts.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.whereabouts.MainActivity
import com.example.whereabouts.data.repository.LocationRepository
import com.example.whereabouts.data.repository.SessionStateRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationForegroundService : Service() {

    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var sessionStateRepository: SessionStateRepository

    private lateinit var fusedClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Fires every 30s to write for stationary users (PRD fallback requirement)
    private val fallbackRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch { checkExpiryAndFallbackWrite() }
            mainHandler.postDelayed(this, FALLBACK_INTERVAL_MS)
        }
    }

    // Fires every 60s to refresh the notification countdown
    private val notificationRefreshRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch { refreshNotification() }
            mainHandler.postDelayed(this, NOTIFICATION_REFRESH_MS)
        }
    }

    // FusedLocationClient callback — fires when device moves >= 50m
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            serviceScope.launch {
                val session = sessionStateRepository.sessionState.first()
                if (session.hasExpired()) {
                    endSession()
                    return@launch
                }
                locationRepository.maybeWriteLocation(location)
                // Reset fallback since we just got a GPS fix
                mainHandler.removeCallbacks(fallbackRunnable)
                mainHandler.postDelayed(fallbackRunnable, FALLBACK_INTERVAL_MS)
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch { endSession() }
            return START_NOT_STICKY
        }

        locationRepository.resetSmartFilterState()

        // Promote to foreground immediately
        startForeground(NOTIFICATION_ID, buildNotification("Starting location sharing…"))

        startLocationUpdates()
        mainHandler.postDelayed(fallbackRunnable, FALLBACK_INTERVAL_MS)
        mainHandler.post(notificationRefreshRunnable)

        return START_STICKY // OS restarts service if killed; DataStore flag resumes session
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        mainHandler.removeCallbacks(fallbackRunnable)
        mainHandler.removeCallbacks(notificationRefreshRunnable)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Location updates ─────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(50f)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            serviceScope.launch { endSession() }
        }
    }

    // Fallback: for stationary users the GPS callback never fires,
    // so we use lastLocation + the 30s elapsed check in LocationRepository
    private suspend fun checkExpiryAndFallbackWrite() {
        val session = sessionStateRepository.sessionState.first()
        if (session.hasExpired()) {
            endSession()
            return
        }
        try {
            fusedClient.lastLocation.addOnSuccessListener { location ->
                location ?: return@addOnSuccessListener
                serviceScope.launch { locationRepository.maybeWriteLocation(location) }
            }
        } catch (_: SecurityException) { /* permission revoked */ }
    }

    // ── Session end ──────────────────────────────────────────────────────────

    private suspend fun endSession() {
        locationRepository.markSessionEnded()
        sessionStateRepository.stopSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private suspend fun refreshNotification() {
        val session = sessionStateRepository.sessionState.first()
        if (!session.isActive) return
        if (session.hasExpired()) {
            endSession()
            return
        }
        val text = buildNotificationText(session)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotificationText(
        session: com.example.whereabouts.data.repository.SessionState
    ): String {
        if (!session.hasFixedDuration) return "Sharing location · Until you stop"
        val mins = session.remainingMinutes()
        return when {
            mins > 60 -> "Sharing location · ${mins / 60}h ${mins % 60}m remaining"
            mins > 1  -> "Sharing location · ${mins}m remaining"
            mins == 1L -> "Sharing location · 1m remaining"
            else       -> "Sharing location · Ending soon…"
        }
    }

    private fun buildNotification(text: String): Notification {
        // "Stop" action taps back into the service with ACTION_STOP
        val stopIntent = Intent(this, LocationForegroundService::class.java)
            .apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tap notification → open app
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Whereabouts")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .setOngoing(true)   // can't be swiped away
            .setSilent(true)    // no sound/vibration on updates
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Sharing",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active while you are sharing your location"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sharing_location"
        const val ACTION_STOP = "com.example.whereabouts.ACTION_STOP_SHARING"

        private const val FALLBACK_INTERVAL_MS = 30_000L
        private const val NOTIFICATION_REFRESH_MS = 60_000L
    }
}
