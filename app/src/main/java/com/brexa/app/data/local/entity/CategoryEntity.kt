package com.brexa.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID o "cat_food", "cat_salary", etc.

    @ColumnInfo(name = "name")
    val name: String, // ej. "Alimentación", "Salario Principal"

    @ColumnInfo(name = "type")
    val type: String, // "EXPENSE" | "INCOME"

    @ColumnInfo(name = "icon_id")
    val iconId: String, // Uno de los 6 iconos del sistema

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#D4FF00",

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false, // true para las 10 predeterminadas

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false, // Soft-delete

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
