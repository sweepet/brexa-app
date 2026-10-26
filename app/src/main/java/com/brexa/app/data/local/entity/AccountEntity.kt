package com.brexa.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["currency_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["currency_id"])
    ]
)
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID o "acc_cash_usd"

    @ColumnInfo(name = "name")
    val name: String, // ej. "Dólares Efectivo", "Banesco Pago Móvil", "Binance USDT"

    @ColumnInfo(name = "currency_id")
    val currencyId: String, // FK a CurrencyEntity

    @ColumnInfo(name = "icon_type")
    val iconType: String, // "CASH" | "BANK" | "CRYPTO" | "GENERAL" (4 exactos)

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#D4FF00", // Acento neobrutalista

    @ColumnInfo(name = "initial_balance")
    val initialBalance: Double = 0.0,

    @ColumnInfo(name = "current_balance")
    val currentBalance: Double = 0.0,

    @ColumnInfo(name = "include_in_total")
    val includeInTotal: Boolean = true, // Toggle de visibilidad en saldo consolidado

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false, // true para "Dólares Efectivo"

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false, // Soft-delete contable: oculta sin corromper reportes

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
