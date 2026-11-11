package com.mdibrahim.expensetracker.core.model

import java.time.Instant

/**
 * Domain models. Deliberately free of Room and Compose annotations so the schema
 * and the UI can change independently of each other.
 *
 * Money is always a [Long] count of minor units (paise for INR). Never Double --
 * binary floating point cannot represent 0.10 exactly, so totals drift and stop
 * reconciling in a way that cannot be fixed after the fact.
 */

enum class AccountType { BANK, CASH, CREDIT_CARD, WALLET }

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalanceMinor: Long = 0,
    val currencyCode: String = "INR",
)

data class Category(
    val id: Long = 0,
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
    /** Set for subcategories. Null means a top-level category. */
    val parentId: Long? = null,
)

/** Which way the money moved, from the perspective of the account. */
enum class TransactionDirection { DEBIT, CREDIT }

/** How a transaction entered the app. */
enum class TransactionSource { MANUAL, SMS }

data class Transaction(
    val id: Long = 0,
    val amountMinor: Long,
    val direction: TransactionDirection,
    val occurredAt: Instant,
    val accountId: Long,
    val categoryId: Long? = null,
    /** What it was for, written by the user. */
    val note: String? = null,
    /** Who was paid or who paid, as reported by the bank SMS. */
    val counterparty: String? = null,
    /** Account balance right after this transaction, if the bank reported it. Paise. */
    val balanceAfterMinor: Long? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    /**
     * Stable identity of the thing this was captured from (e.g. a hash of the SMS
     * body). Unique when non-null, so re-importing the same SMS is a no-op.
     */
    val externalId: String? = null,
) {
    /** Not yet given a category -- what the daily review works through. */
    val needsReview: Boolean get() = categoryId == null
}

/** The bank-reported balance of an account, from its most recent SMS. */
data class AccountBalance(
    val accountId: Long,
    val balanceMinor: Long,
    val asOf: Instant,
)
