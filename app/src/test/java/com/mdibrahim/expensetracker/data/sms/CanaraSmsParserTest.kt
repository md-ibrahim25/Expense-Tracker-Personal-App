package com.mdibrahim.expensetracker.data.sms

import com.mdibrahim.expensetracker.core.model.TransactionDirection.CREDIT
import com.mdibrahim.expensetracker.core.model.TransactionDirection.DEBIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sample bodies follow Canara's known wording but are NOT copied from real messages.
 * Replace/extend them with real (masked) SMS from the phone -- those are the only
 * samples that actually prove the parser.
 */
class CanaraSmsParserTest {

    @Test
    fun `account debit`() {
        val sms = "An amount of INR 1,250.50 has been DEBITED to your account XXX1234 on 11/11/2026 " +
            "towards UPI/432112345678/SWIGGY. Total Avail.bal INR 8,500.00. - Canara Bank"
        val parsed = CanaraSmsParser.parse(sms)!!
        assertEquals(125_050L, parsed.amountMinor)
        assertEquals(DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountSuffix)
        assertEquals("UPI/432112345678/SWIGGY", parsed.description)
    }

    @Test
    fun `account credit`() {
        val sms = "An amount of INR 50,000.00 has been CREDITED to your account XXX1234 on 01/11/2026 " +
            "towards NEFT-ACME PAYROLL. Total Avail.bal INR 58,500.00. - Canara Bank"
        val parsed = CanaraSmsParser.parse(sms)!!
        assertEquals(5_000_000L, parsed.amountMinor)
        assertEquals(CREDIT, parsed.direction)
        assertEquals("NEFT-ACME PAYROLL", parsed.description)
    }

    @Test
    fun `upi payment`() {
        val sms = "Rs.450.00 paid thru A/C XX1234 on 11-11-26 13:45:10 to RAMESH TEA STALL, " +
            "UPI Ref 432198765432. If not done, SMS BLOCKUPI to 9901771222.-Canara Bank"
        val parsed = CanaraSmsParser.parse(sms)!!
        assertEquals(45_000L, parsed.amountMinor)
        assertEquals(DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountSuffix)
        assertEquals("RAMESH TEA STALL", parsed.description)
    }

    @Test
    fun `the transaction amount is taken, not the balance`() {
        val sms = "Your a/c XX1234 is credited with Rs 200.00 on 11-11-26. Avl Bal Rs 9,999.00 -Canara Bank"
        assertEquals(20_000L, CanaraSmsParser.parse(sms)!!.amountMinor)
    }

    @Test
    fun `first direction keyword wins`() {
        val sms = "INR 300.00 debited from your a/c XX1234 and credited to a/c XX9876. -Canara Bank"
        assertEquals(DEBIT, CanaraSmsParser.parse(sms)!!.direction)
    }

    @Test
    fun `messages where money has not moved are ignored`() {
        listOf(
            "Your OTP for transaction of INR 500.00 is 123456. Do not share. -Canara Bank",
            "INR 999.00 will be debited from your a/c XX1234 on 15/11/2026 for mandate. -Canara Bank",
            "PAYTM has requested money of Rs 200.00 from you via UPI. -Canara Bank",
            "Transaction of INR 300.00 on your a/c XX1234 failed. -Canara Bank",
        ).forEach { assertNull(it, CanaraSmsParser.parse(it)) }
    }

    @Test
    fun `messages with no amount or no direction are ignored`() {
        assertNull(CanaraSmsParser.parse("Dear customer, update your KYC at the nearest branch. -Canara Bank"))
        assertNull(CanaraSmsParser.parse("Avl bal in your a/c XX1234 is INR 5,000.00. -Canara Bank"))
    }

    @Test
    fun `sender detection`() {
        assertTrue(CanaraSmsParser.isFromCanara("AD-CANBNK", "anything"))
        assertTrue(CanaraSmsParser.isFromCanara("VM-CANBNK-S", "anything"))
        assertTrue(CanaraSmsParser.isFromCanara("+919999999999", "... -Canara Bank"))
        assertFalse(CanaraSmsParser.isFromCanara("AD-HDFCBK", "Rs 100 debited"))
    }
}
