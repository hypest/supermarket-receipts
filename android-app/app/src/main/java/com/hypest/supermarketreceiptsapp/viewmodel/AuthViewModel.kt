package com.hypest.supermarketreceiptsapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus // Add SessionStatus import
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val sessionStatus: StateFlow<SessionStatus> = authRepository.sessionStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SessionStatus.Initializing // Or appropriate initial state for v3
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
