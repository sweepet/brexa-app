package com.brexa.app.domain.model

enum class TransactionType(val label: String) {
    EXPENSE("GASTO"),
    INCOME("INGRESO"),
    EXCHANGE("EXCHANGE"),
    BALANCE_ADJUSTMENT("AJUSTE")
}

enum class AccountIconType(val iconResName: String, val label: String) {
    CASH("ic_account_cash", "EFECTIVO"),
    BANK("ic_account_bank", "BANCO"),
    CRYPTO("ic_account_crypto", "CRIPTO"),
    GENERAL("ic_account_general", "GENERAL")
}

data class Currency(
    val id: String,
    val code: String,
    val name: String,
    val symbol: String,
    val rateToUsd: Double,
    val iconId: String,
    val isBase: Boolean,
    val isActive: Boolean,
    val sortOrder: Int
)

data class Account(
    val id: String,
    val name: String,
    val currencyId: String,
    val iconType: AccountIconType,
    val colorHex: String,
    val initialBalance: Double,
    val currentBalance: Double,
    val includeInTotal: Boolean,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int
)

data class Category(
    val id: String,
    val name: String,
    val type: String, // "EXPENSE" | "INCOME"
    val iconId: String,
    val colorHex: String,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int
)

data class TransactionItem(
    val id: String,
    val type: TransactionType,
    val accountId: String,
    val accountName: String,
    val destinationAccountId: String? = null,
    val destinationAccountName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categoryIconId: String? = null,
    val nativeAmount: Double,
    val currencyCode: String,
    val exchangeRate: Double,
    val usdEquivalent: Double,
    val destinationReceivedAmount: Double? = null,
    val implicitExchangeRate: Double? = null,
    val note: String = "",
    val dateTimestamp: Long = System.currentTimeMillis()
)
