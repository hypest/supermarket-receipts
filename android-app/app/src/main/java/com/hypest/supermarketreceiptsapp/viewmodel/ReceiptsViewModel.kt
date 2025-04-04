package com.hypest.supermarketreceiptsapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow

// Unified state representation for the Receipts Screen
sealed class ReceiptsScreenState {
    data object ReadyToScan : ReceiptsScreenState()
    data object Scanning : ReceiptsScreenState()
    data class ExtractingHtml(val url: String, val showOverlay: Boolean = true) : ReceiptsScreenState()
    data class Processing(val url: String, val html: String?) : ReceiptsScreenState()
    data object Success : ReceiptsScreenState()
    data class Error(val message: String) : ReceiptsScreenState()
}

// Event to represent scanned data
private data class ScanEvent(val url: String, val html: String? = null)

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

    // Initial state is ReadyToScan as permission is handled by the scanner library
    private val _screenState = MutableStateFlow<ReceiptsScreenState>(ReceiptsScreenState.ReadyToScan)
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

    // Flow to handle scan events, ensuring only the latest is processed if auth is delayed
    private val _scanEventFlow = MutableSharedFlow<ScanEvent>(
        replay = 1, // Keep the last event for late subscribers (like auth state change)
        onBufferOverflow = BufferOverflow.DROP_OLDEST // Drop older events if buffer overflows
    )
    private val scanEventFlow = _scanEventFlow.asSharedFlow()

    // Called when the user clicks the "Scan QR Code" button
    fun startScanning() {
        // Directly transition to Scanning state. Permission handled by the scanner library.
        _screenState.value = ReceiptsScreenState.Scanning
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
            // Emit event to the flow
            viewModelScope.launch {
                _scanEventFlow.emit(ScanEvent(url = url, html = null))
            }
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
        _screenState.value = ReceiptsScreenState.Processing(url, html) // Show processing state
        // Emit event to the flow
        viewModelScope.launch {
            _scanEventFlow.emit(ScanEvent(url = url, html = html))
        }
    }

    // Observe auth status and combine it with the latest scan event
    init {
        viewModelScope.launch {
            // Combine the latest scan event with the auth status.
            // `combine` ensures we only proceed when both flows have emitted at least once
            // and triggers whenever either flow emits a new value.
            scanEventFlow.combine(authRepository.sessionStatus) { event, status ->
                Pair(event, status)
            }
                // We only care about processing when the user is authenticated.
                .filter { (_, status) -> status is SessionStatus.Authenticated }
                // Avoid processing the same event multiple times if auth status flaps quickly
                .distinctUntilChangedBy { (event, _) -> event }
                .collectLatest { (event, status) ->
                    // Double-check status just in case, though filter should handle it.
                    if (status is SessionStatus.Authenticated) {
                        Log.d(TAG, "User authenticated, processing event for URL: ${event.url}")
                        // Check if the current state is Processing, otherwise the event might be stale
                        // (e.g., user scanned, logged out, logged back in - we shouldn't process the old scan)
                        // Or maybe we *should* process it? Let's assume yes for now, the replay=1 handles this.
                        if (_screenState.value is ReceiptsScreenState.Processing &&
                            (_screenState.value as ReceiptsScreenState.Processing).url == event.url) {

                            // Call the suspend function and wait for its result
                            val result = processScannedData(event.url, event.html)

                            // Update state based on the result *within* the collectLatest block
                            result.onSuccess {
                                Log.d(TAG, "Receipt data processed successfully (within collectLatest) for URL: ${event.url}")
                                // Only transition from Processing to Success/Error
                                if (_screenState.value is ReceiptsScreenState.Processing && (_screenState.value as ReceiptsScreenState.Processing).url == event.url) {
                                    _screenState.value = ReceiptsScreenState.Success
                                }
                            }.onFailure { error ->
                                Log.e(TAG, "Error processing receipt data (within collectLatest) for URL: ${event.url}", error)
                                // Only transition from Processing to Success/Error
                                if (_screenState.value is ReceiptsScreenState.Processing && (_screenState.value as ReceiptsScreenState.Processing).url == event.url) {
                                    _screenState.value = ReceiptsScreenState.Error(error.message ?: "Failed to process receipt data.")
                                }
                            }
                            // Consume the event? Replay=1 handles overwriting.
                            // If double processing is an issue, more complex state management or event clearing needed.
                            // but SharedFlow with replay=1 and collectLatest is often sufficient.
                        } else {
                             Log.w(TAG, "Auth confirmed, but current state (${_screenState.value}) doesn't match pending event URL (${event.url}). Ignoring stale event.")
                        }
                    }
                }
        }

        // Also handle the case where the user logs out *while* processing is pending
        viewModelScope.launch {
            authRepository.sessionStatus
                .filter { it is SessionStatus.NotAuthenticated }
                .collect {
                    // If we were in a processing state, the user logged out before it could be saved.
                    if (_screenState.value is ReceiptsScreenState.Processing) {
                        Log.w(TAG, "User logged out while receipt processing was pending.")
                        _screenState.value = ReceiptsScreenState.Error("User logged out before receipt could be saved.")
                        // Clear any pending event in the flow? Replay cache makes this tricky without
                        // emitting a "clear" event or using a different mechanism.
                        // For now, the combine logic will prevent processing if auth fails.
                    }
                }
        }
    }


    // Central suspend function to handle submitting data to the repository
    // Returns Result<Unit> to indicate success or failure
    private suspend fun processScannedData(url: String, html: String?): Result<Unit> {
        // We know user is authenticated here because this is only called from the observer logic
        Log.d(TAG, "Processing receipt data: URL=$url, HasHTML=${html != null}")

        // Choose repository function based on whether HTML was extracted
        // Assuming repository functions return Result directly or are suspend fun returning Unit/throwing Exception
        // Let's wrap in runCatching for safety if they aren't returning Result already.
        // NOTE: If repository functions are already suspend fun returning Result, runCatching is redundant.
        // Adjust based on actual ReceiptRepository implementation.
        return runCatching { // Use runCatching to ensure a Result is always returned
            if (html != null) {
                // Assuming submitReceiptData is suspend or returns Result
                receiptRepository.submitReceiptData(url, html).getOrThrow() // Use getOrThrow if it returns Result
            } else {
                // Assuming saveReceiptUrl is suspend or returns Result
                receiptRepository.saveReceiptUrl(url).getOrThrow() // Use getOrThrow if it returns Result
            }
            // If repository functions are suspend fun returning Unit, the above lines simplify:
            // if (html != null) receiptRepository.submitReceiptData(url, html)
            // else receiptRepository.saveReceiptUrl(url)
        }
        // runCatching automatically wraps success (Unit) or failure (Exception) into a Result<Unit>
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
            Log.d(TAG, "Challenge interaction required for ${currentState.url}. Hiding overlay.")
            _screenState.value = currentState.copy(showOverlay = false)
        }
    }

    // Called when user clicks "Scan Another" or "Try Again" from Success/Error state
    fun resetToReadyState() {
        // Simply go back to the ready state
        _screenState.value = ReceiptsScreenState.ReadyToScan
    }
}
