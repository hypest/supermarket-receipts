package com.hypest.supermarketreceiptsapp.domain.repository

import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import kotlinx.coroutines.flow.Flow

interface ReceiptRepository {
    // Function to submit URL and potentially extracted HTML
    suspend fun submitReceiptData(url: String, htmlContent: String): Result<Unit> // Removed userId

    // Function to just save the URL (for providers where client-side extraction isn't needed/possible)
    suspend fun saveReceiptUrl(url: String): Result<Unit> // Removed userId

    // Function to fetch all processed receipts for the current user (Flow handles suspension)
    fun getReceipts(): Flow<Result<List<Receipt>>>

    // Function to fetch a single receipt by its ID
    suspend fun getReceiptById(id: String): Receipt? // Returns nullable Receipt or throws exception
}
