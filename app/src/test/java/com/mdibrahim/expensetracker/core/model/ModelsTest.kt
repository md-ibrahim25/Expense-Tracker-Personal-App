package com.mdibrahim.expensetracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ModelsTest {

    @Test
    fun `payee key ignores case and padding`() {
        // The bank writes the same person both ways across SMS.
        assertEquals(payeeKey("MOHAMMED IBR"), payeeKey("Mohammed Ibr"))
        assertEquals("SHIV RAJ SINGH", payeeKey("  Shiv Raj   Singh "))
    }

    private fun tx(categoryId: Long?) = Transaction(
        amountMinor = 100,
        direction = TransactionDirection.DEBIT,
        occurredAt = Instant.EPOCH,
        accountId = 1,
        categoryId = categoryId,
    )

    @Test
    fun `excluded categories don't count in totals`() {
        val selfTransfer = 8L
        assertFalse(tx(selfTransfer).countsInTotals(setOf(selfTransfer)))
        assertTrue(tx(1L).countsInTotals(setOf(selfTransfer)))
        // Not yet categorised still counts -- it's real money until the user says otherwise.
        assertTrue(tx(null).countsInTotals(setOf(selfTransfer)))
    }
}
