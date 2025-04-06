package com.hypest.supermarketreceiptsapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_scans")
data class PendingScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val htmlContent: String?, // Nullable if only URL was saved initially
    val userId: String,
    val timestamp: Long = System.currentTimeMillis() // Record when it was saved locally
)
