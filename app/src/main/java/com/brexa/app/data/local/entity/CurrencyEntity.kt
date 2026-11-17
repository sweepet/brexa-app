package com.brexa.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // ej. "USD", "VES", "EUR", "USDT"

    @ColumnInfo(name = "code")
    val code: String, // Sigla max 4 caracteres (USD, VES, USDT, etc.)

    @ColumnInfo(name = "name")
    val name: String, // ej. "Dólar Americano", "Bolívar Digital"

    @ColumnInfo(name = "symbol")
    val symbol: String, // ej. "$", "Bs", "€", "₮"

    @ColumnInfo(name = "rate_to_usd")
    val rateToUsd: Double, // Relación psicológica: 1 USD = X {Sigla} (ej. 72.50)

    @ColumnInfo(name = "icon_id")
    val iconId: String, // Icono asociado

    @ColumnInfo(name = "is_base")
    val isBase: Boolean = false, // true solo para USD

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

object OfficialRateBindings {
    const val MANUAL = "MANUAL"
    const val BCV_USD = "OFFICIAL_BCV_USD"
    const val BCV_EUR = "OFFICIAL_BCV_EUR"
    const val USDT = "OFFICIAL_USDT"
    const val BCV_AVG = "OFFICIAL_BCV_AVG"
    const val COP_TRM = "OFFICIAL_COP_TRM"

    val AVAILABLE_BINDINGS = listOf(
        MANUAL to "Ninguna (Tasa manual)",
        BCV_USD to "BCV Dólar (Oficial Venezuela)",
        BCV_EUR to "BCV Euro (Oficial Venezuela)",
        USDT to "Promedio USDT (Binance P2P)",
        BCV_AVG to "Promedio BCV (Punto medio)",
        COP_TRM to "TRM Colombia (Banco República)"
    )
}

fun CurrencyEntity.getOfficialBinding(): String? {
    return when {
        iconId == OfficialRateBindings.BCV_USD -> "BCV_USD"
        iconId == OfficialRateBindings.BCV_EUR -> "BCV_EUR"
        iconId == OfficialRateBindings.USDT -> "USDT"
        iconId == OfficialRateBindings.BCV_AVG -> "BCV_AVG"
        iconId == OfficialRateBindings.COP_TRM -> "COP_TRM"
        iconId != OfficialRateBindings.MANUAL && (code.equals("Bs", true) || code.equals("VES", true)) -> "BCV_USD"
        iconId != OfficialRateBindings.MANUAL && code.equals("COP", true) -> "COP_TRM"
        else -> null
    }
}

fun CurrencyEntity.getOfficialBadgeTitle(): String? {
    return when (getOfficialBinding()) {
        "BCV_USD" -> "OFICIAL BCV 🏛"
        "BCV_EUR" -> "OFICIAL EUR 🏛"
        "USDT" -> "BINANCE P2P ⚡"
        "BCV_AVG" -> "PROMEDIO BCV"
        "COP_TRM" -> "OFICIAL TRM 🏛"
        else -> null
    }
}
