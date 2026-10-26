package com.brexa.app.data.repository

import com.brexa.app.data.local.BrexaDatabase
import com.brexa.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class CategoryDistribution(
    val categoryId: String?,
    val categoryName: String,
    val categoryIconId: String,
    val colorHex: String,
    val totalUsd: Double,
    val percentage: Double
)

class BrexaRepository(private val db: BrexaDatabase) {

    private val currencyDao = db.currencyDao()
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val exchangeRateHistoryDao = db.exchangeRateHistoryDao()
    private val appSettingDao = db.appSettingDao()

    // --- MONEDAS ---
    fun getAllCurrencies(): Flow<List<CurrencyEntity>> = currencyDao.getAllCurrencies()
    fun getActiveCurrencies(): Flow<List<CurrencyEntity>> = currencyDao.getActiveCurrencies()

    suspend fun addCustomCurrency(
        code: String,
        name: String,
        symbol: String,
        rateToUsd: Double,
        iconId: String = "ic_account_bank"
    ): Result<CurrencyEntity> {
        val currentCount = currencyDao.getCustomCurrenciesCount()
        if (currentCount >= 10) {
            return Result.failure(IllegalStateException("Límite alcanzado: Máximo 10 monedas personalizadas."))
        }
        val cleanCode = code.trim().uppercase().take(4)
        if (cleanCode.isEmpty()) {
            return Result.failure(IllegalArgumentException("La sigla de la moneda no puede estar vacía."))
        }
        val currency = CurrencyEntity(
            id = "CUR_${cleanCode}_${System.currentTimeMillis() % 1000}",
            code = cleanCode,
            name = name.trim(),
            symbol = symbol.trim().ifEmpty { cleanCode },
            rateToUsd = if (rateToUsd <= 0.0) 1.0 else rateToUsd,
            iconId = iconId,
            isBase = false,
            isActive = true,
            sortOrder = currentCount + 1
        )
        currencyDao.insertCurrency(currency)
        // Guardar en historial de tasas
        exchangeRateHistoryDao.insertRateRecord(
            ExchangeRateHistoryEntity(
                id = UUID.randomUUID().toString(),
                currencyId = currency.id,
                currencyCode = currency.code,
                rate = currency.rateToUsd
            )
        )
        return Result.success(currency)
    }

    suspend fun updateCurrencyRate(currencyId: String, newRate: Double): Result<Unit> {
        if (newRate <= 0.0) return Result.failure(IllegalArgumentException("La tasa debe ser mayor a 0."))
        val currency = currencyDao.getCurrencyByIdSync(currencyId)
            ?: return Result.failure(IllegalArgumentException("Moneda no encontrada."))

        currencyDao.updateExchangeRate(currencyId, newRate)
        exchangeRateHistoryDao.insertRateRecord(
            ExchangeRateHistoryEntity(
                id = UUID.randomUUID().toString(),
                currencyId = currencyId,
                currencyCode = currency.code,
                rate = newRate
            )
        )
        return Result.success(Unit)
    }

    // --- CUENTAS ---
    fun getActiveAccounts(): Flow<List<AccountEntity>> = accountDao.getActiveAccounts()
    fun getAllAccounts(): Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    suspend fun addCustomAccount(
        name: String,
        currencyId: String,
        iconType: String,
        colorHex: String = "#D4FF00",
        initialBalance: Double = 0.0,
        includeInTotal: Boolean = true
    ): Result<AccountEntity> {
        val currentCount = accountDao.getCustomAccountsCount()
        if (currentCount >= 10) {
            return Result.failure(IllegalStateException("Límite alcanzado: Máximo 10 cuentas personalizadas."))
        }
        val account = AccountEntity(
            id = "ACC_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            currencyId = currencyId,
            iconType = iconType,
            colorHex = colorHex,
            initialBalance = initialBalance,
            currentBalance = initialBalance,
            includeInTotal = includeInTotal,
            isDefault = false,
            isArchived = false,
            sortOrder = currentCount + 1
        )
        accountDao.insertAccount(account)
        return Result.success(account)
    }

