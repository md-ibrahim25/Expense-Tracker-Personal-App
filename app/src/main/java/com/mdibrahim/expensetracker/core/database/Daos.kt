package com.mdibrahim.expensetracker.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMillis DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAtEpochMillis >= :fromEpochMillis
          AND occurredAtEpochMillis < :toEpochMillis
        ORDER BY occurredAtEpochMillis DESC, id DESC
        """,
    )
    fun observeInRange(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /** Per account, the balance from its most recent transaction that reported one. */
    @Query(
        """
        SELECT accountId, balanceAfterMinor, occurredAtEpochMillis FROM transactions t
        WHERE balanceAfterMinor IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM transactions n
            WHERE n.accountId = t.accountId AND n.balanceAfterMinor IS NOT NULL
              AND (n.occurredAtEpochMillis > t.occurredAtEpochMillis
                   OR (n.occurredAtEpochMillis = t.occurredAtEpochMillis AND n.id > t.id))
        )
        """,
    )
    fun observeLatestBalances(): Flow<List<BalanceRow>>

    /**
     * Review queue: the next uncategorised transaction in [fromEpochMillis, toEpochMillis)
     * that comes strictly after (afterEpochMillis, afterId) in date order. Walking
     * forward means one the user skips without a category isn't served again.
     */
    @Query(
        """
        SELECT id FROM transactions
        WHERE categoryId IS NULL
          AND occurredAtEpochMillis >= :fromEpochMillis AND occurredAtEpochMillis < :toEpochMillis
          AND (occurredAtEpochMillis > :afterEpochMillis
               OR (occurredAtEpochMillis = :afterEpochMillis AND id > :afterId))
        ORDER BY occurredAtEpochMillis ASC, id ASC LIMIT 1
        """,
    )
    suspend fun nextNeedingReview(
        fromEpochMillis: Long,
        toEpochMillis: Long,
        afterEpochMillis: Long,
        afterId: Long,
    ): Long?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    /** Returns -1 when a row with the same externalId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}
