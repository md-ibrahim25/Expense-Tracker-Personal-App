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
 * Inserts a small amount of starter data on first launch so the foundation is
 * visibly working. This is what proves the Room wiring actually persists: the
 * seeded rows must survive a force-stop.
 *
 * Idempotent -- it no-ops once any account exists, so it never duplicates.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) {

    suspend fun seedIfEmpty() {
        if (accountDao.count() > 0) return

        val accountId = accountDao.insert(
            Account(
                name = "Main Bank",
                type = AccountType.BANK,
                openingBalanceMinor = 0,
            ).toEntity(),
        )

        val categoryIds = categoryDao.insertAll(
            DEFAULT_CATEGORIES.map { (name, color) ->
                Category(name = name, iconKey = name.lowercase(), colorArgb = color).toEntity()
            },
        )

        val now = Instant.now()
        transactionDao.insert(
            Transaction(
                // 450.00 stored as 45000 paise.
                amountMinor = 45_000L,
                direction = TransactionDirection.DEBIT,
                occurredAt = now.minus(1, ChronoUnit.HOURS),
                accountId = accountId,
                categoryId = categoryIds.firstOrNull(),
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
