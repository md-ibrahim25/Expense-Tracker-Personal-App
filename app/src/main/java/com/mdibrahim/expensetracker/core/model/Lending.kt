package com.mdibrahim.expensetracker.core.model

import com.mdibrahim.expensetracker.core.util.formatMoney
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A friend who can owe the user money. */
data class Person(
    val id: Long = 0,
    val name: String,
    /** Optional, digits with country code (e.g. "919876543210"), for WhatsApp reminders. */
    val phone: String? = null,
)

enum class DebtKind { SPLIT, LOAN }

/**
 * Money a person owes the user, created from one of the user's debit transactions:
 * their share of a bill the user paid (SPLIT), or money lent outright (LOAN).
 */
data class Debt(
    val id: Long = 0,
    val personId: Long,
    val transactionId: Long,
    val amountMinor: Long,
    val kind: DebtKind,
    /** What it was for, e.g. "Dinner" -- used in reminder messages. */
    val description: String?,
    val occurredAt: Instant,
    /** When the user expects it back; drives reminder notifications. */
    val dueDate: LocalDate? = null,
)

/** Money a person paid back -- from a credit transaction, or in cash (no transaction). */
data class Repayment(
    val id: Long = 0,
    val personId: Long,
    val transactionId: Long?,
    val amountMinor: Long,
    val occurredAt: Instant,
)

data class OpenDebt(val debt: Debt, val remainingMinor: Long)

data class PersonSummary(
    val person: Person,
    /** Positive: they owe the user. Negative: they've paid back more than they owed. */
    val outstandingMinor: Long,
    /** Debts not yet covered by repayments, oldest first. */
    val openDebts: List<OpenDebt>,
) {
    /** Earliest due date among debts still open -- when the next reminder is due. */
    val nextDue: LocalDate? get() = openDebts.mapNotNull { it.debt.dueDate }.minOrNull()

    fun isOverdue(today: LocalDate): Boolean = nextDue?.let { it < today } == true
}

/**
 * Applies repayments to debts oldest-first. Repayments aren't tied to particular
 * debts -- a friend just sends money -- so FIFO decides which debts are settled,
 * and therefore which due dates still matter.
 */
fun summarize(person: Person, debts: List<Debt>, repayments: List<Repayment>): PersonSummary {
    val mine = debts.filter { it.personId == person.id }.sortedWith(compareBy({ it.occurredAt }, { it.id }))
    var repaid = repayments.filter { it.personId == person.id }.sumOf { it.amountMinor }
    val owed = mine.sumOf { it.amountMinor }
    val open = mine.mapNotNull { debt ->
        val covered = minOf(repaid, debt.amountMinor)
        repaid -= covered
        val remaining = debt.amountMinor - covered
        if (remaining > 0) OpenDebt(debt, remaining) else null
    }
    val totalRepaid = repayments.filter { it.personId == person.id }.sumOf { it.amountMinor }
    return PersonSummary(person, owed - totalRepaid, open)
}

/**
 * Remind on the due date, then every 3 days while still unpaid: persistent without
 * being a daily nag. Stateless, so the daily worker needs no "last notified" store.
 */
fun shouldRemind(due: LocalDate, today: LocalDate): Boolean {
    if (today < due) return false
    return ChronoUnit.DAYS.between(due, today) % 3 == 0L
}

/**
 * Equal split of [totalMinor] between the user and [others] people. Each other
 * person owes the floor share; any leftover paise stay with the user, so the
 * debts never add up to more than was paid.
 */
fun equalShareMinor(totalMinor: Long, others: Int): Long {
    require(others > 0) { "need at least one other person" }
    return totalMinor / (others + 1)
}

private val REMINDER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** The text the user sends a friend. */
fun reminderMessage(summary: PersonSummary, zone: ZoneId = ZoneId.systemDefault()): String {
    val items = summary.openDebts.joinToString("\n") { open ->
        val what = open.debt.description ?: if (open.debt.kind == DebtKind.LOAN) "Loan" else "Split"
        val date = open.debt.occurredAt.atZone(zone).toLocalDate().format(REMINDER_DATE)
        "• $what ($date): ${formatMoney(open.remainingMinor)}"
    }
    return "Hi ${summary.person.name}, a quick reminder that ${formatMoney(summary.outstandingMinor)} is pending:\n" +
        "$items\nThanks!"
}

/** Built-in category for loans and repayments (excluded from totals). Seeded by name. */
const val LENDING_CATEGORY_NAME = "Lending"

/** Everything about one person, for their detail screen. */
data class PersonDetail(
    val summary: PersonSummary,
    val debts: List<Debt>,
    val repayments: List<Repayment>,
)

data class MoneyTotals(val spentMinor: Long, val receivedMinor: Long) {
    val netMinor: Long get() = receivedMinor - spentMinor
}

/**
 * The user's own spent/received over [transactions]: leaves out categories that
 * aren't spending (Self transfer, Lending) and the part of a split bill that
 * friends owe. Every total in the app goes through here.
 */
fun ownTotals(
    transactions: List<Transaction>,
    excludedCategoryIds: Set<Long>,
    owedByOthers: Map<Long, Long>,
): MoneyTotals {
    var spent = 0L
    var received = 0L
    transactions.filter { it.countsInTotals(excludedCategoryIds) }.forEach { t ->
        val own = (t.amountMinor - (owedByOthers[t.id] ?: 0)).coerceAtLeast(0)
        if (t.direction == TransactionDirection.DEBIT) spent += own else received += own
    }
    return MoneyTotals(spent, received)
}
