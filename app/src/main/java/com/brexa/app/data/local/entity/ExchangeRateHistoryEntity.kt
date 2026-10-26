package com.brexa.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exchange_rate_history",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["currency_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["currency_id"]),
        Index(value = ["recorded_at"])
    ]
)
data class ExchangeRateHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID

    @ColumnInfo(name = "currency_id")
    val currencyId: String,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String, // ej. "VES", "EUR"

    @ColumnInfo(name = "rate")
    val rate: Double, // Tasa en ese momento

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis()
)
