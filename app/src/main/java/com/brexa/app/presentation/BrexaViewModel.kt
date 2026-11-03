package com.brexa.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brexa.app.data.local.entity.*
import com.brexa.app.data.repository.BackupPayload
import com.brexa.app.data.repository.BrexaRepository
import com.brexa.app.data.repository.CategoryDistribution
import com.brexa.app.data.sync.GoogleDriveSyncManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class AnalyticsPeriod {
    WEEK, MONTH
}

data class PendingProjections(
    val total7DaysUsd: Double,
    val count7Days: Int,
    val total15DaysUsd: Double,
    val count15Days: Int,
    val totalMonthUsd: Double,
    val countMonth: Int,
    val totalAllUsd: Double,
    val countAll: Int,
    val overdueCount: Int
)

class BrexaViewModel(
    private val repository: BrexaRepository,
    private val syncManager: GoogleDriveSyncManager? = null
) : ViewModel() {

    private var autoBackupJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.repairInconsistentFixedExpenses()
        }
    }

    /**
     * Sincronización automática silenciosa con Google Drive si la sesión está activa.
     * Consolida operaciones rápidas con debounce de 1.2s y actualiza el timestamp en Room.
     */
    fun triggerAutoBackup() {
        val manager = syncManager ?: return
        autoBackupJob?.cancel()
        autoBackupJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1200)
            val account = manager.getLastSignedInAccount() ?: return@launch
            try {
                val json = exportBackupJson()
                val uploadResult = manager.uploadBackup(account, json)
                uploadResult.onSuccess {
                    repository.setLastBackupTimestamp(System.currentTimeMillis())
                }
            } catch (_: Exception) { }
        }
    }

    val lastBackupTimestamp: StateFlow<Long?> = repository.getLastBackupTimestamp()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setLastBackupTimestamp(timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.setLastBackupTimestamp(timestamp)
        }
    }

    // --- ESTADO REACTIVO ---
    private val _isSyncingOfficialRates = MutableStateFlow(false)
    val isSyncingOfficialRates: StateFlow<Boolean> = _isSyncingOfficialRates.asStateFlow()

    fun syncOfficialRates(
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isSyncingOfficialRates.value) return
        _isSyncingOfficialRates.value = true
        viewModelScope.launch {
            val result = repository.syncOfficialRates()
            _isSyncingOfficialRates.value = false
            result.onSuccess { summary ->
                triggerAutoBackup()
                onSuccess(summary)
            }.onFailure { err ->
                onError(err.message ?: "Error al actualizar tasas oficiales.")
            }
        }
    }

    fun syncSingleOfficialRate(
        currencyCode: String,
        onSuccess: (Double) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.syncSingleOfficialRate(currencyCode)
            result.onSuccess { rate ->
                triggerAutoBackup()
                onSuccess(rate)
            }.onFailure { err ->
                onError(err.message ?: "Error al consultar tasa.")
            }
        }
    }

    val consolidatedUsdBalance: StateFlow<Double> = repository.calculateConsolidatedUsdBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val activeAccounts: StateFlow<List<AccountEntity>> = repository.getActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCurrencies: StateFlow<List<CurrencyEntity>> = repository.getAllCurrencies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val baseCurrency: StateFlow<CurrencyEntity?> = repository.getBaseCurrency()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentTransactions: StateFlow<List<TransactionEntity>> = repository.getRecentTransactions(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<CategoryEntity>> = repository.getActiveCategoriesByType("EXPENSE")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<CategoryEntity>> = repository.getActiveCategoriesByType("INCOME")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incognitoMode: StateFlow<Boolean> = repository.getIncognitoMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- GASTOS / PAGOS PENDIENTES ---
    val allPendingExpenses: StateFlow<List<PendingExpenseEntity>> = repository.getAllPendingExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePendingExpenses: StateFlow<List<PendingExpenseEntity>> = repository.getActivePendingExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paidPendingExpenses: StateFlow<List<PendingExpenseEntity>> = repository.getPaidPendingExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Total de compromisos activos en Moneda Base (USD)
    val totalPendingUsd: StateFlow<Double> = combine(
        activePendingExpenses,
        allCurrencies
    ) { pendingList, currencies ->
        val currMap = currencies.associateBy { it.code.uppercase() }
        pendingList.sumOf { item ->
            val curr = currMap[item.currencyCode.uppercase()]
            val rate = curr?.rateToUsd ?: 1.0
            if (curr?.isBase == true || item.currencyCode.equals("USD", ignoreCase = true)) {
                item.amount
            } else if (rate > 0.0) {
                item.amount / rate
            } else {
                item.amount
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Saldo realmente libre: Patrimonio total menos compromisos pendientes
    val realAvailableBalanceUsd: StateFlow<Double> = combine(
        consolidatedUsdBalance,
        totalPendingUsd
    ) { total, pending ->
        total - pending
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Proyecciones por periodo: Próximos 7 días, Próximos 15 días (quincena), Este mes
    val pendingProjections: StateFlow<PendingProjections> = combine(
        activePendingExpenses,
        allCurrencies
    ) { pendingList, currencies ->
        val currMap = currencies.associateBy { it.code.uppercase() }

        val cal7Days = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis

        val cal15Days = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 15)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis

        val calEndOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis

        val calTodayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun toUsd(item: PendingExpenseEntity): Double {
            val curr = currMap[item.currencyCode.uppercase()]
            val rate = curr?.rateToUsd ?: 1.0
            return if (curr?.isBase == true || item.currencyCode.equals("USD", ignoreCase = true)) {
                item.amount
            } else if (rate > 0.0) {
                item.amount / rate
            } else {
                item.amount
            }
        }

        var sum7 = 0.0
        var count7 = 0
        var sum15 = 0.0
        var count15 = 0
        var sumMonth = 0.0
        var countMonth = 0
        var sumAll = 0.0
        var countAll = 0
        var overdue = 0

        for (item in pendingList) {
            val usd = toUsd(item)
            sumAll += usd
            countAll++

            if (item.dueDateTimestamp < calTodayStart) {
                overdue++
            }

            if (item.dueDateTimestamp <= cal7Days) {
                sum7 += usd
                count7++
            }
            if (item.dueDateTimestamp <= cal15Days) {
                sum15 += usd
                count15++
            }
            if (item.dueDateTimestamp <= calEndOfMonth) {
                sumMonth += usd
                countMonth++
            }
        }

        PendingProjections(
            total7DaysUsd = sum7,
            count7Days = count7,
            total15DaysUsd = sum15,
            count15Days = count15,
            totalMonthUsd = sumMonth,
            countMonth = countMonth,
            totalAllUsd = sumAll,
            countAll = countAll,
            overdueCount = overdue
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PendingProjections(0.0, 0, 0.0, 0, 0.0, 0, 0.0, 0, 0)
    )

    // Filtros de fecha para analítica
    private val _analyticsPeriod = MutableStateFlow(AnalyticsPeriod.MONTH)
    val analyticsPeriod = _analyticsPeriod.asStateFlow()

    private val _analyticsMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val analyticsMonth = _analyticsMonth.asStateFlow()

    private val _analyticsYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val analyticsYear = _analyticsYear.asStateFlow()

    private val _analyticsWeekCalendar = MutableStateFlow(Calendar.getInstance())
    val analyticsWeekCalendar = _analyticsWeekCalendar.asStateFlow()

    private val _analyticsType = MutableStateFlow("EXPENSE") // "EXPENSE" o "INCOME"
    val analyticsType = _analyticsType.asStateFlow()

    private fun calculatePeriodDateRange(
        period: AnalyticsPeriod,
        month: Int,
        year: Int,
        weekCal: Calendar
    ): Pair<Long, Long> {
        return if (period == AnalyticsPeriod.MONTH) {
            val cal = Calendar.getInstance()
            cal.set(year, month, 1, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            Pair(start, end)
        } else {
            // Semana de Lunes a Domingo
            val cal = weekCal.clone() as Calendar
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
            cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            cal.add(Calendar.DAY_OF_MONTH, 6)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            Pair(start, end)
        }
    }

    // Distribución por categoría para el período seleccionado (Semanal o Mensual)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categoryDistribution: StateFlow<List<CategoryDistribution>> = combine(
        _analyticsType,
        _analyticsPeriod,
        _analyticsMonth,
        _analyticsYear,
        _analyticsWeekCalendar
    ) { type, period, month, year, weekCal ->
        val (startDate, endDate) = calculatePeriodDateRange(period, month, year, weekCal)
        repository.getCategoryDistribution(type, startDate, endDate)
    }.flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Movimientos individuales del período seleccionado para ver el desglose detallado
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val analyticsTransactions: StateFlow<List<TransactionEntity>> = combine(
        _analyticsType,
        _analyticsPeriod,
        _analyticsMonth,
        _analyticsYear,
        _analyticsWeekCalendar
    ) { type, period, month, year, weekCal ->
        val (startDate, endDate) = calculatePeriodDateRange(period, month, year, weekCal)
        Pair(type, Pair(startDate, endDate))
    }.flatMapLatest { (type, range) ->
        repository.getTransactionsByTypeAndPeriod(type, range.first, range.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- ACCIONES ---

    fun toggleIncognito() {
        viewModelScope.launch {
            repository.setIncognitoMode(!incognitoMode.value)
        }
    }

    fun setAnalyticsType(type: String) {
        _analyticsType.value = type
    }

    fun setAnalyticsPeriod(period: AnalyticsPeriod) {
        _analyticsPeriod.value = period
    }

    fun previousPeriod() {
        if (_analyticsPeriod.value == AnalyticsPeriod.MONTH) {
            previousMonth()
        } else {
            val cal = _analyticsWeekCalendar.value.clone() as Calendar
            cal.add(Calendar.DAY_OF_MONTH, -7)
            _analyticsWeekCalendar.value = cal
        }
    }

    fun nextPeriod() {
        if (_analyticsPeriod.value == AnalyticsPeriod.MONTH) {
            nextMonth()
        } else {
            val cal = _analyticsWeekCalendar.value.clone() as Calendar
            cal.add(Calendar.DAY_OF_MONTH, 7)
            _analyticsWeekCalendar.value = cal
        }
    }

    fun previousMonth() {
        if (_analyticsMonth.value == 0) {
            _analyticsMonth.value = 11
            _analyticsYear.value -= 1
        } else {
            _analyticsMonth.value -= 1
        }
    }

    fun nextMonth() {
        if (_analyticsMonth.value == 11) {
            _analyticsMonth.value = 0
            _analyticsYear.value += 1
        } else {
            _analyticsMonth.value += 1
        }
    }

    fun registerExpense(
        accountId: String,
        categoryId: String,
        amount: Double,
        rate: Double,
        note: String,
        dateTimestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerExpense(
                accountId = accountId,
                categoryId = categoryId,
                nativeAmount = amount,
                exchangeRate = rate,
                note = note,
                dateTimestamp = dateTimestamp
            )
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al registrar gasto.") }
        }
    }

    fun registerIncome(
        accountId: String,
        categoryId: String,
        amount: Double,
        rate: Double,
        note: String,
        dateTimestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerIncome(
                accountId = accountId,
                categoryId = categoryId,
                nativeAmount = amount,
                exchangeRate = rate,
                note = note,
                dateTimestamp = dateTimestamp
            )
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al registrar ingreso.") }
        }
    }

    fun registerExchange(
        sourceAccountId: String,
        targetAccountId: String,
        sourceAmount: Double,
        targetReceivedAmount: Double,
        note: String,
        dateTimestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerExchange(
                sourceAccountId = sourceAccountId,
                targetAccountId = targetAccountId,
                sourceAmount = sourceAmount,
                targetReceivedAmount = targetReceivedAmount,
                note = note,
                dateTimestamp = dateTimestamp
            )
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al realizar Exchange.") }
        }
    }

    fun deleteTransaction(transactionId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.revertTransaction(transactionId)
            triggerAutoBackup()
            onComplete()
        }
    }

    fun addCustomAccount(
        name: String,
        currencyId: String,
        iconType: String,
        colorHex: String,
        initialBalance: Double,
        includeInTotal: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.addCustomAccount(
                name = name,
                currencyId = currencyId,
                iconType = iconType,
                colorHex = colorHex,
                initialBalance = initialBalance,
                includeInTotal = includeInTotal
            )
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al crear cuenta.") }
        }
    }

    fun adjustAccountBalance(
        accountId: String,
        newBalance: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.adjustAccountBalance(accountId, newBalance)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al ajustar saldo.") }
        }
    }

    fun toggleAdjustmentSign(
        transactionId: String,
        onSuccess: (TransactionEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.toggleAdjustmentSign(transactionId)
            result.onSuccess { updatedTx ->
                triggerAutoBackup()
                onSuccess(updatedTx)
            }.onFailure { onError(it.message ?: "Error al cambiar signo del ajuste.") }
        }
    }

    fun toggleIncludeInTotal(accountId: String, currentVal: Boolean) {
        viewModelScope.launch {
            repository.toggleIncludeInTotal(accountId, !currentVal)
            triggerAutoBackup()
        }
    }

    fun addCustomCurrency(
        code: String,
        name: String,
        symbol: String,
        rateToUsd: Double,
        iconId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.addCustomCurrency(
                code = code,
                name = name,
                symbol = symbol,
                rateToUsd = rateToUsd,
                iconId = iconId
            )
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al crear moneda.") }
        }
    }

    fun updateBaseCurrency(
        code: String,
        name: String,
        symbol: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updateBaseCurrency(code, name, symbol)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al actualizar moneda principal.") }
        }
    }

    fun updateCurrencyRate(
        currencyId: String,
        newRate: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updateCurrencyRate(currencyId, newRate)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al actualizar tasa.") }
        }
    }

    fun deleteCurrency(
        currencyId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteCurrency(currencyId)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al eliminar moneda.") }
        }
    }

    fun updateAccount(
        accountId: String,
        name: String,
        currencyId: String,
        iconType: String,
        colorHex: String = "#D4FF00",
        includeInTotal: Boolean = true,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updateAccount(accountId, name, currencyId, iconType, colorHex, includeInTotal)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al actualizar cuenta.") }
        }
    }

    fun deleteAccount(
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteAccount(accountId)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al eliminar cuenta.") }
        }
    }

    fun addCustomCategory(
        name: String,
        type: String,
        iconId: String,
        colorHex: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.addCustomCategory(name, type, iconId, colorHex)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al crear categoría.") }
        }
    }

    fun deleteCategory(
        categoryId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteCategory(categoryId)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }
                .onFailure { onError(it.message ?: "Error al eliminar categoría.") }
        }
    }

    // --- ACCIONES DE GASTOS / PAGOS PENDIENTES ---
    fun addPendingExpense(
        title: String,
        amount: Double,
        currencyCode: String,
        dueDateTimestamp: Long,
        categoryId: String? = null,
        accountId: String? = null,
        note: String = "",
        expenseType: String = "VARIABLE",
        recurrencePeriod: String? = null,
        recurrenceDay: Int? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.addPendingExpense(
                title = title,
                amount = amount,
                currencyCode = currencyCode,
                dueDateTimestamp = dueDateTimestamp,
                categoryId = categoryId,
                accountId = accountId,
                note = note,
                expenseType = expenseType,
                recurrencePeriod = recurrencePeriod,
                recurrenceDay = recurrenceDay
            )
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }.onFailure { onError(it.message ?: "Error al registrar pago pendiente.") }
        }
    }

    fun updatePendingExpense(
        item: PendingExpenseEntity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.updatePendingExpense(item)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }.onFailure { onError(it.message ?: "Error al actualizar pago pendiente.") }
        }
    }

    fun deletePendingExpense(
        id: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.deletePendingExpense(id)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }.onFailure { onError(it.message ?: "Error al eliminar pago pendiente.") }
        }
    }

    fun markPendingAsPaidWithoutExpense(
        id: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.markPendingAsPaidWithoutExpense(id)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }.onFailure { onError(it.message ?: "Error al marcar como pagado.") }
        }
    }

    fun reopenPendingExpense(
        id: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.reopenPendingExpense(id)
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }.onFailure { onError(it.message ?: "Error al reabrir pago pendiente.") }
        }
    }

    fun payPendingExpense(
        pendingId: String,
        accountId: String,
        exchangeRate: Double,
        overrideNativeAmount: Double? = null,
        note: String = "",
        paymentDateTimestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.payPendingExpense(
                pendingId = pendingId,
                accountId = accountId,
                exchangeRate = exchangeRate,
                overrideNativeAmount = overrideNativeAmount,
                note = note,
                paymentDateTimestamp = paymentDateTimestamp
            )
            result.onSuccess {
                triggerAutoBackup()
                onSuccess()
            }.onFailure { onError(it.message ?: "Error al registrar pago.") }
        }
    }

    // --- RESPALDO MANUAL LOCAL Y EN LA NUBE (.JSON / .BREXA) ---
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val payload = repository.exportAllDataSync()
        Gson().toJson(payload)
    }

    suspend fun restoreBackupFromJson(jsonString: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = Gson().fromJson(jsonString, BackupPayload::class.java)
                ?: return@withContext Result.failure(IllegalArgumentException("El archivo de respaldo está corrupto o vacío."))

            val result = repository.restoreBackup(payload)
            result.onSuccess {
                repository.setLastBackupTimestamp(System.currentTimeMillis())
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class BrexaViewModelFactory(
    private val repository: BrexaRepository,
    private val syncManager: GoogleDriveSyncManager? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrexaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BrexaViewModel(repository, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
