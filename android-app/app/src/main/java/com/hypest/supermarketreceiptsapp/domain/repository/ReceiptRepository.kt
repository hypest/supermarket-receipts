package com.hypest.supermarketreceiptsapp.domain.repository

// Removed Receipt import as it's not used here
// import com.hypest.supermarketreceiptsapp.domain.model.Receipt

interface ReceiptRepository {
    // Function to submit URL and potentially extracted HTML
    suspend fun submitReceiptData(url: String, htmlContent: String, userId: String): Result<Unit>

    // Function to just save the URL (for providers where client-side extraction isn't needed/possible)
    suspend fun saveReceiptUrl(url: String, userId: String): Result<Unit>

    // Add functions for fetching receipts later if needed
}
