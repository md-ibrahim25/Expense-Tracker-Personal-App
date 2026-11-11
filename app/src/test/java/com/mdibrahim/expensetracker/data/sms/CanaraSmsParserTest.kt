package com.mdibrahim.expensetracker.data.sms

import com.mdibrahim.expensetracker.core.model.TransactionDirection.CREDIT
import com.mdibrahim.expensetracker.core.model.TransactionDirection.DEBIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every body here is a real Canara Bank SMS, with the account number, payee names
 * and UPI references replaced. Wording, spacing and punctuation are kept exactly,
 * because that's what the parser actually depends on. Add a new case here whenever
 * a real SMS is parsed wrongly.
 */
class CanaraSmsParserTest {

    @Test
    fun `UPI debit -- Dr format`() {
        val sms = "Dear Customer, Acct XXXX1234 Dr. INR 75.00 on 12/07/26 to Ravi Kumar; " +
            "UPI: 100000000001; Bal INR 670.36.Not you?SMS BLOCKUPI to 9901771222-CanaraBank"
        val parsed = CanaraSmsParser.parse(sms)!!
        assertEquals(7_500L, parsed.amountMinor)
        assertEquals(DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountSuffix)
        assertEquals("Ravi Kumar", parsed.counterparty)
        assertEquals(67_036L, parsed.balanceMinor)
    }

    @Test
    fun `UPI debit -- large amount with grouping`() {
        val sms = "Dear Customer, Acct XXXX1234 Dr. INR 12,000.00 on 09/08/26 to ANIL SHARMA; " +
            "UPI: 100000000002; Bal INR 4,330.39.Not you?SMS BLOCKUPI to 9901771222-CanaraBank"
        assertEquals(1_200_000L, CanaraSmsParser.parse(sms)!!.amountMinor)
    }

    @Test
    fun `UPI credit`() {
        val sms = "Dear Customer, Acct XXXX1234 credited with INR 216.67 on 24/06/26 from PRIYA  NAIR; " +
            "UPI:100000000003; Bal INR 2,117.03-CanaraBank"
        val parsed = CanaraSmsParser.parse(sms)!!
        assertEquals(21_667L, parsed.amountMinor)
        assertEquals(CREDIT, parsed.direction)
        assertEquals("1234", parsed.accountSuffix)
        // Double space in the bank's text is collapsed.
        assertEquals("PRIYA NAIR", parsed.counterparty)
        assertEquals(211_703L, parsed.balanceMinor)
    }

    @Test
    fun `older UPI debit -- paid thru format`() {
        val sms = "Rs.250.00 paid thru A/C XX1234 on 10-6-26 16:54:45 to RAJ  KUMAR  SINGH, " +
            "UPI Ref 100000000004. If not done, SMS BLOCKUPI to 9901771222.-Canara Bank"
        val parsed = CanaraSmsParser.parse(sms)!!
        assertEquals(25_000L, parsed.amountMinor)
        assertEquals(DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountSuffix)
        assertEquals("RAJ KUMAR SINGH", parsed.counterparty)
        // This older format doesn't report a balance.
        assertNull(parsed.balanceMinor)
    }

    @Test
    fun `generic debit alert has no payee`() {
        val sms = "An amount of INR 83.33 has been DEBITED to your account XXXX1234 on 12/06/2026. " +
            "Total Avail.bal INR 5,195.61.Dial 1930 to report cyber fraud - Canara Bank"
        val parsed = CanaraSmsParser.parse(sms)!!
        assertEquals(8_333L, parsed.amountMinor)
        assertEquals(DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountSuffix)
        // "to report cyber fraud" must not be read as a payee.
        assertNull(parsed.counterparty)
        assertEquals(519_561L, parsed.balanceMinor)
    }

    @Test
    fun `ATM withdrawal`() {
        val sms = "A/c XXXX1234 linked to card  debited Rs.INR 10,000.00 on 09/08/26 ATM txn Seq 3117. " +
            "Avl Bal Rs.INR 16,330.39. Use Canara Bank ATM to avoid charges. Report fraud SMS SUSPECT " +
            "to 56161. Dial 1930 to report cyber fraud. -Canara Bank"
        val parsed = CanaraSmsParser.parse(sms)!!
        // "Rs.INR 10,000.00" -- the amount after the doubled currency marker, not the balance.
        assertEquals(1_000_000L, parsed.amountMinor)
        assertEquals(DEBIT, parsed.direction)
        assertEquals("1234", parsed.accountSuffix)
        assertEquals("ATM withdrawal", parsed.counterparty)
        assertEquals(1_633_039L, parsed.balanceMinor)
    }

    @Test
    fun `messages where money has not moved are ignored`() {
        listOf(
            // Autopay reminder -- the actual debit arrives as its own SMS later.
            "UPI Autopay Mandate for Google debit of Rs.99.00 is scheduled on 27/09/2026 for the Monthly " +
                "Subscri with abc123@ybl. Please ensure sufficient balance in your account. To stop, " +
                "PAUSE/REVOKE mandate- Canara Bank.",
            // Cardless cash code -- the withdrawal arrives as an ATM SMS.
            "Cardless Cash for Rs 5,000 initiated @Canara ai1 on 07-10-26 02:29:43. Visit Canara ATM in " +
                "15 mins for cash. If not you, send SUSPECT to 56161.\n-Canara Bank",
            // Awareness broadcast.
            "Dear Customer, Beware of fake KYC, investment, loan & reward calls. Never share OTP/PIN or " +
                "click unknown links. Stay safe, stay alert! -Canara Bank",
        ).forEach { assertNull(it, CanaraSmsParser.parse(it)) }
    }

    @Test
    fun `Dr only counts as a debit marker before an amount`() {
        // A payee called "Dr ..." must not turn a credit into a debit.
        val sms = "Dear Customer, Acct XXXX1234 credited with INR 500.00 on 01/09/26 from Dr Mehta; " +
            "UPI:100000000005; Bal INR 2,793.39-CanaraBank"
        assertEquals(CREDIT, CanaraSmsParser.parse(sms)!!.direction)
    }

    @Test
    fun `sender detection`() {
        assertTrue(CanaraSmsParser.isFromCanara("AD-CANBNK", "anything"))
        assertTrue(CanaraSmsParser.isFromCanara("VM-CANBNK-S", "anything"))
        assertTrue(CanaraSmsParser.isFromCanara(null, "... Bal INR 2,117.03-CanaraBank"))
        assertTrue(CanaraSmsParser.isFromCanara(null, "... to report cyber fraud - Canara Bank"))
        assertFalse(CanaraSmsParser.isFromCanara("AD-HDFCBK", "Rs 100 debited"))
    }
}
