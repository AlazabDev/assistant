package com.alazab.assistant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedEventDao {
    @Query("SELECT * FROM captured_events ORDER BY id DESC")
    fun getAllEvents(): Flow<List<CapturedEvent>>

    @Query("SELECT * FROM captured_events WHERE isSynced = 0 ORDER BY id ASC")
    suspend fun getUnsyncedEvents(): List<CapturedEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CapturedEvent): Long

    @Update
    suspend fun updateEvent(event: CapturedEvent)

    @Query("UPDATE captured_events SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    @Delete
    suspend fun deleteEvent(event: CapturedEvent)

    @Query("DELETE FROM captured_events")
    suspend fun clearAllEvents()
}
