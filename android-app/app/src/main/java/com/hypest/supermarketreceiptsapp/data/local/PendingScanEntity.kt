package com.hypest.supermarketreceiptsapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_scans")
data class PendingScanEntity(
    @PrimaryKey val url: String, // Use URL as primary key to enforce uniqueness
    val htmlContent: String?, // Nullable if only URL was saved initially
    val userId: String?, // Allow null if user scanned while logged out
    val timestamp: Long = System.currentTimeMillis() // Record when it was saved locally
)
