package com.hypest.supermarketreceiptsapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Unified state representation for the Main Screen
sealed class MainScreenState {
    object CheckingPermission : MainScreenState()
    data class NoPermission(val message: String) : MainScreenState()
    object ReadyToScan : MainScreenState()
    object Scanning : MainScreenState()
    data class ExtractingHtml(val url: String) : MainScreenState() // State for WebView extraction
    data class Processing(val url: String, val html: String?) : MainScreenState() // Include optional HTML
    object Success : MainScreenState()
    data class Error(val message: String) : MainScreenState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val authRepository: AuthRepository // Keep AuthRepository if needed for user ID etc.
) : ViewModel() {

    private val _screenState = MutableStateFlow<MainScreenState>(MainScreenState.CheckingPermission)
    val screenState: StateFlow<MainScreenState> = _screenState.asStateFlow()

    // Keep track of actual permission status internally if needed for logic,
    // but UI state is driven by _screenState
    private var hasCameraPermissionInternal = false

    fun onPermissionResult(isGranted: Boolean) {
        hasCameraPermissionInternal = isGranted
        if (isGranted) {
            // Only transition to ReadyToScan if we were checking or previously denied
            if (_screenState.value is MainScreenState.CheckingPermission || _screenState.value is MainScreenState.NoPermission) {
                 _screenState.value = MainScreenState.ReadyToScan
            }
            // If permission granted while in another state (e.g. Error), stay there until user action
        } else {
            _screenState.value = MainScreenState.NoPermission("Camera permission is required to scan QR codes.")
        }
    }

    // Called when the user clicks the "Scan QR Code" button
    fun startScanning() {
        if (hasCameraPermissionInternal) {
            _screenState.value = MainScreenState.Scanning
        } else {
             // Should ideally not happen if button is only shown when permission granted, but handle defensively
             _screenState.value = MainScreenState.NoPermission("Camera permission is required.")
             // Consider triggering permission request again here
        }
    }

    // Called by the QrCodeScanner component when a code is successfully scanned
    fun onQrCodeScanned(url: String) {
        // Transition to ExtractingHtml state to trigger the WebView
        Log.d("MainViewModel", "QR Code scanned, transitioning to ExtractingHtml state for URL: $url")
        _screenState.value = MainScreenState.ExtractingHtml(url)
        // The actual processing will be triggered by onHtmlExtracted
    }

    // Called by HtmlExtractorWebView when HTML is ready (or if extraction failed)
    fun onHtmlExtracted(url: String, html: String?) {
        if (html == null) {
            Log.e("MainViewModel", "HTML extraction failed for URL: $url")
            _screenState.value = MainScreenState.Error("Failed to extract HTML content from the receipt page.")
            return
        }

        Log.d("MainViewModel", "HTML extracted (length: ${html.length}), transitioning to Processing state for URL: $url")
        // Transition to Processing state, now including the HTML
        _screenState.value = MainScreenState.Processing(url, html)

        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e("MainViewModel", "User not logged in, cannot save receipt.")
                _screenState.value = MainScreenState.Error("User not logged in.")
                return@launch
            }

            Log.d("MainViewModel", "Attempting to submit URL and HTML for processing: $url for user: $userId")
            // Call the updated repository function
            val result = receiptRepository.submitReceiptData(url, html, userId)

            result.onSuccess {
                Log.d("MainViewModel", "Receipt data submitted successfully for URL: $url")
                _screenState.value = MainScreenState.Success
            }.onFailure { error ->
                Log.e("MainViewModel", "Error submitting receipt data for URL: $url", error)
                _screenState.value = MainScreenState.Error(error.message ?: "Failed to submit receipt data.")
            }
        }
    }

    // Called by the QrCodeScanner component when the user cancels the scan
    fun onScanCancelled() {
        // Go back to ready state only if we were actually scanning
        if (_screenState.value == MainScreenState.Scanning) {
             _screenState.value = MainScreenState.ReadyToScan
        }
    }

     // Called by the QrCodeScanner component if it encounters an internal error
     fun onScanError(exception: Exception) {
         Log.e("MainViewModel", "Scanner internal error", exception)
         // Go back to ready state or show specific error
         _screenState.value = MainScreenState.Error("Scanner error: ${exception.message ?: "Unknown scanner issue"}")
         // Consider transitioning back to ReadyToScan after a delay or let user click "Try Again"
     }

    // Called by HtmlExtractorWebView if it encounters an error
    fun onHtmlExtractionError(url: String, error: String) {
        Log.e("MainViewModel", "HTML extraction error for URL $url: $error")
        _screenState.value = MainScreenState.Error("Failed to extract HTML: $error")
        // Consider going back to ReadyToScan automatically or require user action
    }


    // Called when user clicks "Scan Another" or "Try Again"
    fun resetToReadyState() {
        if (hasCameraPermissionInternal) {
            _screenState.value = MainScreenState.ReadyToScan
        } else {
            // If permission somehow got revoked, reflect that
             _screenState.value = MainScreenState.NoPermission("Camera permission is required.")
        }
    }
}
