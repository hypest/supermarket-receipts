package com.hypest.supermarketreceiptsapp.data.repository

import android.util.Log
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository // Import AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns // Import Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class ReceiptRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository // Inject AuthRepository
) : ReceiptRepository {

    companion object {
        private const val SCANNED_URLS_TABLE = "scanned_urls"
        private const val RECEIPTS_TABLE = "receipts" // Add constant for receipts table
        private const val TAG = "ReceiptRepository"
    }

    // Use the 'postgrest' extension property
    private val postgrest = supabaseClient.postgrest

    // Implementation for submitting URL and HTML
    override suspend fun submitReceiptData(url: String, htmlContent: String, userId: String): Result<Unit> {
        return try {
            // Create Receipt object including htmlContent
            // Create a map for insertion as the Receipt model is now different
            val dataToInsert = mapOf(
                "url" to url,
                "user_id" to userId,
                "html_content" to htmlContent
            )
            Log.d(TAG, "Attempting to submit receipt data: URL=$url, HTML Length=${htmlContent.length}, User=$userId")
            postgrest[SCANNED_URLS_TABLE].insert(dataToInsert)
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
            // Create a map for insertion
             val dataToInsert = mapOf(
                "url" to url,
                "user_id" to userId
                // html_content will be null/default in the DB
            )
            Log.d(TAG, "Attempting to save receipt URL only: URL=$url, User=$userId")
            postgrest[SCANNED_URLS_TABLE].insert(dataToInsert)
            Log.d(TAG, "Receipt URL saved successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving receipt URL for user $userId", e)
            Result.failure(e)
        }
    }

    // Implementation for fetching receipts (remove suspend keyword)
    override fun getReceipts(): Flow<Result<List<Receipt>>> = flow {
        try {
            // Get current user - assuming AuthRepository provides a way, or use supabaseClient.auth directly
            // For simplicity, using supabaseClient.auth directly here. Inject AuthRepository for cleaner separation.
            val currentUser = supabaseClient.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "getReceipts: No logged-in user found.")
                emit(Result.failure(IllegalStateException("User not logged in")))
                return@flow
            }
            val userId = currentUser.id
            Log.d(TAG, "Fetching receipts for user: $userId")

            // Query receipts table, joining receipt_items using Columns.raw()
            val queryResult = postgrest[RECEIPTS_TABLE].select(
                columns = Columns.raw(
                    """
                    id,
                    receipt_date,
                    total_amount,
                    store_name,
                    uid,
                    created_at,
                    receipt_items (
                        id,
                        name,
                        quantity,
                        price
                    )
                    """
                ) // Pass the raw string to Columns.raw()
            ) {
                filter {
                    eq("user_id", userId)
                }
                order("receipt_date", Order.DESCENDING, nullsFirst = false) // Order by date descending
            }

            // Decode the result into List<Receipt>
            val receipts = queryResult.decodeList<Receipt>()
            Log.d(TAG, "Successfully fetched ${receipts.size} receipts for user $userId")
            emit(Result.success(receipts))

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching receipts", e)
            emit(Result.failure(e))
        }
    }
}
