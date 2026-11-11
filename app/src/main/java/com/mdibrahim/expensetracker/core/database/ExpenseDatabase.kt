package com.mdibrahim.expensetracker.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * exportSchema stays true and the generated JSON under app/schemas is committed.
 * Without it, future migrations have to be written blind; with it, every schema
 * change is diffable and testable.
 *
 * Version history:
 * - 1: accounts, categories, transactions.
 * - 2: transactions.source + transactions.externalId (unique) for SMS capture.
 *      Purely additive, so Room generates the migration from the two schema JSONs.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "expense_tracker.db"
    }
}
