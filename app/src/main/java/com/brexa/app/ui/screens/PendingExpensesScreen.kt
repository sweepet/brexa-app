package com.brexa.app.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.data.local.entity.AccountEntity
import com.brexa.app.data.local.entity.CurrencyEntity
import com.brexa.app.data.local.entity.PendingExpenseEntity
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistButton
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.components.BrutalistTextField
import com.brexa.app.ui.theme.*
import com.brexa.app.ui.util.CurrencyFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DueStatus(
    val label: String,
    val isOverdue: Boolean,
    val isDueToday: Boolean,
    val isDueSoon: Boolean,
    val daysDiff: Long
)

fun calculateDueStatus(dueTimestamp: Long): DueStatus {
    val calNow = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val calDue = Calendar.getInstance().apply {
        timeInMillis = dueTimestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffMillis = calDue.timeInMillis - calNow.timeInMillis
    val diffDays = (diffMillis / (1000 * 60 * 60 * 24))

    return when {
        diffDays < 0 -> DueStatus(
            label = if (diffDays == -1L) "VENCIDO AYER" else "VENCIDO HACE ${-diffDays} DÍAS",
            isOverdue = true,
            isDueToday = false,
            isDueSoon = false,
            daysDiff = diffDays
        )
        diffDays == 0L -> DueStatus(
            label = "VENCE HOY",
            isOverdue = false,
            isDueToday = true,
            isDueSoon = true,
            daysDiff = 0
        )
        diffDays == 1L -> DueStatus(
            label = "VENCE MAÑANA",
            isOverdue = false,
            isDueToday = false,
            isDueSoon = true,
            daysDiff = 1
        )
        diffDays in 2..7 -> DueStatus(
            label = "EN $diffDays DÍAS",
            isOverdue = false,
            isDueToday = false,
            isDueSoon = true,
            daysDiff = diffDays
        )
        else -> DueStatus(
            label = "EN $diffDays DÍAS",
            isOverdue = false,
            isDueToday = false,
            isDueSoon = false,
            daysDiff = diffDays
        )
    }
}

@Composable
fun PendingExpensesScreen(
    viewModel: BrexaViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val activePending by viewModel.activePendingExpenses.collectAsState()
    val paidPending by viewModel.paidPendingExpenses.collectAsState()
    val totalPendingUsd by viewModel.totalPendingUsd.collectAsState()
    val realAvailableUsd by viewModel.realAvailableBalanceUsd.collectAsState()
    val totalUsd by viewModel.consolidatedUsdBalance.collectAsState()
    val projections by viewModel.pendingProjections.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val currencies by viewModel.allCurrencies.collectAsState()
    val accounts by viewModel.activeAccounts.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val isIncognito by viewModel.incognitoMode.collectAsState()

    val baseCode = baseCurrency?.code ?: "USD"
    val baseSymbol = baseCurrency?.symbol ?: "$"
    val formatter = remember { CurrencyFormat.standardFormatter }
    val dateFormatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale("es", "ES")) }

    // Separación en dos funciones: Gastos Variables vs Gastos Fijos
    var selectedSection by remember { mutableStateOf("VARIABLES") } // "VARIABLES" o "FIJOS"
    var selectedVarFilter by remember { mutableStateOf("TODOS") } // "TODOS", "7_DIAS", "15_DIAS", "ESTE_MES"
    var selectedFixedFilter by remember { mutableStateOf("TODOS") } // "TODOS", "MONTHLY", "BIWEEKLY", "WEEKLY"

    // Modales
    var showCreateModal by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<PendingExpenseEntity?>(null) }
    var itemToPay by remember { mutableStateOf<PendingExpenseEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<PendingExpenseEntity?>(null) }

    // Listas separadas
    val variableExpenses = remember(activePending) {
        activePending.filter { it.expenseType != "FIXED" }
    }
    val fixedExpenses = remember(activePending) {
        activePending.filter { it.expenseType == "FIXED" }
    }

    // Cálculos de montos consolidados
    val currMap = remember(currencies) { currencies.associateBy { it.code.uppercase() } }

    val totalVariablesUsd = remember(variableExpenses, currMap) {
        variableExpenses.sumOf { item ->
            val curr = currMap[item.currencyCode.uppercase()]
            val rate = curr?.rateToUsd ?: 1.0
            if (curr?.isBase == true || item.currencyCode.equals("USD", ignoreCase = true)) {
                item.amount
            } else if (rate > 0.0) {
                item.amount / rate
            } else item.amount
        }
    }

    val totalFixedMonthlyUsd = remember(fixedExpenses, currMap) {
        fixedExpenses.sumOf { item ->
            val curr = currMap[item.currencyCode.uppercase()]
            val rate = curr?.rateToUsd ?: 1.0
            val baseAmt = if (curr?.isBase == true || item.currencyCode.equals("USD", ignoreCase = true)) {
                item.amount
            } else if (rate > 0.0) {
                item.amount / rate
            } else item.amount
            when (item.recurrencePeriod) {
                "WEEKLY" -> baseAmt * 4.33
                "BIWEEKLY" -> baseAmt * 2.0
                else -> baseAmt
            }
        }
    }

    // Filtros de fecha para Variables
    val cal7Days = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
    }
    val cal15Days = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 15)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
    }
    val calEndOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
    }

    val displayedVariableItems = remember(variableExpenses, selectedVarFilter, cal7Days, cal15Days, calEndOfMonth) {
        when (selectedVarFilter) {
            "7_DIAS" -> variableExpenses.filter { it.dueDateTimestamp <= cal7Days }
            "15_DIAS" -> variableExpenses.filter { it.dueDateTimestamp <= cal15Days }
            "ESTE_MES" -> variableExpenses.filter { it.dueDateTimestamp <= calEndOfMonth }
            else -> variableExpenses
        }
    }

    val displayedFixedItems = remember(fixedExpenses, selectedFixedFilter) {
        when (selectedFixedFilter) {
            "MONTHLY" -> fixedExpenses.filter { it.recurrencePeriod == "MONTHLY" || it.recurrencePeriod == null }
            "BIWEEKLY" -> fixedExpenses.filter { it.recurrencePeriod == "BIWEEKLY" }
            "WEEKLY" -> fixedExpenses.filter { it.recurrencePeriod == "WEEKLY" }
            else -> fixedExpenses
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
    ) {
        // --- 1. BARRA SUPERIOR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .border(width = 1.dp, color = BorderSolid)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PAGOS PENDIENTES",
                    style = BrexaTypography.titleLarge.copy(color = AccentLime, fontSize = 16.sp)
                )
                Text(
                    text = "COMPROMISOS VARIABLES Y RECURRENTES",
                    style = BrexaTypography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp)
                )
            }

            BrutalistBadge(
                text = "VOLVER ✕",
                backgroundColor = BackgroundBase,
                contentColor = TextPrimary,
                borderColor = BorderSolid,
                fontSize = 10.sp,
                modifier = Modifier.clickable { onBack() }
            )
        }

        // --- 2. SELECTOR DE SECCIÓN (VARIABLES VS FIJOS) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isVar = selectedSection == "VARIABLES"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isVar) AccentLime else SurfaceCard, RectangleShape)
                    .border(2.dp, if (isVar) AccentLime else BorderSolid, RectangleShape)
                    .clickable { selectedSection = "VARIABLES" }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚡ VARIABLES (${variableExpenses.size})",
                        style = BrexaTypography.labelMedium.copy(
                            color = if (isVar) Color.Black else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    )
                    Text(
                        text = "Puntuales • Se borran al pagar",
                        style = BrexaTypography.labelSmall.copy(
                            color = if (isVar) Color.Black.copy(alpha = 0.8f) else TextMuted,
                            fontSize = 8.5.sp
                        )
                    )
                }
            }

            val isFixed = selectedSection == "FIJOS"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isFixed) AccentPurple else SurfaceCard, RectangleShape)
                    .border(2.dp, if (isFixed) AccentPurple else BorderSolid, RectangleShape)
                    .clickable { selectedSection = "FIJOS" }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🔄 FIJOS (${fixedExpenses.size})",
                        style = BrexaTypography.labelMedium.copy(
                            color = if (isFixed) Color.White else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    )
                    Text(
                        text = "Recurrentes • Mes / Quincena",
                        style = BrexaTypography.labelSmall.copy(
                            color = if (isFixed) Color.White.copy(alpha = 0.8f) else TextMuted,
                            fontSize = 8.5.sp
                        )
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
        ) {
            // --- 3. TARJETA RESUMEN DINÁMICA ---
            item {
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SurfaceCard,
                    borderColor = if (selectedSection == "VARIABLES") (if (projections.overdueCount > 0) ExpenseRed else AccentLime) else AccentPurple,
                    borderWidth = 2.dp,
                    shadowColor = if (selectedSection == "VARIABLES") (if (projections.overdueCount > 0) ExpenseRed else AccentLime) else AccentPurple,
                    shadowOffset = 4.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedSection == "VARIABLES") "TOTAL VARIABLES POR PAGAR" else "TOTAL FIJOS ESTIMADO / MES",
                                style = BrexaTypography.labelMedium.copy(
                                    color = TextSecondary,
                                    letterSpacing = 1.sp
                                )
                            )

                            if (selectedSection == "VARIABLES") {
                                if (projections.overdueCount > 0) {
                                    BrutalistBadge(
                                        text = "🔴 ${projections.overdueCount} VENCIDO${if (projections.overdueCount > 1) "S" else ""}",
                                        backgroundColor = ExpenseRed,
                                        contentColor = Color.White,
                                        borderColor = ExpenseRed
                                    )
                                } else {
                                    BrutalistBadge(
                                        text = "${variableExpenses.size} PENDIENTE${if (variableExpenses.size != 1) "S" else ""}",
                                        backgroundColor = BackgroundBase,
                                        contentColor = AccentLime,
                                        borderColor = AccentLime
                                    )
                                }
                            } else {
                                BrutalistBadge(
                                    text = "${fixedExpenses.size} PROGRAMADO${if (fixedExpenses.size != 1) "S" else ""}",
                                    backgroundColor = BackgroundBase,
                                    contentColor = AccentPurple,
                                    borderColor = AccentPurple
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val activeSumUsd = if (selectedSection == "VARIABLES") totalVariablesUsd else totalFixedMonthlyUsd
                        val displayAmount = if (isIncognito) "$baseSymbol •••••••" else "$baseSymbol ${formatter.format(activeSumUsd)}"
                        Text(
                            text = displayAmount,
                            style = BrexaTypography.displayLarge.copy(
                                color = if (selectedSection == "VARIABLES") (if (totalVariablesUsd > 0.0) ExpenseRed else AccentLime) else AccentPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(BorderSolid)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedSection == "VARIABLES") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SALDO LIBRE REAL",
                                    style = BrexaTypography.labelSmall.copy(
                                        color = AccentLime,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )

                                val realDisplay = if (isIncognito) "$baseSymbol •••••••" else "$baseSymbol ${formatter.format(realAvailableUsd)}"
                                Text(
                                    text = realDisplay,
                                    style = BrexaTypography.titleLarge.copy(
                                        color = if (realAvailableUsd >= 0.0) IncomeGreen else ExpenseRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Dinero en cuentas totalmente libre para gastar ($baseSymbol${formatter.format(totalUsd)})",
                                style = BrexaTypography.bodySmall.copy(color = TextMuted, fontSize = 10.5.sp)
                            )
                        } else {
                            Text(
                                text = "💡 Compromisos periódicos recurrentes (internet, alquiler, servicios, suscripciones). Al registrar su pago, el gasto se anota en el historial y su fecha avanza automáticamente.",
                                style = BrexaTypography.bodySmall.copy(color = TextMuted, fontSize = 10.5.sp, lineHeight = 14.sp)
                            )
                        }
                    }
                }
            }

            // --- 4. SUB-FILTROS DE VISUALIZACIÓN ---
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (selectedSection == "VARIABLES") {
                        val varTabs = listOf(
                            Pair("TODOS", "TODOS (${variableExpenses.size})"),
                            Pair("7_DIAS", "PRÓX. 7 DÍAS"),
                            Pair("15_DIAS", "QUINCENA"),
                            Pair("ESTE_MES", "ESTE MES")
                        )
                        items(varTabs) { (key, label) ->
                            val isSel = selectedVarFilter == key
                            Box(
                                modifier = Modifier
                                    .background(if (isSel) AccentLime else SurfaceCard, RectangleShape)
                                    .border(1.5.dp, if (isSel) AccentLime else BorderSolid, RectangleShape)
                                    .clickable { selectedVarFilter = key }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = BrexaTypography.labelMedium.copy(
                                        color = if (isSel) Color.Black else TextSecondary,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }
                        }
                    } else {
                        val fixedTabs = listOf(
                            Pair("TODOS", "TODOS (${fixedExpenses.size})"),
                            Pair("MONTHLY", "MENSUALES"),
                            Pair("BIWEEKLY", "QUINCENALES"),
                            Pair("WEEKLY", "SEMANALES")
                        )
                        items(fixedTabs) { (key, label) ->
                            val isSel = selectedFixedFilter == key
                            Box(
                                modifier = Modifier
                                    .background(if (isSel) AccentPurple else SurfaceCard, RectangleShape)
                                    .border(1.5.dp, if (isSel) AccentPurple else BorderSolid, RectangleShape)
                                    .clickable { selectedFixedFilter = key }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = BrexaTypography.labelMedium.copy(
                                        color = if (isSel) Color.White else TextSecondary,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // --- 5. LISTA DE COMPROMISOS ---
            val activeList = if (selectedSection == "VARIABLES") displayedVariableItems else displayedFixedItems

            if (activeList.isEmpty()) {
                item {
                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = SurfaceCard,
                        borderColor = BorderSolid
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (selectedSection == "VARIABLES") "¡SIN GASTOS VARIABLES PENDIENTES!" else "¡SIN GASTOS FIJOS REGISTRADOS!",
                                style = BrexaTypography.titleLarge.copy(color = if (selectedSection == "VARIABLES") AccentLime else AccentPurple, fontSize = 16.sp),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (selectedSection == "VARIABLES")
                                    "No tienes compromisos puntuales anotados. Usa el botón de abajo para anotar uno y se descontará cuando lo pagues."
                                else
                                    "Guarda tus pagos fijos como internet, alquiler, condominio o suscripciones con su fecha de cobro recurrente.",
                                style = BrexaTypography.bodyMedium.copy(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(activeList, key = { it.id }) { item ->
                    val status = calculateDueStatus(item.dueDateTimestamp)
                    val curr = currencies.find { it.code.equals(item.currencyCode, ignoreCase = true) }
                    val rate = curr?.rateToUsd ?: 1.0
                    val isBase = curr?.isBase == true || item.currencyCode.equals("USD", ignoreCase = true)
                    val equivUsd = if (isBase) item.amount else if (rate > 0.0) item.amount / rate else item.amount

                    val category = expenseCategories.find { it.id == item.categoryId }
                    val account = accounts.find { it.id == item.accountId }

                    val isFixedItem = item.expenseType == "FIXED"

                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = SurfaceCard,
                        borderColor = when {
                            status.isOverdue -> ExpenseRed
                            status.isDueToday -> AccentLime
                            isFixedItem -> AccentPurple
                            else -> BorderSolid
                        },
                        borderWidth = if (status.isOverdue || status.isDueToday) 2.dp else 1.5.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Cabecera: Tipo / Frecuencia y Vencimiento
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isFixedItem) {
                                    val freqLabel = when (item.recurrencePeriod) {
                                        "WEEKLY" -> "SEMANAL"
                                        "BIWEEKLY" -> "CADA 15 DÍAS"
                                        else -> "TODOS LOS ${item.recurrenceDay ?: 1} DE CADA MES"
                                    }
                                    BrutalistBadge(
                                        text = freqLabel,
                                        backgroundColor = BackgroundBase,
                                        contentColor = AccentPurple,
                                        borderColor = AccentPurple,
                                        fontSize = 8.5.sp
                                    )
                                } else {
                                    val badgeBg = when {
                                        status.isOverdue -> ExpenseRed
                                        status.isDueToday -> AccentLime
                                        else -> BackgroundBase
                                    }
                                    val badgeContent = when {
                                        status.isOverdue -> Color.White
                                        status.isDueToday -> Color.Black
                                        status.isDueSoon -> AccentLime
                                        else -> TextSecondary
                                    }
                                    BrutalistBadge(
                                        text = status.label,
                                        backgroundColor = badgeBg,
                                        contentColor = badgeContent,
                                        borderColor = if (status.isOverdue) ExpenseRed else BorderSolid,
                                        fontSize = 8.5.sp
                                    )
                                }

                                Text(
                                    text = if (isFixedItem) "Próximo: ${dateFormatter.format(Date(item.dueDateTimestamp))}" else dateFormatter.format(Date(item.dueDateTimestamp)),
                                    style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.5.sp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Concepto / Monto
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    style = BrexaTypography.titleLarge.copy(
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${formatter.format(item.amount)} ${item.currencyCode}",
                                        style = BrexaTypography.titleLarge.copy(
                                            color = if (status.isOverdue) ExpenseRed else AccentLime,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    )
                                    if (!isBase) {
                                        Text(
                                            text = "≈ $baseSymbol ${formatter.format(equivUsd)}",
                                            style = BrexaTypography.labelSmall.copy(
                                                color = AccentPurple,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }

                            // Detalles: Categoría, Cuenta sugerida y Nota
                            if (category != null || account != null || item.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (category != null) {
                                        BrutalistBadge(
                                            text = category.name.uppercase(),
                                            backgroundColor = BackgroundBase,
                                            contentColor = TextSecondary,
                                            borderColor = BorderSolid,
                                            fontSize = 8.5.sp
                                        )
                                    }
                                    if (account != null) {
                                        BrutalistBadge(
                                            text = "💳 ${account.name}",
                                            backgroundColor = BackgroundBase,
                                            contentColor = AccentLime,
                                            borderColor = BorderSolid,
                                            fontSize = 8.5.sp
                                        )
                                    }
                                    if (item.note.isNotBlank()) {
                                        Text(
                                            text = "• ${item.note}",
                                            style = BrexaTypography.bodySmall.copy(color = TextMuted, fontSize = 10.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Botones de Acción
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Botón Pagar / Registrar Pago
                                Box(
                                    modifier = Modifier
                                        .background(if (isFixedItem) AccentPurple else AccentLime, RectangleShape)
                                        .border(1.5.dp, BorderBright, RectangleShape)
                                        .clickable { itemToPay = item }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (isFixedItem) "✓ REGISTRAR PAGO ➔" else "✓ PAGAR AHORA ➔",
                                        style = BrexaTypography.labelMedium.copy(
                                            color = if (isFixedItem) Color.White else Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }

                                // 2. Botón Editar y Botón Eliminar
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BrutalistBadge(
                                        text = "✎ EDITAR",
                                        backgroundColor = BackgroundBase,
                                        contentColor = AccentLime,
                                        borderColor = BorderSolid,
                                        fontSize = 9.sp,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                                        modifier = Modifier.clickable { itemToEdit = item }
                                    )

                                    BrutalistBadge(
                                        text = "🗑 ELIMINAR",
                                        backgroundColor = BackgroundBase,
                                        contentColor = ExpenseRed,
                                        borderColor = BorderSolid,
                                        fontSize = 9.sp,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                                        modifier = Modifier.clickable { itemToDelete = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 6. BOTÓN FIJO INFERIOR ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .border(width = 1.dp, color = BorderSolid)
                .padding(16.dp)
        ) {
            val btnColor = if (selectedSection == "VARIABLES") AccentLime else AccentPurple
            val btnText = if (selectedSection == "VARIABLES") "+ ANOTAR GASTO VARIABLE" else "+ CREAR GASTO FIJO RECURRENTE"
            val btnTextColor = if (selectedSection == "VARIABLES") Color.Black else Color.White

            BrutalistButton(
                text = btnText,
                onClick = {
                    itemToEdit = null
                    showCreateModal = true
                },
                containerColor = btnColor,
                contentColor = btnTextColor,
                borderColor = btnColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // --- MODAL DE CONFIRMACIÓN DE PAGO (CONFIRMAR CUENTA, MONEDA, MONTO REAL Y FECHA) ---
    if (itemToPay != null) {
        val pending = itemToPay!!
        PayPendingConfirmDialog(
            pending = pending,
            accounts = accounts,
            currencies = currencies,
            baseCurrency = baseCurrency,
            onDismiss = { itemToPay = null },
            onConfirmPay = { accId, rate, nativeAmt, note, dateTs ->
                viewModel.payPendingExpense(
                    pendingId = pending.id,
                    accountId = accId,
                    exchangeRate = rate,
                    overrideNativeAmount = nativeAmt,
                    note = note,
                    paymentDateTimestamp = dateTs,
                    onSuccess = { itemToPay = null }
                )
            },
            onAdvanceWithoutDeduct = {
                viewModel.markPendingAsPaidWithoutExpense(pending.id)
                itemToPay = null
            }
        )
    }

    // --- MODAL: CREAR / EDITAR GASTO PENDIENTE (VARIABLE O FIJO) ---
    if (showCreateModal || itemToEdit != null) {
        CreateOrEditPendingExpenseDialog(
            initialItem = itemToEdit,
            defaultExpenseType = if (itemToEdit != null) itemToEdit!!.expenseType else selectedSection,
            accounts = accounts,
            currencies = currencies,
            baseCurrency = baseCurrency,
            categories = expenseCategories,
            onDismiss = {
                showCreateModal = false
                itemToEdit = null
            },
            onSave = { title, amount, currencyCode, dueDateTimestamp, categoryId, accountId, note, expenseType, recurrencePeriod, recurrenceDay ->
                if (itemToEdit != null) {
                    val updated = itemToEdit!!.copy(
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
                    viewModel.updatePendingExpense(
                        item = updated,
                        onSuccess = {
                            itemToEdit = null
                            showCreateModal = false
                        }
                    )
                } else {
                    viewModel.addPendingExpense(
                        title = title,
                        amount = amount,
                        currencyCode = currencyCode,
                        dueDateTimestamp = dueDateTimestamp,
                        categoryId = categoryId,
                        accountId = accountId,
                        note = note,
                        expenseType = expenseType,
                        recurrencePeriod = recurrencePeriod,
                        recurrenceDay = recurrenceDay,
                        onSuccess = {
                            showCreateModal = false
                        }
                    )
                }
            }
        )
    }

    // --- MODAL: CONFIRMAR ELIMINACIÓN ---
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = if (target.expenseType == "FIXED") "ELIMINAR GASTO FIJO" else "ELIMINAR GASTO VARIABLE",
                    style = BrexaTypography.titleLarge.copy(color = ExpenseRed)
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas eliminar '${target.title}' por ${formatter.format(target.amount)} ${target.currencyCode}?",
                    style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                )
            },
            confirmButton = {
                BrutalistBadge(
                    text = "ELIMINAR",
                    backgroundColor = ExpenseRed,
                    contentColor = Color.White,
                    borderColor = ExpenseRed,
                    modifier = Modifier.clickable {
                        viewModel.deletePendingExpense(target.id)
                        itemToDelete = null
                    }
                )
            },
            dismissButton = {
                BrutalistBadge(
                    text = "CANCELAR",
                    backgroundColor = BackgroundBase,
                    contentColor = TextSecondary,
                    borderColor = BorderSolid,
                    modifier = Modifier.clickable { itemToDelete = null }
                )
            }
        )
    }
}

/**
 * Modal Neobrutalista de Confirmación de Pago.
 * Permite confirmar / cambiar la cuenta, moneda, monto real y fecha antes de registrar la operación.
 */
@Composable
fun PayPendingConfirmDialog(
    pending: PendingExpenseEntity,
    accounts: List<AccountEntity>,
    currencies: List<CurrencyEntity>,
    baseCurrency: CurrencyEntity?,
    onDismiss: () -> Unit,
    onConfirmPay: (accountId: String, rate: Double, overrideNativeAmount: Double, note: String, dateTimestamp: Long) -> Unit,
    onAdvanceWithoutDeduct: () -> Unit
) {
    val context = LocalContext.current
    var selectedAccountId by remember {
        mutableStateOf(pending.accountId ?: accounts.firstOrNull()?.id ?: "")
    }
    val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val accountCurrency = currencies.find { it.id == selectedAccount?.currencyId }

    // Moneda con la que se paga (por defecto la de la cuenta o la estipulada)
    var selectedPaymentCurrencyCode by remember(selectedAccount) {
        mutableStateOf(accountCurrency?.code ?: pending.currencyCode)
    }

    // Monto real pagado (editable)
    var amountText by remember(pending, selectedPaymentCurrencyCode) {
        val currObj = currencies.find { it.code.equals(selectedPaymentCurrencyCode, ignoreCase = true) }
        val pendCurrObj = currencies.find { it.code.equals(pending.currencyCode, ignoreCase = true) }
        val initialAmount = when {
            selectedPaymentCurrencyCode.equals(pending.currencyCode, ignoreCase = true) -> pending.amount
            selectedPaymentCurrencyCode.equals("USD", ignoreCase = true) && pendCurrObj != null && pendCurrObj.rateToUsd > 0.0 ->
                pending.amount / pendCurrObj.rateToUsd
            !selectedPaymentCurrencyCode.equals("USD", ignoreCase = true) && currObj != null && pending.currencyCode.equals("USD", ignoreCase = true) ->
                pending.amount * currObj.rateToUsd
            else -> pending.amount
        }
        mutableStateOf(CurrencyFormat.formatForInput(initialAmount))
    }
    val parsedAmount = CurrencyFormat.parseNumber(amountText) ?: pending.amount

    // Tasa si aplica
    val selectedPaymentCurr = currencies.find { it.code.equals(selectedPaymentCurrencyCode, ignoreCase = true) }
    val isForeignCurrency = selectedPaymentCurr?.isBase == false
    var rateText by remember(selectedPaymentCurrencyCode) {
        val defaultRate = selectedPaymentCurr?.rateToUsd ?: 1.0
        mutableStateOf(CurrencyFormat.formatForInput(defaultRate))
    }
    val rateNum = CurrencyFormat.parseNumber(rateText) ?: 1.0

    // Cálculo del dinero que saldrá de la cuenta seleccionada
    val accCurrCode = accountCurrency?.code ?: "USD"
    val calculatedDeduction = remember(selectedAccount, selectedPaymentCurrencyCode, parsedAmount, rateNum) {
        when {
            accCurrCode.equals(selectedPaymentCurrencyCode, ignoreCase = true) -> parsedAmount
            accCurrCode.equals("USD", ignoreCase = true) && !selectedPaymentCurrencyCode.equals("USD", ignoreCase = true) && rateNum > 0.0 ->
                parsedAmount / rateNum
            !accCurrCode.equals("USD", ignoreCase = true) && selectedPaymentCurrencyCode.equals("USD", ignoreCase = true) ->
                parsedAmount * rateNum
            else -> parsedAmount
        }
    }

    var paymentDateTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var noteText by remember { mutableStateOf(pending.title) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val formatter = remember { CurrencyFormat.standardFormatter }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = SurfaceCard,
        title = {
            Column {
                Text(
                    text = "CONFIRMAR PAGO",
                    style = BrexaTypography.titleLarge.copy(color = AccentLime, fontSize = 16.sp)
                )
                Text(
                    text = "${pending.title} • Estipulado: ${formatter.format(pending.amount)} ${pending.currencyCode}",
                    style = BrexaTypography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Selector de Cuenta Utilizada
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "¿CON CUÁL CUENTA PAGASTE?",
                        style = BrexaTypography.labelSmall.copy(color = TextSecondary, letterSpacing = 0.5.sp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(accounts) { acc ->
                            val isSel = acc.id == selectedAccountId
                            val accCurr = currencies.find { it.id == acc.currencyId }
                            Box(
                                modifier = Modifier
                                    .background(if (isSel) SurfaceCardElevated else BackgroundBase, RectangleShape)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSel) AccentLime else BorderSolid,
                                        shape = RectangleShape
                                    )
                                    .clickable { selectedAccountId = acc.id }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = acc.name,
                                        style = BrexaTypography.labelMedium.copy(
                                            color = if (isSel) AccentLime else TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = "${formatter.format(acc.currentBalance)} ${accCurr?.code ?: ""}",
                                        style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Selector de Moneda de Pago
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "¿EN QUÉ MONEDA SE PAGÓ?",
                        style = BrexaTypography.labelSmall.copy(color = TextSecondary, letterSpacing = 0.5.sp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(currencies) { curr ->
                            val isSel = curr.code.equals(selectedPaymentCurrencyCode, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .background(if (isSel) AccentLime else BackgroundBase, RectangleShape)
                                    .border(1.5.dp, if (isSel) AccentLime else BorderSolid, RectangleShape)
                                    .clickable { selectedPaymentCurrencyCode = curr.code }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = curr.code,
                                    style = BrexaTypography.labelMedium.copy(
                                        color = if (isSel) Color.Black else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Monto Real Pagado (Editable)
                BrutalistTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "MONTO REAL PAGADO",
                    suffix = selectedPaymentCurrencyCode,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // 4. Tasa si aplica
                if (isForeignCurrency) {
                    BrutalistTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = "TASA APLICADA (1 USD =)",
                        suffix = selectedPaymentCurrencyCode,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                // 5. Tarjeta Resumen de Salida de Dinero
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundBase, RectangleShape)
                        .border(1.dp, BorderSolid, RectangleShape)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SE DESCONTARÁ DE:",
                                style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                            )
                            Text(
                                text = selectedAccount?.name ?: "Cuenta",
                                style = BrexaTypography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "MONTO FINAL:",
                                style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                            )
                            Text(
                                text = "- ${formatter.format(calculatedDeduction)} $accCurrCode",
                                style = BrexaTypography.titleLarge.copy(
                                    color = ExpenseRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }

                // 6. Selector de Fecha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateFormatted = SimpleDateFormat("dd MMM, yyyy", Locale("es", "ES")).format(Date(paymentDateTimestamp))
                    Text(
                        text = "FECHA: $dateFormatted",
                        style = BrexaTypography.labelMedium.copy(color = TextPrimary, fontSize = 11.5.sp)
                    )

                    BrutalistBadge(
                        text = "📅 CAMBIAR",
                        backgroundColor = BackgroundBase,
                        contentColor = TextPrimary,
                        borderColor = BorderSolid,
                        fontSize = 9.sp,
                        modifier = Modifier.clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = paymentDateTimestamp }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val updated = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    paymentDateTimestamp = updated.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    )
                }

                // 7. Concepto en Historial
                BrutalistTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = "CONCEPTO EN HISTORIAL DE GASTOS",
                    placeholder = "ej. Pago de internet..."
                )

                // 8. Información de destino
                val hint = if (pending.expenseType == "FIXED")
                    "🔄 Al confirmar, se registrará el gasto y su próximo vencimiento avanzará al siguiente ciclo."
                else
                    "✓ Al confirmar, se registrará el gasto y se borrará de tus pagos pendientes."
                Text(
                    text = hint,
                    style = BrexaTypography.bodySmall.copy(color = TextMuted, fontSize = 10.sp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = BrexaTypography.bodySmall.copy(color = ExpenseRed)
                    )
                }

                // Opción para avanzar sin descontar saldo
                Text(
                    text = "• ¿Pagado por un tercero? Avanzar sin descontar saldo",
                    style = BrexaTypography.labelSmall.copy(
                        color = AccentPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.clickable { onAdvanceWithoutDeduct() }
                )
            }
        },
        confirmButton = {
            BrutalistBadge(
                text = "✓ CONFIRMAR PAGO ➔",
                backgroundColor = AccentLime,
                contentColor = Color.Black,
                borderColor = AccentLime,
                fontSize = 11.sp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.clickable {
                    if (selectedAccount == null) {
                        errorMessage = "Selecciona una cuenta de pago."
                        return@clickable
                    }
                    if (parsedAmount <= 0.0) {
                        errorMessage = "El monto debe ser mayor a cero."
                        return@clickable
                    }
                    if (rateNum <= 0.0) {
                        errorMessage = "La tasa debe ser mayor a 0."
                        return@clickable
                    }
                    onConfirmPay(
                        selectedAccount.id,
                        rateNum,
                        calculatedDeduction,
                        noteText,
                        paymentDateTimestamp
                    )
                }
            )
        },
        dismissButton = {
            BrutalistBadge(
                text = "CANCELAR",
                backgroundColor = BackgroundBase,
                contentColor = TextSecondary,
                borderColor = BorderSolid,
                fontSize = 11.sp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    )
}

/**
 * Modal Neobrutalista para Crear o Editar un Compromiso (Gasto Variable o Gasto Fijo Recurrente).
 */
@Composable
fun CreateOrEditPendingExpenseDialog(
    initialItem: PendingExpenseEntity? = null,
    defaultExpenseType: String = "VARIABLE",
    accounts: List<AccountEntity>,
    currencies: List<CurrencyEntity>,
    baseCurrency: CurrencyEntity?,
    categories: List<com.brexa.app.data.local.entity.CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        amount: Double,
        currencyCode: String,
        dueDateTimestamp: Long,
        categoryId: String?,
        accountId: String?,
        note: String,
        expenseType: String,
        recurrencePeriod: String?,
        recurrenceDay: Int?
    ) -> Unit
) {
    val context = LocalContext.current
    var expenseType by remember {
        mutableStateOf(initialItem?.expenseType ?: if (defaultExpenseType == "FIJOS") "FIXED" else "VARIABLE")
    }
    var recurrencePeriod by remember {
        mutableStateOf(initialItem?.recurrencePeriod ?: "MONTHLY") // "MONTHLY", "BIWEEKLY", "WEEKLY"
    }
    var recurrenceDay by remember {
        mutableIntStateOf(initialItem?.recurrenceDay ?: 1) // 1 al 31
    }

    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var amountText by remember {
        mutableStateOf(if (initialItem != null) CurrencyFormat.formatForInput(initialItem.amount) else "")
    }
    var selectedCurrencyCode by remember {
        mutableStateOf(initialItem?.currencyCode ?: baseCurrency?.code ?: "USD")
    }
    var selectedCategoryId by remember { mutableStateOf(initialItem?.categoryId) }
    var selectedAccountId by remember { mutableStateOf(initialItem?.accountId) }
    var note by remember { mutableStateOf(initialItem?.note ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fecha de vencimiento
    var dueTimestamp by remember {
        mutableLongStateOf(
            initialItem?.dueDateTimestamp ?: Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }.timeInMillis
        )
    }

    val dateFormatter = remember { SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es", "ES")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = if (initialItem != null) "EDITAR PAGO" else if (expenseType == "FIXED") "NUEVO GASTO FIJO" else "NUEVO GASTO VARIABLE",
                style = BrexaTypography.titleLarge.copy(color = if (expenseType == "FIXED") AccentPurple else AccentLime)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Selector de Tipo de Gasto (solo al crear)
                if (initialItem == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isVar = expenseType == "VARIABLE"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isVar) AccentLime else BackgroundBase, RectangleShape)
                                .border(1.5.dp, if (isVar) AccentLime else BorderSolid, RectangleShape)
                                .clickable { expenseType = "VARIABLE" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚡ VARIABLE",
                                style = BrexaTypography.labelMedium.copy(
                                    color = if (isVar) Color.Black else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        val isFix = expenseType == "FIXED"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isFix) AccentPurple else BackgroundBase, RectangleShape)
                                .border(1.5.dp, if (isFix) AccentPurple else BorderSolid, RectangleShape)
                                .clickable { expenseType = "FIXED" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔄 FIJO (RECURRENTE)",
                                style = BrexaTypography.labelMedium.copy(
                                    color = if (isFix) Color.White else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Si es Gasto Fijo: Configurar Frecuencia y Día de cobro
                if (expenseType == "FIXED") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "FRECUENCIA DEL GASTO FIJO",
                            style = BrexaTypography.labelSmall.copy(color = TextSecondary, letterSpacing = 0.5.sp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val periods = listOf(
                                Pair("MONTHLY", "MENSUAL"),
                                Pair("BIWEEKLY", "QUINCENAL"),
                                Pair("WEEKLY", "SEMANAL")
                            )
                            periods.forEach { (pKey, pLabel) ->
                                val isSel = recurrencePeriod == pKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (isSel) AccentPurple else BackgroundBase, RectangleShape)
                                        .border(1.5.dp, if (isSel) AccentPurple else BorderSolid, RectangleShape)
                                        .clickable { recurrencePeriod = pKey }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pLabel,
                                        style = BrexaTypography.labelSmall.copy(
                                            color = if (isSel) Color.White else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Si es mensual: Día del mes (ej. Todos los 1 de cada mes)
                        if (recurrencePeriod == "MONTHLY") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "¿QUÉ DÍA DE CADA MES SE PAGA? (1 al 31)",
                                style = BrexaTypography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(BackgroundBase, RectangleShape)
                                        .border(1.dp, BorderSolid, RectangleShape)
                                        .clickable { if (recurrenceDay > 1) recurrenceDay-- }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(text = "−", style = BrexaTypography.titleLarge.copy(color = AccentLime))
                                }

                                Text(
                                    text = "TODOS LOS $recurrenceDay DE CADA MES",
                                    style = BrexaTypography.labelMedium.copy(color = AccentLime, fontWeight = FontWeight.Bold)
                                )

                                Box(
                                    modifier = Modifier
                                        .background(BackgroundBase, RectangleShape)
                                        .border(1.dp, BorderSolid, RectangleShape)
                                        .clickable { if (recurrenceDay < 31) recurrenceDay++ }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(text = "+", style = BrexaTypography.titleLarge.copy(color = AccentLime))
                                }
                            }

                            // Chips rápidos de días populares
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val popularDays = listOf(1, 5, 15, 20, 25, 30)
                                popularDays.forEach { d ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(if (recurrenceDay == d) AccentPurple else BackgroundBase, RectangleShape)
                                            .border(1.dp, if (recurrenceDay == d) AccentPurple else BorderSolid, RectangleShape)
                                            .clickable { recurrenceDay = d }
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "DÍA $d",
                                            style = BrexaTypography.labelSmall.copy(
                                                color = if (recurrenceDay == d) Color.White else TextSecondary,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Concepto / Título
                BrutalistTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "¿QUÉ TIENES QUE PAGAR?",
                    placeholder = if (expenseType == "FIXED") "ej. Pago internet, Alquiler, Netflix..." else "ej. Reparación moto, Compra repuesto..."
                )

                // Monto y Moneda
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = "MONTO ESTIPULADO",
                        placeholder = "0.00",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    Column(modifier = Modifier.width(90.dp)) {
                        Text(
                            text = "MONEDA",
                            style = BrexaTypography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundBase, RectangleShape)
                                .border(1.5.dp, AccentLime, RectangleShape)
                                .clickable {
                                    val codes = currencies.map { it.code }
                                    val nextIndex = (codes.indexOf(selectedCurrencyCode) + 1) % codes.size.coerceAtLeast(1)
                                    selectedCurrencyCode = codes.getOrElse(nextIndex) { "USD" }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$selectedCurrencyCode ⇄",
                                style = BrexaTypography.labelMedium.copy(
                                    color = AccentLime,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Selector de Fecha de Vencimiento / Primer cobro
                Column {
                    Text(
                        text = if (expenseType == "FIXED") "PRÓXIMO VENCIMIENTO" else "¿CUÁNDO DEBES PAGARLO?",
                        style = BrexaTypography.labelSmall.copy(color = TextSecondary, letterSpacing = 0.5.sp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundBase, RectangleShape)
                            .border(1.dp, BorderSolid, RectangleShape)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val status = calculateDueStatus(dueTimestamp)
                            Column {
                                Text(
                                    text = dateFormatter.format(Date(dueTimestamp)),
                                    style = BrexaTypography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = status.label,
                                    style = BrexaTypography.labelSmall.copy(color = AccentLime, fontSize = 10.sp)
                                )
                            }

                            BrutalistBadge(
                                text = "📅 CALENDARIO",
                                backgroundColor = BackgroundBase,
                                contentColor = TextPrimary,
                                borderColor = BorderSolid,
                                fontSize = 9.5.sp,
                                modifier = Modifier.clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = dueTimestamp }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val updated = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, y)
                                                set(Calendar.MONTH, m)
                                                set(Calendar.DAY_OF_MONTH, d)
                                            }
                                            dueTimestamp = updated.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            )
                        }
                    }
                }

                // Cuenta Sugerida de Pago (Opcional)
                if (accounts.isNotEmpty()) {
                    Column {
                        Text(
                            text = "CUENTA SUGERIDA (OPCIONAL)",
                            style = BrexaTypography.labelSmall.copy(color = TextSecondary, letterSpacing = 0.5.sp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(accounts) { acc ->
                                val isSel = acc.id == selectedAccountId
                                Box(
                                    modifier = Modifier
                                        .background(if (isSel) SurfaceCardElevated else BackgroundBase, RectangleShape)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSel) AccentLime else BorderSolid,
                                            shape = RectangleShape
                                        )
                                        .clickable {
                                            selectedAccountId = if (isSel) null else acc.id
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = acc.name,
                                        style = BrexaTypography.labelSmall.copy(
                                            color = if (isSel) AccentLime else TextSecondary,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Categoría Opcional
                if (categories.isNotEmpty()) {
                    Column {
                        Text(
                            text = "CATEGORÍA (OPCIONAL)",
                            style = BrexaTypography.labelSmall.copy(color = TextSecondary, letterSpacing = 0.5.sp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categories) { cat ->
                                val isSel = cat.id == selectedCategoryId
                                Box(
                                    modifier = Modifier
                                        .background(if (isSel) SurfaceCardElevated else BackgroundBase, RectangleShape)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSel) AccentLime else BorderSolid,
                                            shape = RectangleShape
                                        )
                                        .clickable {
                                            selectedCategoryId = if (isSel) null else cat.id
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = cat.name.uppercase(),
                                        style = BrexaTypography.labelSmall.copy(
                                            color = if (isSel) AccentLime else TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Nota adicional
                BrutalistTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "NOTA O DETALLES (OPCIONAL)",
                    placeholder = "ej. Número de contrato, referencia de factura..."
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = BrexaTypography.bodySmall.copy(color = ExpenseRed)
                    )
                }
            }
        },
        confirmButton = {
            BrutalistBadge(
                text = if (initialItem != null) "✓ ACTUALIZAR" else "✓ GUARDAR",
                backgroundColor = if (expenseType == "FIXED") AccentPurple else AccentLime,
                contentColor = if (expenseType == "FIXED") Color.White else Color.Black,
                borderColor = if (expenseType == "FIXED") AccentPurple else AccentLime,
                fontSize = 11.sp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.clickable {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isEmpty()) {
                        errorMessage = "Ingresa el concepto del pago."
                        return@clickable
                    }
                    val amount = CurrencyFormat.parseNumber(amountText)
                    if (amount == null || amount <= 0.0) {
                        errorMessage = "Ingresa un monto válido mayor a cero."
                        return@clickable
                    }

                    onSave(
                        cleanTitle,
                        amount,
                        selectedCurrencyCode,
                        dueTimestamp,
                        selectedCategoryId,
                        selectedAccountId,
                        note,
                        expenseType,
                        if (expenseType == "FIXED") recurrencePeriod else null,
                        if (expenseType == "FIXED" && recurrencePeriod == "MONTHLY") recurrenceDay else null
                    )
                }
            )
        },
        dismissButton = {
            BrutalistBadge(
                text = "CANCELAR",
                backgroundColor = BackgroundBase,
                contentColor = TextSecondary,
                borderColor = BorderSolid,
                fontSize = 11.sp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    )
}
