package com.hypest.supermarketreceiptsapp.domain.repository

import com.hypest.supermarketreceiptsapp.domain.model.Receipt

interface ReceiptRepository {
    suspend fun saveReceiptUrl(url: String, userId: String): Result<Unit>
    // Add functions for fetching receipts later if needed
}
