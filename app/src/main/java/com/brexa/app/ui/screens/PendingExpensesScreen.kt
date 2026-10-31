package com.brexa.app.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.R
import com.brexa.app.data.local.entity.AccountEntity
import com.brexa.app.data.local.entity.PendingExpenseEntity
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistButton
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.components.BrutalistTextField
import com.brexa.app.ui.theme.*
import com.brexa.app.ui.util.CurrencyFormat
import java.math.BigDecimal
import java.math.RoundingMode
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
    val context = LocalContext.current
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

    // Moneda secundaria para equivalencias
    val secondaryCurrencies = remember(currencies) { currencies.filter { !it.isBase } }
    val primarySecondary = secondaryCurrencies.firstOrNull()

    // Filtros de visualización
    // "TODOS", "7_DIAS", "15_DIAS", "ESTE_MES", "PAGADOS"
    var selectedFilter by remember { mutableStateOf("TODOS") }

    // Modales
    var showCreateModal by remember { mutableStateOf(false) }
    var itemToPay by remember { mutableStateOf<PendingExpenseEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<PendingExpenseEntity?>(null) }

    // Filtrar lista según pestaña seleccionada
    val now = System.currentTimeMillis()
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

    val displayedItems = when (selectedFilter) {
        "7_DIAS" -> activePending.filter { it.dueDateTimestamp <= cal7Days }
        "15_DIAS" -> activePending.filter { it.dueDateTimestamp <= cal15Days }
        "ESTE_MES" -> activePending.filter { it.dueDateTimestamp <= calEndOfMonth }
        "PAGADOS" -> paidPending
        else -> activePending
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
                    text = "DINERO QUE DEBES O PAGARÁS PRONTO",
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

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // --- 2. TARJETA RESUMEN FINANCIERO Y SALDO LIBRE REAL ---
            item {
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SurfaceCard,
                    borderColor = if (projections.overdueCount > 0) ExpenseRed else AccentLime,
                    borderWidth = 2.dp,
                    shadowColor = if (projections.overdueCount > 0) ExpenseRed else AccentLime,
                    shadowOffset = 4.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL POR PAGAR",
                                style = BrexaTypography.labelMedium.copy(
                                    color = TextSecondary,
                                    letterSpacing = 1.sp
                                )
                            )

                            if (projections.overdueCount > 0) {
                                BrutalistBadge(
                                    text = "🔴 ${projections.overdueCount} VENCIDO${if (projections.overdueCount > 1) "S" else ""}",
                                    backgroundColor = ExpenseRed,
                                    contentColor = Color.White,
                                    borderColor = ExpenseRed
                                )
                            } else {
                                BrutalistBadge(
                                    text = "${activePending.size} PENDIENTE${if (activePending.size != 1) "S" else ""}",
                                    backgroundColor = BackgroundBase,
                                    contentColor = AccentLime,
                                    borderColor = AccentLime
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val pendingDisplay = if (isIncognito) "$baseSymbol •••••••" else "$baseSymbol ${formatter.format(totalPendingUsd)}"
                        Text(
                            text = pendingDisplay,
                            style = BrexaTypography.displayLarge.copy(
                                color = if (totalPendingUsd > 0.0) ExpenseRed else AccentLime,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )
                        )

                        if (!isIncognito && primarySecondary != null && totalPendingUsd > 0.0) {
                            val eqSec = totalPendingUsd * primarySecondary.rateToUsd
                            Text(
                                text = "≈ ${formatter.format(eqSec)} ${primarySecondary.code}",
                                style = BrexaTypography.labelMedium.copy(
                                    color = AccentPurple,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(BorderSolid)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Saldo Realmente Libre
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SALDO LIBRE REAL",
                                    style = BrexaTypography.labelSmall.copy(
                                        color = AccentLime,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "Dinero en cuentas ($${formatter.format(totalUsd)}) menos compromisos",
                                    style = BrexaTypography.bodySmall.copy(color = TextMuted, fontSize = 10.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val realDisplay = if (isIncognito) "$baseSymbol •••••••" else "$baseSymbol ${formatter.format(realAvailableUsd)}"
                            Text(
                                text = realDisplay,
                                style = BrexaTypography.titleLarge.copy(
                                    color = if (realAvailableUsd >= 0.0) IncomeGreen else ExpenseRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                        }
                    }
                }
            }

            // --- 3. PESTAÑAS DE HORIZONTE TEMPORAL (FILTROS) ---
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tabs = listOf(
                        Triple("TODOS", "TODOS (${projections.countAll})", projections.totalAllUsd),
                        Triple("7_DIAS", "PRÓX. 7 DÍAS (${projections.count7Days})", projections.total7DaysUsd),
                        Triple("15_DIAS", "QUINCENA (${projections.count15Days})", projections.total15DaysUsd),
                        Triple("ESTE_MES", "ESTE MES (${projections.countMonth})", projections.totalMonthUsd),
                        Triple("PAGADOS", "PAGADOS (${paidPending.size})", 0.0)
                    )

                    items(tabs) { (key, label, _) ->
                        val isSelected = selectedFilter == key
                        Box(
                            modifier = Modifier
                                .background(if (isSelected) AccentLime else SurfaceCard, RectangleShape)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) AccentLime else BorderSolid,
                                    shape = RectangleShape
                                )
                                .clickable { selectedFilter = key }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                style = BrexaTypography.labelMedium.copy(
                                    color = if (isSelected) Color.Black else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                    }
                }
            }

            // --- 4. LISTA DE COMPROMISOS / PAGOS ---
            if (displayedItems.isEmpty()) {
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
                                text = if (selectedFilter == "PAGADOS") "NO HAY PAGOS REGISTRADOS" else "¡ESTÁS AL DÍA!",
                                style = BrexaTypography.titleLarge.copy(color = AccentLime, fontSize = 16.sp),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (selectedFilter == "PAGADOS")
                                    "Cuando pagues un compromiso pendiente, aparecerá aquí como comprobante."
                                else
                                    "No tienes compromisos anotados para este periodo. Si tienes una deuda, servicio o gasto próximo, anótalo.",
                                style = BrexaTypography.bodyMedium.copy(color = TextSecondary, fontSize = 12.sp),
                                textAlign = TextAlign.Center
                            )
                            if (selectedFilter != "PAGADOS") {
                                Spacer(modifier = Modifier.height(4.dp))
                                BrutalistBadge(
                                    text = "+ ANOTAR PAGO PENDIENTE",
                                    backgroundColor = AccentLime,
                                    contentColor = Color.Black,
                                    borderColor = AccentLime,
                                    fontSize = 11.sp,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.clickable { showCreateModal = true }
                                )
                            }
                        }
                    }
                }
            } else {
                items(displayedItems, key = { it.id }) { item ->
                    val status = calculateDueStatus(item.dueDateTimestamp)
                    val curr = currencies.find { it.code.equals(item.currencyCode, ignoreCase = true) }
                    val rate = curr?.rateToUsd ?: 1.0
                    val isBase = curr?.isBase == true || item.currencyCode.equals("USD", ignoreCase = true)
                    val equivUsd = if (isBase) item.amount else if (rate > 0.0) item.amount / rate else item.amount

                    val category = expenseCategories.find { it.id == item.categoryId }
                    val account = accounts.find { it.id == item.accountId }

                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = if (item.isPaid) BackgroundBase else SurfaceCard,
                        borderColor = when {
                            item.isPaid -> BorderSolid
                            status.isOverdue -> ExpenseRed
                            status.isDueToday -> AccentLime
                            status.isDueSoon -> AccentLime
                            else -> BorderSolid
                        },
                        borderWidth = if (status.isOverdue || status.isDueToday) 2.dp else 1.5.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Cabecera de la tarjeta: Estado de urgencia y fecha
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.isPaid) {
                                    val paidDateStr = item.paidAtTimestamp?.let { dateFormatter.format(Date(it)) } ?: "PAGADO"
                                    BrutalistBadge(
                                        text = "✓ PAGADO ($paidDateStr)",
                                        backgroundColor = BackgroundBase,
                                        contentColor = IncomeGreen,
                                        borderColor = IncomeGreen,
                                        fontSize = 9.sp
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
                                    val badgeBorder = when {
                                        status.isOverdue -> ExpenseRed
                                        status.isDueToday -> AccentLime
                                        status.isDueSoon -> AccentLime
                                        else -> BorderSolid
                                    }

                                    BrutalistBadge(
                                        text = status.label,
                                        backgroundColor = badgeBg,
                                        contentColor = badgeContent,
                                        borderColor = badgeBorder,
                                        fontSize = 9.sp
                                    )
                                }

                                Text(
                                    text = dateFormatter.format(Date(item.dueDateTimestamp)),
                                    style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.5.sp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Concepto / Título
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    style = BrexaTypography.titleLarge.copy(
                                        color = if (item.isPaid) TextSecondary else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                // Monto nativo en grande
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${formatter.format(item.amount)} ${item.currencyCode}",
                                        style = BrexaTypography.titleLarge.copy(
                                            color = if (item.isPaid) TextSecondary else if (status.isOverdue) ExpenseRed else AccentLime,
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

                            // Detalles opcionales: Categoría, Cuenta y Nota
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
                                            text = account.name,
                                            backgroundColor = BackgroundBase,
                                            contentColor = AccentLime,
                                            borderColor = BorderSolid,
                                            fontSize = 8.5.sp
                                        )
                                    }
                                    if (item.note.isNotBlank()) {
                                        Text(
                                            text = "• ${item.note}",
                                            style = BrexaTypography.bodySmall.copy(color = TextMuted, fontSize = 10.5.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Botones de acción
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!item.isPaid) {
                                    // Botón Pagar Ahora (Acción Principal con 1 Clic)
                                    Box(
                                        modifier = Modifier
                                            .background(AccentLime, RectangleShape)
                                            .border(1.5.dp, BorderBright, RectangleShape)
                                            .clickable { itemToPay = item }
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = "PAGAR AHORA ➔",
                                            style = BrexaTypography.labelMedium.copy(
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                } else {
                                    // Botón Reabrir (para desmarcar)
                                    Box(
                                        modifier = Modifier
                                            .background(BackgroundBase, RectangleShape)
                                            .border(1.dp, BorderSolid, RectangleShape)
                                            .clickable { viewModel.reopenPendingExpense(item.id) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "↺ REABRIR PAGO",
                                            style = BrexaTypography.labelSmall.copy(
                                                color = TextSecondary,
                                                fontSize = 9.5.sp
                                            )
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // --- 5. BOTÓN FIJO INFERIOR: + NUEVO PAGO PENDIENTE ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .border(width = 1.dp, color = BorderSolid)
                .padding(16.dp)
        ) {
            BrutalistButton(
                text = "+ ANOTAR PAGO PENDIENTE",
                onClick = { showCreateModal = true },
                containerColor = AccentLime,
                contentColor = Color.Black,
                shadowColor = AccentLime,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // --- MODAL: REGISTRAR / PAGAR COMPROMISO ---
    if (itemToPay != null) {
        val pending = itemToPay!!
        PayPendingDialog(
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
            onMarkPaidWithoutDeduct = {
                viewModel.markPendingAsPaidWithoutExpense(
                    id = pending.id,
                    onSuccess = { itemToPay = null }
                )
            }
        )
    }

    // --- MODAL: NUEVO PAGO PENDIENTE ---
    if (showCreateModal) {
        CreatePendingExpenseDialog(
            accounts = accounts,
            currencies = currencies,
            baseCurrency = baseCurrency,
            categories = expenseCategories,
            onDismiss = { showCreateModal = false },
            onSave = { title, amount, currCode, dueTs, catId, accId, note ->
                viewModel.addPendingExpense(
                    title = title,
                    amount = amount,
                    currencyCode = currCode,
                    dueDateTimestamp = dueTs,
                    categoryId = catId,
                    accountId = accId,
                    note = note,
                    onSuccess = { showCreateModal = false }
                )
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
                    text = "ELIMINAR PAGO PENDIENTE",
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
 * Modal Neobrutalista para Registrar el Pago en 1 Clic con Selección de Cuenta
 */
@Composable
fun PayPendingDialog(
    pending: PendingExpenseEntity,
    accounts: List<AccountEntity>,
    currencies: List<com.brexa.app.data.local.entity.CurrencyEntity>,
    baseCurrency: com.brexa.app.data.local.entity.CurrencyEntity?,
    onDismiss: () -> Unit,
    onConfirmPay: (accountId: String, rate: Double, overrideNativeAmount: Double?, note: String, dateTimestamp: Long) -> Unit,
    onMarkPaidWithoutDeduct: () -> Unit
) {
    val context = LocalContext.current
    var selectedAccountId by remember {
        mutableStateOf(pending.accountId ?: accounts.firstOrNull()?.id ?: "")
    }
    val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val accountCurrency = currencies.find { it.id == selectedAccount?.currencyId }

    // Tasa editable si aplica
    val isForeignCurrency = accountCurrency?.isBase == false
    var rateText by remember(selectedAccount) {
        val defaultRate = accountCurrency?.rateToUsd ?: 1.0
        mutableStateOf(CurrencyFormat.formatForInput(defaultRate))
    }
    val rateNum = CurrencyFormat.parseNumber(rateText) ?: 1.0

    // Cálculo del débito estimado
    val accCurrCode = accountCurrency?.code ?: "USD"
    val pendCurrCode = pending.currencyCode
    val calculatedNativeAmount = remember(selectedAccount, rateNum, pending) {
        when {
            accCurrCode.equals(pendCurrCode, ignoreCase = true) -> pending.amount
            !accCurrCode.equals("USD", ignoreCase = true) && pendCurrCode.equals("USD", ignoreCase = true) ->
                pending.amount * rateNum
            accCurrCode.equals("USD", ignoreCase = true) && !pendCurrCode.equals("USD", ignoreCase = true) && rateNum > 0.0 ->
                pending.amount / rateNum
            else -> pending.amount
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
                    text = "REGISTRAR PAGO",
                    style = BrexaTypography.titleLarge.copy(color = AccentLime)
                )
                Text(
                    text = "${pending.title} • ${formatter.format(pending.amount)} ${pending.currencyCode}",
                    style = BrexaTypography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Selector de Cuenta
                Text(
                    text = "¿CON CUÁL CUENTA PAGAS?",
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

                // Tasa si la cuenta es en moneda secundaria (ej. Bolívares)
                if (isForeignCurrency) {
                    BrutalistTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = "TASA APLICADA (1 USD =)",
                        suffix = accCurrCode,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                // Tarjeta con resumen del dinero que saldrá de la cuenta
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundBase, RectangleShape)
                        .border(1.dp, BorderSolid, RectangleShape)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SE DESCONTARÁ:",
                            style = BrexaTypography.labelSmall.copy(color = TextSecondary)
                        )
                        Text(
                            text = "- ${formatter.format(calculatedNativeAmount)} $accCurrCode",
                            style = BrexaTypography.titleLarge.copy(
                                color = ExpenseRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }

                // Selector de Fecha
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

                // Nota / Concepto
                BrutalistTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = "CONCEPTO O NOTA",
                    placeholder = "ej. Pago de servicio..."
                )

                if (errorMessage != null) {
                    Text(
                        text = "⚠️ $errorMessage",
                        style = BrexaTypography.labelSmall.copy(color = ExpenseRed)
                    )
                }

                // Opción rápida: solo marcar como pagado sin debitar cuenta
                Text(
                    text = "• Solo marcar como pagado sin descontar saldo",
                    style = BrexaTypography.labelSmall.copy(
                        color = AccentPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    ),
                    modifier = Modifier.clickable {
                        onMarkPaidWithoutDeduct()
                    }
                )
            }
        },
        confirmButton = {
            BrutalistBadge(
                text = "✓ CONFIRMAR PAGO",
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
                    if (rateNum <= 0.0) {
                        errorMessage = "La tasa debe ser mayor a 0."
                        return@clickable
                    }
                    onConfirmPay(
                        selectedAccount.id,
                        rateNum,
                        calculatedNativeAmount,
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
 * Modal Neobrutalista para Añadir un Nuevo Pago Pendiente
 */
@Composable
fun CreatePendingExpenseDialog(
    accounts: List<AccountEntity>,
    currencies: List<com.brexa.app.data.local.entity.CurrencyEntity>,
    baseCurrency: com.brexa.app.data.local.entity.CurrencyEntity?,
    categories: List<com.brexa.app.data.local.entity.CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, currencyCode: String, dueDateTimestamp: Long, categoryId: String?, accountId: String?, note: String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCurrencyCode by remember {
        mutableStateOf(baseCurrency?.code ?: "USD")
    }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fecha de vencimiento (por defecto hoy + 3 días)
    var dueTimestamp by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }.timeInMillis
        )
    }

    val dateFormatter = remember { SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es", "ES")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "NUEVO PAGO PENDIENTE",
                style = BrexaTypography.titleLarge.copy(color = AccentLime)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Concepto
                BrutalistTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "¿QUÉ TIENES QUE PAGAR?",
                    placeholder = "ej. Alquiler, Internet, Tarjeta, Condominio..."
                )

                // Monto y Moneda
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = "MONTO",
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

                // Selector de Fecha de Vencimiento
                Column {
                    Text(
                        text = "¿CUÁNDO DEBES PAGARLO?",
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Accesos rápidos de fecha
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickDays = listOf(
                            Pair("HOY", 0),
                            Pair("MAÑANA", 1),
                            Pair("7 DÍAS", 7),
                            Pair("15 DÍAS", 15)
                        )
                        quickDays.forEach { (lbl, days) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(BackgroundBase, RectangleShape)
                                    .border(1.dp, BorderSolid, RectangleShape)
                                    .clickable {
                                        dueTimestamp = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lbl,
                                    style = BrexaTypography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
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
                    placeholder = "ej. Número de contrato, referencia..."
                )

                if (errorMessage != null) {
                    Text(
                        text = "⚠️ $errorMessage",
                        style = BrexaTypography.labelSmall.copy(color = ExpenseRed)
                    )
                }
            }
        },
        confirmButton = {
            BrutalistBadge(
                text = "GUARDAR PAGO",
                backgroundColor = AccentLime,
                contentColor = Color.Black,
                borderColor = AccentLime,
                fontSize = 11.sp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.clickable {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isEmpty()) {
                        errorMessage = "Escribe qué vas a pagar."
                        return@clickable
                    }
                    val amountVal = CurrencyFormat.parseNumber(amountText) ?: 0.0
                    if (amountVal <= 0.0) {
                        errorMessage = "Ingresa un monto válido mayor a 0."
                        return@clickable
                    }
                    onSave(
                        cleanTitle,
                        amountVal,
                        selectedCurrencyCode,
                        dueTimestamp,
                        selectedCategoryId,
                        selectedAccountId,
                        note.trim()
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
