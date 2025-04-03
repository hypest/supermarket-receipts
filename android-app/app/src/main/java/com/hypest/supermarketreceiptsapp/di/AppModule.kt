package com.hypest.supermarketreceiptsapp.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth // Auth v2 Module
import io.github.jan.supabase.postgrest.Postgrest // Postgrest v2 Module
import io.github.jan.supabase.realtime.Realtime // Realtime v2 Module
// import io.github.jan.supabase.storage.Storage // Storage v2 Module (if needed)
import io.ktor.client.engine.okhttp.OkHttp // Ktor OkHttp engine factory
import okhttp3.OkHttpClient // OkHttp Client
import okhttp3.logging.HttpLoggingInterceptor // OkHttp Logging Interceptor
import java.util.concurrent.TimeUnit // TimeUnit for timeouts
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

    // Provide a configured OkHttpClient instance
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Add logging interceptor only for debug builds
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient // Inject the preconfigured OkHttpClient
    ): SupabaseClient {
        // Read credentials from BuildConfig
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseKey = BuildConfig.SUPABASE_KEY

        return createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey,
//            // Pass the preconfigured OkHttpClient using the correct parameter name for v2
//            httpClientEngine = OkHttp.create { // Assuming parameter name is httpClientEngine for v2
//                 preconfigured = okHttpClient
//            }
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
