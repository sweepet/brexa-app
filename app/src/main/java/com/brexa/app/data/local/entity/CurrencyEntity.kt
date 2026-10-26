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
