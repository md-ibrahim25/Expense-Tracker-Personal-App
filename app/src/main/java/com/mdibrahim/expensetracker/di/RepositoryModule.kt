package com.mdibrahim.expensetracker.di

import com.mdibrahim.expensetracker.data.repository.AccountRepository
import com.mdibrahim.expensetracker.data.repository.CategoryRepository
import com.mdibrahim.expensetracker.data.repository.PayeeRuleRepository
import com.mdibrahim.expensetracker.data.repository.RoomPayeeRuleRepository
import com.mdibrahim.expensetracker.data.repository.RoomAccountRepository
import com.mdibrahim.expensetracker.data.repository.RoomCategoryRepository
import com.mdibrahim.expensetracker.data.repository.RoomTransactionRepository
import com.mdibrahim.expensetracker.data.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The single place where "which storage backend" is decided. Swapping in a syncing
 * repository later is a one-line change here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: RoomAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindPayeeRuleRepository(impl: RoomPayeeRuleRepository): PayeeRuleRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository
}
