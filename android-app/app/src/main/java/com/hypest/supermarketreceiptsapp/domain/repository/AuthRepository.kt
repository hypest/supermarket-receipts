package com.hypest.supermarketreceiptsapp.domain.repository

import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val sessionStatus: Flow<SessionStatus>
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>
    suspend fun signOut()
    fun getCurrentUserId(): String?
}
