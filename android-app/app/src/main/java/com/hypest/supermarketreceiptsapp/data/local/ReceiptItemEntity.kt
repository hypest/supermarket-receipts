package com.hypest.supermarketreceiptsapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "receipt_items",
    foreignKeys = [ForeignKey(
        entity = ReceiptEntity::class,
        parentColumns = ["id"],
        childColumns = ["receiptId"],
        onDelete = ForeignKey.CASCADE // Delete items if the parent receipt is deleted
    )],
    indices = [Index("receiptId")] // Index for faster queries based on receiptId
)
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val itemId: Long = 0, // Local auto-generated ID for the item
    val receiptId: String, // Foreign key linking to ReceiptEntity
    val name: String,
    val quantity: Double, // Make non-nullable
    val price: Double, // Make non-nullable - This is the total price
    val unitPrice: Double? // Add nullable unit price
    // Note: We might not need the remote 'id' from Supabase for local items,
    // unless we need to sync individual item changes back.
    // For simple caching, an auto-generated local ID is sufficient.
)
