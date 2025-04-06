package com.hypest.supermarketreceiptsapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Data class to hold a Receipt and its associated Items
data class ReceiptWithItems(
    @Embedded val receipt: ReceiptEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "receiptId"
    )
    val items: List<ReceiptItemEntity>
)

@Dao
interface ReceiptDao {

    // --- Insert Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ReceiptItemEntity>)

    // Transaction to insert a receipt and its items together
    @Transaction
    suspend fun insertReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>) {
        insertReceipt(receipt)
        // Ensure items have the correct receiptId before inserting
        val itemsWithReceiptId = items.map { it.copy(receiptId = receipt.id) }
        // Delete old items for this receipt before inserting new ones to handle updates
        deleteItemsByReceiptId(receipt.id)
        insertItems(itemsWithReceiptId)
    }

    // --- Query Operations ---

    @Transaction
    @Query("SELECT * FROM receipts WHERE userId = :userId ORDER BY receiptDate DESC")
    fun getReceiptsWithItemsForUser(userId: String): Flow<List<ReceiptWithItems>>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId LIMIT 1")
    suspend fun getReceiptWithItemsById(receiptId: String): ReceiptWithItems?

    // --- Delete Operations ---

    @Query("DELETE FROM receipts WHERE userId = :userId")
    suspend fun deleteAllReceiptsForUser(userId: String)

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteItemsByReceiptId(receiptId: String)

    @Query("DELETE FROM receipts WHERE id = :receiptId")
    suspend fun deleteReceiptById(receiptId: String) // Also deletes items due to CASCADE

    // Transaction to replace all receipts for a user
    @Transaction
    suspend fun replaceAllReceiptsForUser(userId: String, receipts: List<ReceiptWithItems>) {
        deleteAllReceiptsForUser(userId) // Clear existing data for the user
        receipts.forEach { receiptWithItems ->
            insertReceipt(receiptWithItems.receipt) // Insert receipt
            if (receiptWithItems.items.isNotEmpty()) {
                insertItems(receiptWithItems.items) // Insert associated items
            }
        }
    }
}
