package com.example.whereabouts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingLocationDao {

    @Insert
    suspend fun insert(entity: PendingLocationEntity): Long

    @Query("SELECT * FROM pending_locations WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<PendingLocationEntity>

    @Query("DELETE FROM pending_locations WHERE synced = 1")
    suspend fun deleteSynced()

    @Query("UPDATE pending_locations SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM pending_locations")
    suspend fun deleteAll()
}
