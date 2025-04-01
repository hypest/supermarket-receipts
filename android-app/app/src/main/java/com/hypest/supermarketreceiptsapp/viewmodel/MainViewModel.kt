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
    object CheckingPermission : MainScreenState() // Initial state
    data class NoPermission(val message: String) : MainScreenState()
    object ReadyToScan : MainScreenState() // Permission granted, show "Scan" button
    object Scanning : MainScreenState() // Scanner UI should be active/launched
    data class Processing(val url: String) : MainScreenState() // Saving URL
    object Success : MainScreenState() // Save successful
    data class Error(val message: String) : MainScreenState() // General error state
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
        // Transition to Processing state immediately
        _screenState.value = MainScreenState.Processing(url)
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e("MainViewModel", "User not logged in, cannot save receipt.")
                _screenState.value = MainScreenState.Error("User not logged in.")
                return@launch
            }

            Log.d("MainViewModel", "Attempting to save URL: $url for user: $userId")
            val result = receiptRepository.saveReceiptUrl(url, userId)
            result.onSuccess {
                Log.d("MainViewModel", "URL saved successfully: $url")
                _screenState.value = MainScreenState.Success
            }.onFailure { error ->
                Log.e("MainViewModel", "Error saving URL: $url", error)
                _screenState.value = MainScreenState.Error(error.message ?: "Failed to save URL.")
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
