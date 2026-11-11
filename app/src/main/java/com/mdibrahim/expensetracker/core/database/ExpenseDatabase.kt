package com.mdibrahim.expensetracker.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * exportSchema stays true and the generated JSON under app/schemas is committed.
 * Without it, future migrations have to be written blind; with it, every schema
 * change is diffable and testable.
 *
 * Version history:
 * - 1: accounts, categories, transactions.
 * - 2: transactions.source + transactions.externalId (unique) for SMS capture.
 *      Purely additive, so Room generates the migration from the two schema JSONs.
 * - 3: transactions.counterparty + transactions.balanceAfterMinor. Before v3 an SMS
 *      transaction's payee was stored in `note`; [V3MovePayeeOutOfNote] moves it so
 *      `note` is free for the user's own "what was it for".
 * - 4: ignored_external_ids table, so deleted SMS transactions stay deleted.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        IgnoredExternalIdEntity::class,
    ],
    version = ExpenseDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = ExpenseDatabase.V3MovePayeeOutOfNote::class),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    class V3MovePayeeOutOfNote : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            // "Canara Bank SMS" was the placeholder note when the SMS named no payee.
            db.execSQL(
                """
                UPDATE transactions
                SET counterparty = CASE WHEN note = 'Canara Bank SMS' THEN NULL ELSE note END,
                    note = NULL
                WHERE source = 'SMS'
                """.trimIndent(),
            )
        }
    }

    companion object {
        const val NAME = "expense_tracker.db"

        /** Bump with every schema change. Also used to refuse backups from a newer app. */
        const val VERSION = 4
    }
}
