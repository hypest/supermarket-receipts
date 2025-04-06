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

        val pendingScans = pendingScanDao.getAllPendingScans()
        if (pendingScans.isEmpty()) {
            Log.d(TAG, "No pending scans to sync.")
            return Result.success()
        }

        Log.d(TAG, "Found ${pendingScans.size} pending scans to sync.")
        var allSucceeded = true

        for (scan in pendingScans) {
            try {
                Log.d(TAG, "Attempting to sync scan ID: ${scan.id}, URL: ${scan.url}")

                // Call a dedicated function in the repository to handle the actual network submission
                // This keeps network logic centralized in the repository
                val syncResult = if (scan.htmlContent != null) {
                    receiptRepository.syncPendingScanToServer(scan.url, scan.htmlContent, scan.userId)
                } else {
                    receiptRepository.syncPendingScanUrlToServer(scan.url, scan.userId)
                }

                // Use fold to handle success and failure cases of the Result
                syncResult.fold(
                    onSuccess = {
                        Log.d(TAG, "Successfully synced scan ID: ${scan.id}")
                        // Delete the scan from local DB after successful sync
                        pendingScanDao.deletePendingScanById(scan.id)
                        Log.d(TAG, "Deleted synced scan ID: ${scan.id} from local DB.")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to sync scan ID: ${scan.id}. Error: ${error.message}")
                        allSucceeded = false
                        // Keep the item in the DB to retry later
                    }
                )

            } catch (e: Exception) { // Catch unexpected errors during the loop (e.g., DB access)
                Log.e(TAG, "Unexpected error syncing scan ID: ${scan.id}. Error: ${e.message}", e)
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
}
