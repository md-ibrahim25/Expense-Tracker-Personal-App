package com.mdibrahim.expensetracker.data.repository

import com.mdibrahim.expensetracker.core.database.AccountDao
import com.mdibrahim.expensetracker.core.database.CategoryDao
import com.mdibrahim.expensetracker.core.database.TransactionDao
import com.mdibrahim.expensetracker.core.database.toDomain
import com.mdibrahim.expensetracker.core.database.toEntity
import com.mdibrahim.expensetracker.core.model.Account
import com.mdibrahim.expensetracker.core.model.AccountBalance
import com.mdibrahim.expensetracker.core.model.AccountType
import com.mdibrahim.expensetracker.core.model.Category
import com.mdibrahim.expensetracker.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class RoomAccountRepository @Inject constructor(
    private val dao: AccountDao,
) : AccountRepository {
    // Serialises find-then-insert so two SMS arriving together can't create the
    // same account twice. The binding is @Singleton, so one lock covers the app.
    private val createLock = Mutex()

    override fun observeAccounts(): Flow<List<Account>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(account: Account): Long = dao.insert(account.toEntity())
    override suspend fun update(account: Account) = dao.update(account.toEntity())
    override suspend fun delete(account: Account) = dao.delete(account.toEntity())

    override suspend fun getOrCreate(name: String, type: AccountType): Long = createLock.withLock {
        dao.findByName(name)?.id ?: dao.insert(Account(name = name, type = type).toEntity())
    }
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

    override suspend fun get(id: Long): Transaction? = dao.getById(id)?.toDomain()
    override fun observeLatestBalances(): Flow<List<AccountBalance>> =
        dao.observeLatestBalances().map { rows -> rows.map { it.toDomain() } }
    override suspend fun nextNeedingReview(fromEpochMillis: Long, toEpochMillis: Long, after: Transaction?): Long? =
        dao.nextNeedingReview(
            fromEpochMillis = fromEpochMillis,
            toEpochMillis = toEpochMillis,
            afterEpochMillis = after?.occurredAt?.toEpochMilli() ?: Long.MIN_VALUE,
            afterId = after?.id ?: Long.MIN_VALUE,
        )
    override suspend fun add(transaction: Transaction): Long = dao.insert(transaction.toEntity())
    override suspend fun addIfNew(transaction: Transaction): Boolean =
        dao.insertIfNew(transaction.toEntity()) != -1L
    override suspend fun update(transaction: Transaction) = dao.update(transaction.toEntity())
    override suspend fun delete(transaction: Transaction) = dao.delete(transaction.toEntity())
}
