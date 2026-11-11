package com.mdibrahim.expensetracker.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/**
 * Single source of truth for turning stored minor units (paise) into text and back.
 *
 * Every amount displayed in the app must go through [formatMoney] so formatting is
 * never inconsistent between screens, and every amount typed by the user must go
 * through [rupeesToMinor] so rounding happens in exactly one place.
 */

private const val MINOR_UNITS_PER_MAJOR = 100L

private val INDIA: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()

/**
 * Plain decimal: optional sign, digits, optional fraction. Deliberately rejects
 * exponent notation ("1e3"), which BigDecimal would otherwise accept as 1000.
 */
private val AMOUNT_PATTERN = Regex("""[+-]?(\d+(\.\d*)?|\.\d+)""")

/**
 * Formats [amountMinor] paise as currency, e.g. 123456 -> "₹1,234.56".
 *
 * Grouping is done here rather than by [java.text.NumberFormat]: the JVM's
 * formatter cannot do lakh/crore grouping (it yields 1,234,567) while Android's
 * ICU-backed one can (12,34,567). Doing it by hand makes unit tests and the device
 * produce identical output.
 *
 * @param withSymbol when false, omits the currency symbol -- useful in tables and
 *   charts where the symbol is already implied by a header.
 */
fun formatMoney(
    amountMinor: Long,
    currencyCode: String = "INR",
    withSymbol: Boolean = true,
): String {
    val plain = minorToMajor(amountMinor).abs().setScale(2, RoundingMode.UNNECESSARY).toPlainString()
    val (whole, fraction) = plain.split('.')
    val sign = if (amountMinor < 0) "-" else ""
    val symbol = if (withSymbol) currencySymbol(currencyCode) else ""
    return "$sign$symbol${groupIndian(whole)}.$fraction"
}

/** Converts stored paise to an exact decimal rupee value. */
fun minorToMajor(amountMinor: Long): BigDecimal =
    BigDecimal.valueOf(amountMinor).divide(BigDecimal.valueOf(MINOR_UNITS_PER_MAJOR))

/**
 * Parses user input in rupees into paise, or null if the text is not a valid amount.
 *
 * Accepts grouping separators and surrounding whitespace ("1,234.5" -> 123450).
 * Rounds half-up at the paise boundary, matching how banks present amounts.
 * Returns null (never a wrapped-around value) if the amount does not fit in a Long.
 */
fun rupeesToMinor(input: String): Long? {
    val cleaned = input.trim()
        .removePrefix("₹")
        .replace(",", "")
        .replace(" ", "")
    if (!AMOUNT_PATTERN.matches(cleaned)) return null

    return runCatching {
        BigDecimal(cleaned)
            .multiply(BigDecimal.valueOf(MINOR_UNITS_PER_MAJOR))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()
}

/** "1234567" -> "12,34,567": last three digits, then groups of two. */
private fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    val headGroups = head.reversed().chunked(2).joinToString(",").reversed()
    return "$headGroups,$tail"
}

private fun currencySymbol(currencyCode: String): String {
    val currency = runCatching { Currency.getInstance(currencyCode) }
        .getOrElse { Currency.getInstance("INR") }
    return currency.getSymbol(INDIA)
}
