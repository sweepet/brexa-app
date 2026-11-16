package com.brexa.app.data.ai

/**
 * Representa el resumen financiero procesado en tiempo real desde Room DB
 * para suministrar contexto analítico exacto a Brexaman.
 */
data class BrexamanFinancialSnapshot(
    val totalNetWorthUsd: Double,
    val totalNetWorthVes: Double,
    val bcvRate: Double,
    val copRate: Double = 0.0,
    val accountsSummary: List<AccountBalanceSummary>,
    
    // Presupuesto mensual
    val budgetAmountUsd: Double = 0.0,
    val budgetSpentUsd: Double = 0.0,
    val budgetRemainingUsd: Double = 0.0,
    val budgetPercentageUsed: Float = 0f,
    val isOverBudget: Boolean = false,
    
    // Mes actual
    val monthName: String,
    val currentMonthIncomeUsd: Double,
    val currentMonthExpenseUsd: Double,
    val netSavingsUsd: Double,
    val savingsRatePercentage: Float,
    val topExpenseCategories: List<CategoryExpenseSummary>,
    
    // Comparativa mes anterior
    val previousMonthExpenseUsd: Double,
    val expenseDifferencePercentage: Float,
    
    // Compromisos y deudas
    val overdueDebtsCount: Int,
    val overdueDebtsAmountUsd: Double,
    val upcoming7DaysDebtsCount: Int,
    val upcoming7DaysDebtsAmountUsd: Double,
    
    // Picos y alertas
    val unusualExpenses: List<UnusualExpenseSummary>
) {
    /**
     * Construye un bloque de contexto en tiempo real para inyectar en Gemini.
     */
    fun toContextString(): String {
        val sb = StringBuilder()
        sb.appendLine("=== DATOS FINANCIEROS REALES DEL USUARIO EN TIEMPO REAL (BREXA APP) ===")
        sb.appendLine("• TASAS OFICIALES REGISTRADAS:")
        sb.appendLine("  - Tasa Oficial BCV (Venezuela): ${String.format("%.2f", bcvRate)} Bs por cada 1 USD")
        if (copRate > 0) {
            sb.appendLine("  - Tasa TRM (Colombia): ${String.format("%.2f", copRate)} COP por cada 1 USD")
        }
        sb.appendLine("• PATRIMONIO TOTAL:")
        sb.appendLine("  - $${String.format("%.2f", totalNetWorthUsd)} USD (equivalente a ${String.format("%.2f", totalNetWorthVes)} Bs a tasa BCV)")
        
        sb.appendLine("• CUENTAS Y SALDOS:")
        accountsSummary.forEach { acc ->
            sb.appendLine("  - ${acc.accountName}: ${String.format("%.2f", acc.nativeBalance)} ${acc.currencyCode} (~$${String.format("%.2f", acc.usdBalance)} USD)")
        }
        
        if (budgetAmountUsd > 0) {
            sb.appendLine("• PRESUPUESTO DE $monthName:")
            sb.appendLine("  - Asignado: $${String.format("%.2f", budgetAmountUsd)} USD")
            sb.appendLine("  - Gastado: $${String.format("%.2f", budgetSpentUsd)} USD (${String.format("%.0f", budgetPercentageUsed * 100)}%)")
            sb.appendLine("  - Disponible Libre: $${String.format("%.2f", budgetRemainingUsd)} USD")
            if (isOverBudget) {
                sb.appendLine("  - ALERTA: ¡Ha superado su presupuesto mensual!")
            }
        }
        
        sb.appendLine("• RESUMEN DEL MES ($monthName):")
        sb.appendLine("  - Ingresos registrados: $${String.format("%.2f", currentMonthIncomeUsd)} USD")
        sb.appendLine("  - Gastos registrados: $${String.format("%.2f", currentMonthExpenseUsd)} USD")
        sb.appendLine("  - Balance Neto: $${String.format("%.2f", netSavingsUsd)} USD")
        
        if (topExpenseCategories.isNotEmpty()) {
            sb.appendLine("• CATEGORÍAS DONDE MÁS HA GASTADO EN $monthName:")
            topExpenseCategories.forEachIndexed { i, cat ->
                sb.appendLine("  ${i + 1}. ${cat.categoryName}: $${String.format("%.2f", cat.spentUsd)} USD (${String.format("%.1f", cat.percentageOfTotal)}% de los gastos)")
            }
        }
        
        sb.appendLine("• ESTADO DE COMPROMISOS / DEUDAS PENDIENTES:")
        if (overdueDebtsCount == 0 && upcoming7DaysDebtsCount == 0) {
            sb.appendLine("  - AL DÍA: No tiene pagos vencidos ni deudas próximas en los siguientes 7 días.")
        } else {
            if (overdueDebtsCount > 0) {
                sb.appendLine("  - VENCIDOS: $overdueDebtsCount pagos por un total de $${String.format("%.2f", overdueDebtsAmountUsd)} USD.")
            }
            if (upcoming7DaysDebtsCount > 0) {
                sb.appendLine("  - PRÓXIMOS 7 DÍAS: $upcoming7DaysDebtsCount pagos por un total de $${String.format("%.2f", upcoming7DaysDebtsAmountUsd)} USD.")
            }
        }
        
        if (unusualExpenses.isNotEmpty()) {
            sb.appendLine("• MOVIMIENTOS RECIENTES RELEVANTES:")
            unusualExpenses.forEach { ue ->
                sb.appendLine("  - ${ue.description} (${ue.categoryName}): $${String.format("%.2f", ue.usdAmount)} USD [${ue.dateFormatted}]")
            }
        }
        sb.appendLine("=========================================================================")
        return sb.toString()
    }
}

data class AccountBalanceSummary(
    val accountName: String,
    val currencyCode: String,
    val nativeBalance: Double,
    val usdBalance: Double
)

data class CategoryExpenseSummary(
    val categoryName: String,
    val spentUsd: Double,
    val percentageOfTotal: Float
)

data class UnusualExpenseSummary(
    val description: String,
    val categoryName: String,
    val usdAmount: Double,
    val dateFormatted: String
)

data class BrexamanChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
    USER,
    BREXAMAN
}
