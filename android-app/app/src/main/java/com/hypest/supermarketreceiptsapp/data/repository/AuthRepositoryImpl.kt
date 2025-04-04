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

    // Get sessionStatus from gotrue module in v2
    override val sessionStatus: Flow<SessionStatus> = supabaseClient.auth.sessionStatus

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        return try {
            // Use gotrue module and signInWith(Email) for v2
            supabaseClient.auth.signInWith(Email) {
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
            // Use gotrue module and logout() for v2
            supabaseClient.auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace() // Keep basic error handling
        }
    }

    override fun getCurrentUserId(): String? {
        // Accessing user id in v2 might be directly on the currentUser
        return supabaseClient.auth.currentUserOrNull()?.id
    }
}
