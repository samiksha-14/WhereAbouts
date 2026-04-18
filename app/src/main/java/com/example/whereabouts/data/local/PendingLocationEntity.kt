package com.example.whereabouts.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_locations")
data class PendingLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val battery: Int,
    val timestamp: Long,
    val synced: Boolean = false,
)
