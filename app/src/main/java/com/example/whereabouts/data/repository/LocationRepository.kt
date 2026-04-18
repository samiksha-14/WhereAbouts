package com.example.whereabouts.data.repository

import android.content.Context
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.example.whereabouts.data.local.PendingLocationDao
import com.example.whereabouts.data.local.PendingLocationEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles location persistence — smart write filtering, RTDB writes, offline queue.
 * Smart rule (from PRD): write only when moved >= 50m OR 30s elapsed since last write.
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val pendingLocationDao: PendingLocationDao
) {
    // In-memory state of the last successful write (per PRD pseudocode)
    private var lastWrittenLat: Double? = null
    private var lastWrittenLng: Double? = null
    private var lastWriteTimestampMillis: Long = 0L

    companion object {
        private const val MIN_DISTANCE_METERS = 50.0
        private const val MIN_ELAPSED_MILLIS = 30_000L
    }

    /** Resets smart-write state — call at session start. */
    fun resetSmartFilterState() {
        lastWrittenLat = null
        lastWrittenLng = null
        lastWriteTimestampMillis = 0L
    }

    /**
     * Decides whether to write based on the smart-write rule, then either
     * writes to RTDB or queues to Room if offline.
     * Returns true if a write (or enqueue) happened, false if filtered out.
     */
    suspend fun maybeWriteLocation(location: Location): Boolean {
        val now = System.currentTimeMillis()
        val last = lastWrittenLat to lastWrittenLng
        val shouldWrite = when {
            last.first == null || last.second == null -> true // first fix
            else -> {
                val distance = haversineMeters(
                    last.first!!, last.second!!, location.latitude, location.longitude
                )
                val elapsed = now - lastWriteTimestampMillis
                distance >= MIN_DISTANCE_METERS || elapsed >= MIN_ELAPSED_MILLIS
            }
        }
        if (!shouldWrite) return false

        val battery = currentBatteryPercent()
        if (isOnline()) {
            writeToRtdb(location, battery)
        } else {
            queueLocally(location, battery, now)
        }

        lastWrittenLat = location.latitude
        lastWrittenLng = location.longitude
        lastWriteTimestampMillis = now
        return true
    }

    /** Marks the session as ended in RTDB. Called on stop/expiry. */
    suspend fun markSessionEnded() {
        val uid = auth.currentUser?.uid ?: return
        val node = database.getReference("locations").child(uid)
        val update = mapOf(
            "sessionEnd" to ServerValue.TIMESTAMP,
            "isLive" to false
        )
        runCatching { node.updateChildren(update).await() }
    }

    // --- internals ---

    private suspend fun writeToRtdb(location: Location, battery: Int) {
        val uid = auth.currentUser?.uid ?: return
        val payload = mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "accuracy" to location.accuracy,
            "battery" to battery,
            "timestamp" to ServerValue.TIMESTAMP,
            "sessionEnd" to null,
            "isLive" to true
        )
        runCatching {
            database.getReference("locations").child(uid).setValue(payload).await()
        }
    }

    private suspend fun queueLocally(location: Location, battery: Int, now: Long) {
        pendingLocationDao.insert(
            PendingLocationEntity(
                lat = location.latitude,
                lng = location.longitude,
                accuracy = location.accuracy,
                battery = battery,
                timestamp = now,
                synced = false
            )
        )
    }

    private fun currentBatteryPercent(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun haversineMeters(
        lat1: Double, lng1: Double, lat2: Double, lng2: Double
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2).let { it * it }
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
