package com.brexa.app.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Utilidad global para formateo y parseo de números con convención hispana:
 * - Separador de miles: punto (.) -> ej. 1.250,50
 * - Separador de decimales: coma (,) -> ej. 0,75
 */
object CurrencyFormat {

    val spanishSymbols: DecimalFormatSymbols = DecimalFormatSymbols(Locale("es", "ES")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    val standardFormatter: DecimalFormat = DecimalFormat("#,##0.00", spanishSymbols)

    val rateFormatter: DecimalFormat = DecimalFormat("#,##0.##", spanishSymbols).apply {
        maximumFractionDigits = 4
    }

    fun format(value: Double): String = standardFormatter.format(value)

    fun formatRate(value: Double): String = rateFormatter.format(value)

    /**
     * Parsea de manera inteligente un texto numérico escrito por el usuario,
     * admitiendo tanto comas como puntos decimales y separadores de miles:
     * - "1.250,50" -> 1250.50
     * - "1250,50"   -> 1250.50
     * - "1250.50"   -> 1250.50
     * - "1000"      -> 1000.0
     */
    fun parseNumber(input: String): Double? {
        val clean = input.trim()
        if (clean.isEmpty()) return null

        return try {
            if (clean.contains(",") && clean.contains(".")) {
                // Ambos presentes: el punto es miles y la coma es decimal (ej. 1.250,50)
                clean.replace(".", "").replace(",", ".").toDoubleOrNull()
            } else if (clean.contains(",")) {
                // Solo coma: es decimal (ej. 1250,50)
                clean.replace(",", ".").toDoubleOrNull()
            } else {
                // Solo punto o sin separador (ej. 1250.50 o 1250)
                clean.toDoubleOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }
}

fun Double.formatAmount(): String = CurrencyFormat.format(this)
fun Double.formatRate(): String = CurrencyFormat.formatRate(this)
