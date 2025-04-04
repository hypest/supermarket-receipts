package com.hypest.supermarketreceiptsapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.* // Import necessary flow operators
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import javax.inject.Inject

// Unified state representation for the Main Screen
sealed class ReceiptsScreenState {
    object CheckingPermission : ReceiptsScreenState()
    data class NoPermission(val message: String) : ReceiptsScreenState()
    object ReadyToScan : ReceiptsScreenState()
    object Scanning : ReceiptsScreenState()
    // Add showOverlay flag, defaulting to true
    data class ExtractingHtml(val url: String, val showOverlay: Boolean = true) : ReceiptsScreenState()
    data class Processing(val url: String, val html: String?) : ReceiptsScreenState()
    object Success : ReceiptsScreenState()
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
    private val authRepository: AuthRepository // Keep AuthRepository if needed for user ID etc.
) : ViewModel() {

    companion object {
        private const val TAG = "ReceiptsViewModel" // Add TAG definition
    }

    private val _screenState = MutableStateFlow<ReceiptsScreenState>(ReceiptsScreenState.CheckingPermission)
    val screenState: StateFlow<ReceiptsScreenState> = _screenState.asStateFlow()

    // Convert the Flow<Result<List<Receipt>>> directly into StateFlow<ReceiptsUiState>
    val receiptsListState: StateFlow<ReceiptsListState> = receiptRepository.getReceipts()
        .map { result -> // Map Result<List<Receipt>> to ReceiptsUiState
            result.fold(
                onSuccess = { receipts ->
                    ReceiptsListState(isLoading = false, receipts = receipts, error = null)
                },
                onFailure = { throwable ->
                    // Keep existing receipts if available during error? Or clear them?
                    // Let's keep them for now, but show error.
                    // Could also use stateIn's initialValue to handle initial loading state better.
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
            started = WhileSubscribed(5000), // Keep upstream flow active for 5s after last collector disappears
            initialValue = ReceiptsListState(isLoading = true) // Start with loading state
        )

    // Store pending data while waiting for auth
    private var pendingUrl: String? = null
    private var pendingHtml: String? = null

    // Keep track of actual permission status internally if needed for logic,
    // but UI state is driven by _screenState
    private var hasCameraPermissionInternal = false

    fun onPermissionResult(isGranted: Boolean) {
        hasCameraPermissionInternal = isGranted
        if (isGranted) {
            // Only transition to ReadyToScan if we were checking or previously denied
            if (_screenState.value is ReceiptsScreenState.CheckingPermission ||
                _screenState.value is ReceiptsScreenState.NoPermission) {
                 _screenState.value = ReceiptsScreenState.ReadyToScan
            }
            // If permission granted while in another state (e.g. Error), stay there until user action
        } else {
            _screenState.value = ReceiptsScreenState.NoPermission("Camera permission is required to scan QR codes.")
        }
    }

    // Called when the user clicks the "Scan QR Code" button
    fun startScanning() {
        if (hasCameraPermissionInternal) {
            _screenState.value = ReceiptsScreenState.Scanning
        } else {
             // Should ideally not happen if button is only shown when permission granted, but handle defensively
             _screenState.value = ReceiptsScreenState.NoPermission("Camera permission is required.")
             // Consider triggering permission request again here
        }
    }

    // Called by the QrCodeScanner component when a code is successfully scanned
    fun onQrCodeScanned(url: String) {
        Log.d("MainViewModel", "QR Code scanned: $url")
        // Decide whether to extract HTML based on URL
        if (shouldExtractHtmlForUrl(url)) {
            Log.d("MainViewModel", "Transitioning to ExtractingHtml state for URL: $url")
            _screenState.value = ReceiptsScreenState.ExtractingHtml(url)
            // Actual processing triggered by onHtmlExtracted
        } else {
            Log.d("MainViewModel", "Skipping HTML extraction, transitioning directly to Processing state for URL: $url")
            // For providers like Entersoft, process immediately without HTML
            _screenState.value = ReceiptsScreenState.Processing(url, null) // Pass null for HTML
            // Store data and wait for auth confirmation before processing
            pendingUrl = url
            pendingHtml = null
        }
    }

    // Called by HtmlExtractorWebView when HTML is ready (or if extraction failed)
    fun onHtmlExtracted(url: String, html: String?) {
        if (html == null) {
            Log.e("MainViewModel", "HTML extraction failed for URL: $url")
            _screenState.value = ReceiptsScreenState.Error("Failed to extract HTML content from the receipt page.")
            return
        }
        Log.d("MainViewModel", "HTML extracted (length: ${html.length}), transitioning to Processing state for URL: $url")
        // Store data and wait for auth confirmation before processing
        pendingUrl = url
        pendingHtml = html
        _screenState.value = ReceiptsScreenState.Processing(url, html) // Show processing state
        // processScannedData(url, html) // Don't call directly, wait for auth state
    }

     // Observe auth status and process pending data when authenticated
     init {
         authRepository.sessionStatus // Should now use the v2 SessionStatus from AuthRepository
             .onEach { status ->
                 if (status is SessionStatus.Authenticated && pendingUrl != null) {
                     Log.d("MainViewModel", "User authenticated, processing pending receipt for URL: $pendingUrl")
                     processScannedData(pendingUrl!!, pendingHtml) // Pass stored data
                     // Clear pending data after processing attempt starts
                     pendingUrl = null
                     pendingHtml = null
                 } else if (status is SessionStatus.NotAuthenticated && _screenState.value is ReceiptsScreenState.Processing) { // Check v2 NotAuthenticated state
                     // Handle case where user logs out while processing was pending
                     Log.w("MainViewModel", "User logged out while receipt processing was pending.")
                     _screenState.value = ReceiptsScreenState.Error("User logged out before receipt could be saved.")
                     pendingUrl = null
                     pendingHtml = null
                 }
             }
             .launchIn(viewModelScope)
     }


    // Central function to handle submitting data to the repository
    // Now called only when auth state is confirmed Authenticated and pending data exists
    private fun processScannedData(url: String, html: String?) {
         viewModelScope.launch {
             // We know user is authenticated here because this is only called from the observer
            Log.d("MainViewModel", "Processing receipt data: URL=$url, HasHTML=${html != null}")

            Log.d("MainViewModel", "Processing receipt data: URL=$url, HasHTML=${html != null}") // Removed UserID from log

            // Choose repository function based on whether HTML was extracted
            val result = if (html != null) {
                receiptRepository.submitReceiptData(url, html) // Call without userId
            } else {
                receiptRepository.saveReceiptUrl(url) // Call without userId
            }

            result.onSuccess {
                Log.d("MainViewModel", "Receipt data processed successfully for URL: $url")
                _screenState.value = ReceiptsScreenState.Success
            }.onFailure { error ->
                Log.e("MainViewModel", "Error processing receipt data for URL: $url", error)
                _screenState.value = ReceiptsScreenState.Error(error.message ?: "Failed to process receipt data.")
            }
        }
    }

    // Called by the QrCodeScanner component when the user cancels the scan
    fun onScanCancelled() {
        // Go back to ready state only if we were actually scanning
        if (_screenState.value == ReceiptsScreenState.Scanning) {
             _screenState.value = ReceiptsScreenState.ReadyToScan
        }
    }

     // Called by the QrCodeScanner component if it encounters an internal error
     fun onScanError(exception: Exception) {
         Log.e("MainViewModel", "Scanner internal error", exception)
         // Go back to ready state or show specific error
         _screenState.value = ReceiptsScreenState.Error("Scanner error: ${exception.message ?: "Unknown scanner issue"}")
         // Consider transitioning back to ReadyToScan after a delay or let user click "Try Again"
     }

    // Called by HtmlExtractorWebView if it encounters an error
    fun onHtmlExtractionError(url: String, error: String) {
        Log.e("MainViewModel", "HTML extraction error for URL $url: $error")
        _screenState.value = ReceiptsScreenState.Error("Failed to extract HTML: $error")
         // Consider going back to ReadyToScan automatically or require user action
     }

     // Helper function to decide if WebView extraction is needed using Regex
     private fun shouldExtractHtmlForUrl(url: String): Boolean {
         return try {
             val hostname = java.net.URL(url).host.lowercase() // Use lowercase for case-insensitive match

             // Define regex patterns for providers requiring client-side extraction
             // Currently targets any subdomain ending in .epsilonnet.gr
             val clientSideExtractionPatterns = listOf(
                 Regex(""".*\.epsilonnet\.gr$""")
                 // Add Regex(""".*\.another-provider\.com/path/.*""") etc. if needed later
             )

             val needsExtraction = clientSideExtractionPatterns.any { pattern -> pattern.matches(hostname) }

             if (needsExtraction) {
                 Log.d("MainViewModel", "URL $url matches client-side extraction pattern.")
             } else {
                 Log.d("MainViewModel", "URL $url does NOT match client-side extraction pattern (will use backend fetching).")
             }
             needsExtraction

         } catch (e: Exception) {
             Log.w("MainViewModel", "Could not parse URL hostname or match regex for: $url", e)
             false // Default to not extracting if URL is invalid or regex fails
         }
     }

    // Called by HtmlExtractorWebView if it detects a persistent challenge page
    fun onChallengeInteractionRequired() {
        if (_screenState.value is ReceiptsScreenState.ExtractingHtml) {
            val currentState = _screenState.value as ReceiptsScreenState.ExtractingHtml
            Log.d("MainViewModel", "Challenge interaction required for ${currentState.url}. Hiding overlay.")
            _screenState.value = currentState.copy(showOverlay = false)
        }
    }


    // Called when user clicks "Scan Another" or "Try Again"
    fun resetToReadyState() {
        if (hasCameraPermissionInternal) {
            _screenState.value = ReceiptsScreenState.ReadyToScan
        } else {
            // If permission somehow got revoked, reflect that
             _screenState.value = ReceiptsScreenState.NoPermission("Camera permission is required.")
        }
    }
}
