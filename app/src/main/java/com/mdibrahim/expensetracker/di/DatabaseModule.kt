package com.mdibrahim.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.mdibrahim.expensetracker.core.database.AccountDao
import com.mdibrahim.expensetracker.core.database.CategoryDao
import com.mdibrahim.expensetracker.core.database.ExpenseDatabase
import com.mdibrahim.expensetracker.core.database.PayeeRuleDao
import com.mdibrahim.expensetracker.core.database.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExpenseDatabase =
        Room.databaseBuilder(context, ExpenseDatabase::class.java, ExpenseDatabase.NAME)
            // No fallbackToDestructiveMigration: losing real expense history to a
            // schema change would be unacceptable. Migrations get written properly.
            .build()

    @Provides
    fun provideAccountDao(db: ExpenseDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCategoryDao(db: ExpenseDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: ExpenseDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun providePayeeRuleDao(db: ExpenseDatabase): PayeeRuleDao = db.payeeRuleDao()
}
