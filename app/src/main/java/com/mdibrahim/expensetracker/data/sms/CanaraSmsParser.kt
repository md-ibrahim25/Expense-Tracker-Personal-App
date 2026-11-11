package com.mdibrahim.expensetracker.data.sms

import com.mdibrahim.expensetracker.core.model.TransactionDirection
import com.mdibrahim.expensetracker.core.util.rupeesToMinor

/** What a bank SMS says happened. Amount is unsigned paise, like everywhere else. */
data class ParsedSms(
    val amountMinor: Long,
    val direction: TransactionDirection,
    /** Last digits of the account as printed in the SMS ("1234"), if present. */
    val accountSuffix: String?,
    /** Best-effort counterparty / description, for the transaction note. */
    val description: String?,
)

/**
 * Parses Canara Bank debit/credit SMS. Pure Kotlin so it is unit-testable on the JVM.
 *
 * Deliberately conservative: anything that doesn't clearly describe money that has
 * already moved (OTPs, payment requests, "will be debited" mandate reminders,
 * balance enquiries) returns null. A missed SMS can be entered by hand; a phantom
 * transaction silently corrupts totals.
 */
object CanaraSmsParser {

    /** Canara's DLT sender IDs look like "AD-CANBNK", "VM-CANBNK-S". */
    private val SENDER = Regex("CANBNK|CANARA", RegexOption.IGNORE_CASE)

    private val AMOUNT = Regex("""(?:INR|Rs\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    private val DEBIT = Regex("""\b(debited|paid|withdrawn|sent|spent|purchase)\b""", RegexOption.IGNORE_CASE)
    private val CREDIT = Regex("""\b(credited|received|deposited|refunded)\b""", RegexOption.IGNORE_CASE)

    /** Messages about money that hasn't moved (or isn't a transaction at all). */
    private val REJECT = Regex(
        """\bOTP\b|\bwill be (debited|credited)\b|\brequested\b|\brequest\b|\bdue\b|\bfailed\b|\bdeclined\b""",
        RegexOption.IGNORE_CASE,
    )

    private val ACCOUNT = Regex("""\b(?:a/c|account|acct)\s*(?:no\.?\s*)?[Xx*]*\s*([0-9]{3,6})\b""", RegexOption.IGNORE_CASE)

    private val TOWARDS = Regex("""\btowards\s+(.+?)(?:\.\s|\.$|\s+Total\b|\s+Avl|$)""", RegexOption.IGNORE_CASE)
    private val TO_OR_FROM = Regex("""\b(?:to|from)\s+(?!your\b|a/c\b|account\b)([A-Za-z0-9][A-Za-z0-9 .&@_/-]{1,40}?)(?:,|\s+UPI\b|\s+on\b|\.\s|$)""", RegexOption.IGNORE_CASE)

    fun isFromCanara(sender: String?, body: String): Boolean =
        (sender != null && SENDER.containsMatchIn(sender)) || body.contains("Canara Bank", ignoreCase = true)

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
            description = describe(body),
        )
    }

    private fun describe(body: String): String? {
        val raw = TOWARDS.find(body)?.groupValues?.get(1)
            ?: TO_OR_FROM.find(body)?.groupValues?.get(1)
            ?: return null
        return raw.trim().trimEnd('.', ',').take(60).ifBlank { null }
    }
}
