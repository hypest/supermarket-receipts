package com.hypest.supermarketreceiptsapp.di

import com.hypest.supermarketreceiptsapp.data.repository.AuthRepositoryImpl
import com.hypest.supermarketreceiptsapp.data.repository.ReceiptRepositoryImpl
import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindReceiptRepository(
        receiptRepositoryImpl: ReceiptRepositoryImpl
    ): ReceiptRepository
}
