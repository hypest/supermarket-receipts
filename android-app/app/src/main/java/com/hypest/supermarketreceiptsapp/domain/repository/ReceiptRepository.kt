package com.hypest.supermarketreceiptsapp.domain.repository

import com.hypest.supermarketreceiptsapp.data.local.PendingScanEntity // Import PendingScanEntity
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import kotlinx.coroutines.flow.Flow

interface ReceiptRepository {
    // Function to submit URL and potentially extracted HTML
    suspend fun submitReceiptData(url: String, htmlContent: String): Result<Unit> // Removed userId again

    // Function to just save the URL
    suspend fun saveReceiptUrl(url: String): Result<Unit> // Removed userId again

    // Function to fetch all processed receipts for the current user (Flow handles suspension)
    fun getReceipts(): Flow<Result<List<Receipt>>>

    // Function to fetch a single receipt by its ID
    suspend fun getReceiptById(id: String): Receipt? // Returns nullable Receipt or throws exception

    // --- Functions for syncing pending scans (called by Worker) ---
    suspend fun syncPendingScanToServer(url: String, htmlContent: String, userId: String): Result<Unit>
    suspend fun syncPendingScanUrlToServer(url: String, userId: String): Result<Unit>

    // Function to observe the count of pending scans
    fun getPendingScanCountFlow(): Flow<Int>

    // Function to observe the list of pending scans
    fun getPendingScansFlow(): Flow<List<PendingScanEntity>>

    // Function to get a snapshot list of pending scans
    suspend fun getPendingScans(): List<PendingScanEntity>
}
