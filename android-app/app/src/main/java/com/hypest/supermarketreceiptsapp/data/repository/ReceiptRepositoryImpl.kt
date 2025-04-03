package com.hypest.supermarketreceiptsapp.data.repository

import android.util.Log
import com.hypest.supermarketreceiptsapp.domain.model.Receipt // Keep this import for getReceipts
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository // Import AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth // Import auth extension
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns // Import Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class ReceiptRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository // Keep AuthRepository injection if needed elsewhere, though not used in these methods now
) : ReceiptRepository {

    companion object {
        private const val SCANNED_URLS_TABLE = "scanned_urls"
        private const val RECEIPTS_TABLE = "receipts"
        private const val TAG = "ReceiptRepository"
    }

    private val postgrest = supabaseClient.postgrest

    // Implementation for submitting URL and HTML (fetches userId internally)
    override suspend fun submitReceiptData(url: String, htmlContent: String): Result<Unit> {
        return try {
            val currentUser = supabaseClient.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("User not logged in"))
            val userId = currentUser.id

            val dataToInsert = mapOf(
                "url" to url,
                "user_id" to userId, // Use fetched userId
                "html_content" to htmlContent
            )
            Log.d(TAG, "Attempting to submit receipt data: URL=$url, HTML Length=${htmlContent.length}, User=$userId")
            postgrest[SCANNED_URLS_TABLE].insert(dataToInsert)
            Log.d(TAG, "Receipt data submitted successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            // userId is not available in this scope anymore if fetching failed early
            Log.e(TAG, "Error submitting receipt data for URL $url", e)
            Result.failure(e)
        }
    }

    // Implementation for saving only the URL (fetches userId internally)
    override suspend fun saveReceiptUrl(url: String): Result<Unit> {
        return try {
            val currentUser = supabaseClient.auth.currentUserOrNull()
                 ?: return Result.failure(IllegalStateException("User not logged in"))
            val userId = currentUser.id

            val dataToInsert = mapOf(
                "url" to url,
                "user_id" to userId // Use fetched userId
                // html_content will be null/default in the DB
            )
            Log.d(TAG, "Attempting to save receipt URL only: URL=$url, User=$userId")
            postgrest[SCANNED_URLS_TABLE].insert(dataToInsert)
            Log.d(TAG, "Receipt URL saved successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            // userId is not available in this scope anymore if fetching failed early
            Log.e(TAG, "Error saving receipt URL for URL $url", e)
            Result.failure(e)
        }
    }

    // Implementation for fetching receipts
    override fun getReceipts(): Flow<Result<List<Receipt>>> = flow {
        try {
            val currentUser = supabaseClient.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "getReceipts: No logged-in user found.")
                emit(Result.failure(IllegalStateException("User not logged in")))
                return@flow
            }
            val userId = currentUser.id
            Log.d(TAG, "Fetching receipts for user: $userId")

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
                )
            ) {
                filter {
                    eq("user_id", userId)
                }
                order("receipt_date", Order.DESCENDING, nullsFirst = false)
            }

            val receipts = queryResult.decodeList<Receipt>()
            Log.d(TAG, "Successfully fetched ${receipts.size} receipts for user $userId")
            emit(Result.success(receipts))

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching receipts", e)
            emit(Result.failure(e))
        }
    }
}
