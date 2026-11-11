package com.mdibrahim.expensetracker.data.sms

import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.core.util.rupeesToMinor

/** What a bank SMS says happened. Amount is unsigned paise, like everywhere else. */
data class ParsedSms(
    val amountMinor: Long,
    val direction: TransactionDirection,
    /** Last digits of the account as printed in the SMS ("1234"), if present. */
    val accountSuffix: String?,
    /** Who was paid / who paid, or e.g. "ATM withdrawal". Null when the SMS doesn't say. */
    val counterparty: String?,
    /** Account balance after this transaction, as reported by the bank, in paise. */
    val balanceMinor: Long?,
)

/**
 * Parses Canara Bank debit/credit SMS. Pure Kotlin so it is unit-testable on the JVM.
 *
 * Formats seen in real messages (see CanaraSmsParserTest for one of each):
 * - "Acct XXXX1234 Dr. INR 75.00 on .. to NAME; UPI: ..; Bal INR .."      (UPI debit)
 * - "Acct XXXX1234 credited with INR 100.00 on .. from NAME; UPI:..; Bal"  (UPI credit)
 * - "Rs.5.00 paid thru A/C XX1234 on .. to NAME, UPI Ref .."               (older UPI debit)
 * - "An amount of INR 260.00 has been DEBITED to your account XXXX1234 .." (generic alert)
 * - "A/c XXXX1234 linked to card debited Rs.INR 10,000.00 .. ATM txn .."   (ATM withdrawal)
 *
 * Deliberately conservative: anything that doesn't clearly describe money that has
 * already moved (OTPs, payment requests, "will be debited" mandate reminders,
 * balance enquiries) returns null. A missed SMS can be entered by hand; a phantom
 * transaction silently corrupts totals.
 */
object CanaraSmsParser {

    /** Canara's DLT sender IDs look like "AD-CANBNK", "VM-CANBNK-S". */
    private val SENDER = Regex("CANBNK|CANARA", RegexOption.IGNORE_CASE)

    /** Body signature; newer messages end "-CanaraBank" with no space. */
    private val SIGNATURE = Regex("""Canara\s?Bank""", RegexOption.IGNORE_CASE)

    private val AMOUNT = Regex("""(?:INR|Rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    // "Dr."/"Cr." only count right before an amount, so a payee called "Dr Rao" can't flip it.
    private val DEBIT = Regex("""\b(debited|paid|withdrawn|sent|spent|purchase)\b|\bDr\.?\s+(?:INR|Rs)""", RegexOption.IGNORE_CASE)
    private val CREDIT = Regex("""\b(credited|received|deposited|refunded)\b|\bCr\.?\s+(?:INR|Rs)""", RegexOption.IGNORE_CASE)

    /** Messages about money that hasn't moved (or isn't a transaction at all). */
    private val REJECT = Regex(
        """\bOTP\b|\bwill be (debited|credited)\b|\brequested\b|\brequest\b|\bdue\b|\bfailed\b|\bdeclined\b""" +
            // Autopay reminders ("Mandate .. debit of Rs.99 is scheduled") and cardless-cash
            // codes ("initiated") come before the money moves; the real debit has its own SMS.
            """|\bmandate\b|\bscheduled\b|\binitiated\b""",
        RegexOption.IGNORE_CASE,
    )

    private val ACCOUNT = Regex("""\b(?:a/c|account|acct)\s*(?:no\.?\s*)?[Xx*]*\s*([0-9]{3,6})\b""", RegexOption.IGNORE_CASE)

    /**
     * Counterparty in UPI messages: "to NAME; UPI", "from NAME; UPI", "to NAME, UPI Ref".
     * Anchoring on the UPI marker is what keeps phrases like "SMS BLOCKUPI to 99.." or
     * "Dial 1930 to report cyber fraud" from being mistaken for a payee.
     */
    private val UPI_PARTY = Regex("""\b(?:to|from)\s+([^,;]+?)\s*[,;]\s*UPI\b""", RegexOption.IGNORE_CASE)
    private val ATM = Regex("""\bATM txn\b""", RegexOption.IGNORE_CASE)

    /** "Bal INR 670.36", "Total Avail.bal INR 3,764.94", "Avl Bal Rs.INR 16,330.39". */
    private val BALANCE = Regex(
        """\b(?:Avail\.?\s*bal|Avl\.?\s*Bal|Bal)\b[.:]?\s*(?:Rs\.?\s*)?(?:INR\s*)?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    fun isFromCanara(sender: String?, body: String): Boolean =
        (sender != null && SENDER.containsMatchIn(sender)) || SIGNATURE.containsMatchIn(body)

    fun parse(body: String): ParsedSms? {
        if (REJECT.containsMatchIn(body)) return null

        // The first amount is the transaction; later ones are usually "Avl Bal".
        val amountText = AMOUNT.find(body)?.groupValues?.get(1) ?: return null
        val amountMinor = rupeesToMinor(amountText)?.takeIf { it > 0 } ?: return null

        // Whichever keyword appears first wins: "debited from your a/c and
        // credited to X" is a debit from the user's point of view.
        val debitAt = DEBIT.find(body)?.range?.first
        val creditAt = CREDIT.find(body)?.range?.first
        val direction = when {
            debitAt == null && creditAt == null -> return null
            creditAt == null -> TransactionDirection.DEBIT
            debitAt == null -> TransactionDirection.CREDIT
            debitAt < creditAt -> TransactionDirection.DEBIT
            else -> TransactionDirection.CREDIT
        }

        return ParsedSms(
            amountMinor = amountMinor,
            direction = direction,
            accountSuffix = ACCOUNT.find(body)?.groupValues?.get(1),
            counterparty = counterparty(body, direction),
            balanceMinor = BALANCE.find(body)?.groupValues?.get(1)?.let(::rupeesToMinor),
        )
    }

    /** Null when the SMS names no counterparty (e.g. the generic "An amount of .." alert). */
    private fun counterparty(body: String, direction: TransactionDirection): String? {
        if (direction == TransactionDirection.DEBIT && ATM.containsMatchIn(body)) return "ATM withdrawal"
        val party = UPI_PARTY.find(body)?.groupValues?.get(1) ?: return null
        // Banks pad names with double spaces ("SHIV RAJ  SINGH").
        return party.replace(Regex("""\s+"""), " ").trim().take(60).ifBlank { null }
    }
}
