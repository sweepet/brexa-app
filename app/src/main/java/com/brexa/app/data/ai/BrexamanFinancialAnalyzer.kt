package com.brexa.app.data.ai

import com.brexa.app.data.local.entity.AccountEntity
import com.brexa.app.data.local.entity.CategoryEntity
import com.brexa.app.data.local.entity.CurrencyEntity
import com.brexa.app.data.local.entity.PendingExpenseEntity
import com.brexa.app.data.local.entity.TransactionEntity
import com.brexa.app.presentation.MonthlyBudgetInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object BrexamanFinancialAnalyzer {

    fun generateSnapshot(
        accounts: List<AccountEntity>,
        currencies: List<CurrencyEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        pendingExpenses: List<PendingExpenseEntity>,
        budgetInfo: MonthlyBudgetInfo? = null
    ): BrexamanFinancialSnapshot {
        val currMap = currencies.associateBy { it.id }
        val currByCode = currencies.associateBy { it.code.uppercase() }
        val catMap = categories.associateBy { it.id }

        val bcvCurrency = currencies.find { it.code.equals("VES", ignoreCase = true) }
        val bcvRate = bcvCurrency?.rateToUsd ?: 1.0

        val copCurrency = currencies.find { it.code.equals("COP", ignoreCase = true) }
        val copRate = copCurrency?.rateToUsd ?: 0.0

        fun toUsd(amount: Double, currencyIdOrCode: String): Double {
            val curr = currMap[currencyIdOrCode] ?: currByCode[currencyIdOrCode.uppercase()]
            val rate = curr?.rateToUsd ?: 1.0
            return if (curr?.isBase == true || curr?.code.equals("USD", ignoreCase = true)) {
                amount
            } else if (rate > 0.0) {
                amount / rate
            } else {
                amount
            }
        }

        val accountsSummary = accounts.filter { it.includeInTotal && !it.isArchived }.map { acc ->
            val curr = currMap[acc.currencyId]
            val code = curr?.code ?: "USD"
            val usd = toUsd(acc.currentBalance, acc.currencyId)
            AccountBalanceSummary(
                accountName = acc.name,
                currencyCode = code,
                nativeBalance = acc.currentBalance,
                usdBalance = usd
            )
        }
        val totalUsd = accountsSummary.sumOf { it.usdBalance }
        val totalVes = totalUsd * bcvRate

        val cal = Calendar.getInstance()
        val monthName = SimpleDateFormat("MMMM", Locale("es", "ES")).format(cal.time)
            .replaceFirstChar { it.uppercase() }

        val calStartCurrentMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val calEndCurrentMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val calStartPrevMonth = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val calEndPrevMonth = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val currentMonthTransactions = transactions.filter {
            it.dateTimestamp in calStartCurrentMonth..calEndCurrentMonth
        }
        val currentMonthIncome = currentMonthTransactions
            .filter { it.type == "INCOME" }
            .sumOf { it.usdEquivalent }

        val currentMonthExpense = currentMonthTransactions
            .filter { it.type == "EXPENSE" }
            .sumOf { it.usdEquivalent }

        val netSavings = currentMonthIncome - currentMonthExpense
        val savingsRate = if (currentMonthIncome > 0.0) {
            ((netSavings / currentMonthIncome) * 100).toFloat().coerceIn(-100f, 100f)
        } else 0f

        val categoryExpenses = mutableMapOf<String, Double>()
        currentMonthTransactions.filter { it.type == "EXPENSE" && it.categoryId != null }.forEach { tx ->
            val catName = catMap[tx.categoryId]?.name ?: "Otros"
            categoryExpenses[catName] = (categoryExpenses[catName] ?: 0.0) + tx.usdEquivalent
        }

        val topExpenseCategories = categoryExpenses.entries
            .sortedByDescending { it.value }
            .take(4)
            .map { entry ->
                val pct = if (currentMonthExpense > 0.0) ((entry.value / currentMonthExpense) * 100).toFloat() else 0f
                CategoryExpenseSummary(entry.key, entry.value, pct)
            }

        val prevMonthExpense = transactions.filter {
            it.type == "EXPENSE" && it.dateTimestamp in calStartPrevMonth..calEndPrevMonth
        }.sumOf { it.usdEquivalent }

        val expenseDiffPct = if (prevMonthExpense > 0.0) {
            (((currentMonthExpense - prevMonthExpense) / prevMonthExpense) * 100).toFloat()
        } else 0f

        val now = System.currentTimeMillis()
        val sevenDaysLater = now + (7L * 24 * 60 * 60 * 1000)

        val activePending = pendingExpenses.filter { !it.isPaid }
        val overdueItems = activePending.filter { it.dueDateTimestamp < now }
        val overdueAmount = overdueItems.sumOf { toUsd(it.amount, it.currencyCode) }

        val upcoming7DaysItems = activePending.filter { it.dueDateTimestamp in now..sevenDaysLater }
        val upcoming7DaysAmount = upcoming7DaysItems.sumOf { toUsd(it.amount, it.currencyCode) }

        val avgExpense = if (currentMonthTransactions.isNotEmpty()) {
            val expenseCount = currentMonthTransactions.count { it.type == "EXPENSE" }
            if (expenseCount > 0) currentMonthExpense / expenseCount else 0.0
        } else 0.0

        val dateFormat = SimpleDateFormat("dd MMM", Locale("es", "ES"))
        val unusual = currentMonthTransactions
            .filter { it.type == "EXPENSE" && it.usdEquivalent > (avgExpense * 1.8).coerceAtLeast(30.0) }
            .sortedByDescending { it.usdEquivalent }
            .take(3)
            .map { tx ->
                UnusualExpenseSummary(
                    description = tx.note.ifBlank { "Gasto sin descripción" },
                    categoryName = catMap[tx.categoryId]?.name ?: "General",
                    usdAmount = tx.usdEquivalent,
                    dateFormatted = dateFormat.format(tx.dateTimestamp)
                )
            }

        return BrexamanFinancialSnapshot(
            totalNetWorthUsd = totalUsd,
            totalNetWorthVes = totalVes,
            bcvRate = bcvRate,
            copRate = copRate,
            accountsSummary = accountsSummary,
            budgetAmountUsd = budgetInfo?.budgetAmountUsd ?: 0.0,
            budgetSpentUsd = budgetInfo?.spentThisMonthUsd ?: currentMonthExpense,
            budgetRemainingUsd = budgetInfo?.remainingUsd ?: 0.0,
            budgetPercentageUsed = budgetInfo?.percentageUsed ?: 0f,
            isOverBudget = budgetInfo?.isOverBudget ?: false,
            monthName = monthName,
            currentMonthIncomeUsd = currentMonthIncome,
            currentMonthExpenseUsd = currentMonthExpense,
            netSavingsUsd = netSavings,
            savingsRatePercentage = savingsRate,
            topExpenseCategories = topExpenseCategories,
            previousMonthExpenseUsd = prevMonthExpense,
            expenseDifferencePercentage = expenseDiffPct,
            overdueDebtsCount = overdueItems.size,
            overdueDebtsAmountUsd = overdueAmount,
            upcoming7DaysDebtsCount = upcoming7DaysItems.size,
            upcoming7DaysDebtsAmountUsd = upcoming7DaysAmount,
            unusualExpenses = unusual
        )
    }

    fun answerQuickPromptLocally(promptType: String, snapshot: BrexamanFinancialSnapshot): String {
        return when (promptType) {
            "MONTH_STATUS" -> {
                val savingsStatus = when {
                    snapshot.netSavingsUsd > 0 -> "¡Vas con saldo a favor! Has ahorrado $${String.format("%.2f", snapshot.netSavingsUsd)} USD (${String.format("%.1f", snapshot.savingsRatePercentage)}% de tus ingresos)."
                    snapshot.netSavingsUsd < 0 -> "Cuidado: tus gastos superan tus ingresos por $${String.format("%.2f", -snapshot.netSavingsUsd)} USD este mes."
                    else -> "Tus gastos están exactamente empatados con tus ingresos."
                }
                val budgetLine = if (snapshot.budgetAmountUsd > 0) {
                    "\n• Presupuesto mensual: $${String.format("%.2f", snapshot.budgetSpentUsd)} de $${String.format("%.2f", snapshot.budgetAmountUsd)} USD (Libre: $${String.format("%.2f", snapshot.budgetRemainingUsd)} USD)"
                } else ""

                """
                ⚡ Resumen de ${snapshot.monthName}:
                • Ingresos totales: $${String.format("%.2f", snapshot.currentMonthIncomeUsd)} USD
                • Gastos registrados: $${String.format("%.2f", snapshot.currentMonthExpenseUsd)} USD$budgetLine
                • $savingsStatus
                • Patrimonio en cuentas: $${String.format("%.2f", snapshot.totalNetWorthUsd)} USD (~${String.format("%.2f", snapshot.totalNetWorthVes)} Bs a tasa BCV: ${String.format("%.2f", snapshot.bcvRate)})
                """.trimIndent()
            }
            "TOP_EXPENSES" -> {
                if (snapshot.topExpenseCategories.isEmpty()) {
                    "Aún no tienes gastos clasificados por categoría este mes. ¡Registra tus compras para empezar a analizarlas!"
                } else {
                    val sb = StringBuilder("📊 Tus mayores salidas de dinero en ${snapshot.monthName}:\n")
                    snapshot.topExpenseCategories.forEachIndexed { i, cat ->
                        sb.appendLine("${i + 1}. ${cat.categoryName}: $${String.format("%.2f", cat.spentUsd)} USD (${String.format("%.1f", cat.percentageOfTotal)}%)")
                    }
                    val topCat = snapshot.topExpenseCategories.first()
                    sb.append("\n💡 Tu mayor foco de atención está en '${topCat.categoryName}' (${String.format("%.0f", topCat.percentageOfTotal)}% de tus gastos).")
                    sb.toString()
                }
            }
            "PENDING_DEBTS" -> {
                if (snapshot.overdueDebtsCount == 0 && snapshot.upcoming7DaysDebtsCount == 0) {
                    "¡Excelente noticia! No tienes pagos vencidos ni cuentas urgentes programadas para los próximos 7 días. Tu horizonte financiero está totalmente despejado."
                } else {
                    val sb = StringBuilder("🚨 Estado de tus compromisos por pagar:\n")
                    if (snapshot.overdueDebtsCount > 0) {
                        sb.appendLine("• ⚠️ VENCIDOS: ${snapshot.overdueDebtsCount} pagos por $${String.format("%.2f", snapshot.overdueDebtsAmountUsd)} USD. ¡Atiéndelos cuanto antes!")
                    }
                    if (snapshot.upcoming7DaysDebtsCount > 0) {
                        sb.appendLine("• 📅 Próximos 7 días: ${snapshot.upcoming7DaysDebtsCount} pagos por $${String.format("%.2f", snapshot.upcoming7DaysDebtsAmountUsd)} USD.")
                    }
                    val totalCompromisos = snapshot.overdueDebtsAmountUsd + snapshot.upcoming7DaysDebtsAmountUsd
                    val saldoLibre = snapshot.totalNetWorthUsd - totalCompromisos
                    sb.append("\nSaldo libre tras cubrir estos pagos: $${String.format("%.2f", saldoLibre)} USD.")
                    sb.toString()
                }
            }
            "DAILY_TIP" -> {
                when {
                    snapshot.isOverBudget ->
                        "💡 Consejo de Brexaman: ¡Alerta de presupuesto! Has sobrepasado el límite fijado para este mes. Congela gastos no esenciales y usa solo efectivo para compras estrictamente necesarias."
                    snapshot.savingsRatePercentage > 20f ->
                        "💡 Consejo de Brexaman: Llevas una excelente tasa de ahorro (${String.format("%.1f", snapshot.savingsRatePercentage)}%). Es un gran momento para apartar una porción a tu fondo de reserva o Binance antes de que surjan gastos imprevistos."
                    snapshot.overdueDebtsCount > 0 ->
                        "💡 Consejo de Brexaman: Tu prioridad número uno hoy debe ser liquidar esos ${snapshot.overdueDebtsCount} pagos vencidos. Quitarte ese compromiso te dará tranquilidad y evitará recargos."
                    snapshot.expenseDifferencePercentage > 15f ->
                        "💡 Consejo de Brexaman: Tus gastos van un ${String.format("%.1f", snapshot.expenseDifferencePercentage)}% más altos que el mes anterior. Aplica la regla de esperar 24 horas antes de compras por impulso."
                    else ->
                        "💡 Consejo de Brexaman: Recuerda que la tasa oficial BCV está en ${String.format("%.2f", snapshot.bcvRate)} Bs/USD. Si en algún comercio te cobran una tasa paralela muy superior, prefiere pagar en bolívares a la oficial para proteger tu bolsillo."
                }
            }
            else -> "¡Hola! Soy Brexaman. Estoy monitoreando tus finanzas y la tasa oficial en tiempo real."
        }
    }
}