    suspend fun adjustAccountBalance(accountId: String, newBalance: Double, reason: String = "Ajuste manual"): Result<Unit> {
        val account = accountDao.getAccountByIdSync(accountId)
            ?: return Result.failure(IllegalArgumentException("Cuenta no encontrada."))
        val diff = newBalance - account.currentBalance
        accountDao.updateBalance(accountId, newBalance)

        val currency = currencyDao.getCurrencyByIdSync(account.currencyId)
        val rate = currency?.rateToUsd ?: 1.0
        val diffUsd = BigDecimal(Math.abs(diff))
            .divide(BigDecimal(rate), 2, RoundingMode.HALF_EVEN)
            .toDouble()

        // Registro de auditoría
        val tx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            type = "BALANCE_ADJUSTMENT",
            accountId = accountId,
            nativeAmount = Math.abs(diff),
            currencyCode = currency?.code ?: "USD",
            exchangeRate = rate,
            usdEquivalent = diffUsd,
            note = "$reason: saldo ajustado a $newBalance"
        )
        transactionDao.insertTransaction(tx)
        return Result.success(Unit)
    }

    suspend fun archiveAccount(accountId: String): Result<Unit> {
        val account = accountDao.getAccountByIdSync(accountId)
            ?: return Result.failure(IllegalArgumentException("Cuenta no encontrada."))
        if (account.isDefault) {
            return Result.failure(IllegalStateException("No se puede archivar la cuenta predeterminada."))
        }
        accountDao.setArchived(accountId, true)
        return Result.success(Unit)
    }

    suspend fun toggleIncludeInTotal(accountId: String, include: Boolean) {
        accountDao.setIncludeInTotal(accountId, include)
    }

    // --- CATEGORÍAS ---
    fun getActiveCategoriesByType(type: String): Flow<List<CategoryEntity>> =
        categoryDao.getActiveCategoriesByType(type)

    suspend fun addCustomCategory(
        name: String,
        type: String, // "EXPENSE" | "INCOME"
        iconId: String = "ic_cat_general",
        colorHex: String = "#D4FF00"
    ): Result<CategoryEntity> {
        val count = categoryDao.getCustomCategoriesCountByType(type)
        if (count >= 10) {
            return Result.failure(IllegalStateException("Límite alcanzado: Máximo 10 categorías personalizadas de $type."))
        }
        val category = CategoryEntity(
            id = "CAT_${type.take(3)}_${UUID.randomUUID().toString().take(6)}",
            name = name.trim(),
            type = type,
            iconId = iconId,
            colorHex = colorHex,
            isDefault = false,
            sortOrder = count + 5
        )
        categoryDao.insertCategory(category)
        return Result.success(category)
    }

    suspend fun archiveCategory(categoryId: String): Result<Unit> {
        val cat = categoryDao.getCategoryByIdSync(categoryId)
            ?: return Result.failure(IllegalArgumentException("Categoría no encontrada."))
        if (cat.isDefault) {
            return Result.failure(IllegalStateException("No se pueden archivar categorías predeterminadas."))
        }
        categoryDao.setArchived(categoryId, true)
        return Result.success(Unit)
    }

    // --- TRANSACCIONES CON REVERSIÓN Y FÓRMULAS CONTABLES ---
    fun getRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>> =
        transactionDao.getRecentTransactions(limit)

    fun getTransactionsByPeriod(startDate: Long, endDate: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByDateRange(startDate, endDate)

    suspend fun registerExpense(
        accountId: String,
        categoryId: String,
        nativeAmount: Double,
        exchangeRate: Double,
        note: String = "",
        dateTimestamp: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> {
        if (nativeAmount <= 0.0) return Result.failure(IllegalArgumentException("El monto debe ser mayor a cero."))
        if (exchangeRate <= 0.0) return Result.failure(IllegalArgumentException("La tasa debe ser mayor a cero."))

        val account = accountDao.getAccountByIdSync(accountId)
            ?: return Result.failure(IllegalArgumentException("Cuenta de origen no encontrada."))
        val currency = currencyDao.getCurrencyByIdSync(account.currencyId)

        // Fórmula de Oro: Monto_USD = Monto_Nativo / Tasa
        val usdEquiv = BigDecimal(nativeAmount)
            .divide(BigDecimal(exchangeRate), 2, RoundingMode.HALF_EVEN)
            .toDouble()

        val tx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            type = "EXPENSE",
            accountId = accountId,
            categoryId = categoryId,
            nativeAmount = nativeAmount,
            currencyCode = currency?.code ?: "USD",
            exchangeRate = exchangeRate,
            usdEquivalent = usdEquiv,
            note = note.trim(),
            dateTimestamp = dateTimestamp
        )

        // Débito atómico
        val updatedBalance = account.currentBalance - nativeAmount
        accountDao.updateBalance(accountId, updatedBalance)
        transactionDao.insertTransaction(tx)

        return Result.success(tx)
    }

    suspend fun registerIncome(
        accountId: String,
        categoryId: String,
        nativeAmount: Double,
        exchangeRate: Double,
        note: String = "",
        dateTimestamp: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> {
        if (nativeAmount <= 0.0) return Result.failure(IllegalArgumentException("El monto debe ser mayor a cero."))
        if (exchangeRate <= 0.0) return Result.failure(IllegalArgumentException("La tasa debe ser mayor a cero."))

        val account = accountDao.getAccountByIdSync(accountId)
            ?: return Result.failure(IllegalArgumentException("Cuenta de destino no encontrada."))
        val currency = currencyDao.getCurrencyByIdSync(account.currencyId)

        val usdEquiv = BigDecimal(nativeAmount)
            .divide(BigDecimal(exchangeRate), 2, RoundingMode.HALF_EVEN)
            .toDouble()

        val tx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            type = "INCOME",
            accountId = accountId,
            categoryId = categoryId,
            nativeAmount = nativeAmount,
            currencyCode = currency?.code ?: "USD",
            exchangeRate = exchangeRate,
            usdEquivalent = usdEquiv,
            note = note.trim(),
            dateTimestamp = dateTimestamp
        )

        // Crédito atómico
        val updatedBalance = account.currentBalance + nativeAmount
        accountDao.updateBalance(accountId, updatedBalance)
        transactionDao.insertTransaction(tx)

        return Result.success(tx)
    }

    suspend fun registerExchange(
        sourceAccountId: String,
        targetAccountId: String,
        sourceAmount: Double,
        targetReceivedAmount: Double,
        note: String = "",
        dateTimestamp: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> {
        if (sourceAccountId == targetAccountId) {
            return Result.failure(IllegalArgumentException("La cuenta de origen y destino deben ser distintas."))
        }
        if (sourceAmount <= 0.0 || targetReceivedAmount <= 0.0) {
            return Result.failure(IllegalArgumentException("Los montos de envío y recepción deben ser mayores a cero."))
        }

        val sourceAccount = accountDao.getAccountByIdSync(sourceAccountId)
            ?: return Result.failure(IllegalArgumentException("Cuenta de origen no encontrada."))
        val targetAccount = accountDao.getAccountByIdSync(targetAccountId)
            ?: return Result.failure(IllegalArgumentException("Cuenta de destino no encontrada."))

        val sourceCurrency = currencyDao.getCurrencyByIdSync(sourceAccount.currencyId)
        val targetCurrency = currencyDao.getCurrencyByIdSync(targetAccount.currencyId)

        // Tasa real implícita del canje
        val implicitRate = if (sourceCurrency?.isBase == true) {
            // Salió USD, entraron VES -> Tasa = VES / USD
            BigDecimal(targetReceivedAmount).divide(BigDecimal(sourceAmount), 4, RoundingMode.HALF_EVEN).toDouble()
        } else if (targetCurrency?.isBase == true) {
            // Salieron VES, entraron USD -> Tasa = VES / USD
            BigDecimal(sourceAmount).divide(BigDecimal(targetReceivedAmount), 4, RoundingMode.HALF_EVEN).toDouble()
        } else {
            BigDecimal(targetReceivedAmount).divide(BigDecimal(sourceAmount), 4, RoundingMode.HALF_EVEN).toDouble()
        }

        val usdEquiv = if (sourceCurrency?.isBase == true) {
            sourceAmount
        } else if (targetCurrency?.isBase == true) {
            targetReceivedAmount
        } else {
            val srcRate = sourceCurrency?.rateToUsd ?: 1.0
            BigDecimal(sourceAmount).divide(BigDecimal(srcRate), 2, RoundingMode.HALF_EVEN).toDouble()
        }

        val tx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            type = "EXCHANGE",
            accountId = sourceAccountId,
            destinationAccountId = targetAccountId,
            nativeAmount = sourceAmount,
            currencyCode = sourceCurrency?.code ?: "USD",
            exchangeRate = implicitRate,
            usdEquivalent = usdEquiv,
            destinationReceivedAmount = targetReceivedAmount,
            implicitExchangeRate = implicitRate,
            note = note.trim(),
            dateTimestamp = dateTimestamp
        )

        // Movimiento atómico de fondos entre cuentas
        accountDao.updateBalance(sourceAccountId, sourceAccount.currentBalance - sourceAmount)
        accountDao.updateBalance(targetAccountId, targetAccount.currentBalance + targetReceivedAmount)
        transactionDao.insertTransaction(tx)

        return Result.success(tx)
    }

    suspend fun revertTransaction(transactionId: String): Result<Unit> {
        val tx = transactionDao.getTransactionByIdSync(transactionId)
            ?: return Result.failure(IllegalArgumentException("Transacción no encontrada."))

        when (tx.type) {
            "EXPENSE" -> {
                // Reintegrar dinero a la cuenta origen
                val account = accountDao.getAccountByIdSync(tx.accountId)
                if (account != null) {
                    accountDao.updateBalance(tx.accountId, account.currentBalance + tx.nativeAmount)
                }
            }
            "INCOME" -> {
                // Restar dinero que había ingresado
                val account = accountDao.getAccountByIdSync(tx.accountId)
                if (account != null) {
                    accountDao.updateBalance(tx.accountId, account.currentBalance - tx.nativeAmount)
                }
            }
            "EXCHANGE" -> {
                // Devolver origen y retirar de destino
                val source = accountDao.getAccountByIdSync(tx.accountId)
                if (source != null) {
                    accountDao.updateBalance(tx.accountId, source.currentBalance + tx.nativeAmount)
                }
                if (tx.destinationAccountId != null && tx.destinationReceivedAmount != null) {
                    val dest = accountDao.getAccountByIdSync(tx.destinationAccountId)
                    if (dest != null) {
                        accountDao.updateBalance(tx.destinationAccountId, dest.currentBalance - tx.destinationReceivedAmount)
                    }
                }
            }
            "BALANCE_ADJUSTMENT" -> {
                // Para ajuste no revertimos saldo a ciegas para evitar descalibre
            }
        }

        transactionDao.deleteTransaction(tx)
        return Result.success(Unit)
    }

    // --- CÁLCULOS ANALÍTICOS Y DASHBOARD ---
    fun calculateConsolidatedUsdBalance(): Flow<Double> {
        return combine(
            accountDao.getIncludedInTotalAccounts(),
            currencyDao.getAllCurrencies()
        ) { accounts, currencies ->
            val currencyMap = currencies.associateBy { it.id }
            var totalUsd = BigDecimal.ZERO

            for (acc in accounts) {
                val curr = currencyMap[acc.currencyId]
                val rate = curr?.rateToUsd ?: 1.0
                if (rate > 0.0) {
                    val converted = BigDecimal(acc.currentBalance).divide(BigDecimal(rate), 2, RoundingMode.HALF_EVEN)
                    totalUsd = totalUsd.add(converted)
                }
            }
            totalUsd.toDouble()
        }
    }

    fun getCategoryDistribution(
        type: String, // "EXPENSE" o "INCOME"
        startDate: Long,
        endDate: Long
    ): Flow<List<CategoryDistribution>> {
        return combine(
            transactionDao.getCategoryTotals(type, startDate, endDate),
            categoryDao.getAllActiveCategories(),
            transactionDao.getTotalUsdByTypeAndDateRange(type, startDate, endDate)
        ) { totals, categories, grandTotal ->
            val catMap = categories.associateBy { it.id }
            val totalAll = if (grandTotal <= 0.0) 1.0 else grandTotal

            totals.map { item ->
                val cat = item.categoryId?.let { catMap[it] }
                val pct = BigDecimal(item.totalUsd * 100.0)
                    .divide(BigDecimal(totalAll), 1, RoundingMode.HALF_EVEN)
                    .toDouble()

                CategoryDistribution(
                    categoryId = item.categoryId,
                    categoryName = cat?.name ?: "Sin Categoría / General",
                    categoryIconId = cat?.iconId ?: "ic_cat_general",
                    colorHex = cat?.colorHex ?: "#D4FF00",
                    totalUsd = item.totalUsd,
                    percentage = pct
                )
            }
        }
    }

    // --- AJUSTES Y PRIVACIDAD ---
    fun getIncognitoMode(): Flow<Boolean> = combine(
        appSettingDao.getSetting("incognito_mode")
    ) { arrayOfSetting ->
        arrayOfSetting[0] == "true"
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        appSettingDao.setSetting(AppSettingEntity("incognito_mode", enabled.toString()))
    }
}
