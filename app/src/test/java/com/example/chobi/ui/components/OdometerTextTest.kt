package com.example.chobi.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class OdometerTextTest {

    @Test
    fun testSplitFormattedCurrency_usd() {
        val (prefix, body, suffix) = splitFormattedCurrency("$1,234.56")
        assertEquals("$", prefix)
        assertEquals("1,234.56", body)
        assertEquals("", suffix)
    }

    @Test
    fun testSplitFormattedCurrency_euro_suffix() {
        val (prefix, body, suffix) = splitFormattedCurrency("12.345,67 €")
        assertEquals("", prefix)
        assertEquals("12.345,67", body)
        assertEquals(" €", suffix)
    }

    @Test
    fun testSplitFormattedCurrency_euro_prefix() {
        val (prefix, body, suffix) = splitFormattedCurrency("€ 1,234.56")
        assertEquals("€ ", prefix)
        assertEquals("1,234.56", body)
        assertEquals("", suffix)
    }

    @Test
    fun testSplitFormattedCurrency_no_digits() {
        val (prefix, body, suffix) = splitFormattedCurrency("Free")
        assertEquals("", prefix)
        assertEquals("Free", body)
        assertEquals("", suffix)
    }

    @Test
    fun testSplitFormattedCurrency_single_digit() {
        val (prefix, body, suffix) = splitFormattedCurrency("Total: 5")
        assertEquals("Total: ", prefix)
        assertEquals("5", body)
        assertEquals("", suffix)
    }
}






