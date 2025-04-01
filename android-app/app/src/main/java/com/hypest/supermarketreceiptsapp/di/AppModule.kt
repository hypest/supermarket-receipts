package com.hypest.supermarketreceiptsapp.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth // v3 Auth
import io.github.jan.supabase.postgrest.Postgrest // v3 Postgrest
// import io.github.jan_tennert.supabase.compose.auth.ComposeAuth // Add if needed
import javax.inject.Singleton
import com.hypest.supermarketreceiptsapp.BuildConfig // Import BuildConfig

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(@ApplicationContext context: Context): SupabaseClient {
        // Read credentials from BuildConfig
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseKey = BuildConfig.SUPABASE_KEY

        return createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Auth) {
                // configure Auth settings if needed
            }
            install(Postgrest) {
                // configure Postgrest settings if needed
            }
            // install(ComposeAuth) { ... } // Add if needed
        }
    }
}
