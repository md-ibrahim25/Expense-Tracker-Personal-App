package com.mdibrahim.expensetracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class LendingTest {

    private val ravi = Person(id = 1, name = "Ravi")

    private fun debt(id: Long, day: Int, amount: Long, due: LocalDate? = null, what: String? = null) = Debt(
        id = id, personId = 1, transactionId = id, amountMinor = amount,
        kind = DebtKind.SPLIT, description = what,
        occurredAt = Instant.parse("2026-11-%02dT12:00:00Z".format(day)), dueDate = due,
    )

    private fun repay(amount: Long) = Repayment(personId = 1, transactionId = null, amountMinor = amount, occurredAt = Instant.EPOCH)

    @Test
    fun `repayments settle the oldest debt first`() {
        val dinner = debt(1, day = 5, amount = 30_000, due = LocalDate.of(2026, 11, 15))
        val loan = debt(2, day = 8, amount = 200_000, due = LocalDate.of(2026, 11, 30))
        val s = summarize(ravi, listOf(loan, dinner), listOf(repay(30_000)))
        assertEquals(200_000L, s.outstandingMinor)
        assertEquals(listOf(2L), s.openDebts.map { it.debt.id })
        // The dinner's due date no longer matters; the loan's does.
        assertEquals(LocalDate.of(2026, 11, 30), s.nextDue)
    }

    @Test
    fun `partial repayment leaves the remainder open`() {
        val s = summarize(ravi, listOf(debt(1, 5, 30_000)), listOf(repay(10_000)))
        assertEquals(20_000L, s.outstandingMinor)
        assertEquals(20_000L, s.openDebts.single().remainingMinor)
    }

    @Test
    fun `overpaying goes negative and closes everything`() {
        val s = summarize(ravi, listOf(debt(1, 5, 30_000)), listOf(repay(50_000)))
        assertEquals(-20_000L, s.outstandingMinor)
        assertTrue(s.openDebts.isEmpty())
        assertNull(s.nextDue)
    }

    @Test
    fun `other people's records are ignored`() {
        val other = debt(9, 5, 99_999).copy(personId = 2)
        assertEquals(0L, summarize(ravi, listOf(other), emptyList()).outstandingMinor)
    }

    @Test
    fun `overdue only after the due date`() {
        val s = summarize(ravi, listOf(debt(1, 5, 100, due = LocalDate.of(2026, 11, 15))), emptyList())
        assertFalse(s.isOverdue(LocalDate.of(2026, 11, 15)))
        assertTrue(s.isOverdue(LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun `remind on the due date then every third day`() {
        val due = LocalDate.of(2026, 11, 15)
        val reminded = (14..25).filter { shouldRemind(due, LocalDate.of(2026, 11, it)) }
        assertEquals(listOf(15, 18, 21, 24), reminded)
    }

    @Test
    fun `equal split keeps leftover paise with the payer`() {
        // ₹1,000.00 between the user and 2 friends: friends owe 333.33 each.
        assertEquals(33_333L, equalShareMinor(100_000, others = 2))
        assertEquals(30_000L, equalShareMinor(120_000, others = 3))
    }

    @Test
    fun `reminder message lists what is still open`() {
        val s = summarize(
            ravi,
            listOf(debt(1, 5, 30_000, what = "Dinner"), debt(2, 8, 200_000).copy(kind = DebtKind.LOAN)),
            emptyList(),
        )
        val msg = reminderMessage(s, ZoneOffset.UTC)
        assertTrue(msg, msg.startsWith("Hi Ravi, a quick reminder that ₹2,300.00 is pending:"))
        assertTrue(msg, "• Dinner (5 Nov): ₹300.00" in msg)
        assertTrue(msg, "• Loan (8 Nov): ₹2,000.00" in msg)
    }

    @Test
    fun `own totals count only the user's share of a split`() {
        fun t(id: Long, amount: Long, dir: TransactionDirection, cat: Long? = 1) = Transaction(
            id = id, amountMinor = amount, direction = dir, occurredAt = Instant.EPOCH, accountId = 1, categoryId = cat,
        )
        val lending = 99L
        val totals = ownTotals(
            transactions = listOf(
                t(1, 120_000, TransactionDirection.DEBIT), // dinner, friends owe 90_000 of it
                t(2, 200_000, TransactionDirection.DEBIT, cat = lending), // loan
                t(3, 30_000, TransactionDirection.CREDIT, cat = lending), // repayment
                t(4, 50_000, TransactionDirection.CREDIT), // real income
            ),
            excludedCategoryIds = setOf(lending),
            owedByOthers = mapOf(1L to 90_000L),
        )
        assertEquals(30_000L, totals.spentMinor)
        assertEquals(50_000L, totals.receivedMinor)
    }
}
