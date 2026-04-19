package com.example.whereabouts.service

import com.example.whereabouts.data.repository.CircleRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives FCM token refresh events and incoming data messages.
 * Token is persisted to Firestore so Cloud Functions can push to this device.
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject lateinit var circleRepository: CircleRepository
    @Inject lateinit var auth: FirebaseAuth

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called when the FCM token is generated or refreshed. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = auth.currentUser?.uid ?: return
        serviceScope.launch {
            circleRepository.saveFcmToken(uid, token)
        }
    }

    /**
     * Called when a data message arrives while the app is in foreground.
     * Phase 5: token-saving only.
     * Phase 6+: show local notification for inactivity alerts / new invites.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Future: handle data payload from Cloud Functions
    }
}
