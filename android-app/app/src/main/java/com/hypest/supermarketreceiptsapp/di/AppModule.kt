package com.hypest.supermarketreceiptsapp.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth // Auth v2 Module
import io.github.jan.supabase.postgrest.Postgrest // Postgrest v2 Module
import io.github.jan.supabase.realtime.Realtime // Realtime v2 Module
import javax.inject.Singleton
import com.hypest.supermarketreceiptsapp.BuildConfig // BuildConfig for secrets
import kotlin.time.Duration.Companion.seconds // Import for Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Provide a Singleton CoroutineScope for background tasks
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        // SupervisorJob ensures that if one child coroutine fails, others are not cancelled
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context
    ): SupabaseClient {
        // Read credentials from BuildConfig
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseKey = BuildConfig.SUPABASE_KEY

        return createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey,
        ) {
            // Install modules using the v2 syntax
            install(Auth) {
                // configure Auth settings if needed
            }
            install(Postgrest) {
                // configure Postgrest settings if needed
            }
            install(Realtime) {
                // Set heartbeat interval to 1 second using Duration
                heartbeatInterval = 5.seconds
            }
            // install(Storage) { ... } // Install Storage if needed
        }
    }
}
