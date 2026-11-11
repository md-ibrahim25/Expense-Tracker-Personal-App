package com.mdibrahim.expensetracker.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * These tests exist because money rounding is the one thing in this app that cannot
 * be fixed after the fact -- a wrong conversion corrupts stored history.
 */
class MoneyTest {

    @Test
    fun `whole rupees convert to paise`() {
        assertEquals(45_000L, rupeesToMinor("450"))
    }

    @Test
    fun `two decimal places are preserved exactly`() {
        assertEquals(45_099L, rupeesToMinor("450.99"))
    }

    @Test
    fun `single decimal place is padded, not truncated`() {
        assertEquals(45_050L, rupeesToMinor("450.5"))
    }

    @Test
    fun `grouping separators and symbol are accepted`() {
        assertEquals(123_456L, rupeesToMinor("₹1,234.56"))
    }

    @Test
    fun `third decimal place rounds half up`() {
        assertEquals(101L, rupeesToMinor("1.005"))
        assertEquals(100L, rupeesToMinor("1.004"))
    }

    @Test
    fun `the classic float trap holds as exact integers`() {
        // 0.1 + 0.2 in Double is 0.30000000000000004. In paise it must be exactly 30.
        assertEquals(30L, rupeesToMinor("0.10")!! + rupeesToMinor("0.20")!!)
    }

    @Test
    fun `invalid input returns null rather than zero`() {
        // Returning 0 would silently record a free transaction.
        assertNull(rupeesToMinor(""))
        assertNull(rupeesToMinor("abc"))
        assertNull(rupeesToMinor("12.3.4"))
    }

    @Test
    fun `negative amounts parse -- direction is stored separately`() {
        assertEquals(-45_000L, rupeesToMinor("-450"))
    }

    @Test
    fun `formatting always shows two decimal places`() {
        // Asserting on digits only: the symbol and separators are locale-dependent.
        assertEquals("1,234.56", formatMoney(123_456L, withSymbol = false))
        assertEquals("450.00", formatMoney(45_000L, withSymbol = false))
    }

    @Test
    fun `formatting uses lakh grouping for large amounts`() {
        // en-IN groups as 12,34,567 rather than 1,234,567.
        assertEquals("12,34,567.00", formatMoney(123_456_700L, withSymbol = false))
    }

    @Test
    fun `lakh grouping handles every digit count`() {
        assertEquals("0.05", formatMoney(5L, withSymbol = false))
        assertEquals("999.00", formatMoney(99_900L, withSymbol = false))
        assertEquals("1,00,000.00", formatMoney(10_000_000L, withSymbol = false))
        assertEquals("1,23,45,678.90", formatMoney(1_234_567_890L, withSymbol = false))
    }

    @Test
    fun `negative amounts format with a leading sign`() {
        assertEquals("-1,234.56", formatMoney(-123_456L, withSymbol = false))
    }

    @Test
    fun `symbol is the rupee sign`() {
        assertEquals("₹1,234.56", formatMoney(123_456L))
    }

    @Test
    fun `exponent notation is rejected`() {
        assertNull(rupeesToMinor("1e3"))
        assertNull(rupeesToMinor("1E3"))
    }

    @Test
    fun `amounts too large for a Long return null rather than wrapping`() {
        assertNull(rupeesToMinor("100000000000000000000"))
    }

    @Test
    fun `round trip through paise is lossless`() {
        listOf("0.01", "9.99", "1234.56", "99999.99").forEach { input ->
            val minor = rupeesToMinor(input)!!
            assertEquals(input, formatMoney(minor, withSymbol = false).replace(",", ""))
        }
    }
}
