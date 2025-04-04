package com.hypest.supermarketreceiptsapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import javax.inject.Inject

data class ReceiptsUiState(
    val isLoading: Boolean = false,
    val receipts: List<Receipt> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    receiptRepository: ReceiptRepository // No need for private val if only used here
) : ViewModel() {

    companion object {
        private const val TAG = "ReceiptsViewModel" // Add TAG definition
    }

    // Convert the Flow<Result<List<Receipt>>> directly into StateFlow<ReceiptsUiState>
    val uiState: StateFlow<ReceiptsUiState> = receiptRepository.getReceipts()
        .map { result -> // Map Result<List<Receipt>> to ReceiptsUiState
            result.fold(
                onSuccess = { receipts ->
                    ReceiptsUiState(isLoading = false, receipts = receipts, error = null)
                },
                onFailure = { throwable ->
                    // Keep existing receipts if available during error? Or clear them?
                    // Let's keep them for now, but show error.
                    // Could also use stateIn's initialValue to handle initial loading state better.
                    ReceiptsUiState(isLoading = false, error = throwable.message ?: "Failed to load receipts")
                }
            )
        }
        .catch { e -> // Catch exceptions during the flow transformation
            emit(ReceiptsUiState(isLoading = false, error = e.message ?: "An unexpected error occurred"))
        }
        .stateIn( // Convert to StateFlow
            scope = viewModelScope,
            started = WhileSubscribed(5000), // Keep upstream flow active for 5s after last collector disappears
            initialValue = ReceiptsUiState(isLoading = true) // Start with loading state
        )

    // No init block or loadReceipts function needed anymore
}
