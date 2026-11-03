package com.brexa.app.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.R
import com.brexa.app.data.local.entity.AccountEntity
import com.brexa.app.data.local.entity.CurrencyEntity
import com.brexa.app.data.local.entity.TransactionEntity
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistButton
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.components.OnboardingTour
import com.brexa.app.ui.theme.*
import com.brexa.app.ui.util.CurrencyFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: BrexaViewModel,
    onOpenTransactionForm: (String) -> Unit,
    onOpenAccountsManagement: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPendingExpenses: () -> Unit,
    onOpenTransactionHistory: () -> Unit = {},
    forceStartTour: Boolean = false,
    onTourDismissed: () -> Unit = {}
) {
    val totalUsd by viewModel.consolidatedUsdBalance.collectAsState()
    val accounts by viewModel.activeAccounts.collectAsState()
    val currencies by viewModel.allCurrencies.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val isIncognito by viewModel.incognitoMode.collectAsState()
    val expenseCats by viewModel.expenseCategories.collectAsState()
    val incomeCats by viewModel.incomeCategories.collectAsState()
    val activePending by viewModel.activePendingExpenses.collectAsState()
    val totalPendingUsd by viewModel.totalPendingUsd.collectAsState()
    val realAvailableUsd by viewModel.realAvailableBalanceUsd.collectAsState()
    val projections by viewModel.pendingProjections.collectAsState()
    val categoryMap = remember(expenseCats, incomeCats) {
        (expenseCats + incomeCats).associateBy { it.id }
    }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("brexa_prefs", Context.MODE_PRIVATE) }
    var showTour by remember {
        mutableStateOf(forceStartTour || !prefs.getBoolean("has_seen_onboarding_tour", false))
    }
    var tourStepIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var totalCardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var quickActionsCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var accountsHeaderCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var totalCardRect by remember { mutableStateOf<Rect?>(null) }
    var quickActionsRect by remember { mutableStateOf<Rect?>(null) }
    var accountsHeaderRect by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(forceStartTour) {
        if (forceStartTour) {
            tourStepIndex = 0
            showTour = true
        }
    }

    LaunchedEffect(showTour, tourStepIndex) {
        if (showTour) {
            val targetIdx = when (tourStepIndex) {
                0 -> 0 // Tarjeta Dinero Total
                1 -> 2 // Botones Rápidos (− GASTAR, + INGRESAR, ⇄ TRANSFERIR)
                2 -> 3 // Header Mis Cuentas / Gestionar
                else -> 0
            }
            try {
                listState.animateScrollToItem(targetIdx)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(tourStepIndex, listState.firstVisibleItemScrollOffset, listState.firstVisibleItemIndex) {
        val root = rootLayoutCoordinates ?: return@LaunchedEffect
        if (!root.isAttached) return@LaunchedEffect
        totalCardCoordinates?.takeIf { it.isAttached }?.let { totalCardRect = root.localBoundingBoxOf(it) }
        quickActionsCoordinates?.takeIf { it.isAttached }?.let { quickActionsRect = root.localBoundingBoxOf(it) }
        accountsHeaderCoordinates?.takeIf { it.isAttached }?.let { accountsHeaderRect = root.localBoundingBoxOf(it) }
    }

    var selectedTxDetail by remember { mutableStateOf<TransactionEntity?>(null) }
    var showConfirmDeleteTx by remember { mutableStateOf(false) }

    val formatter = remember { CurrencyFormat.standardFormatter }
    val dateFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("dd 'de' MMMM, yyyy - HH:mm", Locale("es", "ES")) }

    // Moneda Base
    val baseCode = baseCurrency?.code ?: "USD"
    val baseSymbol = baseCurrency?.symbol ?: "$"

    // Desglose de patrimonio por moneda (ordenado: moneda base primero, luego mayor saldo a menor)
    val currencyBreakdown = remember(accounts, currencies) {
        val includedAccounts = accounts.filter { it.includeInTotal }
        val grouped = includedAccounts.groupBy { it.currencyId }
        currencies.mapNotNull { curr ->
            val accs = grouped[curr.id] ?: emptyList()
            val nativeSum = accs.sumOf { it.currentBalance }
            if (accs.isNotEmpty()) {
                val usdSum = if (curr.isBase) {
                    nativeSum
                } else {
                    val rate = if (curr.rateToUsd > 0.0) curr.rateToUsd else 1.0
                    nativeSum / rate
                }
                Triple(curr, nativeSum, usdSum)
            } else null
        }.sortedWith(
            compareByDescending<Triple<CurrencyEntity, Double, Double>> { it.first.isBase }
                .thenByDescending { it.third }
        )
    }
    var showBreakdownModal by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { root ->
                rootLayoutCoordinates = root
                totalCardCoordinates?.takeIf { it.isAttached }?.let { totalCardRect = root.localBoundingBoxOf(it) }
                quickActionsCoordinates?.takeIf { it.isAttached }?.let { quickActionsRect = root.localBoundingBoxOf(it) }
                accountsHeaderCoordinates?.takeIf { it.isAttached }?.let { accountsHeaderRect = root.localBoundingBoxOf(it) }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBase)
        ) {
            // --- 1. BARRA SUPERIOR TERMINAL (100% RESPONSIVE) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard)
                    .border(width = 1.dp, color = BorderSolid)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(BackgroundBase, RectangleShape)
                            .border(1.5.dp, AccentLime, RectangleShape)
                            .padding(4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_brexa),
                            contentDescription = "Brexa",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BREXA // TERMINAL",
                        style = BrexaTypography.labelLarge.copy(color = AccentLime, fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Botón Incógnito
                    BrutalistBadge(
                        text = if (isIncognito) "👁 OCULTO" else "👁 VISIBLE",
                        backgroundColor = if (isIncognito) ExpenseRed else BackgroundBase,
                        contentColor = if (isIncognito) Color.Black else TextPrimary,
                        borderColor = if (isIncognito) ExpenseRed else BorderSolid,
                        modifier = Modifier.clickable { viewModel.toggleIncognito() }
                    )
                    // Botón Analytics
                    BrutalistBadge(
                        text = "📊",
                        backgroundColor = BackgroundBase,
                        contentColor = TextPrimary,
                        borderColor = BorderSolid,
                        modifier = Modifier.clickable { onOpenAnalytics() }
                    )
                    // Botón Ajustes
                    BrutalistBadge(
                        text = "⚙",
                        backgroundColor = BackgroundBase,
                        contentColor = TextPrimary,
                        borderColor = BorderSolid,
                        modifier = Modifier.clickable { onOpenSettings() }
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
            // --- 2. TARJETA PRINCIPAL: PATRIMONIO TOTAL CONSOLIDADO ---
            item {
                BrutalistCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                totalCardCoordinates = coords
                                rootLayoutCoordinates?.takeIf { it.isAttached }?.let { root ->
                                    totalCardRect = root.localBoundingBoxOf(coords)
                                }
                            }
                        },
                    backgroundColor = SurfaceCard,
                    borderColor = AccentLime,
                    borderWidth = 2.dp,
                    shadowColor = AccentLime,
                    shadowOffset = 4.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "DINERO TOTAL",
                                style = BrexaTypography.labelMedium.copy(
                                    color = TextSecondary,
                                    letterSpacing = 1.sp
                                )
                            )
                            BrutalistBadge(
                                text = "$baseCode BASE",
                                backgroundColor = BackgroundBase,
                                contentColor = AccentLime,
                                borderColor = AccentLime
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val bcvCurrency = remember(currencies) {
                            currencies.find { it.code.equals("Bs", ignoreCase = true) || it.code.equals("VES", ignoreCase = true) }
                        }
                        val copCurrency = remember(currencies) {
                            currencies.find { it.code.equals("COP", ignoreCase = true) }
                        }

                        // 1. Monto Total Consolidado (Limpio y destacado a ancho completo)
                        val balanceDisplay = if (isIncognito) "$baseSymbol •••••••" else "$baseSymbol ${formatter.format(totalUsd)}"
                        Text(
                            text = balanceDisplay,
                            style = BrexaTypography.displayLarge.copy(
                                color = AccentLime,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (balanceDisplay.length > 13) 24.sp else 30.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 2. Desglose Unificado por Moneda (Anti-Rupturas: Top 2 en tarjeta + Modal de Patrimonio al tocar)
                        if (currencyBreakdown.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BackgroundBase, RectangleShape)
                                    .border(1.dp, BorderSolid, RectangleShape)
                                    .clickable { showBreakdownModal = true }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Fila de las Top 2 monedas
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        val visibleCurrencies = currencyBreakdown.take(2)
                                        visibleCurrencies.forEachIndexed { index, (curr, _, usdSum) ->
                                            if (index > 0) {
                                                Text(
                                                    text = "•",
                                                    style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                                                )
                                            }
                                            val labelText = if (curr.isBase) "En Efectivo" else "En ${curr.code}"
                                            val valueColor = if (curr.isBase) AccentLime else AccentPurple
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "$labelText = ",
                                                    style = BrexaTypography.labelSmall.copy(
                                                        color = TextSecondary,
                                                        fontWeight = FontWeight.Normal,
                                                        fontSize = 11.sp
                                                    )
                                                )
                                                Text(
                                                    text = if (isIncognito) "••••" else "$baseSymbol ${formatter.format(usdSum)}",
                                                    style = BrexaTypography.labelSmall.copy(
                                                        color = valueColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.5.sp
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    // Si hay más de 2 monedas, mostrar insignia Neobrutalista "+X MÁS ▾"
                                    if (currencyBreakdown.size > 2) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        BrutalistBadge(
                                            text = "+${currencyBreakdown.size - 2} MÁS ▾",
                                            backgroundColor = SurfaceCardElevated,
                                            contentColor = AccentLime,
                                            borderColor = AccentLime,
                                            fontSize = 9.sp,
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Barra Inferior de Tasas Oficiales (Ordenada, simétrica y con acceso directo)
                        if (bcvCurrency != null || copCurrency != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(BorderSolid)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAccountsManagement() },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "🏛 OFICIAL:",
                                        style = BrexaTypography.labelSmall.copy(
                                            color = TextMuted,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (bcvCurrency != null) {
                                        Text(
                                            text = "BCV: ${formatter.format(bcvCurrency.rateToUsd)} Bs",
                                            style = BrexaTypography.labelSmall.copy(
                                                color = AccentPurple,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            )
                                        )
                                    }
                                }

                                if (copCurrency != null) {
                                    Text(
                                        text = "TRM: ${formatter.format(copCurrency.rateToUsd)} COP",
                                        style = BrexaTypography.labelSmall.copy(
                                            color = ExchangeBlue,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        if (totalPendingUsd > 0.0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(BorderSolid)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SALDO LIBRE REAL:",
                                    style = BrexaTypography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                                )
                                Text(
                                    text = if (isIncognito) "$baseSymbol •••••••" else "$baseSymbol ${formatter.format(realAvailableUsd)}",
                                    style = BrexaTypography.labelMedium.copy(
                                        color = if (realAvailableUsd >= 0.0) IncomeGreen else ExpenseRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // --- 2.1 WIDGET ACCESO A PAGOS PENDIENTES ---
            item {
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SurfaceCard,
                    borderColor = if (projections.overdueCount > 0) ExpenseRed else BorderSolid,
                    borderWidth = 1.5.dp,
                    onClick = onOpenPendingExpenses
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "📋 PAGOS PENDIENTES",
                                style = BrexaTypography.labelMedium.copy(
                                    color = if (projections.overdueCount > 0) ExpenseRed else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (activePending.isNotEmpty()) {
                                val badgeText = if (projections.overdueCount > 0) {
                                    "${projections.overdueCount} VENCIDO${if (projections.overdueCount > 1) "S" else ""}"
                                } else {
                                    "${activePending.size} POR PAGAR"
                                }
                                BrutalistBadge(
                                    text = badgeText,
                                    backgroundColor = if (projections.overdueCount > 0) ExpenseRed else BackgroundBase,
                                    contentColor = if (projections.overdueCount > 0) Color.White else AccentLime,
                                    borderColor = if (projections.overdueCount > 0) ExpenseRed else AccentLime,
                                    fontSize = 9.sp
                                )
                            } else {
                                BrutalistBadge(
                                    text = "AL DÍA ✓",
                                    backgroundColor = BackgroundBase,
                                    contentColor = IncomeGreen,
                                    borderColor = IncomeGreen,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (totalPendingUsd > 0.0) {
                                Text(
                                    text = if (isIncognito) "•••••••" else "-$baseSymbol${formatter.format(totalPendingUsd)}",
                                    style = BrexaTypography.labelMedium.copy(
                                        color = ExpenseRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "➔",
                                style = BrexaTypography.labelMedium.copy(
                                    color = AccentLime,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // --- 3. BOTONES DE ACCIÓN RÁPIDA (100% RESPONSIVE) ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                quickActionsCoordinates = coords
                                rootLayoutCoordinates?.takeIf { it.isAttached }?.let { root ->
                                    quickActionsRect = root.localBoundingBoxOf(coords)
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón Gasto
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(ExpenseRed, RectangleShape)
                            .border(2.dp, BorderBright, RectangleShape)
                            .clickable { onOpenTransactionForm("EXPENSE") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "− GASTAR",
                            style = BrexaTypography.labelLarge.copy(
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Botón Ingreso
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(IncomeGreen, RectangleShape)
                            .border(2.dp, BorderBright, RectangleShape)
                            .clickable { onOpenTransactionForm("INCOME") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ INGRESAR",
                            style = BrexaTypography.labelLarge.copy(
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Botón Exchange
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(ExchangeBlue, RectangleShape)
                            .border(2.dp, BorderBright, RectangleShape)
                            .clickable { onOpenTransactionForm("EXCHANGE") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⇄ TRANSFERIR",
                            style = BrexaTypography.labelLarge.copy(
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // --- 4. CARRUSEL DE CUENTAS ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                accountsHeaderCoordinates = coords
                                rootLayoutCoordinates?.takeIf { it.isAttached }?.let { root ->
                                    accountsHeaderRect = root.localBoundingBoxOf(coords)
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MIS CUENTAS (${accounts.size}/11)",
                        style = BrexaTypography.labelLarge.copy(color = TextPrimary)
                    )
                    Text(
                        text = "GESTIONAR ➔",
                        style = BrexaTypography.labelMedium.copy(
                            color = AccentLime,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.clickable { onOpenAccountsManagement() }
                    )
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(accounts) { account ->
                        val curr = currencies.find { it.id == account.currencyId }
                        val rate = curr?.rateToUsd ?: 1.0
                        val usdVal = if (rate > 0.0) account.currentBalance / rate else account.currentBalance

                        val iconRes = when (account.iconType) {
                            "CASH" -> R.drawable.ic_account_cash
                            "BANK" -> R.drawable.ic_account_bank
                            "CRYPTO" -> R.drawable.ic_account_crypto
                            else -> R.drawable.ic_account_general
                        }

                        BrutalistCard(
                            modifier = Modifier
                                .width(180.dp)
                                .height(130.dp),
                            backgroundColor = SurfaceCard,
                            borderColor = if (account.isDefault) AccentLime else BorderSolid,
                            borderWidth = 2.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = account.name,
                                        tint = if (account.isDefault) AccentLime else AccentPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    BrutalistBadge(
                                        text = curr?.code ?: "USD",
                                        backgroundColor = BackgroundBase,
                                        contentColor = TextPrimary,
                                        borderColor = BorderSolid
                                    )
                                }

                                Column {
                                    Text(
                                        text = account.name.uppercase(),
                                        style = BrexaTypography.labelLarge.copy(
                                            fontSize = 12.sp,
                                            color = TextPrimary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isIncognito) "••••••" else "${formatter.format(account.currentBalance)} ${curr?.symbol ?: ""}",
                                        style = BrexaTypography.titleLarge.copy(
                                            color = if (account.currentBalance < 0.0) ExpenseRed else TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (curr?.isBase == false) {
                                        Text(
                                            text = if (isIncognito) "≈ $baseSymbol •••" else "≈ $baseSymbol ${formatter.format(usdVal)} $baseCode",
                                            style = BrexaTypography.labelMedium.copy(
                                                color = AccentLime,
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
                }
            }

            // --- 5. MOVIMIENTOS RECIENTES ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HISTORIAL DE MOVIMIENTOS",
                        style = BrexaTypography.labelLarge.copy(color = TextPrimary)
                    )
                    if (allTransactions.isNotEmpty()) {
                        Text(
                            text = "VER TODO (${allTransactions.size}) ➔",
                            style = BrexaTypography.labelMedium.copy(
                                color = AccentLime,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .clickable { onOpenTransactionHistory() }
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = "0 REGISTROS",
                            style = BrexaTypography.labelMedium.copy(color = TextSecondary)
                        )
                    }
                }
            }

            if (allTransactions.isEmpty()) {
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
                                text = "SIN MOVIMIENTOS REGISTRADOS",
                                style = BrexaTypography.labelLarge.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Comienza registrando un gasto o ingreso arriba.",
                                style = BrexaTypography.labelMedium.copy(color = TextMuted)
                            )
                        }
                    }
                }
            } else {
                items(allTransactions.take(3)) { tx ->
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
                            // Izquierda: Badge y Concepto (con peso flexible y ellipsis)
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
                                        text = if (tx.note.isNotEmpty()) tx.note else (cat?.name ?: if (tx.type == "EXCHANGE") "Transferencia entre cuentas" else "Sin descripción"),
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

                            // Derecha: Cifras bimonetarias
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

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    BrutalistButton(
                        text = "VER HISTORIAL COMPLETO (${allTransactions.size} REGISTROS) ➔",
                        onClick = onOpenTransactionHistory,
                        containerColor = SurfaceCard,
                        contentColor = AccentLime,
                        borderColor = AccentLime,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
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
            tx.type == "EXCHANGE" -> "TRANSFERENCIA"
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

                    // Concepto / Nota completa sin recortes
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
                                style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp, letterSpacing = 0.5.sp)
                            )
                            Text(
                                text = "${acc?.name ?: "Cuenta"} ${if (destAcc != null) "➔ ${destAcc.name}" else ""}",
                                style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                            )
                        }

                        if (cat != null) {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "CATEGORÍA",
                                    style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp, letterSpacing = 0.5.sp)
                                )
                                Text(
                                    text = cat.name,
                                    style = BrexaTypography.bodyMedium.copy(
                                        color = AccentLime,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // Fecha y Hora
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "FECHA Y HORA",
                            style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 10.sp, letterSpacing = 0.5.sp)
                        )
                        Text(
                            text = fullDateFormatter.format(Date(tx.dateTimestamp)),
                            style = BrexaTypography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    // Sección de Confirmación para Reversión / Borrado si se pulsa eliminar
                    if (showConfirmDeleteTx) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundBase, RectangleShape)
                                .border(1.5.dp, ExpenseRed, RectangleShape)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "¿CONFIRMAS REVERTIR Y ELIMINAR ESTE MOVIMIENTO?",
                                style = BrexaTypography.labelMedium.copy(color = ExpenseRed, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "El saldo de ${acc?.name ?: "la cuenta"} se restaurará automáticamente de forma inversa.",
                                style = BrexaTypography.labelSmall.copy(color = TextPrimary, fontSize = 10.sp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BrutalistBadge(
                                    text = "SÍ, ELIMINAR Y REVERTIR",
                                    backgroundColor = ExpenseRed,
                                    contentColor = Color.Black,
                                    borderColor = ExpenseRed,
                                    modifier = Modifier.clickable {
                                        viewModel.deleteTransaction(tx.id) {
                                            selectedTxDetail = null
                                            showConfirmDeleteTx = false
                                        }
                                    }
                                )
                                BrutalistBadge(
                                    text = "CANCELAR",
                                    backgroundColor = BackgroundBase,
                                    contentColor = TextSecondary,
                                    borderColor = BorderSolid,
                                    modifier = Modifier.clickable { showConfirmDeleteTx = false }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!showConfirmDeleteTx) {
                    BrutalistBadge(
                        text = "🗑 ELIMINAR",
                        backgroundColor = BackgroundBase,
                        contentColor = ExpenseRed,
                        borderColor = ExpenseRed,
                        modifier = Modifier.clickable { showConfirmDeleteTx = true }
                    )
                }
            },
            dismissButton = {
                BrutalistBadge(
                    text = "CERRAR",
                    backgroundColor = BackgroundBase,
                    contentColor = TextPrimary,
                    borderColor = BorderSolid,
                    modifier = Modifier.clickable {
                        selectedTxDetail = null
                        showConfirmDeleteTx = false
                    }
                )
            }
        )
    }

    // Modal Neobrutalista de Distribución de Patrimonio (Anti-Rupturas)
    if (showBreakdownModal) {
        AlertDialog(
            onDismissRequest = { showBreakdownModal = false },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            tonalElevation = 0.dp,
            modifier = Modifier.border(2.dp, BorderSolid, RectangleShape),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DISTRIBUCIÓN PATRIMONIAL",
                            style = BrexaTypography.titleLarge.copy(color = AccentLime, fontSize = 15.sp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "TOTAL: $baseSymbol ${formatter.format(totalUsd)} $baseCode",
                            style = BrexaTypography.labelSmall.copy(color = TextSecondary, fontSize = 10.5.sp)
                        )
                    }
                    Text(
                        text = "✕",
                        style = BrexaTypography.titleMedium.copy(color = TextMuted),
                        modifier = Modifier
                            .clickable { showBreakdownModal = false }
                            .padding(4.dp)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Desglose consolidado de tus fondos por cada moneda activa:",
                        style = BrexaTypography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                    )

                    currencyBreakdown.forEach { (curr, nativeSum, usdSum) ->
                        val percentage = if (totalUsd > 0.0) (usdSum / totalUsd) * 100.0 else 0.0
                        val barColor = if (curr.isBase) AccentLime else AccentPurple

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundBase, RectangleShape)
                                .border(1.dp, BorderSolid, RectangleShape)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    BrutalistBadge(
                                        text = curr.code,
                                        backgroundColor = SurfaceCard,
                                        contentColor = barColor,
                                        borderColor = barColor,
                                        fontSize = 10.sp
                                    )
                                    Column {
                                        Text(
                                            text = if (curr.isBase) "Efectivo / Base" else curr.name,
                                            style = BrexaTypography.labelMedium.copy(
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isIncognito) "••••••" else "Saldo: ${formatter.format(nativeSum)} ${curr.code}",
                                            style = BrexaTypography.bodySmall.copy(color = TextMuted, fontSize = 10.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (isIncognito) "••••" else "$baseSymbol ${formatter.format(usdSum)}",
                                        style = BrexaTypography.labelLarge.copy(
                                            color = barColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", percentage),
                                        style = BrexaTypography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp)
                                    )
                                }
                            }

                            // Barra visual de porcentaje de patrimonio
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(SurfaceCardElevated, RectangleShape)
                            ) {
                                val progress = (percentage / 100.0).toFloat().coerceIn(0f, 1f)
                                if (progress > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .fillMaxHeight()
                                            .background(barColor, RectangleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                BrutalistButton(
                    text = "ENTENDIDO",
                    onClick = { showBreakdownModal = false },
                    containerColor = AccentLime,
                    contentColor = TextOnLime,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // --- TOUR GUIADO INTERACTIVO NEOBRUTALISTA ---
    val activeTargetRect = when (tourStepIndex) {
        0 -> totalCardRect
        1 -> quickActionsRect
        2 -> accountsHeaderRect
        else -> null
    }

    if (showTour) {
        OnboardingTour(
            currentStepIndex = tourStepIndex,
            targetRect = activeTargetRect,
            onStepChange = { nextIndex ->
                tourStepIndex = nextIndex
            },
            onFinishTour = {
                prefs.edit().putBoolean("has_seen_onboarding_tour", true).apply()
                showTour = false
                onTourDismissed()
            }
        )
    }
}
}
