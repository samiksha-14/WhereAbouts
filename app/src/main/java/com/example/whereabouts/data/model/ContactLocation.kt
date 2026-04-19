package com.example.whereabouts.data.model

/**
 * Represents a circle contact together with their latest RTDB location data.
 * This is the UI-facing model consumed by MapViewModel and the map screen.
 */
data class ContactLocation(
    val uid: String,
    val name: String,
    val email: String,
    val photoUrl: String,

    // Location fields from RTDB locations/{uid}
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val batteryPercent: Int,       // -1 if unknown
    val timestampMillis: Long,     // server timestamp of last write
    val sessionEnded: Boolean,     // true if sessionEnd field is present in RTDB
    val isLive: Boolean
) {
    /** Two-letter initials derived from name, e.g. "Samiksha Mahure" → "SM" */
    val initials: String
        get() {
            val parts = name.trim().split(" ").filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                parts.size == 1 -> parts.first().take(2).uppercase()
                else            -> "?"
            }
        }

    /** Human-readable "last seen" string derived from timestampMillis. */
    fun lastSeenLabel(now: Long = System.currentTimeMillis()): String {
        val diffMs = now - timestampMillis
        val diffMins = diffMs / 60_000L
        return when {
            diffMins < 1  -> "Just now"
            diffMins < 60 -> "${diffMins}m ago"
            else          -> "${diffMins / 60}h ${diffMins % 60}m ago"
        }
    }
}
