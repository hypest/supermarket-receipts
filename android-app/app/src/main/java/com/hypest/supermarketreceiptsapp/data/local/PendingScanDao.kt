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
    fun getAllPendingScansFlow(): Flow<List<PendingScanEntity>> // Keep Flow for observation

    // Add suspend function to get a snapshot list
    @Query("SELECT * FROM pending_scans ORDER BY timestamp ASC")
    suspend fun getPendingScans(): List<PendingScanEntity>

    @Query("DELETE FROM pending_scans WHERE url = :url") // Query by url instead of id
    suspend fun deletePendingScanByUrl(url: String) // Change parameter to url: String

    // Query to get the count of pending scans as an observable Flow
    @Query("SELECT COUNT(*) FROM pending_scans")
    fun getPendingScanCount(): Flow<Int> // Return Flow<Int>
}
