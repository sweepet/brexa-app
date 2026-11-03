package com.brexa.app.data.repository

import androidx.room.withTransaction
import com.brexa.app.data.local.BrexaDatabase
import com.brexa.app.data.local.entity.*
import com.brexa.app.data.rates.OfficialRatesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.UUID

data class BackupPayload(
    val exportTimestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.14",
    val currencies: List<CurrencyEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val exchangeRateHistory: List<ExchangeRateHistoryEntity> = emptyList(),
    val appSettings: List<AppSettingEntity> = emptyList(),
    val pendingExpenses: List<PendingExpenseEntity> = emptyList()
)

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
    private val pendingExpenseDao = db.pendingExpenseDao()

    // --- MONEDAS ---
    fun getAllCurrencies(): Flow<List<CurrencyEntity>> = currencyDao.getAllCurrencies()
    fun getActiveCurrencies(): Flow<List<CurrencyEntity>> = currencyDao.getActiveCurrencies()
    fun getBaseCurrency(): Flow<CurrencyEntity?> = currencyDao.getBaseCurrency()

    suspend fun updateBaseCurrency(code: String, name: String, symbol: String): Result<Unit> {
        val trimmed = code.trim().take(4)
        val cleanCode = if (trimmed.equals("bs", ignoreCase = true)) "Bs" else trimmed.uppercase()
        if (cleanCode.isEmpty()) {
            return Result.failure(IllegalArgumentException("La sigla de la moneda principal no puede estar vacía."))
        }
        val baseCurr = currencyDao.getBaseCurrency().first()
            ?: return Result.failure(IllegalStateException("Moneda principal no encontrada."))

        currencyDao.updateBaseCurrencyDetails(
            id = baseCurr.id,
            code = cleanCode,
            name = name.trim().ifEmpty { "Moneda Principal" },
            symbol = symbol.trim().ifEmpty { cleanCode }
        )
        return Result.success(Unit)
    }

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
        val trimmed = code.trim().take(4)
        val cleanCode = if (trimmed.equals("bs", ignoreCase = true)) "Bs" else trimmed.uppercase()
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

    suspend fun deleteCurrency(currencyId: String): Result<Unit> {
        val currency = currencyDao.getCurrencyByIdSync(currencyId)
            ?: return Result.failure(IllegalArgumentException("Moneda no encontrada."))
        if (currency.isBase) {
            return Result.failure(IllegalStateException("No se puede eliminar la moneda base ancla."))
        }
        val accountsCount = accountDao.countAccountsByCurrency(currencyId)
        if (accountsCount > 0) {
            return Result.failure(IllegalStateException("No puedes borrar esta moneda porque está asignada a $accountsCount cuenta(s). Modifica o elimina esas cuentas primero."))
        }
        currencyDao.deleteCurrency(currency)
        return Result.success(Unit)
    }

    // --- TASAS OFICIALES AUTOMATIZADAS (BCV & TRM) ---
    private val officialRatesManager = OfficialRatesManager()

    suspend fun syncOfficialRates(): Result<String> {
        val result = officialRatesManager.fetchAllOfficialRates()
        val allCurrencies = currencyDao.getAllCurrenciesSync()
        val updatedList = mutableListOf<String>()

        // 1. Actualizar BCV (Bs / VES) si se obtuvo
        result.bcvRate?.let { bcvRate ->
            val vesCurr = allCurrencies.find { it.code.equals("Bs", ignoreCase = true) || it.code.equals("VES", ignoreCase = true) }
            if (vesCurr != null) {
                updateCurrencyRate(vesCurr.id, bcvRate)
                updatedList.add("BCV: ${String.format(java.util.Locale.US, "%.2f", bcvRate)} Bs")
            }
        }

        // 2. Actualizar TRM (COP) si se obtuvo
        result.copRate?.let { copRate ->
            val copCurr = allCurrencies.find { it.code.equals("COP", ignoreCase = true) }
            if (copCurr != null) {
                updateCurrencyRate(copCurr.id, copRate)
                updatedList.add("TRM: ${String.format(java.util.Locale.US, "%.2f", copRate)} $")
            }
        }

        return if (updatedList.isNotEmpty()) {
            Result.success(updatedList.joinToString(" • "))
        } else if (result.errorMessage != null) {
            Result.failure(Exception(result.errorMessage))
        } else {
            Result.success("Tasas oficiales al día.")
        }
    }

    suspend fun syncSingleOfficialRate(currencyCode: String): Result<Double> {
        return when (currencyCode.uppercase()) {
            "VES", "BCV", "BS" -> {
                val res = officialRatesManager.fetchBcvRate()
                res.mapCatching { (rate, _) ->
                    val allCurrencies = currencyDao.getAllCurrenciesSync()
                    val curr = allCurrencies.find { it.code.equals("Bs", ignoreCase = true) || it.code.equals("VES", ignoreCase = true) }
                    if (curr != null) {
                        updateCurrencyRate(curr.id, rate)
                    }
                    rate
                }
            }
            "COP" -> {
                val res = officialRatesManager.fetchCopTrmRate()
                res.mapCatching { (rate, _) ->
                    val allCurrencies = currencyDao.getAllCurrenciesSync()
                    val curr = allCurrencies.find { it.code.equals("COP", ignoreCase = true) }
                    if (curr != null) {
                        updateCurrencyRate(curr.id, rate)
                    }
                    rate
                }
            }
            else -> Result.failure(IllegalArgumentException("Esta moneda no tiene tasa oficial vinculada."))
        }
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

        val isIncrease = diff >= 0
        val sign = if (isIncrease) "+" else "−"
        val actionText = if (isIncrease) "se sumó al saldo" else "se restó del saldo"

        // Registro de auditoría con indicador explícito de aumento (+) o reducción (-)
        val tx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            type = "BALANCE_ADJUSTMENT",
            accountId = accountId,
            nativeAmount = Math.abs(diff),
            currencyCode = currency?.code ?: "USD",
            exchangeRate = rate,
            usdEquivalent = diffUsd,
            destinationReceivedAmount = if (isIncrease) 1.0 else -1.0,
            note = "$reason ($sign): $actionText (saldo fijado en $newBalance ${currency?.code ?: ""})".trim()
        )
        transactionDao.insertTransaction(tx)
        return Result.success(Unit)
    }

    suspend fun toggleAdjustmentSign(transactionId: String): Result<TransactionEntity> {
        val tx = transactionDao.getTransactionByIdSync(transactionId)
            ?: return Result.failure(IllegalArgumentException("Movimiento no encontrado."))
        if (tx.type != "BALANCE_ADJUSTMENT") {
            return Result.failure(IllegalStateException("Solo se puede cambiar el signo a ajustes de saldo."))
        }

        val currentIsIncrease = if (tx.destinationReceivedAmount != null) {
            tx.destinationReceivedAmount > 0
        } else {
            !tx.note.contains("(-)") && !tx.note.contains("(−)") && !tx.note.contains("rest") && !tx.note.contains("bajó")
        }

        val newIsIncrease = !currentIsIncrease
        val newSign = if (newIsIncrease) "+" else "−"
        val oldSign = if (currentIsIncrease) "+" else "−"

        var updatedNote = tx.note
        if (updatedNote.contains("($oldSign)")) {
            updatedNote = updatedNote.replace("($oldSign)", "($newSign)")
        } else {
            updatedNote = "$updatedNote ($newSign)"
        }

        val updatedTx = tx.copy(
            destinationReceivedAmount = if (newIsIncrease) 1.0 else -1.0,
            note = updatedNote,
            updatedAt = System.currentTimeMillis()
        )
        transactionDao.updateTransaction(updatedTx)
        return Result.success(updatedTx)
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

    suspend fun updateAccount(
        accountId: String,
        name: String,
        currencyId: String,
        iconType: String,
        colorHex: String = "#D4FF00",
        includeInTotal: Boolean = true
    ): Result<Unit> {
        val account = accountDao.getAccountByIdSync(accountId)
            ?: return Result.failure(IllegalArgumentException("Cuenta no encontrada."))
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("El nombre de la cuenta no puede estar vacío."))
        }
        val updated = account.copy(
            name = cleanName,
            currencyId = currencyId,
            iconType = iconType,
            colorHex = colorHex,
            includeInTotal = includeInTotal,
            updatedAt = System.currentTimeMillis()
        )
        accountDao.updateAccount(updated)
        return Result.success(Unit)
    }

    suspend fun deleteAccount(accountId: String): Result<Unit> {
        val account = accountDao.getAccountByIdSync(accountId)
            ?: return Result.failure(IllegalArgumentException("Cuenta no encontrada."))
        if (account.isDefault) {
            return Result.failure(IllegalStateException("No se puede eliminar la cuenta principal predeterminada."))
        }
        // Eliminar transacciones vinculadas a esta cuenta para evitar violaciones de clave foránea
        transactionDao.deleteTransactionsByAccount(accountId)
        accountDao.deleteAccount(account)
        return Result.success(Unit)
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

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        val cat = categoryDao.getCategoryByIdSync(categoryId)
            ?: return Result.failure(IllegalArgumentException("Categoría no encontrada."))
        if (cat.isDefault) {
            return Result.failure(IllegalStateException("No se pueden eliminar categorías base del sistema."))
        }
        categoryDao.deleteCategory(cat)
        return Result.success(Unit)
    }

    // --- TRANSACCIONES CON REVERSIÓN Y FÓRMULAS CONTABLES ---
    fun getAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.getAllTransactions()

    fun getRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>> =
        transactionDao.getRecentTransactions(limit)

    fun getTransactionsByPeriod(startDate: Long, endDate: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByDateRange(startDate, endDate)

    fun getTransactionsByTypeAndPeriod(type: String, startDate: Long, endDate: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByTypeAndDateRange(type, startDate, endDate)

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

        val isSrcDollar = sourceCurrency?.isBase == true || sourceCurrency?.code?.uppercase() in listOf("USD", "USDT", "USDC")
        val isTargetDollar = targetCurrency?.isBase == true || targetCurrency?.code?.uppercase() in listOf("USD", "USDT", "USDC")

        // Tasa real implícita del canje normalizada a estándares de mercado
        val implicitRate = when {
            isSrcDollar && !isTargetDollar -> {
                // Salió dólar/cripto, entró moneda local (ej. 34.700 Bs / 35 USDT = 991,43)
                BigDecimal(targetReceivedAmount).divide(BigDecimal(sourceAmount), 4, RoundingMode.HALF_EVEN).toDouble()
            }
            !isSrcDollar && isTargetDollar -> {
                // Salió moneda local, entró dólar/cripto (ej. 34.700 Bs / 35 USDT = 991,43)
                BigDecimal(sourceAmount).divide(BigDecimal(targetReceivedAmount), 4, RoundingMode.HALF_EVEN).toDouble()
            }
            else -> {
                BigDecimal(targetReceivedAmount).divide(BigDecimal(sourceAmount), 4, RoundingMode.HALF_EVEN).toDouble()
            }
        }

        val rateLabel = when {
            isSrcDollar && !isTargetDollar -> "1 ${sourceCurrency?.code ?: "USD"} = ${String.format(java.util.Locale.US, "%.2f", implicitRate)} ${targetCurrency?.code ?: ""}"
            !isSrcDollar && isTargetDollar -> "1 ${targetCurrency?.code ?: "USD"} = ${String.format(java.util.Locale.US, "%.2f", implicitRate)} ${sourceCurrency?.code ?: ""}"
            else -> "1 ${sourceCurrency?.code ?: ""} = ${String.format(java.util.Locale.US, "%.2f", implicitRate)} ${targetCurrency?.code ?: ""}"
        }

        val finalNote = if (note.trim().isEmpty()) {
            "Tasa: $rateLabel"
        } else {
            note.trim()
        }

        val usdEquiv = if (isSrcDollar) {
            sourceAmount
        } else if (isTargetDollar) {
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
            note = finalNote,
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

    // --- GASTOS / PAGOS PENDIENTES ---
    fun getAllPendingExpenses(): Flow<List<PendingExpenseEntity>> = pendingExpenseDao.getAllPendingExpenses()
    fun getActivePendingExpenses(): Flow<List<PendingExpenseEntity>> = pendingExpenseDao.getActivePendingExpenses()
    fun getPaidPendingExpenses(): Flow<List<PendingExpenseEntity>> = pendingExpenseDao.getPaidPendingExpenses()

    fun calculateNextDueDate(
        currentDueDate: Long,
        recurrencePeriod: String?,
        recurrenceDay: Int?
    ): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = currentDueDate }
        when (recurrencePeriod) {
            "WEEKLY" -> {
                cal.add(Calendar.WEEK_OF_YEAR, 1)
            }
            "BIWEEKLY" -> {
                cal.add(Calendar.DAY_OF_YEAR, 14)
            }
            "MONTHLY" -> {
                cal.add(Calendar.MONTH, 1)
                if (recurrenceDay != null && recurrenceDay in 1..31) {
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, minOf(recurrenceDay, maxDay))
                }
            }
            else -> {
                cal.add(Calendar.MONTH, 1)
            }
        }
        val now = System.currentTimeMillis()
        while (cal.timeInMillis < now) {
            when (recurrencePeriod) {
                "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                "BIWEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 14)
                else -> {
                    cal.add(Calendar.MONTH, 1)
                    if (recurrenceDay != null && recurrenceDay in 1..31) {
                        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        cal.set(Calendar.DAY_OF_MONTH, minOf(recurrenceDay, maxDay))
                    }
                }
            }
        }
        return cal.timeInMillis
    }

    fun calculateInitialDueDate(
        recurrencePeriod: String?,
        recurrenceDay: Int?,
        isCurrentCyclePaid: Boolean = true,
        baseTime: Long = System.currentTimeMillis()
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = baseTime
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)

        when (recurrencePeriod) {
            "WEEKLY" -> {
                cal.add(Calendar.WEEK_OF_YEAR, 1)
            }
            "BIWEEKLY" -> {
                cal.add(Calendar.DAY_OF_YEAR, 14)
            }
            "MONTHLY" -> {
                val day = recurrenceDay ?: 1
                if (day > currentDay) {
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, minOf(day, maxDay))
                } else if (day == currentDay) {
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, minOf(day, maxDay))
                } else {
                    if (isCurrentCyclePaid) {
                        cal.add(Calendar.MONTH, 1)
                        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        cal.set(Calendar.DAY_OF_MONTH, minOf(day, maxDay))
                    } else {
                        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        cal.set(Calendar.DAY_OF_MONTH, minOf(day, maxDay))
                    }
                }
            }
            else -> {
                cal.add(Calendar.MONTH, 1)
            }
        }
        return cal.timeInMillis
    }

    suspend fun repairInconsistentFixedExpenses() {
        try {
            val all = pendingExpenseDao.getAllPendingExpensesSync()
            all.filter { it.expenseType == "FIXED" && it.recurrencePeriod == "MONTHLY" && it.recurrenceDay != null }.forEach { item ->
                val cal = Calendar.getInstance().apply { timeInMillis = item.dueDateTimestamp }
                val targetDay = item.recurrenceDay ?: 1
                if (cal.get(Calendar.DAY_OF_MONTH) != targetDay) {
                    val fixedDueDate = calculateInitialDueDate(
                        recurrencePeriod = item.recurrencePeriod,
                        recurrenceDay = item.recurrenceDay,
                        isCurrentCyclePaid = true
                    )
                    pendingExpenseDao.update(item.copy(dueDateTimestamp = fixedDueDate, updatedAt = System.currentTimeMillis()))
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun addPendingExpense(
        title: String,
        amount: Double,
        currencyCode: String,
        dueDateTimestamp: Long,
        categoryId: String? = null,
        accountId: String? = null,
        note: String = "",
        expenseType: String = "VARIABLE",
        recurrencePeriod: String? = null,
        recurrenceDay: Int? = null
    ): Result<PendingExpenseEntity> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("El concepto del pago no puede estar vacío."))
        }
        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("El monto a pagar debe ser mayor a cero."))
        }

        val item = PendingExpenseEntity(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            amount = amount,
            currencyCode = currencyCode.trim().uppercase(),
            dueDateTimestamp = dueDateTimestamp,
            categoryId = categoryId,
            accountId = accountId,
            note = note.trim(),
            isPaid = false,
            expenseType = expenseType,
            recurrencePeriod = recurrencePeriod,
            recurrenceDay = recurrenceDay,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        pendingExpenseDao.insert(item)
        return Result.success(item)
    }

    suspend fun updatePendingExpense(item: PendingExpenseEntity): Result<Unit> {
        if (item.title.isBlank()) {
            return Result.failure(IllegalArgumentException("El concepto no puede estar vacío."))
        }
        if (item.amount <= 0.0) {
            return Result.failure(IllegalArgumentException("El monto debe ser mayor a cero."))
        }
        pendingExpenseDao.update(item.copy(updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    suspend fun deletePendingExpense(id: String): Result<Unit> {
        pendingExpenseDao.deleteById(id)
        return Result.success(Unit)
    }

    suspend fun markPendingAsPaidWithoutExpense(id: String): Result<Unit> {
        val item = pendingExpenseDao.getPendingExpenseById(id)
            ?: return Result.failure(IllegalArgumentException("Pago pendiente no encontrado."))
        if (item.expenseType == "FIXED") {
            val nextDueDate = calculateNextDueDate(
                currentDueDate = item.dueDateTimestamp,
                recurrencePeriod = item.recurrencePeriod,
                recurrenceDay = item.recurrenceDay
            )
            val updated = item.copy(
                dueDateTimestamp = nextDueDate,
                isPaid = false,
                paidAtTimestamp = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            pendingExpenseDao.update(updated)
        } else {
            // Gasto variable completado: se elimina de la sección de pendientes
            pendingExpenseDao.deleteById(id)
        }
        return Result.success(Unit)
    }

    suspend fun reopenPendingExpense(id: String): Result<Unit> {
        val item = pendingExpenseDao.getPendingExpenseById(id)
            ?: return Result.failure(IllegalArgumentException("Pago pendiente no encontrado."))
        val updated = item.copy(
            isPaid = false,
            paidAtTimestamp = null,
            transactionId = null,
            updatedAt = System.currentTimeMillis()
        )
        pendingExpenseDao.update(updated)
        return Result.success(Unit)
    }

    suspend fun payPendingExpense(
        pendingId: String,
        accountId: String,
        exchangeRate: Double,
        overrideNativeAmount: Double? = null,
        note: String = "",
        paymentDateTimestamp: Long = System.currentTimeMillis()
    ): Result<TransactionEntity> {
        return db.withTransaction {
            val pending = pendingExpenseDao.getPendingExpenseById(pendingId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Pago pendiente no encontrado."))

            val account = accountDao.getAccountByIdSync(accountId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Cuenta de pago no encontrada."))

            val accountCurrency = currencyDao.getCurrencyByIdSync(account.currencyId)

            val nativeAmount = overrideNativeAmount ?: run {
                val accCurrCode = accountCurrency?.code ?: "USD"
                val pendCurrCode = pending.currencyCode
                when {
                    accCurrCode.equals(pendCurrCode, ignoreCase = true) -> pending.amount
                    !accCurrCode.equals("USD", ignoreCase = true) && pendCurrCode.equals("USD", ignoreCase = true) ->
                        pending.amount * exchangeRate
                    accCurrCode.equals("USD", ignoreCase = true) && !pendCurrCode.equals("USD", ignoreCase = true) && exchangeRate > 0.0 ->
                        pending.amount / exchangeRate
                    else -> pending.amount
                }
            }

            val finalCategoryId = pending.categoryId ?: run {
                val expenseCats = categoryDao.getAllCategoriesSync().filter { it.type == "EXPENSE" && it.isDefault }
                expenseCats.firstOrNull()?.id ?: "CAT_EXP_1"
            }

            val finalNote = note.ifBlank {
                if (pending.note.isNotBlank()) "${pending.title}: ${pending.note}" else pending.title
            }

            val txResult = registerExpense(
                accountId = accountId,
                categoryId = finalCategoryId,
                nativeAmount = nativeAmount,
                exchangeRate = exchangeRate,
                note = finalNote,
                dateTimestamp = paymentDateTimestamp
            )

            txResult.fold(
                onSuccess = { tx ->
                    if (pending.expenseType == "FIXED") {
                        val nextDueDate = calculateNextDueDate(
                            currentDueDate = pending.dueDateTimestamp,
                            recurrencePeriod = pending.recurrencePeriod,
                            recurrenceDay = pending.recurrenceDay
                        )
                        val updated = pending.copy(
                            dueDateTimestamp = nextDueDate,
                            isPaid = false,
                            paidAtTimestamp = paymentDateTimestamp,
                            transactionId = tx.id,
                            accountId = accountId,
                            updatedAt = System.currentTimeMillis()
                        )
                        pendingExpenseDao.update(updated)
                    } else {
                        // Gasto variable: se borra de la sección de pendientes
                        // y queda guardado en el historial correspondiente (gastos)
                        pendingExpenseDao.deleteById(pending.id)
                    }
                    Result.success(tx)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        }
    }

    // --- RESPALDO Y RESTAURACIÓN INTEGRAL ---
    suspend fun getAllTransactionsSync(): List<TransactionEntity> {
        return transactionDao.getAllTransactionsSync()
    }

    suspend fun exportAllDataSync(): BackupPayload {
        val currencies = currencyDao.getAllCurrenciesSync()
        val accounts = accountDao.getAllAccountsSync()
        val categories = categoryDao.getAllCategoriesSync()
        val transactions = transactionDao.getAllTransactionsSync()
        val history = exchangeRateHistoryDao.getAllHistorySync()
        val settings = appSettingDao.getAllSettingsSync()
        val pending = pendingExpenseDao.getAllPendingExpensesSync()

        return BackupPayload(
            exportTimestamp = System.currentTimeMillis(),
            currencies = currencies,
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            exchangeRateHistory = history,
            appSettings = settings,
            pendingExpenses = pending
        )
    }

    suspend fun restoreBackup(payload: BackupPayload): Result<Unit> {
        return try {
            db.withTransaction {
                val currencies = payload.currencies ?: emptyList()
                val accounts = payload.accounts ?: emptyList()
                val categories = payload.categories ?: emptyList()
                val transactions = payload.transactions ?: emptyList()
                val history = payload.exchangeRateHistory ?: emptyList()
                val settings = payload.appSettings ?: emptyList()
                val pending = payload.pendingExpenses ?: emptyList()

                // Si el respaldo contiene datos mínimos, limpiamos e insertamos en orden de dependencias
                if (currencies.isNotEmpty() || accounts.isNotEmpty() || categories.isNotEmpty() || transactions.isNotEmpty() || pending.isNotEmpty()) {
                    pendingExpenseDao.deleteAllPendingExpensesSync()
                    transactionDao.deleteAllTransactionsSync()
                    exchangeRateHistoryDao.deleteAllHistorySync()
                    accountDao.deleteAllAccountsSync()
                    categoryDao.deleteAllCategoriesSync()
                    currencyDao.deleteAllCurrenciesSync()

                    if (currencies.isNotEmpty()) currencyDao.insertAll(currencies)
                    if (categories.isNotEmpty()) categoryDao.insertAll(categories)
                    if (accounts.isNotEmpty()) accountDao.insertAll(accounts)
                    if (transactions.isNotEmpty()) transactionDao.insertAll(transactions)
                    if (pending.isNotEmpty()) pendingExpenseDao.insertAll(pending)
                    if (history.isNotEmpty()) exchangeRateHistoryDao.insertAll(history)
                    if (settings.isNotEmpty()) appSettingDao.insertAll(settings)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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

    fun getLastBackupTimestamp(): Flow<Long?> = appSettingDao.getSetting("last_backup_timestamp").map { str ->
        str?.toLongOrNull()
    }

    suspend fun setLastBackupTimestamp(timestamp: Long = System.currentTimeMillis()) {
        appSettingDao.setSetting(AppSettingEntity("last_backup_timestamp", timestamp.toString()))
    }

    suspend fun getSetting(key: String): String? = appSettingDao.getSettingSync(key)

    suspend fun setSetting(key: String, value: String) {
        appSettingDao.setSetting(AppSettingEntity(key, value))
    }
}
