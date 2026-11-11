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
 * - 5: categories.excludeFromTotals and payee_rules. [V5AddSelfTransfer] adds the
 *      "Self transfer" category to existing installs (fresh ones get it from the seeder).
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        IgnoredExternalIdEntity::class,
        PayeeRuleEntity::class,
    ],
    version = ExpenseDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = ExpenseDatabase.V3MovePayeeOutOfNote::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = ExpenseDatabase.V5AddSelfTransfer::class),
    ],
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun payeeRuleDao(): PayeeRuleDao

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

    class V5AddSelfTransfer : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            // Values match DatabaseSeeder's "Self transfer" entry.
            db.execSQL(
                """
                INSERT INTO categories (name, iconKey, colorArgb, parentId, excludeFromTotals)
                SELECT 'Self transfer', 'self_transfer', -11243910, NULL, 1
                WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Self transfer')
                """.trimIndent(),
            )
        }
    }

    companion object {
        const val NAME = "expense_tracker.db"

        /** Bump with every schema change. Also used to refuse backups from a newer app. */
        const val VERSION = 5
    }
}
