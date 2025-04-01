package com.hypest.supermarketreceiptsapp.viewmodel

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// Sealed class to represent the state of saving the URL
sealed class SaveStatus {
    object Idle : SaveStatus()
    object Saving : SaveStatus()
    object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _hasCameraPermission = mutableStateOf(false)
    val hasCameraPermission: State<Boolean> = _hasCameraPermission

    private val _scannedUrl = mutableStateOf<String?>(null)
    val scannedUrl: State<String?> = _scannedUrl

    private val _saveStatus = mutableStateOf<SaveStatus>(SaveStatus.Idle)
    val saveStatus: State<SaveStatus> = _saveStatus

    // State to explicitly control scanner visibility
    private val _isScannerVisible = mutableStateOf(false) // Start hidden
    val isScannerVisible: State<Boolean> = _isScannerVisible

    fun onPermissionResult(isGranted: Boolean) {
        _hasCameraPermission.value = isGranted
        if (!isGranted) {
            _saveStatus.value = SaveStatus.Error("Camera permission denied.")
            _isScannerVisible.value = false // Ensure scanner is hidden if permission revoked/denied
        } else {
             // Clear potential permission error if granted now
             if (_saveStatus.value is SaveStatus.Error && (_saveStatus.value as SaveStatus.Error).message == "Camera permission denied.") {
                 _saveStatus.value = SaveStatus.Idle
             }
             // Show scanner immediately if permission is granted and state is Idle
             // Don't automatically show if status is already Success/Error/Saving
             if (_saveStatus.value == SaveStatus.Idle) {
                _isScannerVisible.value = true
             }
        }
    }

    fun onQrCodeScanned(url: String) {
        if (_saveStatus.value == SaveStatus.Saving) return

        // Hide scanner view immediately when scan starts processing
        _isScannerVisible.value = false
        _scannedUrl.value = url
        _saveStatus.value = SaveStatus.Saving
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e("MainViewModel", "User not logged in, cannot save receipt.")
                _saveStatus.value = SaveStatus.Error("User not logged in.")
                return@launch
            }

            Log.d("MainViewModel", "Attempting to save URL: $url for user: $userId")
            val result = receiptRepository.saveReceiptUrl(url, userId)
            result.onSuccess {
                Log.d("MainViewModel", "URL saved successfully: $url")
                _saveStatus.value = SaveStatus.Success
            }.onFailure { error ->
                Log.e("MainViewModel", "Error saving URL: $url", error)
                _saveStatus.value = SaveStatus.Error(error.message ?: "Failed to save URL.")
            }
        }
    } // End of onQrCodeScanned

    fun resetScanState() {
        _scannedUrl.value = null
        _saveStatus.value = SaveStatus.Idle
        // Show scanner again when resetting state explicitly (e.g., after error/success)
        if (_hasCameraPermission.value) { // Only show if permission is still granted
             _isScannerVisible.value = true
        }
    } // End of resetScanState

    // Function to explicitly show the scanner UI
    fun showScanner() {
        if (_hasCameraPermission.value) {
            // Reset status only if coming from Success/Error to avoid interrupting Saving
            if (_saveStatus.value is SaveStatus.Success || _saveStatus.value is SaveStatus.Error) {
                 _saveStatus.value = SaveStatus.Idle
            }
             _scannedUrl.value = null // Clear any previous URL
            _isScannerVisible.value = true
        } else {
             // Optionally handle the case where permission isn't granted but show is called
             Log.w("MainViewModel", "showScanner called but camera permission not granted.")
             // You might want to trigger the permission request again here or show a message
        }
    } // End of showScanner

     // Function to explicitly hide the scanner UI (e.g., user dismisses it)
     fun hideScanner() {
         _isScannerVisible.value = false
         // If the user hides the scanner while it was Idle, keep it Idle.
         // Don't change status if it was Saving, Success, or Error.
     } // End of hideScanner

} // End of MainViewModel class
