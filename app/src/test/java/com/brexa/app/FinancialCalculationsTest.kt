package com.brexa.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class FinancialCalculationsTest {

    @Test
    fun testUsdConversionHalfEvenRounding() {
        val nativeAmount = 3500.0
        val exchangeRate = 72.50

        val usdEquiv = BigDecimal(nativeAmount)
            .divide(BigDecimal(exchangeRate), 2, RoundingMode.HALF_EVEN)
            .toDouble()

        assertEquals(48.28, usdEquiv, 0.001)
    }

    @Test
    fun testUsdExactConversion() {
        val nativeAmount = 1450.0
        val exchangeRate = 72.50

        val usdEquiv = BigDecimal(nativeAmount)
            .divide(BigDecimal(exchangeRate), 2, RoundingMode.HALF_EVEN)
            .toDouble()

        assertEquals(20.00, usdEquiv, 0.001)
    }

    @Test
    fun testExchangeRateImpliedCalculation() {
        val sourceUsd = 100.0
        val targetVesReceived = 7350.0

        val impliedRate = BigDecimal(targetVesReceived)
            .divide(BigDecimal(sourceUsd), 4, RoundingMode.HALF_EVEN)
            .toDouble()

        assertEquals(73.50, impliedRate, 0.0001)
    }

    @Test
    fun testCurrencyCodeSanitization() {
        val codeInput = "  usdt  "
        val cleanCode = codeInput.trim().uppercase().take(4)
        assertEquals("USDT", cleanCode)

        val longCode = "BITCOIN"
        val truncated = longCode.trim().uppercase().take(4)
        assertEquals("BITC", truncated)
    }
}
