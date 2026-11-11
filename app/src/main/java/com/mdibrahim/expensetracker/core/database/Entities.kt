package com.mdibrahim.expensetracker.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val openingBalanceMinor: Long,
    val currencyCode: String,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            // Deleting a parent promotes its children to top level rather than
            // silently deleting transactions' categories along with them.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
    val parentId: Long? = null,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // An uncategorised transaction is still a real transaction -- keep it.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("categoryId"),
        // Nearly every query is "recent first" or "this month", so date leads.
        Index("occurredAtEpochMillis"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Minor units (paise). Always positive; [direction] carries the sign. */
    val amountMinor: Long,
    val direction: String,
    val occurredAtEpochMillis: Long,
    val accountId: Long,
    val categoryId: Long? = null,
    val note: String? = null,
)
