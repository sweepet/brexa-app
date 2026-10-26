package com.brexa.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["destination_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["destination_account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["date_timestamp"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID

    @ColumnInfo(name = "type")
    val type: String, // "EXPENSE" | "INCOME" | "EXCHANGE" | "BALANCE_ADJUSTMENT"

    @ColumnInfo(name = "account_id")
    val accountId: String, // Cuenta origen / pagadora / única

    @ColumnInfo(name = "destination_account_id")
    val destinationAccountId: String? = null, // Solo para EXCHANGE

    @ColumnInfo(name = "category_id")
    val categoryId: String? = null, // Solo para Gasto e Ingreso (null en Exchange)

    @ColumnInfo(name = "native_amount")
    val nativeAmount: Double, // Monto en la moneda nativa de la cuenta (ej. 3.500 VES)

    @ColumnInfo(name = "currency_code")
    val currencyCode: String, // Sigla snapshot histórica (ej. "VES", "USD")

    @ColumnInfo(name = "exchange_rate")
    val exchangeRate: Double, // Tasa aplicada en ese instante (ej. 72.50)

    @ColumnInfo(name = "usd_equivalent")
    val usdEquivalent: Double, // Costo histórico inmutable en USD (congelado)

    @ColumnInfo(name = "destination_received_amount")
    val destinationReceivedAmount: Double? = null, // Monto acreditado (solo EXCHANGE)

    @ColumnInfo(name = "implicit_exchange_rate")
    val implicitExchangeRate: Double? = null, // Tasa real deducida del canje

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "date_timestamp")
    val dateTimestamp: Long = System.currentTimeMillis(), // Fecha contable de la operación

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
