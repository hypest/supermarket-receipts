package com.hypest.supermarketreceiptsapp.worker

import android.content.Context
import android.util.Log
import android.widget.Toast // Import Toast
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hypest.supermarketreceiptsapp.data.local.PendingScanDao
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository // Inject Repository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.firstOrNull // Import firstOrNull
import java.net.URL // Import URL for hostname check
import com.hypest.supermarketreceiptsapp.data.local.PendingScanEntity // Import PendingScanEntity

@HiltWorker
class SyncPendingScansWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    // Inject dependencies needed for the background task
    private val pendingScanDao: PendingScanDao,
    // Inject Repository to call the actual network submission logic
    private val receiptRepository: ReceiptRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncPendingScansWorker"
        // SCANNED_URLS_TABLE is handled within the repository now
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, ">>> SyncPendingScansWorker: doWork() STARTED.") // Add detailed log
        showToast("Syncing queued receipts...") // Show Toast on start

        // Get the list of pending scans by taking the first emission from the flow
        val pendingScans: List<PendingScanEntity> = pendingScanDao.getAllPendingScansFlow().firstOrNull() ?: emptyList() // Explicit type
        if (pendingScans.isEmpty()) { // Use isEmpty() extension for List
            Log.d(TAG, "No pending scans to sync.")
            return Result.success()
        }

        Log.d(TAG, "Found ${pendingScans.size} pending scans to sync.") // Use size property for List
        var allSucceeded = true

        for (scan in pendingScans) { // Iterate directly over the list
            try {
                // Check if userId is present before attempting sync
                val userId = scan.userId
                if (userId == null) {
                    Log.w(TAG, "Scan URL ${scan.url} has null userId. Skipping sync (likely scanned while logged out).")
                    continue // Move to the next scan
                }

                // userId is confirmed non-null here
                Log.d(TAG, "Attempting to sync scan URL: ${scan.url} for user $userId")

                // Determine if HTML extraction is required for this URL type
                val needsExtraction = shouldExtractHtmlForUrl(scan.url)

                if (scan.htmlContent != null) {
                    // Has HTML content, attempt sync and handle result
                    Log.d(TAG, "Scan URL ${scan.url} has HTML content, attempting sync.")
                    receiptRepository.syncPendingScanToServer(scan.url, scan.htmlContent, userId) // Pass non-null userId
                        .fold(
                            onSuccess = {
                                Log.d(TAG, "Successfully synced scan URL: ${scan.url} (with HTML)")
                                pendingScanDao.deletePendingScanByUrl(scan.url) // Use delete by URL
                                Log.d(TAG, "Deleted synced scan URL: ${scan.url} from local DB.")
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Failed to sync scan URL: ${scan.url} (with HTML). Error: ${error.message}") // Log URL
                                allSucceeded = false
                            }
                        )
                } else if (!needsExtraction) {
                    // No HTML content, and none needed, attempt URL sync and handle result
                    Log.d(TAG, "Scan URL ${scan.url} does not need HTML, attempting URL sync.")
                    receiptRepository.syncPendingScanUrlToServer(scan.url, userId) // Pass non-null userId
                         .fold(
                            onSuccess = {
                                Log.d(TAG, "Successfully synced scan URL: ${scan.url} (URL only)")
                                pendingScanDao.deletePendingScanByUrl(scan.url) // Use delete by URL
                                Log.d(TAG, "Deleted synced scan URL: ${scan.url} from local DB.")
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Failed to sync scan URL: ${scan.url} (URL only). Error: ${error.message}") // Log URL
                                allSucceeded = false
                            }
                        )
                } else {
                    // No HTML content, but HTML *is* needed (Epsilonnet type scanned offline)
                    Log.w(TAG, "Scan URL ${scan.url} needs HTML extraction but has no content. Skipping sync (requires UI interaction).") // Log URL
                    // Do nothing, keep the scan in the queue, don't mark as failure
                }

            } catch (e: Exception) { // Catch unexpected errors during the loop (e.g., DB access)
                Log.e(TAG, "Unexpected error syncing scan URL: ${scan.url}. Error: ${e.message}", e) // Log URL
                allSucceeded = false
            }
        }

        return if (allSucceeded) {
            Log.d(TAG, "Sync work finished successfully.")
            Result.success()
        } else {
            Log.d(TAG, "Sync work finished with some failures. Retrying later.")
            Result.retry()
        }
    }

    // Helper function to show Toast messages on the main thread
    private fun showToast(message: String) {
        // Need applicationContext to show Toast from Worker
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Duplicate helper function from ViewModel to check URL type
    // TODO: Consider moving this to a shared utility class
    private fun shouldExtractHtmlForUrl(url: String): Boolean {
         return try {
             val hostname = URL(url).host.lowercase()
             val clientSideExtractionPatterns = listOf(
                 Regex(""".*\.epsilonnet\.gr$""")
             )
             clientSideExtractionPatterns.any { pattern -> pattern.matches(hostname) }
         } catch (e: Exception) {
             Log.w(TAG, "Could not parse URL hostname or match regex for: $url", e)
             false
         }
     }
}
