package com.hypest.supermarketreceiptsapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow // Add missing import

@Dao
interface PendingScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingScan(scan: PendingScanEntity)

    @Query("SELECT * FROM pending_scans ORDER BY timestamp ASC")
    suspend fun getAllPendingScans(): List<PendingScanEntity>

    @Query("DELETE FROM pending_scans WHERE id = :id")
    suspend fun deletePendingScanById(id: Long)

    // Query to get the count of pending scans as an observable Flow
    @Query("SELECT COUNT(*) FROM pending_scans")
    fun getPendingScanCount(): Flow<Int> // Return Flow<Int>
}
