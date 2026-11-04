package com.brexa.app.data.ai

/**
 * Representa el resumen financiero procesado en tiempo real desde Room DB
 * para suministrar contexto analítico a Brexaman.
 */
data class BrexamanFinancialSnapshot(
    val totalNetWorthUsd: Double,
    val totalNetWorthVes: Double,
    val bcvRate: Double,
    val accountsSummary: List<AccountBalanceSummary>,
    
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
     * Construye un bloque de texto denso y estructurado para el System Prompt de Gemini.
     */
    fun toContextString(): String {
        val sb = StringBuilder()
        sb.appendLine("=== RESUMEN FINANCIERO EN TIEMPO REAL DEL USUARIO (ROOM DB) ===")
        sb.appendLine("• Patrimonio Neto Total: $${String.format("%.2f", totalNetWorthUsd)} USD (~${String.format("%.2f", totalNetWorthVes)} Bs. a tasa BCV: ${String.format("%.2f", bcvRate)} Bs/USD)")
        
        sb.appendLine("• Cuentas activas:")
        accountsSummary.forEach { acc ->
            sb.appendLine("  - ${acc.accountName}: ${String.format("%.2f", acc.nativeBalance)} ${acc.currencyCode} (~$${String.format("%.2f", acc.usdBalance)} USD)")
        }
        
        sb.appendLine("• Mes en curso ($monthName):")
        sb.appendLine("  - Ingresos: $${String.format("%.2f", currentMonthIncomeUsd)} USD")
        sb.appendLine("  - Gastos: $${String.format("%.2f", currentMonthExpenseUsd)} USD")
        sb.appendLine("  - Ahorro Neto: $${String.format("%.2f", netSavingsUsd)} USD (Tasa de Ahorro: ${String.format("%.1f", savingsRatePercentage)}%)")
        
        if (topExpenseCategories.isNotEmpty()) {
            sb.appendLine("• Top Categorías de Gasto de $monthName:")
            topExpenseCategories.forEachIndexed { i, cat ->
                sb.appendLine("  ${i + 1}. ${cat.categoryName}: $${String.format("%.2f", cat.spentUsd)} USD (${String.format("%.1f", cat.percentageOfTotal)}% del gasto mensual)")
            }
        }
        
        sb.appendLine("• Comparativa contra el mes pasado:")
        sb.appendLine("  - Gasto mes anterior: $${String.format("%.2f", previousMonthExpenseUsd)} USD")
        if (expenseDifferencePercentage != 0f) {
            val direction = if (expenseDifferencePercentage > 0) "más alto (+)" else "más bajo (-)"
            sb.appendLine("  - Variación de gasto: ${String.format("%.1f", expenseDifferencePercentage)}% ($direction)")
        }
        
        sb.appendLine("• Compromisos y Cuentas por Pagar:")
        sb.appendLine("  - Vencidos: $overdueDebtsCount compromisos por $${String.format("%.2f", overdueDebtsAmountUsd)} USD")
        sb.appendLine("  - Próximos 7 días: $upcoming7DaysDebtsCount compromisos por $${String.format("%.2f", upcoming7DaysDebtsAmountUsd)} USD")
        
        if (unusualExpenses.isNotEmpty()) {
            sb.appendLine("• Gastos grandes / atípicos recientes:")
            unusualExpenses.forEach { ue ->
                sb.appendLine("  - ${ue.description} (${ue.categoryName}): $${String.format("%.2f", ue.usdAmount)} USD el ${ue.dateFormatted}")
            }
        }
        sb.appendLine("=============================================================")
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
