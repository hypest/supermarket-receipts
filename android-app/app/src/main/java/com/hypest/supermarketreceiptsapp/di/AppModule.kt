package com.hypest.supermarketreceiptsapp.di

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager // Import ConnectivityManager
import androidx.room.Room
import com.hypest.supermarketreceiptsapp.data.local.AppDatabase
import com.hypest.supermarketreceiptsapp.data.local.PendingScanDao // Import new DAO
import com.hypest.supermarketreceiptsapp.data.local.ReceiptDao
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

    // Provide Room Database instance
    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        // .fallbackToDestructiveMigration() // Replace fallback with specific migration
        .addMigrations(AppDatabase.MIGRATION_3_4) // Add our migration
        .build()
    }

    // Provide ReceiptDao instance from the database
    @Provides
    @Singleton
    fun provideReceiptDao(db: AppDatabase): ReceiptDao {
        return db.receiptDao()
    }

    // Provide PendingScanDao instance from the database
    @Provides
    @Singleton
    fun providePendingScanDao(db: AppDatabase): PendingScanDao {
        return db.pendingScanDao()
    }

    // Provide ConnectivityManager instance
    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
}
