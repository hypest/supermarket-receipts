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

    fun onPermissionResult(isGranted: Boolean) {
        _hasCameraPermission.value = isGranted
        if (!isGranted) {
            _saveStatus.value = SaveStatus.Error("Camera permission denied.")
        } else {
             // Clear potential permission error if granted now
             if (_saveStatus.value is SaveStatus.Error && (_saveStatus.value as SaveStatus.Error).message == "Camera permission denied.") {
                 _saveStatus.value = SaveStatus.Idle
             }
        }
    }

    fun onQrCodeScanned(url: String) {
        if (_saveStatus.value == SaveStatus.Saving) return

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
    }

    fun resetScanState() {
        _scannedUrl.value = null
        _saveStatus.value = SaveStatus.Idle
    }
}
