package com.hypest.supermarketreceiptsapp.data.repository

import android.util.Log
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.Auth // Auth v2 extension function
import io.github.jan.supabase.postgrest.postgrest // Postgrest v2 extension function
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
// import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder // Remove unused import
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
// import io.github.jan.supabase.postgrest.query.filter.eq // Removing eq import again
// import io.github.jan.supabase.postgrest.query.PostgrestResult // Not needed for decodeList
// import io.github.jan.supabase.postgrest.request.PostgrestRequestBuilder // Not needed directly
// import io.github.jan.supabase.postgrest.query.filter.FilterOperator // Not needed for string filter
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime // Import Realtime module type
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime // Realtime v2 extension function
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

class ReceiptRepositoryImpl @Inject constructor(
    val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository, // Keep for now
    private val scope: CoroutineScope // Inject CoroutineScope
) : ReceiptRepository {

    companion object {
        private const val SCANNED_URLS_TABLE = "scanned_urls"
        private const val RECEIPTS_TABLE = "receipts"
        private const val TAG = "ReceiptRepository"
    }

    // Implementation for submitting URL and HTML (fetches userId internally)
    override suspend fun submitReceiptData(url: String, htmlContent: String): Result<Unit> {
        return try {
            val currentUser = supabaseClient.auth.currentUserOrNull() // Use property
            // directly in v2
                ?: return Result.failure(IllegalStateException("User not logged in"))
            val userId = currentUser.id

            val dataToInsert = mapOf(
                "url" to url,
                "user_id" to userId,
                "html_content" to htmlContent
            )
            Log.d(TAG, "Attempting to submit receipt data: URL=$url, HTML Length=${htmlContent.length}, User=$userId")
            supabaseClient.postgrest[SCANNED_URLS_TABLE].insert(dataToInsert) // Use postgrest extension
            Log.d(TAG, "Receipt data submitted successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
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
                "user_id" to userId
            )
            Log.d(TAG, "Attempting to save receipt URL only: URL=$url, User=$userId")
            supabaseClient.postgrest[SCANNED_URLS_TABLE].insert(dataToInsert) // Use postgrest extension
            Log.d(TAG, "Receipt URL saved successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving receipt URL for URL $url", e)
            Result.failure(e)
        }
    }

    // Implementation for fetching receipts using Realtime (simplified - removed sessionStatus check)
    override fun getReceipts(): Flow<Result<List<Receipt>>> {
        Log.d(TAG, "getReceipts being called (simplified)")

        val currentUser = supabaseClient.auth.currentUserOrNull()
        if (currentUser == null) {
            Log.w(TAG, "getReceipts: No logged-in user found. Returning error flow.")
            // Return a flow that immediately emits a failure
            return flowOf(Result.failure(IllegalStateException("User not logged in")))
        }

        val userId = currentUser.id
        Log.d(TAG, "getReceipts: User $userId logged in. Setting up realtime channel.")
        Log.d(TAG, "Realtime: Subscribing with filter for user_id = $userId")

        // Use realtime extension function in v2
        val channel = supabaseClient.realtime.channel("receipts_user_$userId")

        // Launch subscribe in the injected scope
        scope.launch {
            try {
                Log.d(TAG, "Attempting to subscribe to channel: ${channel.topic}")
                channel.subscribe() // Explicitly subscribe
                Log.d(TAG, "Successfully subscribed to channel: ${channel.topic}")
            } catch (e: Exception) {
                Log.e(TAG, "Error subscribing to channel ${channel.topic}", e)
            }
        }

        // Use v2 postgresChangeFlow syntax directly
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = RECEIPTS_TABLE
            // Use string-based filter property for v2 realtime
            filter = "user_id=eq.$userId"
        }.onEach { action ->
            Log.d(TAG, "Realtime: Flow emitted action: $action")
        }.map { action ->
            Log.d(TAG, "Realtime: Mapping action details: $action")
            // Make when exhaustive for v2 PostgresAction types
            when (action) {
                is PostgresAction.Insert -> Log.d(TAG, "Realtime: Insert detected for user $userId")
                is PostgresAction.Update -> Log.d(TAG, "Realtime: Update detected for user $userId")
                is PostgresAction.Delete -> Log.d(TAG, "Realtime: Delete detected for user $userId")
                else -> Log.w(TAG, "Realtime: Received unknown action type: $action")
            }
            // Re-fetch the whole list
            fetchReceiptsForUser(userId)
        }.onStart {
            Log.d(TAG, "Realtime: Flow started for user $userId. Performing initial fetch.")
            emit(fetchReceiptsForUser(userId))
        }.onCompletion { cause ->
            Log.d(TAG, "Realtime: postgresChangeFlow completed for user $userId. Cause: $cause")
        }.catch { e ->
            Log.e(TAG, "Realtime: Error in postgresChangeFlow for user $userId", e)
            emit(Result.failure(e))
        }
    }

    // Helper function to perform the actual fetch logic
    private suspend fun fetchReceiptsForUser(userId: String): Result<List<Receipt>> {
        return try {
            Log.d(TAG, "fetchReceiptsForUser: Fetching receipts for user: $userId")
            // Applying select lambda structure based on documentation
            val queryResult = supabaseClient.postgrest[RECEIPTS_TABLE].select(
                 // Specify columns parameter name
                 columns = Columns.list(
                     "id",
                     "receipt_date",
                     "total_amount",
                     "store_name",
                     "uid",
                     "created_at"
                     // Joining receipt_items might require a separate query in v2
                 )
            ) { // Start select lambda (request parameter)
                // Use filter builder lambda with explicit FilterOperation
                filter {
                    eq("user_id", userId)
                }
                order("receipt_date", Order.DESCENDING, nullsFirst = false)
            } // End select lambda
            .decodeList<Receipt>() // Decode the result

            // Assuming Receipt data class has default emptyList() for items
            Log.d(TAG, "fetchReceiptsForUser: Successfully fetched ${queryResult.size} receipts for user $userId")
            Result.success(queryResult) // Return the list as decoded

        } catch (e: Exception) {
            Log.e(TAG, "fetchReceiptsForUser: Error during fetch for user $userId. Exception: ${e::class.simpleName}, Message: ${e.message}", e)
            Result.failure(e)
        }
    }
}
