package com.mdibrahim.expensetracker.core.database

import androidx.room.ColumnInfo
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
    // Added in schema v5.
    @ColumnInfo(defaultValue = "0") val excludeFromTotals: Boolean = false,
)

/** "Always put this payee in this category." Keyed by [payeeKey] of the SMS counterparty. */
@Entity(
    tableName = "payee_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // A rule pointing at a deleted category is meaningless -- drop it.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class PayeeRuleEntity(
    @PrimaryKey val payeeKey: String,
    val categoryId: Long,
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
        // Dedupe for captured transactions. SQLite allows many NULLs in a unique
        // index, so manual entries (externalId = null) are unaffected.
        Index(value = ["externalId"], unique = true),
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
    // Added in schema v2. The default lets the auto-migration backfill old rows.
    @ColumnInfo(defaultValue = "MANUAL") val source: String = "MANUAL",
    val externalId: String? = null,
    // Added in schema v3.
    val counterparty: String? = null,
    val balanceAfterMinor: Long? = null,
)

/**
 * externalIds of captured transactions the user deleted. Without this, deleting an
 * SMS transaction would let the next inbox import bring it straight back.
 */
@Entity(tableName = "ignored_external_ids")
data class IgnoredExternalIdEntity(
    @PrimaryKey val externalId: String,
)

/** Projection for [TransactionDao.observeLatestBalances]. */
data class BalanceRow(
    val accountId: Long,
    val balanceAfterMinor: Long,
    val occurredAtEpochMillis: Long,
)

// ---- Lending (schema v6) ----

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
)

/** Links a UPI name seen in SMS ([com.mdibrahim.expensetracker.core.model.payeeKey]) to a person. */
@Entity(
    tableName = "person_payees",
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId")],
)
data class PersonPayeeEntity(
    @PrimaryKey val payeeKey: String,
    val personId: Long,
)

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
        // A debt is derived from the user's payment; deleting the payment removes it.
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId"), Index("transactionId")],
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val transactionId: Long,
    /** Paise, always positive. */
    val amountMinor: Long,
    /** DebtKind by name. */
    val kind: String,
    val description: String?,
    val occurredAtEpochMillis: Long,
    /** LocalDate.toEpochDay(); null = no expected date. */
    val dueEpochDay: Long?,
)

@Entity(
    tableName = "repayments",
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId"), Index("transactionId")],
)
data class RepaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    /** Null = repaid in cash, recorded by hand. */
    val transactionId: Long?,
    val amountMinor: Long,
    val occurredAtEpochMillis: Long,
)

/** Projection: total owed by others on one transaction. */
data class TransactionAmount(val transactionId: Long, val amountMinor: Long)
