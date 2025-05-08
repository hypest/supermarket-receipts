package com.hypest.supermarketreceiptsapp.data.repository

import android.content.Context // Re-add context
import android.util.Log
import android.widget.Toast // Import Toast
import androidx.work.* // Re-add WorkManager imports
import com.hypest.supermarketreceiptsapp.data.local.PendingScanDao // Re-add PendingScanDao
import com.hypest.supermarketreceiptsapp.data.local.PendingScanEntity // Re-add PendingScanEntity
import com.hypest.supermarketreceiptsapp.data.local.ReceiptDao
import com.hypest.supermarketreceiptsapp.data.local.ReceiptEntity
import com.hypest.supermarketreceiptsapp.data.local.ReceiptItemEntity
import com.hypest.supermarketreceiptsapp.data.local.ReceiptWithItems
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.model.ReceiptItem
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import com.hypest.supermarketreceiptsapp.worker.SyncPendingScansWorker // Re-add worker import
import dagger.hilt.android.qualifiers.ApplicationContext // Re-add qualifier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context, // Re-inject context
    private val supabaseClient: SupabaseClient,
    private val receiptDao: ReceiptDao,
    private val pendingScanDao: PendingScanDao, // Re-inject PendingScanDao
    private val authRepository: AuthRepository,
    private val appScope: CoroutineScope
) : ReceiptRepository {

    companion object {
        private const val SCANNED_URLS_TABLE = "scanned_urls"
        private const val RECEIPTS_TABLE = "receipts"
        private const val TAG = "ReceiptRepository"
    }

    private val userListenerJobs = mutableMapOf<String, Job>()

    // --- Mappers ---
    private fun ReceiptWithItems.toDomainModel(): Receipt {
        return Receipt(
            id = this.receipt.id,
            receiptDate = this.receipt.receiptDate,
            totalAmount = this.receipt.totalAmount,
            storeName = this.receipt.storeName,
            uid = this.receipt.uid,
            createdAt = this.receipt.createdAt,
            items = this.items.map { it.toDomainModel() }
        )
    }

    private fun ReceiptItemEntity.toDomainModel(): ReceiptItem {
        return ReceiptItem(
            id = null, // Local entity doesn't store Supabase item ID
            name = this.name,
            quantity = this.quantity,
            price = this.price, // Total price
            unitPrice = this.unitPrice, // Map unit price
            vatPercentage = this.vatPercentage // Map VAT percentage
        )
    }

    private fun Receipt.toEntity(userId: String): ReceiptEntity {
        return ReceiptEntity(
            id = this.id,
            receiptDate = this.receiptDate,
            totalAmount = this.totalAmount,
            storeName = this.storeName,
            uid = this.uid,
            createdAt = this.createdAt,
            userId = userId
        )
    }

    private fun ReceiptItem.toEntity(receiptId: String): ReceiptItemEntity {
        return ReceiptItemEntity(
            receiptId = receiptId,
            name = this.name,
            quantity = this.quantity,
            price = this.price, // Total price
            unitPrice = this.unitPrice, // Map unit price
            vatPercentage = this.vatPercentage // Map VAT percentage
        )
    }
    // --- End Mappers ---


    // Updated: Save scan data locally and enqueue sync worker
    override suspend fun submitReceiptData(url: String, htmlContent: String): Result<Unit> { // Remove userId parameter
        return try {
            // Get current user ID (can be null if logged out)
            val userId = supabaseClient.auth.currentUserOrNull()?.id

            val pendingScan = PendingScanEntity(
                url = url,
                htmlContent = htmlContent,
                userId = userId
            )
            pendingScanDao.insertPendingScan(pendingScan)
            Log.d(TAG, "Saved pending scan locally: URL=$url, HTML Length=${htmlContent.length}, User=$userId")
            showToast("Receipt scan queued for upload.") // Show Toast
            enqueueOneTimeSyncWork() // Trigger sync attempt
            Result.success(Unit) // Return success immediately
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pending scan locally for URL $url", e)
            Result.failure(e)
        }
    }

    // Updated: Save scan URL locally and enqueue sync worker
    override suspend fun saveReceiptUrl(url: String): Result<Unit> { // Remove userId parameter
         return try {
            // Get current user ID (can be null if logged out)
            val userId = supabaseClient.auth.currentUserOrNull()?.id

            val pendingScan = PendingScanEntity(
                url = url,
                htmlContent = null,
                userId = userId
            )
            pendingScanDao.insertPendingScan(pendingScan)
            Log.d(TAG, "Saved pending scan URL locally: URL=$url, User=$userId")
            showToast("Receipt scan queued for upload.") // Show Toast
            enqueueOneTimeSyncWork() // Trigger sync attempt
            Result.success(Unit) // Return success immediately
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pending scan URL locally for URL $url", e)
            Result.failure(e)
        }
    }

    // --- Functions for Syncing Pending Scans (Called by Worker) ---
    // Re-add implementations for the interface methods

    override suspend fun syncPendingScanToServer(url: String, htmlContent: String, userId: String): Result<Unit> {
         return try {
            val dataToInsert = mapOf(
                "url" to url,
                "user_id" to userId,
                "html_content" to htmlContent
            )
            Log.d(TAG, "SYNC: Attempting to submit receipt data: URL=$url, HTML Length=${htmlContent.length}, User=$userId")
            supabaseClient.postgrest[SCANNED_URLS_TABLE].insert(listOf(dataToInsert))
            Log.d(TAG, "SYNC: Receipt data submitted successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "SYNC: Error submitting receipt data for URL $url", e)
            Result.failure(e)
        }
    }

     override suspend fun syncPendingScanUrlToServer(url: String, userId: String): Result<Unit> {
         return try {
            val dataToInsert = mapOf(
                "url" to url,
                "user_id" to userId
            )
            Log.d(TAG, "SYNC: Attempting to save receipt URL only: URL=$url, User=$userId")
            supabaseClient.postgrest[SCANNED_URLS_TABLE].insert(listOf(dataToInsert))
            Log.d(TAG, "SYNC: Receipt URL saved successfully for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "SYNC: Error saving receipt URL for URL $url", e)
            Result.failure(e)
        }
    }
    // --- End Sync Functions ---


    override suspend fun getReceiptById(id: String): Receipt? {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        val localReceipt = receiptDao.getReceiptWithItemsById(id)
        if (localReceipt != null && localReceipt.receipt.userId == userId) {
            Log.d(TAG, "getReceiptById: Found receipt ID $id in local cache for user $userId.")
            return localReceipt.toDomainModel()
        }

        if (userId == null) {
             Log.w(TAG, "getReceiptById: User not logged in, cannot fetch from network.")
             return null
        }

        Log.d(TAG, "getReceiptById: Receipt ID $id not in cache for user $userId, fetching from network.")
        return try {
            val networkReceipt = supabaseClient.postgrest[RECEIPTS_TABLE].select(
                columns = Columns.list(
                    "id", "receipt_date", "total_amount", "store_name", "uid", "created_at",
                    "receipt_items(id, name, quantity, price, unit_price, vat_percentage)" // Add vat_percentage here
                )
            ) {
                filter {
                    eq("id", id)
                    eq("user_id", userId)
                }
                limit(1)
            }.decodeList<Receipt>().firstOrNull()

            if (networkReceipt != null) {
                Log.d(TAG, "getReceiptById Network: Successfully fetched receipt ID $id. Saving to cache.")
                val receiptEntity = networkReceipt.toEntity(userId)
                val itemEntities = networkReceipt.items.map { it.toEntity(networkReceipt.id) }
                receiptDao.insertReceiptWithItems(receiptEntity, itemEntities)
                networkReceipt
            } else {
                Log.w(TAG, "getReceiptById Network: Receipt with ID $id not found for user $userId.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getReceiptById Network: Error fetching receipt with ID $id.", e)
            null
        }
    }

    // Returns a flow observing the local database, dynamically switching based on auth state
    override fun getReceipts(): Flow<Result<List<Receipt>>> {
        Log.d(TAG, "getReceipts: Setting up auth-state driven flow.")
        return authRepository.sessionStatus.flatMapLatest { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val userId = status.session.user?.id
                    if (userId == null) {
                        Log.e(TAG, "getReceipts: Authenticated but user ID is null!")
                        cancelAndClearListener(null) // Cancel any lingering listener
                        flowOf(Result.failure(IllegalStateException("Authenticated user has null ID.")))
                    } else {
                        Log.i(TAG, "getReceipts: User $userId authenticated. Setting up data flow, ensuring listener, and triggering one-time sync.")
                        // Ensure listener is running for this user
                        ensureListenerIsRunning(userId)
                        // Trigger a one-time sync attempt now that user is logged in
                        enqueueOneTimeSyncWork()
                        // Return the flow observing the DAO for this user
                        receiptDao.getReceiptsWithItemsForUser(userId)
                            .map { list -> Result.success(list.map { it.toDomainModel() }) }
                            .catch { e ->
                                Log.e(TAG, "getReceipts: Error reading from local DAO for user $userId", e)
                                emit(Result.failure(e))
                            }
                    }
                }
                is SessionStatus.NotAuthenticated -> {
                    Log.i(TAG, "getReceipts: User not authenticated. Cancelling listener.")
                    cancelAndClearListener(null) // Cancel listener associated with previous user (if any)
                    flowOf(Result.success(emptyList())) // Emit empty list when not authenticated
                }
                else -> { // Initializing, NetworkError
                    Log.d(TAG, "getReceipts: Auth status is $status. Returning empty success flow.")
                    flowOf(Result.success(emptyList())) // Emit empty list during intermediate states
                }
            }
        }.distinctUntilChanged()
    }

    // Ensures the listener is running for the specified user, starting it if necessary.
    private fun ensureListenerIsRunning(userId: String) {
        if (userListenerJobs[userId]?.isActive == true) {
            Log.d(TAG, "ensureListenerIsRunning: Listener already active for user $userId")
            return
        }
        userListenerJobs.filterKeys { it != userId }.forEach { (id, job) ->
            Log.w(TAG, "ensureListenerIsRunning: Cancelling listener for different user $id")
            job.cancel()
        }
        userListenerJobs.clear()

        Log.i(TAG, "ensureListenerIsRunning: Starting listener and initial fetch for user $userId")
        userListenerJobs[userId] = setupRealtimeListener(userId, appScope)
        appScope.launch { fetchAndCacheReceipts(userId) }
    }

    // Cancels the listener job and unsubscribes from the channel for a specific user ID or all users.
    private fun cancelAndClearListener(userIdToClear: String?) {
        val jobsToCancel = if (userIdToClear != null) {
            listOfNotNull(userListenerJobs.remove(userIdToClear))
        } else {
            val jobs = userListenerJobs.values.toList()
            userListenerJobs.clear()
            jobs
        }

        if (jobsToCancel.isNotEmpty()) {
            Log.i(TAG, "cancelAndClearListener: Cancelling ${jobsToCancel.size} listener job(s).")
            jobsToCancel.forEach { it.cancel() }
        }
    }


    // Sets up the Supabase Realtime listener - Runs within the provided scope
    private fun setupRealtimeListener(userId: String, listenerScope: CoroutineScope): Job {
       return listenerScope.launch {
            var channel: RealtimeChannel? = null
            try {
                Log.i(TAG, ">>> setupRealtimeListener coroutine STARTING for user $userId")
                channel = supabaseClient.realtime.channel("receipts_user_$userId")
                Log.i(TAG, ">>> setupRealtimeListener channel CREATED: ${channel.topic}")

                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = RECEIPTS_TABLE
                    filter(FilterOperation("user_id", FilterOperator.EQ, userId))
                }.catch { e ->
                    Log.e(TAG, ">>> setupRealtimeListener CATCH postgresChangeFlow for user $userId", e)
                }

                Log.i(TAG, ">>> setupRealtimeListener ATTEMPTING subscribe for ${channel.topic}")
                channel.subscribe(blockUntilSubscribed = true)
                Log.i(TAG, ">>> setupRealtimeListener SUBSCRIBED successfully to ${channel.topic}")

                Log.i(TAG, ">>> setupRealtimeListener STARTING collect for ${channel.topic}")
                changeFlow.collect { action ->
                    try {
                        Log.d(TAG, ">>> Realtime Update Received for user $userId: $action. Triggering refresh.")
                        fetchAndCacheReceipts(userId)
                    } catch (e: Exception) {
                        Log.e(TAG, ">>> setupRealtimeListener CATCH processing action for user $userId", e)
                    }
                }
                Log.i(TAG, ">>> setupRealtimeListener FINISHED collect for ${channel.topic}")

            } catch (e: Exception) {
                Log.e(TAG, ">>> setupRealtimeListener CATCH main try block for user $userId", e)
            } finally {
                 Log.i(TAG, ">>> Realtime listener coroutine for $userId FINALLY block (isActive=${isActive}).")
                 channel?.let { ch ->
                     appScope.launch {
                         try {
                             Log.d(TAG, "Unsubscribing from channel ${ch.topic}")
                             ch.unsubscribe()
                         } catch (e: Exception) {
                             Log.e(TAG, "Error unsubscribing from channel ${ch.topic}", e)
                         }
                     }
                 }
                 userListenerJobs.remove(userId)
            }
        }
    }

    // Fetches all receipts from network and updates the local cache
    private suspend fun fetchAndCacheReceipts(userId: String) {
        try {
            Log.d(TAG, "fetchAndCacheReceipts: Fetching all receipts from network for user $userId")
            val networkReceipts = supabaseClient.postgrest[RECEIPTS_TABLE].select(
                columns = Columns.list(
                    "id", "receipt_date", "total_amount", "store_name", "uid", "created_at",
                    "receipt_items(id, name, quantity, price, unit_price, vat_percentage)" // Add vat_percentage here
                )
            ) {
                filter { eq("user_id", userId) }
                order("receipt_date", Order.DESCENDING, nullsFirst = false)
            }.decodeList<Receipt>()

            Log.d(TAG, "fetchAndCacheReceipts: Fetched ${networkReceipts.size} receipts from network. Updating cache.")

            val receiptsToCache = networkReceipts.map { receipt ->
                ReceiptWithItems(
                    receipt = receipt.toEntity(userId),
                    items = receipt.items.map { it.toEntity(receipt.id) }
                )
            }

            receiptDao.replaceAllReceiptsForUser(userId, receiptsToCache)

            Log.d(TAG, "fetchAndCacheReceipts: Cache updated successfully for user $userId.")

        } catch (e: Exception) {
            Log.e(TAG, "fetchAndCacheReceipts: Error fetching or caching receipts for user $userId", e)
        }
    }

    // Helper function to show Toast messages
    private fun showToast(message: String) {
        // Ensure Toast is shown on the main thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Returns a flow observing the count of pending scans
    override fun getPendingScanCountFlow(): Flow<Int> {
        // Directly return the flow from the DAO
        return pendingScanDao.getPendingScanCount()
            .catch { e ->
                Log.e(TAG, "Error getting pending scan count flow", e)
                emit(0) // Emit 0 in case of error
            }
    }

    // Function to observe the list of pending scans
    override fun getPendingScansFlow(): Flow<List<PendingScanEntity>> {
        return pendingScanDao.getAllPendingScansFlow()
            .catch { e ->
                Log.e(TAG, "Error getting pending scans flow", e)
                emit(emptyList()) // Emit empty list on error
            } // Correct brace placement
    }

    // Function to get a snapshot list of pending scans
    override suspend fun getPendingScans(): List<PendingScanEntity> {
        return try {
            pendingScanDao.getPendingScans()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending scans snapshot", e)
            emptyList() // Return empty list on error
        }
    }

    // Function to enqueue a one-time sync request (moved from MainApplication)
    // This is now called when user is authenticated in getReceipts() flow
    // Also called when a new scan is submitted locally
    private fun enqueueOneTimeSyncWork() {
        Log.d(TAG, ">>> enqueueOneTimeSyncWork CALLED from Repository")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncPendingScansWorker>()
            .setConstraints(constraints)
            .build()

        // Use a unique name and REPLACE policy to ensure only one sync attempt is queued
        // if the auth state changes rapidly or this function is called multiple times.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncPendingScansOnceOnAuthOrSubmit", // Updated unique name
            ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
        Log.i(TAG, "One-time SyncPendingScansWork enqueued on authentication or submit.") // Updated log
    }
}
