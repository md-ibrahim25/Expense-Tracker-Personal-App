package com.mdibrahim.expensetracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * exportSchema stays true and the generated JSON under app/schemas is committed.
 * Without it, future migrations have to be written blind; with it, every schema
 * change is diffable and testable.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "expense_tracker.db"
    }
}
