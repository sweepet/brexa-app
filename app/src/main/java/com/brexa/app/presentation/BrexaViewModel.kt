package com.brexa.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brexa.app.data.local.entity.*
import com.brexa.app.data.repository.BrexaRepository
import com.brexa.app.data.repository.CategoryDistribution
import com.google.gson.Gson
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

class BrexaViewModel(private val repository: BrexaRepository) : ViewModel() {

    // --- ESTADO REACTIVO ---
    val consolidatedUsdBalance: StateFlow<Double> = repository.calculateConsolidatedUsdBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val activeAccounts: StateFlow<List<AccountEntity>> = repository.getActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCurrencies: StateFlow<List<CurrencyEntity>> = repository.getAllCurrencies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransactions: StateFlow<List<TransactionEntity>> = repository.getRecentTransactions(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<CategoryEntity>> = repository.getActiveCategoriesByType("EXPENSE")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<CategoryEntity>> = repository.getActiveCategoriesByType("INCOME")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incognitoMode: StateFlow<Boolean> = repository.getIncognitoMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Filtros de fecha para analítica
    private val _analyticsMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val analyticsMonth = _analyticsMonth.asStateFlow()

    private val _analyticsYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val analyticsYear = _analyticsYear.asStateFlow()

    private val _analyticsType = MutableStateFlow("EXPENSE") // "EXPENSE" o "INCOME"
    val analyticsType = _analyticsType.asStateFlow()

    // Distribución por categoría para el período seleccionado
    val categoryDistribution: StateFlow<List<CategoryDistribution>> = combine(
        _analyticsType,
        _analyticsMonth,
        _analyticsYear
    ) { type, month, year ->
        val cal = Calendar.getInstance()
        cal.set(year, month, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startDate = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endDate = cal.timeInMillis

        repository.getCategoryDistribution(type, startDate, endDate)
    }.flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- ACCIONES ---

    fun toggleIncognito() {
        viewModelScope.launch {
            repository.setIncognitoMode(!incognitoMode.value)
        }
    }

    fun setAnalyticsType(type: String) {
        _analyticsType.value = type
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
            result.onSuccess { onSuccess() }
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
            result.onSuccess { onSuccess() }
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
            result.onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Error al realizar Exchange.") }
        }
    }

    fun deleteTransaction(transactionId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.revertTransaction(transactionId)
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
            result.onSuccess { onSuccess() }
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
            result.onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Error al ajustar saldo.") }
        }
    }

    fun toggleIncludeInTotal(accountId: String, currentVal: Boolean) {
        viewModelScope.launch {
            repository.toggleIncludeInTotal(accountId, !currentVal)
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
            result.onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Error al crear moneda.") }
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
            result.onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Error al actualizar tasa.") }
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
            result.onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Error al crear categoría.") }
        }
    }

    // --- RESPALDO MANUAL LOCAL (.JSON / .BREXA) ---
    suspend fun exportBackupJson(): String {
        val currencies = allCurrencies.value
        val accounts = activeAccounts.value
        val categories = expenseCategories.value + incomeCategories.value
        val transactions = recentTransactions.value // o todas

        val payload = BackupPayload(
            exportTimestamp = System.currentTimeMillis(),
            currencies = currencies,
            accounts = accounts,
            categories = categories,
            transactions = transactions
        )
        return Gson().toJson(payload)
    }
}

class BrexaViewModelFactory(private val repository: BrexaRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrexaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BrexaViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
