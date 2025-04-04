package com.hypest.supermarketreceiptsapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.* // Import necessary operators
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed // Import WhileSubscribed
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val sessionStatus: StateFlow<SessionStatus> = authRepository.sessionStatus
        .stateIn(
            scope = viewModelScope,
            started = WhileSubscribed(5000), // Use WhileSubscribed correctly
            initialValue = SessionStatus.Initializing // Use Initializing as
        )

    // TODO: Add state for loading indicators and error messages if needed

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            val result = authRepository.signInWithEmail(email, password)
            result.onFailure { error ->
                error.printStackTrace() // Handle error (e.g., show message)
            }
            // Success is handled by observing sessionStatus
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
