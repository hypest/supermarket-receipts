package com.hypest.supermarketreceiptsapp.domain.repository

// Removed Receipt import as it's not used here
// import com.hypest.supermarketreceiptsapp.domain.model.Receipt

interface ReceiptRepository {
    // Renamed and added htmlContent parameter
    suspend fun submitReceiptData(url: String, htmlContent: String, userId: String): Result<Unit>
    // Add functions for fetching receipts later if needed
}
