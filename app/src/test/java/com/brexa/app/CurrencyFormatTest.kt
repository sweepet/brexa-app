package com.brexa.app

import com.brexa.app.ui.util.CurrencyFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatTest {

    @Test
    fun testParseNumberWithHispanicThousandsDot() {
        val parsed = CurrencyFormat.parseNumber("1.000")
        assertEquals(1000.0, parsed ?: 0.0, 0.001)

        val parsedTenThousand = CurrencyFormat.parseNumber("10.000")
        assertEquals(10000.0, parsedTenThousand ?: 0.0, 0.001)
    }

    @Test
    fun testParseNumberWithDecimalDot() {
        val parsed = CurrencyFormat.parseNumber("72.50")
        assertEquals(72.50, parsed ?: 0.0, 0.001)
    }

    @Test
    fun testParseNumberWithDecimalComma() {
        val parsed = CurrencyFormat.parseNumber("72,50")
        assertEquals(72.50, parsed ?: 0.0, 0.001)

        val parsedFull = CurrencyFormat.parseNumber("1.250,50")
        assertEquals(1250.50, parsedFull ?: 0.0, 0.001)
    }

    @Test
    fun testFormatForInput() {
        assertEquals("1000", CurrencyFormat.formatForInput(1000.0))
        assertEquals("72,5", CurrencyFormat.formatForInput(72.5))
        assertEquals("1250,75", CurrencyFormat.formatForInput(1250.75))
    }

    @Test
    fun testCalculationScenarioFromUserBug() {
        // Amount: 6000 BS, Rate: 1.000 BS/USD
        val amount = CurrencyFormat.parseNumber("6000") ?: 0.0
        val rate = CurrencyFormat.parseNumber("1.000") ?: 1.0
        val equivalentUsd = amount / rate

        assertEquals(6.0, equivalentUsd, 0.001)
        val formattedUsd = CurrencyFormat.format(equivalentUsd)
        assertEquals("6,00", formattedUsd)
    }
}
