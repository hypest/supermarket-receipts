package com.hypest.supermarketreceiptsapp.data.repository

import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : AuthRepository {

    private val auth = supabaseClient.auth

    override val sessionStatus: Flow<SessionStatus> = auth.sessionStatus

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWith(Email) { // Use the imported Email provider
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getCurrentUserId(): String? {
        // Accessing sessionStatus.value might be slightly different or require specific state handling in v3
        // Check library docs if this causes issues.
        return (auth.sessionStatus.value as? SessionStatus.Authenticated)?.session?.user?.id
    }
}
