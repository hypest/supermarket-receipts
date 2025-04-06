package com.hypest.supermarketreceiptsapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ReceiptEntity::class, ReceiptItemEntity::class, PendingScanEntity::class], // Add PendingScanEntity
    version = 2, // Increment version
    exportSchema = false // Schema export is recommended for production apps, but false for simplicity here
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun pendingScanDao(): PendingScanDao // Add abstract fun for the new DAO

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time. Needs context to build. Usually provided via Hilt.
        const val DATABASE_NAME = "receipts_database"
    }
}
