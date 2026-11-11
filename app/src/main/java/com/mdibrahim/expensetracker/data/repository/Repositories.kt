package com.mdibrahim.expensetracker.data.repository

import com.mdibrahim.expensetracker.core.model.Account
import com.mdibrahim.expensetracker.core.model.AccountBalance
import com.mdibrahim.expensetracker.core.model.AccountType
import com.mdibrahim.expensetracker.core.model.Category
import com.mdibrahim.expensetracker.core.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts. The UI depends only on these interfaces, never on Room.
 *
 * This is the whole point of the "local-only now, sync-ready later" decision: adding
 * cloud sync means adding an implementation and rebinding it in the Hilt module, with
 * no ViewModel or Composable touched.
 */

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>
    suspend fun add(account: Account): Long
    suspend fun update(account: Account)
    suspend fun delete(account: Account)

    /** Returns the id of the account called [name], creating it if it doesn't exist. */
    suspend fun getOrCreate(name: String, type: AccountType): Long
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun add(category: Category): Long
    suspend fun update(category: Category)
    suspend fun delete(category: Category)
}

interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    fun observeTransactionsBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<Transaction>>
    suspend fun get(id: Long): Transaction?

    /** Latest bank-reported balance per account (accounts with no reported balance are absent). */
    fun observeLatestBalances(): Flow<List<AccountBalance>>

    /**
     * Next uncategorised transaction in [fromEpochMillis, toEpochMillis) after [after]
     * in date order, or the first one in the range when [after] is null.
     */
    suspend fun nextNeedingReview(fromEpochMillis: Long, toEpochMillis: Long, after: Transaction?): Long?
    suspend fun add(transaction: Transaction): Long

    /**
     * Inserts unless a transaction with the same non-null externalId exists.
     * Returns true if a row was added.
     */
    suspend fun addIfNew(transaction: Transaction): Boolean
    suspend fun update(transaction: Transaction)
    suspend fun delete(transaction: Transaction)

    /** Deletes all of [ids]; captured ones won't be re-imported. Returns the number deleted. */
    suspend fun deleteAll(ids: Collection<Long>): Int
}
