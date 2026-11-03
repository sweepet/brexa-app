package com.brexa.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.data.local.entity.TransactionEntity
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
import kotlin.math.ceil

@Composable
fun TransactionHistoryScreen(
    viewModel: BrexaViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val allTransactions by viewModel.allTransactions.collectAsState()
    val accounts by viewModel.activeAccounts.collectAsState()
    val currencies by viewModel.allCurrencies.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val expenseCats by viewModel.expenseCategories.collectAsState()
    val incomeCats by viewModel.incomeCategories.collectAsState()
    val isIncognito by viewModel.incognitoMode.collectAsState()

    val categoryMap = remember(expenseCats, incomeCats) {
        (expenseCats + incomeCats).associateBy { it.id }
    }

    val formatter = remember { CurrencyFormat.standardFormatter }
    val dateFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("dd 'de' MMMM, yyyy - HH:mm", Locale("es", "ES")) }

    val baseCode = baseCurrency?.code ?: "USD"
    val baseSymbol = baseCurrency?.symbol ?: "$"

    // Filtros de Estado
    var selectedTypeFilter by remember { mutableStateOf("TODOS") } // TODOS, EXPENSE, INCOME, EXCHANGE, BALANCE_ADJUSTMENT
    var selectedPeriodFilter by remember { mutableStateOf("TODOS") } // TODOS, TODAY, 7_DAYS, 30_DAYS, THIS_MONTH, CUSTOM
    var customDaysBack by remember { mutableIntStateOf(15) }
    var showCustomDaysDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Paginación
    var currentPage by remember { mutableIntStateOf(1) }
    val pageSize = 10
    val listState = rememberLazyListState()

    // Modal de Detalle
    var selectedTxDetail by remember { mutableStateOf<TransactionEntity?>(null) }
    var showConfirmDeleteTx by remember { mutableStateOf(false) }

    // Calcular timestamps para períodos
    val now = remember { System.currentTimeMillis() }
    val (periodStart, periodEnd) = remember(selectedPeriodFilter, customDaysBack, now) {
        val cal = Calendar.getInstance()
        when (selectedPeriodFilter) {
            "TODAY" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            "7_DAYS" -> {
                val start = now - (7L * 24 * 60 * 60 * 1000)
                Pair(start, now)
            }
            "30_DAYS" -> {
                val start = now - (30L * 24 * 60 * 60 * 1000)
                Pair(start, now)
            }
            "THIS_MONTH" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, now)
            }
            "CUSTOM" -> {
                val start = now - (customDaysBack.toLong() * 24 * 60 * 60 * 1000)
                Pair(start, now)
            }
            else -> Pair(0L, Long.MAX_VALUE) // TODOS
        }
    }

    // Filtrado de Transacciones
    val filteredTransactions = remember(
        allTransactions,
        selectedTypeFilter,
        selectedPeriodFilter,
        periodStart,
        periodEnd,
        searchQuery,
        accounts,
        categoryMap
    ) {
        allTransactions.filter { tx ->
            // Filtro por tipo
            val typeMatches = when (selectedTypeFilter) {
                "TODOS" -> true
                else -> tx.type.equals(selectedTypeFilter, ignoreCase = true)
            }

            // Filtro por fecha
            val periodMatches = tx.dateTimestamp in periodStart..periodEnd

            // Filtro por búsqueda de texto
            val searchMatches = if (searchQuery.trim().isEmpty()) {
                true
            } else {
                val query = searchQuery.trim().lowercase(Locale.getDefault())
                val noteMatch = tx.note.lowercase(Locale.getDefault()).contains(query)
                val accName = accounts.find { it.id == tx.accountId }?.name?.lowercase(Locale.getDefault()) ?: ""
                val destAccName = tx.destinationAccountId?.let { id -> accounts.find { it.id == id }?.name?.lowercase(Locale.getDefault()) } ?: ""
                val catName = tx.categoryId?.let { categoryMap[it]?.name?.lowercase(Locale.getDefault()) } ?: ""
                val codeMatch = tx.currencyCode.lowercase(Locale.getDefault()).contains(query)

                noteMatch || accName.contains(query) || destAccName.contains(query) || catName.contains(query) || codeMatch
            }

            typeMatches && periodMatches && searchMatches
        }
    }

    // Resetear página al cambiar filtros
    LaunchedEffect(selectedTypeFilter, selectedPeriodFilter, customDaysBack, searchQuery) {
        currentPage = 1
        listState.scrollToItem(0)
    }

    // Calcular páginas
    val totalPages = remember(filteredTransactions.size) {
        maxOf(1, ceil(filteredTransactions.size.toDouble() / pageSize).toInt())
    }

    val pagedTransactions = remember(filteredTransactions, currentPage) {
        val safePage = currentPage.coerceIn(1, totalPages)
        val start = (safePage - 1) * pageSize
        val end = minOf(start + pageSize, filteredTransactions.size)
        if (start < filteredTransactions.size) {
            filteredTransactions.subList(start, end)
        } else {
            emptyList()
        }
    }

    // Cálculos analíticos rápidos del filtro actual
    val totalExpensesUsd = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == "EXPENSE" }.sumOf { it.usdEquivalent }
    }
    val totalIncomesUsd = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == "INCOME" }.sumOf { it.usdEquivalent }
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HISTORIAL COMPLETO",
                    style = BrexaTypography.titleLarge.copy(color = AccentLime, fontSize = 18.sp)
                )
                Text(
                    text = "${allTransactions.size} REGISTROS EN TOTAL",
                    style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                )
            }
            BrutalistButton(
                text = "VOLVER X",
                onClick = onBack,
                containerColor = SurfaceCardElevated,
                contentColor = TextPrimary,
                borderColor = BorderSolid
            )
        }

        // --- 2. CONTENIDO PRINCIPAL CON SCROLL ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Buscador de Texto
            item {
                BrutalistTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "BUSCADOR DE MOVIMIENTOS",
                    placeholder = "ej. Gasolina, Binance, Almuerzo, Banesco...",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Filtro 1: Selector de Tipo de Movimiento
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "FILTRAR POR TIPO",
                        style = BrexaTypography.labelSmall.copy(color = TextSecondary, letterSpacing = 1.sp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val types = listOf(
                            Pair("TODOS", "TODOS"),
                            Pair("EXPENSE", "− GASTOS"),
                            Pair("INCOME", "+ INGRESOS"),
                            Pair("EXCHANGE", "⇄ EXCHANGES"),
                            Pair("BALANCE_ADJUSTMENT", "⚡ AJUSTES")
                        )
                        types.forEach { (typeKey, label) ->
                            val isSel = selectedTypeFilter == typeKey
                            val accentColor = when (typeKey) {
                                "EXPENSE" -> ExpenseRed
                                "INCOME" -> IncomeGreen
                                "EXCHANGE" -> ExchangeBlue
                                "BALANCE_ADJUSTMENT" -> AccentPurple
                                else -> AccentLime
                            }
                            BrutalistBadge(
                                text = label,
                                backgroundColor = if (isSel) accentColor else BackgroundBase,
                                contentColor = if (isSel) Color.Black else TextPrimary,
                                borderColor = if (isSel) accentColor else BorderSolid,
                                fontSize = 10.sp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                modifier = Modifier.clickable { selectedTypeFilter = typeKey }
                            )
                        }
                    }
                }
            }

            // Filtro 2: Selector de Período de Tiempo
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "FILTRAR POR PERÍODO",
                        style = BrexaTypography.labelSmall.copy(color = TextSecondary, letterSpacing = 1.sp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val periods = listOf(
                            Pair("TODOS", "TODO EL TIEMPO"),
                            Pair("TODAY", "HOY"),
                            Pair("7_DAYS", "ÚLTIMOS 7 DÍAS"),
                            Pair("30_DAYS", "ÚLTIMOS 30 DÍAS"),
                            Pair("THIS_MONTH", "ESTE MES"),
                            Pair("CUSTOM", if (selectedPeriodFilter == "CUSTOM") "ÚLTIMOS $customDaysBack DÍAS 📅" else "PERSONALIZADO 📅")
                        )
                        periods.forEach { (periodKey, label) ->
                            val isSel = selectedPeriodFilter == periodKey
                            BrutalistBadge(
                                text = label,
                                backgroundColor = if (isSel) AccentLime else BackgroundBase,
                                contentColor = if (isSel) Color.Black else TextPrimary,
                                borderColor = if (isSel) AccentLime else BorderSolid,
                                fontSize = 10.sp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                modifier = Modifier.clickable {
                                    if (periodKey == "CUSTOM") {
                                        showCustomDaysDialog = true
                                    } else {
                                        selectedPeriodFilter = periodKey
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Barra de Resumen del Filtro Actual
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, RectangleShape)
                        .border(1.dp, BorderSolid, RectangleShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredTransactions.size} RESULTADOS",
                            style = BrexaTypography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (totalExpensesUsd > 0.0) {
                                Text(
                                    text = "− $baseSymbol ${formatter.format(totalExpensesUsd)}",
                                    style = BrexaTypography.labelSmall.copy(color = ExpenseRed, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                                )
                            }
                            if (totalIncomesUsd > 0.0) {
                                Text(
                                    text = "+ $baseSymbol ${formatter.format(totalIncomesUsd)}",
                                    style = BrexaTypography.labelSmall.copy(color = IncomeGreen, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                                )
                            }
                        }
                    }
                }
            }

            // Lista Paginada de Transacciones (10 por página)
            if (filteredTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCard, RectangleShape)
                            .border(1.5.dp, BorderSolid, RectangleShape)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NO SE ENCONTRARON MOVIMIENTOS",
                                style = BrexaTypography.labelLarge.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Prueba cambiando o limpiando los filtros aplicados.",
                                style = BrexaTypography.labelMedium.copy(color = TextMuted)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            BrutalistButton(
                                text = "RESTABLECER FILTROS",
                                onClick = {
                                    selectedTypeFilter = "TODOS"
                                    selectedPeriodFilter = "TODOS"
                                    searchQuery = ""
                                },
                                containerColor = SurfaceCardElevated,
                                contentColor = AccentLime,
                                borderColor = AccentLime
                            )
                        }
                    }
                }
            } else {
                items(pagedTransactions) { tx ->
                    val acc = accounts.find { it.id == tx.accountId }
                    val destAcc = tx.destinationAccountId?.let { id -> accounts.find { it.id == id } }
                    val cat = tx.categoryId?.let { categoryMap[it] }

                    val isAdjustment = tx.type == "BALANCE_ADJUSTMENT"
                    val adjIsIncrease = if (isAdjustment) {
                        if (tx.destinationReceivedAmount != null) {
                            tx.destinationReceivedAmount > 0
                        } else if (tx.note.contains("(-)") || tx.note.contains("(−)") || tx.note.contains("se restó") || tx.note.contains("bajó")) {
                            false
                        } else {
                            true
                        }
                    } else false

                    val badgeColor = when {
                        tx.type == "EXPENSE" -> ExpenseRed
                        tx.type == "INCOME" -> IncomeGreen
                        tx.type == "EXCHANGE" -> ExchangeBlue
                        isAdjustment && adjIsIncrease -> IncomeGreen
                        isAdjustment && !adjIsIncrease -> ExpenseRed
                        else -> AccentPurple
                    }

                    val typeLabel = when {
                        tx.type == "EXPENSE" -> "GASTO"
                        tx.type == "INCOME" -> "INGRESO"
                        tx.type == "EXCHANGE" -> "CAMBIO"
                        isAdjustment && adjIsIncrease -> "AJUSTE (+)"
                        isAdjustment && !adjIsIncrease -> "AJUSTE (−)"
                        else -> tx.type
                    }

                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = SurfaceCard,
                        borderColor = BorderSolid,
                        onClick = {
                            selectedTxDetail = tx
                            showConfirmDeleteTx = false
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Izquierda: Badge y Concepto
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BrutalistBadge(
                                        text = typeLabel,
                                        backgroundColor = BackgroundBase,
                                        contentColor = badgeColor,
                                        borderColor = badgeColor,
                                        fontSize = 8.5.sp,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (tx.note.isNotEmpty()) tx.note else (cat?.name ?: if (tx.type == "EXCHANGE") "Exchange entre cuentas" else "Sin descripción"),
                                        style = BrexaTypography.titleMedium.copy(
                                            color = TextPrimary,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val subtitle = buildAnnotatedString {
                                    withStyle(SpanStyle(color = TextMuted)) {
                                        append(acc?.name ?: "Cuenta")
                                        if (destAcc != null) append(" ➔ ${destAcc.name}")
                                    }
                                    if (tx.type == "EXCHANGE" && tx.implicitExchangeRate != null && tx.implicitExchangeRate > 0.0) {
                                        val destCurr = currencies.find { it.id == destAcc?.currencyId }
                                        val destCurrCode = destCurr?.code ?: ""
                                        val isSrcDollar = tx.currencyCode.uppercase() in listOf("USD", "USDT", "USDC")
                                        val isDestDollar = destCurrCode.uppercase() in listOf("USD", "USDT", "USDC")
                                        val rateSummary = when {
                                            !isSrcDollar && isDestDollar -> "1 $destCurrCode = ${CurrencyFormat.formatRate(tx.implicitExchangeRate)} ${tx.currencyCode}"
                                            else -> "1 ${tx.currencyCode} = ${CurrencyFormat.formatRate(tx.implicitExchangeRate)} $destCurrCode"
                                        }
                                        withStyle(SpanStyle(color = TextMuted)) { append(" • ") }
                                        withStyle(SpanStyle(color = ExchangeBlue, fontWeight = FontWeight.Bold)) { append(rateSummary) }
                                    }
                                    if (cat != null) {
                                        withStyle(SpanStyle(color = TextMuted)) { append(" • ") }
                                        withStyle(SpanStyle(color = AccentLime)) { append(cat.name) }
                                    }
                                    withStyle(SpanStyle(color = TextMuted)) {
                                        append(" • ")
                                        append(dateFormatter.format(Date(tx.dateTimestamp)))
                                    }
                                }
                                Text(
                                    text = subtitle,
                                    style = BrexaTypography.labelMedium.copy(fontSize = 10.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Derecha: Cifras
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.wrapContentWidth()) {
                                val prefix = when {
                                    tx.type == "EXPENSE" -> "− "
                                    tx.type == "INCOME" -> "+ "
                                    tx.type == "EXCHANGE" -> "⇄ "
                                    isAdjustment && adjIsIncrease -> "+ "
                                    isAdjustment && !adjIsIncrease -> "− "
                                    else -> ""
                                }
                                val amountColor = when {
                                    tx.type == "EXPENSE" -> ExpenseRed
                                    tx.type == "INCOME" -> IncomeGreen
                                    tx.type == "EXCHANGE" -> ExchangeBlue
                                    isAdjustment && adjIsIncrease -> IncomeGreen
                                    isAdjustment && !adjIsIncrease -> ExpenseRed
                                    else -> TextPrimary
                                }
                                Text(
                                    text = if (isIncognito) "••••••" else "$prefix${formatter.format(tx.nativeAmount)} ${tx.currencyCode}",
                                    style = BrexaTypography.labelLarge.copy(
                                        color = amountColor,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val secondaryLine = if (isIncognito) {
                                    "••••••"
                                } else if (tx.type == "EXCHANGE" && tx.destinationReceivedAmount != null) {
                                    val destCurr = currencies.find { it.id == destAcc?.currencyId }
                                    "➔ ${formatter.format(tx.destinationReceivedAmount)} ${destCurr?.code ?: ""}"
                                } else {
                                    "≈ $baseSymbol ${formatter.format(tx.usdEquivalent)} $baseCode"
                                }
                                Text(
                                    text = secondaryLine,
                                    style = BrexaTypography.labelMedium.copy(
                                        color = if (tx.type == "EXCHANGE") IncomeGreen else AccentLime,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // --- 3. BARRA DE CONTROLES DE PAGINACIÓN ---
            if (filteredTransactions.size > pageSize) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCardElevated, RectangleShape)
                            .border(1.5.dp, BorderSolid, RectangleShape)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrutalistButton(
                                text = "← ANTERIOR",
                                onClick = { if (currentPage > 1) currentPage-- },
                                enabled = currentPage > 1,
                                containerColor = BackgroundBase,
                                contentColor = if (currentPage > 1) AccentLime else TextMuted,
                                borderColor = if (currentPage > 1) AccentLime else BorderSolid
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "PÁG $currentPage DE $totalPages",
                                    style = BrexaTypography.labelMedium.copy(
                                        color = AccentLime,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                val startCount = (currentPage - 1) * pageSize + 1
                                val endCount = minOf(currentPage * pageSize, filteredTransactions.size)
                                Text(
                                    text = "$startCount-$endCount de ${filteredTransactions.size}",
                                    style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                                )
                            }

                            BrutalistButton(
                                text = "SIGUIENTE →",
                                onClick = { if (currentPage < totalPages) currentPage++ },
                                enabled = currentPage < totalPages,
                                containerColor = BackgroundBase,
                                contentColor = if (currentPage < totalPages) AccentLime else TextMuted,
                                borderColor = if (currentPage < totalPages) AccentLime else BorderSolid
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Modal para Período Personalizado
    if (showCustomDaysDialog) {
        var daysInput by remember { mutableStateOf(customDaysBack.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomDaysDialog = false },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "PERÍODO PERSONALIZADO",
                    style = BrexaTypography.titleLarge.copy(color = AccentLime, fontSize = 16.sp)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "¿Cuántos días atrás deseas incluir en el historial?",
                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(7, 15, 30, 60, 90).forEach { presetDays ->
                            BrutalistBadge(
                                text = "$presetDays DÍAS",
                                backgroundColor = if (daysInput == presetDays.toString()) AccentLime else BackgroundBase,
                                contentColor = if (daysInput == presetDays.toString()) Color.Black else TextPrimary,
                                borderColor = if (daysInput == presetDays.toString()) AccentLime else BorderSolid,
                                modifier = Modifier.clickable { daysInput = presetDays.toString() }
                            )
                        }
                    }
                    BrutalistTextField(
                        value = daysInput,
                        onValueChange = { daysInput = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = "CANTIDAD DE DÍAS (EJ. 15, 45, 90)",
                        placeholder = "15"
                    )
                }
            },
            confirmButton = {
                BrutalistButton(
                    text = "APLICAR FILTRO",
                    onClick = {
                        val parsed = daysInput.toIntOrNull() ?: 15
                        customDaysBack = maxOf(1, parsed)
                        selectedPeriodFilter = "CUSTOM"
                        showCustomDaysDialog = false
                    },
                    containerColor = AccentLime,
                    contentColor = Color.Black
                )
            },
            dismissButton = {
                BrutalistButton(
                    text = "CANCELAR",
                    onClick = { showCustomDaysDialog = false },
                    containerColor = BackgroundBase,
                    contentColor = TextSecondary,
                    borderColor = BorderSolid
                )
            }
        )
    }

    // Modal de Detalle de Movimiento
    if (selectedTxDetail != null) {
        val tx = selectedTxDetail!!
        val acc = accounts.find { it.id == tx.accountId }
        val destAcc = tx.destinationAccountId?.let { id -> accounts.find { it.id == id } }
        val cat = tx.categoryId?.let { categoryMap[it] }

        val isAdjustment = tx.type == "BALANCE_ADJUSTMENT"
        val adjIsIncrease = if (isAdjustment) {
            if (tx.destinationReceivedAmount != null) {
                tx.destinationReceivedAmount > 0
            } else if (tx.note.contains("(-)") || tx.note.contains("(−)") || tx.note.contains("se restó") || tx.note.contains("bajó")) {
                false
            } else {
                true
            }
        } else false

        val badgeColor = when {
            tx.type == "EXPENSE" -> ExpenseRed
            tx.type == "INCOME" -> IncomeGreen
            tx.type == "EXCHANGE" -> ExchangeBlue
            isAdjustment && adjIsIncrease -> IncomeGreen
            isAdjustment && !adjIsIncrease -> ExpenseRed
            else -> AccentPurple
        }

        val typeLabel = when {
            tx.type == "EXPENSE" -> "GASTO"
            tx.type == "INCOME" -> "INGRESO"
            tx.type == "EXCHANGE" -> "EXCHANGE"
            isAdjustment && adjIsIncrease -> "AJUSTE (+)"
            isAdjustment && !adjIsIncrease -> "AJUSTE (−)"
            else -> tx.type
        }

        val prefix = when {
            tx.type == "EXPENSE" -> "− "
            tx.type == "INCOME" -> "+ "
            tx.type == "EXCHANGE" -> "⇄ "
            isAdjustment && adjIsIncrease -> "+ "
            isAdjustment && !adjIsIncrease -> "− "
            else -> ""
        }

        val amountColor = when {
            tx.type == "EXPENSE" -> ExpenseRed
            tx.type == "INCOME" -> IncomeGreen
            isAdjustment && adjIsIncrease -> IncomeGreen
            isAdjustment && !adjIsIncrease -> ExpenseRed
            else -> TextPrimary
        }

        AlertDialog(
            onDismissRequest = {
                selectedTxDetail = null
                showConfirmDeleteTx = false
            },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETALLE DE MOVIMIENTO",
                        style = BrexaTypography.titleLarge.copy(color = AccentLime, fontSize = 14.sp),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BrutalistBadge(
                        text = typeLabel,
                        backgroundColor = BackgroundBase,
                        contentColor = badgeColor,
                        borderColor = badgeColor,
                        fontSize = 8.5.sp,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Tarjeta de Cifras
                    if (tx.type == "EXCHANGE" && tx.destinationReceivedAmount != null) {
                        val destCurr = currencies.find { it.id == destAcc?.currencyId }
                        val destCurrCode = destCurr?.code ?: ""
                        val isSrcDollar = tx.currencyCode.uppercase() in listOf("USD", "USDT", "USDC")
                        val isDestDollar = destCurrCode.uppercase() in listOf("USD", "USDT", "USDC")
                        val rateText = if (tx.implicitExchangeRate != null && tx.implicitExchangeRate > 0.0) {
                            when {
                                !isSrcDollar && isDestDollar -> "1 $destCurrCode = ${CurrencyFormat.formatRate(tx.implicitExchangeRate)} ${tx.currencyCode}"
                                else -> "1 ${tx.currencyCode} = ${CurrencyFormat.formatRate(tx.implicitExchangeRate)} $destCurrCode"
                            }
                        } else null

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundBase, RectangleShape)
                                .border(1.5.dp, ExchangeBlue, RectangleShape)
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "ENTREGADO (ORIGEN)", style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                                    Text(
                                        text = "− ${formatter.format(tx.nativeAmount)} ${tx.currencyCode}",
                                        style = BrexaTypography.titleMedium.copy(color = ExpenseRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    )
                                }
                                Text(text = "➔", style = BrexaTypography.titleLarge.copy(color = ExchangeBlue, fontSize = 20.sp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "RECIBIDO (DESTINO)", style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                                    Text(
                                        text = "+ ${formatter.format(tx.destinationReceivedAmount)} $destCurrCode",
                                        style = BrexaTypography.titleMedium.copy(color = IncomeGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    )
                                }
                            }

                            if (rateText != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceCard, RectangleShape)
                                        .border(1.dp, BorderSolid, RectangleShape)
                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "TASA APLICADA: $rateText",
                                        style = BrexaTypography.labelMedium.copy(
                                            color = AccentLime,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "≈ $baseSymbol ${formatter.format(tx.usdEquivalent)} $baseCode",
                                style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundBase, RectangleShape)
                                .border(1.5.dp, BorderSolid, RectangleShape)
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$prefix${formatter.format(tx.nativeAmount)} ${tx.currencyCode}",
                                style = BrexaTypography.titleLarge.copy(
                                    color = amountColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "≈ $baseSymbol ${formatter.format(tx.usdEquivalent)} $baseCode",
                                style = BrexaTypography.labelLarge.copy(
                                    color = AccentLime,
                                    fontSize = 13.sp
                                )
                            )
                            if (tx.currencyCode != baseCode) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tasa de cotización: 1 $baseCode = ${CurrencyFormat.formatRate(tx.exchangeRate)} ${tx.currencyCode}",
                                    style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                                )
                            }
                        }
                    }

                    // Indicador y botón interactivo para Ajustes de Saldo
                    if (isAdjustment) {
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (adjIsIncrease) "✓ ESTE MONTO SE SUMÓ AL SALDO" else "− ESTE MONTO SE RESTÓ DEL SALDO",
                                        style = BrexaTypography.labelSmall.copy(
                                            color = if (adjIsIncrease) IncomeGreen else ExpenseRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = if (adjIsIncrease) "Toca si en realidad querías restar (−)" else "Toca si en realidad querías sumar (+)",
                                        style = BrexaTypography.bodySmall.copy(color = TextMuted, fontSize = 9.5.sp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                BrutalistBadge(
                                    text = if (adjIsIncrease) "CAMBIAR A (−)" else "CAMBIAR A (+)",
                                    backgroundColor = BackgroundBase,
                                    contentColor = AccentLime,
                                    borderColor = AccentLime,
                                    fontSize = 8.5.sp,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.clickable {
                                        viewModel.toggleAdjustmentSign(
                                            transactionId = tx.id,
                                            onSuccess = { updated ->
                                                selectedTxDetail = updated
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Concepto / Nota completa
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "CONCEPTO // NOTA",
                            style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp, letterSpacing = 0.5.sp)
                        )
                        Text(
                            text = if (tx.note.isNotEmpty()) tx.note else (cat?.name ?: "Sin descripción"),
                            style = BrexaTypography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Cuenta y Categoría
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (destAcc != null) "ORIGEN ➔ DESTINO" else "CUENTA",
                                style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                            )
                            Text(
                                text = if (destAcc != null) "${acc?.name ?: "Origen"} ➔ ${destAcc.name}" else (acc?.name ?: "Cuenta"),
                                style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                            )
                        }

                        if (cat != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "CATEGORÍA",
                                    style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                                )
                                Text(
                                    text = cat.name,
                                    style = BrexaTypography.bodyMedium.copy(color = AccentLime)
                                )
                            }
                        }
                    }

                    // Fecha y Hora
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "FECHA Y HORA",
                            style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                        )
                        Text(
                            text = fullDateFormatter.format(Date(tx.dateTimestamp)),
                            style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                        )
                    }
                }
            },
            confirmButton = {
                if (showConfirmDeleteTx) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrutalistButton(
                            text = "CANCELAR",
                            onClick = { showConfirmDeleteTx = false },
                            containerColor = BackgroundBase,
                            contentColor = TextSecondary,
                            borderColor = BorderSolid
                        )
                        BrutalistButton(
                            text = "SÍ, ELIMINAR 🗑",
                            onClick = {
                                viewModel.deleteTransaction(tx.id) {
                                    selectedTxDetail = null
                                    showConfirmDeleteTx = false
                                }
                            },
                            containerColor = ExpenseRed,
                            contentColor = Color.Black
                        )
                    }
                } else {
                    BrutalistButton(
                        text = "🗑 ELIMINAR",
                        onClick = { showConfirmDeleteTx = true },
                        containerColor = BackgroundBase,
                        contentColor = ExpenseRed,
                        borderColor = ExpenseRed
                    )
                }
            },
            dismissButton = {
                if (!showConfirmDeleteTx) {
                    BrutalistButton(
                        text = "CERRAR",
                        onClick = { selectedTxDetail = null },
                        containerColor = BackgroundBase,
                        contentColor = TextPrimary,
                        borderColor = BorderSolid
                    )
                }
            }
        )
    }
}
