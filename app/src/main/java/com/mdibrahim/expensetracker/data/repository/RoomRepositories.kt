package com.mdibrahim.expensetracker.data.repository

import com.mdibrahim.expensetracker.core.database.AccountDao
import com.mdibrahim.expensetracker.core.database.CategoryDao
import com.mdibrahim.expensetracker.core.database.TransactionDao
import com.mdibrahim.expensetracker.core.database.toDomain
import com.mdibrahim.expensetracker.core.database.toEntity
import com.mdibrahim.expensetracker.core.model.Account
import com.mdibrahim.expensetracker.core.model.Category
import com.mdibrahim.expensetracker.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomAccountRepository @Inject constructor(
    private val dao: AccountDao,
) : AccountRepository {
    override fun observeAccounts(): Flow<List<Account>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(account: Account): Long = dao.insert(account.toEntity())
    override suspend fun update(account: Account) = dao.update(account.toEntity())
    override suspend fun delete(account: Account) = dao.delete(account.toEntity())
}

class RoomCategoryRepository @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(category: Category): Long = dao.insert(category.toEntity())
    override suspend fun update(category: Category) = dao.update(category.toEntity())
    override suspend fun delete(category: Category) = dao.delete(category.toEntity())
}

class RoomTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {
    override fun observeTransactions(): Flow<List<Transaction>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeTransactionsBetween(
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): Flow<List<Transaction>> =
        dao.observeInRange(fromEpochMillis, toEpochMillis).map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(transaction: Transaction): Long = dao.insert(transaction.toEntity())
    override suspend fun update(transaction: Transaction) = dao.update(transaction.toEntity())
    override suspend fun delete(transaction: Transaction) = dao.delete(transaction.toEntity())
}
