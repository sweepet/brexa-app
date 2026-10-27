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

    // Formateador estándar para visualización con separador de miles y 2 decimales: ej. 1.250,50
    val standardFormatter: DecimalFormat = DecimalFormat("#,##0.00", spanishSymbols)

    // Formateador para mostrar tasas en texto de lectura: ej. 72,50 o 1.000,00
    val rateFormatter: DecimalFormat = DecimalFormat("#,##0.##", spanishSymbols).apply {
        maximumFractionDigits = 4
    }

    fun format(value: Double): String = standardFormatter.format(value)

    fun formatRate(value: Double): String = rateFormatter.format(value)

    /**
     * Formateador específico para campos de texto editables (TextFields).
     * Evita incluir separadores de miles para que no interfieran con la edición
     * ni se confundan con decimales:
     * - 1000.0 -> "1000"
     * - 72.5   -> "72,5"
     * - 1250.75 -> "1250,75"
     */
    fun formatForInput(value: Double): String {
        if (value <= 0.0) return ""
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            val df = DecimalFormat("0.##", spanishSymbols).apply {
                isGroupingUsed = false
                maximumFractionDigits = 4
            }
            df.format(value)
        }
    }

    /**
     * Parsea de manera inteligente un texto numérico escrito por el usuario:
     * - "1.250,50" -> 1250.50 (miles con punto, decimal con coma)
     * - "1250,50"   -> 1250.50 (decimal con coma)
     * - "1.000"     -> 1000.0  (miles hispanos con punto)
     * - "10.000"    -> 10000.0 (miles hispanos con punto)
     * - "72.50"     -> 72.50   (decimal con punto en teclado numérico en inglés)
     * - "6000"      -> 6000.0  (entero)
     */
    fun parseNumber(input: String): Double? {
        val clean = input.trim()
        if (clean.isEmpty()) return null

        return try {
            if (clean.contains(",") && clean.contains(".")) {
                // Ambos presentes: el punto es miles y la coma es decimal (ej. 1.250,50)
                clean.replace(".", "").replace(",", ".").toDoubleOrNull()
            } else if (clean.contains(",")) {
                // Solo coma: es decimal (ej. 1250,50 o 1000,00)
                clean.replace(",", ".").toDoubleOrNull()
            } else if (clean.contains(".")) {
                // Tiene punto pero no coma:
                val parts = clean.split(".")
                if (parts.size == 2 && parts[1].length == 3 && parts[0].isNotEmpty() && parts[0].all { it.isDigit() } && parts[0].length in 1..3) {
                    // Patrón de miles hispano: ej. "1.000" -> 1000.0, "10.000" -> 10000.0
                    clean.replace(".", "").toDoubleOrNull()
                } else if (parts.size > 2) {
                    // Múltiples puntos: ej. "1.000.000" -> 1000000.0
                    clean.replace(".", "").toDoubleOrNull()
                } else {
                    // Punto decimal de teclado en inglés: ej. "72.50", "12.5"
                    clean.toDoubleOrNull()
                }
            } else {
                // Entero simple: ej. "6000"
                clean.toDoubleOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }
}

fun Double.formatAmount(): String = CurrencyFormat.format(this)
fun Double.formatRate(): String = CurrencyFormat.formatRate(this)
