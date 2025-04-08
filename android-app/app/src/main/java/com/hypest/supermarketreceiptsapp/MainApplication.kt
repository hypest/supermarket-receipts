package com.hypest.supermarketreceiptsapp

import android.app.Application
import android.util.Log // Import Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.* // Import WorkManager classes
import com.hypest.supermarketreceiptsapp.worker.SyncPendingScansWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit // Import TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider { // Implement Configuration.Provider

    @Inject // Inject HiltWorkerFactory
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG) // Add verbose logging for WorkManager
            .build()

    override fun onCreate() {
        Log.d("MainApplication", ">>> onCreate START") // Add log
        super.onCreate()
        Log.d("MainApplication", "onCreate: Enqueueing periodic sync work.") // Add log
        enqueuePeriodicSyncWork() // Enqueue periodic worker
        Log.d("MainApplication", ">>> onCreate END") // Add log
    }

    private fun enqueuePeriodicSyncWork() {
        Log.d("MainApplication", ">>> enqueuePeriodicSyncWork CALLED") // Add log
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncPendingScansWorker>(
            repeatInterval = 1, // Repeat every 6 hours
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncPendingScansWork",
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work
            syncWorkRequest
        )
        Log.i("MainApplication", "Periodic SyncPendingScansWork enqueued.") // Add log
    }

}
