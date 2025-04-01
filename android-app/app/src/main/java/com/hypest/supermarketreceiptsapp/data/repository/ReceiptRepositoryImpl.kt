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

    override suspend fun saveReceiptUrl(url: String, userId: String): Result<Unit> {
        return try {
            // Ensure Receipt class has @Serializable annotation
            val receipt = Receipt(url = url, user_id = userId)
            Log.d(TAG, "Attempting to save receipt: $receipt")
            // Insert using the v3 API
            postgrest[TABLE_NAME].insert(receipt) // Check if serialization needs explicit configuration
            Log.d(TAG, "Receipt saved successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving receipt URL for user $userId", e)
            Result.failure(e)
        }
    }
}
