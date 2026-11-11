package com.mdibrahim.expensetracker.core.database

import com.mdibrahim.expensetracker.core.model.Account
import com.mdibrahim.expensetracker.core.model.AccountBalance
import com.mdibrahim.expensetracker.core.model.AccountType
import com.mdibrahim.expensetracker.core.model.Category
import com.mdibrahim.expensetracker.core.model.Debt
import com.mdibrahim.expensetracker.core.model.DebtKind
import com.mdibrahim.expensetracker.core.model.Person
import com.mdibrahim.expensetracker.core.model.Repayment
import com.mdibrahim.expensetracker.core.model.Transaction
import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.core.model.TransactionSource
import java.time.Instant
import java.time.LocalDate

/**
 * Entity <-> domain mapping. Enums are stored as their `name` string rather than an
 * ordinal so that reordering an enum later cannot silently reinterpret existing rows.
 */

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    type = enumValueOrDefault(type, AccountType.BANK),
    openingBalanceMinor = openingBalanceMinor,
    currencyCode = currencyCode,
)

fun Account.toEntity() = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    openingBalanceMinor = openingBalanceMinor,
    currencyCode = currencyCode,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    parentId = parentId,
    excludeFromTotals = excludeFromTotals,
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    parentId = parentId,
    excludeFromTotals = excludeFromTotals,
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    amountMinor = amountMinor,
    direction = enumValueOrDefault(direction, TransactionDirection.DEBIT),
    occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
    accountId = accountId,
    categoryId = categoryId,
    note = note,
    counterparty = counterparty,
    balanceAfterMinor = balanceAfterMinor,
    source = enumValueOrDefault(source, TransactionSource.MANUAL),
    externalId = externalId,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amountMinor = amountMinor,
    direction = direction.name,
    occurredAtEpochMillis = occurredAt.toEpochMilli(),
    accountId = accountId,
    categoryId = categoryId,
    note = note,
    counterparty = counterparty,
    balanceAfterMinor = balanceAfterMinor,
    source = source.name,
    externalId = externalId,
)

fun PersonEntity.toDomain() = Person(id = id, name = name, phone = phone)
fun Person.toEntity() = PersonEntity(id = id, name = name, phone = phone)

fun DebtEntity.toDomain() = Debt(
    id = id,
    personId = personId,
    transactionId = transactionId,
    amountMinor = amountMinor,
    kind = enumValueOrDefault(kind, DebtKind.SPLIT),
    description = description,
    occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
    dueDate = dueEpochDay?.let(LocalDate::ofEpochDay),
)

fun Debt.toEntity() = DebtEntity(
    id = id,
    personId = personId,
    transactionId = transactionId,
    amountMinor = amountMinor,
    kind = kind.name,
    description = description,
    occurredAtEpochMillis = occurredAt.toEpochMilli(),
    dueEpochDay = dueDate?.toEpochDay(),
)

fun RepaymentEntity.toDomain() = Repayment(
    id = id,
    personId = personId,
    transactionId = transactionId,
    amountMinor = amountMinor,
    occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
)

fun Repayment.toEntity() = RepaymentEntity(
    id = id,
    personId = personId,
    transactionId = transactionId,
    amountMinor = amountMinor,
    occurredAtEpochMillis = occurredAt.toEpochMilli(),
)

fun BalanceRow.toDomain() = AccountBalance(
    accountId = accountId,
    balanceMinor = balanceAfterMinor,
    asOf = Instant.ofEpochMilli(occurredAtEpochMillis),
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
