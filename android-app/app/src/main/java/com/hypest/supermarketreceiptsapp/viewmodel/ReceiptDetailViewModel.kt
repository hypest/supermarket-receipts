package com.hypest.supermarketreceiptsapp.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReceiptDetailState {
    data object Loading : ReceiptDetailState()
    data class Success(val receipt: Receipt) : ReceiptDetailState()
    data class Error(val message: String) : ReceiptDetailState()
}

@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    savedStateHandle: SavedStateHandle // Inject SavedStateHandle to get navigation arguments
) : ViewModel() {

    private val _receiptDetailState = MutableStateFlow<ReceiptDetailState>(ReceiptDetailState.Loading)
    val receiptDetailState: StateFlow<ReceiptDetailState> = _receiptDetailState.asStateFlow()

    private val receiptId: String? = savedStateHandle["receiptId"] // Get receiptId from navigation

    init {
        fetchReceiptDetails()
    }

    private fun fetchReceiptDetails() {
        if (receiptId == null) {
            _receiptDetailState.value = ReceiptDetailState.Error("Receipt ID not found.")
            return
        }

        viewModelScope.launch {
            _receiptDetailState.value = ReceiptDetailState.Loading
            try {
                // Assuming ReceiptRepository has a method like getReceiptById
                val receipt = receiptRepository.getReceiptById(receiptId)
                if (receipt != null) {
                    _receiptDetailState.value = ReceiptDetailState.Success(receipt)
                } else {
                    _receiptDetailState.value = ReceiptDetailState.Error("Receipt not found.")
                }
            } catch (e: Exception) {
                _receiptDetailState.value = ReceiptDetailState.Error("Failed to load receipt details: ${e.message}")
            }
        }
    }

    // TODO: Add functions for Delete and Edit actions if needed
    fun deleteReceipt() {
        // Implementation for deleting the receipt
    }

    fun editReceipt() {
        // Implementation for navigating to an edit screen or handling edit logic
    }
}
