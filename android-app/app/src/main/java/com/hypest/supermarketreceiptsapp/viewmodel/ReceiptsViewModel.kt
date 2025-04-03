package com.hypest.supermarketreceiptsapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.domain.model.Receipt
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ReceiptsUiState(
    val isLoading: Boolean = false,
    val receipts: List<Receipt> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptsUiState())
    val uiState: StateFlow<ReceiptsUiState> = _uiState.asStateFlow()

    init {
        loadReceipts()
    }

    fun loadReceipts() {
        _uiState.update { it.copy(isLoading = true, error = null) }

        receiptRepository.getReceipts()
            .onEach { result ->
                result.onSuccess { receipts ->
                    _uiState.update {
                        it.copy(isLoading = false, receipts = receipts, error = null)
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.message ?: "Failed to load receipts")
                    }
                }
            }
            .catch { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "An unexpected error occurred")
                }
            }
            .launchIn(viewModelScope)
    }
}
