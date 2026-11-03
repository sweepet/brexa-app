package com.brexa.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["due_date_timestamp"]),
        Index(value = ["is_paid"]),
        Index(value = ["category_id"]),
        Index(value = ["account_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["expense_type"])
    ]
)
data class PendingExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    @ColumnInfo(name = "due_date_timestamp")
    val dueDateTimestamp: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,

    @ColumnInfo(name = "account_id")
    val accountId: String? = null,

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "is_paid")
    val isPaid: Boolean = false,

    @ColumnInfo(name = "paid_at_timestamp")
    val paidAtTimestamp: Long? = null,

    @ColumnInfo(name = "transaction_id")
    val transactionId: String? = null,

    @ColumnInfo(name = "expense_type")
    val expenseType: String = "VARIABLE", // "VARIABLE" (puntual) o "FIXED" (recurrente)

    @ColumnInfo(name = "recurrence_period")
    val recurrencePeriod: String? = null, // "WEEKLY", "BIWEEKLY", "MONTHLY"

    @ColumnInfo(name = "recurrence_day")
    val recurrenceDay: Int? = null, // Día del mes para fijos (ej. 1 para todos los 1)

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
