package com.mdibrahim.expensetracker.data.seed

import com.mdibrahim.expensetracker.core.database.AccountDao
import com.mdibrahim.expensetracker.core.database.CategoryDao
import com.mdibrahim.expensetracker.core.database.TransactionDao
import com.mdibrahim.expensetracker.core.model.Account
import com.mdibrahim.expensetracker.core.model.AccountType
import com.mdibrahim.expensetracker.core.model.Category
import com.mdibrahim.expensetracker.core.model.Transaction
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.core.database.toEntity
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-launch data. Idempotent -- each part no-ops once its table has rows.
 *
 * - Default categories: every build, so the category picker is never empty.
 * - Demo account + ₹450 transaction: debug builds only. This is what proves Room
 *   actually persists: the seeded row must survive a force-stop.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) {

    suspend fun seedIfEmpty(includeDemoData: Boolean) {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(
                DEFAULT_CATEGORIES.map { (name, color) ->
                    Category(name = name, iconKey = name.lowercase(), colorArgb = color).toEntity()
                },
            )
        }

        if (!includeDemoData || accountDao.count() > 0) return

        val accountId = accountDao.insert(
            Account(
                name = "Main Bank",
                type = AccountType.BANK,
                openingBalanceMinor = 0,
            ).toEntity(),
        )

        val now = Instant.now()
        transactionDao.insert(
            Transaction(
                // 450.00 stored as 45000 paise.
                amountMinor = 45_000L,
                direction = TransactionDirection.DEBIT,
                occurredAt = now.minus(1, ChronoUnit.HOURS),
                accountId = accountId,
                categoryId = null,
                note = "Seed transaction -- proves persistence works",
            ).toEntity(),
        )
    }

    private companion object {
        /**
         * Placeholder starter categories. The real category set is a features-round
         * decision, not a foundation one.
         */
        val DEFAULT_CATEGORIES = listOf(
            "Food" to 0xFFEF6C00.toInt(),
            "Transport" to 0xFF1E88E5.toInt(),
            "Groceries" to 0xFF43A047.toInt(),
            "Bills" to 0xFF8E24AA.toInt(),
            "Shopping" to 0xFFD81B60.toInt(),
            "Health" to 0xFF00897B.toInt(),
            "Other" to 0xFF757575.toInt(),
        )
    }
}
