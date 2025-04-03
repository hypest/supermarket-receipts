package com.hypest.supermarketreceiptsapp.domain.repository

import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import kotlinx.coroutines.flow.Flow

interface ReceiptRepository {
    // Function to submit URL and potentially extracted HTML
    suspend fun submitReceiptData(url: String, htmlContent: String, userId: String): Result<Unit>

    // Function to just save the URL (for providers where client-side extraction isn't needed/possible)
    suspend fun saveReceiptUrl(url: String, userId: String): Result<Unit>

    // Function to fetch all processed receipts for the current user (Flow handles suspension)
    fun getReceipts(): Flow<Result<List<Receipt>>>
}
