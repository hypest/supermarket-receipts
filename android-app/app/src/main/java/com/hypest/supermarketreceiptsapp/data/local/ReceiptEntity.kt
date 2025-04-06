package com.hypest.supermarketreceiptsapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val id: String, // Use the same ID as the remote DB (UUID string)
    val receiptDate: String?, // ISO 8601 timestamp string
    val totalAmount: Double?,
    val storeName: String?,
    val uid: String?, // Government service UID
    val createdAt: String, // ISO 8601 timestamp string for when it was scanned/processed
    val userId: String // Store the user ID this receipt belongs to
    // Items will be stored in a separate table (ReceiptItemEntity)
)
