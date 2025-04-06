package com.hypest.supermarketreceiptsapp.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.data.local.PendingScanEntity // Import PendingScanEntity
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient // Import SupabaseClient
import io.github.jan.supabase.auth.auth // Import auth extension
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.map // Ensure map is imported
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose // Import awaitClose
import kotlinx.coroutines.delay // Import delay

// Unified state representation for the Receipts Screen
sealed class ReceiptsScreenState {
    data object ReadyToScan : ReceiptsScreenState()
    data object Scanning : ReceiptsScreenState()
    data class ExtractingHtml(val url: String, val showOverlay: Boolean = true) : ReceiptsScreenState()
    // data class Processing(val url: String, val html: String?) : ReceiptsScreenState() // Removed state
    data object Success : ReceiptsScreenState() // Indicates successful queuing
    data class Error(val message: String) : ReceiptsScreenState()
}

data class ReceiptsListState(
    val isLoading: Boolean = false,
    val receipts: List<Receipt> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val authRepository: AuthRepository,
    private val supabaseClient: SupabaseClient, // Inject SupabaseClient
    @ApplicationContext private val context: Context // Inject context for ConnectivityManager
) : ViewModel() {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    companion object {
        private const val TAG = "ReceiptsViewModel" // Add TAG definition
    }

    // Initial state is ReadyToScan as permission is handled by the scanner library
    private val _screenState = MutableStateFlow<ReceiptsScreenState>(ReceiptsScreenState.ReadyToScan)
    val screenState: StateFlow<ReceiptsScreenState> = _screenState.asStateFlow()

    // Flow for pending scan count
    val pendingScanCount: StateFlow<Int> = receiptRepository.getPendingScanCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0 // Start with 0 pending scans
        )

    // Flow for the list of pending scans
    private val pendingScansFlow: Flow<List<PendingScanEntity>> = receiptRepository.getPendingScansFlow()

    // Flow for network connectivity status
    private val networkStatusFlow: Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Check internet capability specifically
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                if (hasInternet) {
                    Log.d(TAG, "Network available")
                    trySend(true)
                }
            }
            override fun onLost(network: Network) {
                 Log.d(TAG, "Network lost")
                trySend(false)
            }
            override fun onUnavailable() {
                 Log.d(TAG, "Network unavailable")
                trySend(false)
            }
        }

        // Check initial state
        val initialState = isNetworkAvailable()
        trySend(initialState)

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        awaitClose { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }.distinctUntilChanged() // Only emit when status changes

    // Convert the Flow<Result<List<Receipt>>> directly into StateFlow<ReceiptsUiState>
    val receiptsListState: StateFlow<ReceiptsListState> = receiptRepository.getReceipts()
        .map { result -> // Map Result<List<Receipt>> to ReceiptsListState
            result.fold(
                onSuccess = { receipts ->
                    ReceiptsListState(isLoading = false, receipts = receipts, error = null)
                },
                onFailure = { throwable ->
                    ReceiptsListState(isLoading = false, error = throwable.message ?: "Failed to load receipts")
                }
            )
        }
        .catch { e -> // Catch exceptions during the flow transformation
            emit(
                ReceiptsListState(isLoading = false, error = e.message ?: ("An " +
                        "unexpected error occurred")
                )
            )
        }
        .stateIn( // Convert to StateFlow
            scope = viewModelScope,
            started = WhileSubscribed(5000),
            initialValue = ReceiptsListState(isLoading = true) // Start with loading state
        )

    // New function to check for pending extractions (moved before init)
    private suspend fun checkForPendingHtmlExtractions() {
        // Only proceed if the UI is currently idle/ready
        if (_screenState.value != ReceiptsScreenState.ReadyToScan) {
            Log.d(TAG, "checkForPendingHtmlExtractions: UI not ready (${_screenState.value}), skipping check.")
            return
        }

        if (isNetworkAvailable()) {
            Log.d(TAG, "checkForPendingHtmlExtractions: Network available, checking DB...")
            val pendingScans = receiptRepository.getPendingScans() // Get snapshot
            val scanToExtract = pendingScans.firstOrNull { scan ->
                shouldExtractHtmlForUrl(scan.url) && scan.htmlContent == null
            }
            if (scanToExtract != null) {
                Log.i(TAG, "Found next pending scan needing HTML extraction: ${scanToExtract.url}. Triggering.")
                _screenState.value = ReceiptsScreenState.ExtractingHtml(scanToExtract.url)
            } else {
                 Log.d(TAG, "No more pending scans needing HTML extraction found.")
            }
        } else {
             Log.d(TAG, "Network not available, cannot check for pending HTML extractions.")
        }
    }

    // Observe pending scans and network status to trigger HTML extraction
    init {
        viewModelScope.launch {
            combine(pendingScansFlow, networkStatusFlow) { scans, isOnline ->
                Pair(scans, isOnline)
            }
            // No filter here, check conditions inside collect
            .collect { (pendingScans, isOnline) ->
                // Check conditions *inside* collect
                if (isOnline && _screenState.value == ReceiptsScreenState.ReadyToScan) {
                    val scanToExtract = pendingScans.firstOrNull { scan ->
                        shouldExtractHtmlForUrl(scan.url) && scan.htmlContent == null
                    }
                    if (scanToExtract != null) {
                        Log.i(TAG, "Conditions met (Online, Ready, Pending): Found scan needing HTML extraction: ${scanToExtract.url}. Triggering.")
                        _screenState.value = ReceiptsScreenState.ExtractingHtml(scanToExtract.url)
                    } else {
                        Log.d(TAG, "Conditions met (Online, Ready), but no pending scans need HTML extraction.")
                    }
                } else {
                     Log.d(TAG, "Conditions not met for auto-extraction check (isOnline=$isOnline, state=${_screenState.value})")
                }
            }
        }
    }
    // Corrected brace placement


    // Called when the user clicks the "Scan QR Code" button
    fun startScanning() {
        _screenState.value = ReceiptsScreenState.Scanning
    }

    // Called by the QrCodeScanner component when a code is successfully scanned
    fun onQrCodeScanned(url: String) {
        Log.d(TAG, "QR Code scanned: $url")

        viewModelScope.launch {
            val needsExtraction = shouldExtractHtmlForUrl(url)
            val isOnline = isNetworkAvailable()

            if (needsExtraction && isOnline) {
                // Online and needs HTML: Start extraction process WITHOUT saving locally yet.
                Log.d(TAG, "Online and HTML needed. Transitioning to ExtractingHtml state for URL: $url")
                _screenState.value = ReceiptsScreenState.ExtractingHtml(url)
            } else {
                // Either offline OR HTML not needed: Save locally immediately (URL only).
                Log.d(TAG, "Offline or HTML not needed. Saving scan locally for URL: $url")
                val saveResult = receiptRepository.saveReceiptUrl(url) // Save URL only

                if (saveResult.isFailure) {
                    Log.e(TAG, "Failed to save pending scan locally for URL: $url", saveResult.exceptionOrNull())
                    _screenState.value = ReceiptsScreenState.Error("Failed to queue scan.")
                    return@launch
                }

                Log.d(TAG, "Scan queued locally for URL: $url")
                _screenState.value = ReceiptsScreenState.Success // Indicate queuing success
                 delay(1500)
                 resetToReadyState()
            }
        }
    }

    // Called by HtmlExtractorWebView when HTML is ready (or if extraction failed)
    fun onHtmlExtracted(url: String, html: String?) {
        if (html == null) {
            Log.e(TAG, "HTML extraction failed for URL: $url")
            _screenState.value = ReceiptsScreenState.Error("Failed to extract HTML content from the receipt page.")
            return
        }
        Log.d(TAG, "HTML extracted (length: ${html.length}), submitting data for URL: $url")

        viewModelScope.launch {
            val submitResult = receiptRepository.submitReceiptData(url, html)
            if (submitResult.isSuccess) {
                _screenState.value = ReceiptsScreenState.Success // Indicate success (local save)
                delay(1500)
                resetToReadyState()
            } else {
                 Log.e(TAG, "Failed to save pending scan with HTML locally for URL: $url", submitResult.exceptionOrNull())
                _screenState.value = ReceiptsScreenState.Error("Failed to queue scan with HTML.")
            }
        }
    }

    // Called by the QrCodeScanner component when the user cancels the scan
    fun onScanCancelled() {
        if (_screenState.value == ReceiptsScreenState.Scanning) {
             _screenState.value = ReceiptsScreenState.ReadyToScan
        }
    }

     // Called by the QrCodeScanner component if it encounters an internal error
     fun onScanError(exception: Exception) {
         Log.e(TAG, "Scanner internal error", exception)
         _screenState.value = ReceiptsScreenState.Error("Scanner error: ${exception.message ?: "Unknown scanner issue"}")
     }

    // Called by HtmlExtractorWebView if it encounters an error
    fun onHtmlExtractionError(url: String, error: String) {
        Log.e(TAG, "HTML extraction error for URL $url: $error")
        _screenState.value = ReceiptsScreenState.Error("Failed to extract HTML: $error")
     }

     // Helper function to decide if WebView extraction is needed using Regex
     private fun shouldExtractHtmlForUrl(url: String): Boolean {
         return try {
             val hostname = java.net.URL(url).host.lowercase()
             val clientSideExtractionPatterns = listOf(
                 Regex(""".*\.epsilonnet\.gr$""")
             )
             val needsExtraction = clientSideExtractionPatterns.any { pattern -> pattern.matches(hostname) }
             if (needsExtraction) {
                 Log.d(TAG, "URL $url matches client-side extraction pattern.")
             } else {
                 Log.d(TAG, "URL $url does NOT match client-side extraction pattern.")
             }
             needsExtraction
         } catch (e: Exception) {
             Log.w(TAG, "Could not parse URL hostname or match regex for: $url", e)
             false
         }
     }

     // Helper function to check network availability
     private fun isNetworkAvailable(): Boolean {
         val activeNetwork = connectivityManager.activeNetwork ?: return false
         val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
         return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
     }


    // Called by HtmlExtractorWebView if it detects a persistent challenge page
    fun onChallengeInteractionRequired() {
        if (_screenState.value is ReceiptsScreenState.ExtractingHtml) {
            val currentState = _screenState.value as ReceiptsScreenState.ExtractingHtml
            Log.d(TAG, "Challenge interaction required for ${currentState.url}. Hiding overlay.")
            _screenState.value = currentState.copy(showOverlay = false)
        }
    }

    // Called when user clicks "Scan Another" or "Try Again" from Success/Error state,
    // or automatically after a successful queue/extraction.
    fun resetToReadyState() {
        _screenState.value = ReceiptsScreenState.ReadyToScan
        // After resetting, immediately check if there's another pending scan to process
        viewModelScope.launch {
            checkForPendingHtmlExtractions() // Call the function defined above
        }
    }

    // Removed duplicate definition from here
}
// Corrected brace placement
