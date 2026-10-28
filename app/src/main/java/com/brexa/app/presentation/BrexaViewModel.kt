package com.brexa.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brexa.app.data.local.entity.*
import com.brexa.app.data.repository.BrexaRepository
import com.brexa.app.data.repository.CategoryDistribution
import com.brexa.app.data.sync.GoogleDriveSyncManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class BackupPayload(
    val exportTimestamp: Long,
    val appVersion: String = "1.0.0",
    val currencies: List<CurrencyEntity>,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>
)

enum class AnalyticsPeriod {
    WEEK, MONTH
}

class BrexaViewModel(
    private val repository: BrexaRepository,
    private val syncManager: GoogleDriveSyncManager? = null
) : ViewModel() {

    /**
     * Sincronización automática silenciosa con Google Drive si la sesión está activa
     */
    fun triggerAutoBackup() {
        val manager = syncManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val account = manager.getLastSignedInAccount() ?: return@launch
            try {
                val json = exportBackupJson()
                manager.uploadBackup(account, json)
            } catch (_: Exception) { }
        }
    }

    // --- ESTADO REACTIVO ---
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

    val expenseCategories: StateFlow<List<CategoryEntity>> = repository.getActiveCategoriesByType("EXPENSE")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<CategoryEntity>> = repository.getActiveCategoriesByType("INCOME")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incognitoMode: StateFlow<Boolean> = repository.getIncognitoMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerExpense(
                accountId = accountId,
                categoryId = categoryId,
                nativeAmount = amount,
                exchangeRate = rate,
                note = note
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
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerIncome(
                accountId = accountId,
                categoryId = categoryId,
                nativeAmount = amount,
                exchangeRate = rate,
                note = note
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
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerExchange(
                sourceAccountId = sourceAccountId,
                targetAccountId = targetAccountId,
                sourceAmount = sourceAmount,
                targetReceivedAmount = targetReceivedAmount,
                note = note
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

    // --- RESPALDO MANUAL LOCAL Y EN LA NUBE (.JSON / .BREXA) ---
    suspend fun exportBackupJson(): String {
        val currencies = allCurrencies.value
        val accounts = activeAccounts.value
        val categories = expenseCategories.value + incomeCategories.value
        val transactions = repository.getAllTransactionsSync()

        val payload = BackupPayload(
            exportTimestamp = System.currentTimeMillis(),
            currencies = currencies,
            accounts = accounts,
            categories = categories,
            transactions = transactions
        )
        return Gson().toJson(payload)
    }

    suspend fun restoreBackupFromJson(jsonString: String): Result<Unit> {
        return try {
            val payload = Gson().fromJson(jsonString, BackupPayload::class.java)
                ?: return Result.failure(IllegalArgumentException("El archivo de respaldo está corrupto o vacío."))

            repository.restoreBackup(
                currencies = payload.currencies ?: emptyList(),
                accounts = payload.accounts ?: emptyList(),
                categories = payload.categories ?: emptyList(),
                transactions = payload.transactions ?: emptyList()
            )
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
