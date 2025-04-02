package com.hypest.supermarketreceiptsapp.data.repository

import android.util.Log
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject

class ReceiptRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ReceiptRepository {

    companion object {
        private const val TABLE_NAME = "scanned_urls"
        private const val TAG = "ReceiptRepository"
    }

    // Use the 'postgrest' extension property from v3
    private val postgrest = supabaseClient.postgrest

    // Implementation for submitting URL and HTML
    override suspend fun submitReceiptData(url: String, htmlContent: String, userId: String): Result<Unit> {
        return try {
            // Create Receipt object including htmlContent
            val receiptData = Receipt(url = url, user_id = userId, htmlContent = htmlContent)
            Log.d(TAG, "Attempting to submit receipt data: URL=$url, HTML Length=${htmlContent.length}, User=$userId")
            // Insert using the v3 API
            postgrest[TABLE_NAME].insert(receiptData) // Supabase client handles serialization
            Log.d(TAG, "Receipt data submitted successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting receipt data for user $userId", e)
            Result.failure(e)
        }
    }

    // Implementation for saving only the URL
    override suspend fun saveReceiptUrl(url: String, userId: String): Result<Unit> {
        return try {
            // Create Receipt object without htmlContent
            val receipt = Receipt(url = url, user_id = userId) // htmlContent will be null by default
            Log.d(TAG, "Attempting to save receipt URL only: $receipt")
            // Insert using the v3 API
            postgrest[TABLE_NAME].insert(receipt)
            Log.d(TAG, "Receipt URL saved successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving receipt URL for user $userId", e)
            Result.failure(e)
        }
    }
}
