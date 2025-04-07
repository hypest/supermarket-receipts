package com.hypest.supermarketreceiptsapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReceiptEntity::class, ReceiptItemEntity::class, PendingScanEntity::class],
    version = 4, // Incremented version for adding unitPrice
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun pendingScanDao(): PendingScanDao // Add abstract fun for the new DAO

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time. Needs context to build. Usually provided via Hilt.
        const val DATABASE_NAME = "receipts_database"

        // Migration from version 3 to 4: Add unitPrice column to receipt_items
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receipt_items ADD COLUMN unitPrice REAL") // Use REAL for Double?
            }
        }
    }
}
