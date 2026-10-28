package com.brexa.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.brexa.app.data.local.entity.TransactionEntity
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistCard
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
    onOpenSettings: () -> Unit
) {
    val totalUsd by viewModel.consolidatedUsdBalance.collectAsState()
    val accounts by viewModel.activeAccounts.collectAsState()
    val currencies by viewModel.allCurrencies.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val isIncognito by viewModel.incognitoMode.collectAsState()
    val expenseCats by viewModel.expenseCategories.collectAsState()
    val incomeCats by viewModel.incomeCategories.collectAsState()
    val categoryMap = remember(expenseCats, incomeCats) {
        (expenseCats + incomeCats).associateBy { it.id }
    }

    var selectedTxDetail by remember { mutableStateOf<TransactionEntity?>(null) }
    var showConfirmDeleteTx by remember { mutableStateOf(false) }

    val formatter = remember { CurrencyFormat.standardFormatter }
    val dateFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("dd 'de' MMMM, yyyy - HH:mm", Locale("es", "ES")) }

    // Moneda Base y Monedas Secundarias
    val baseCode = baseCurrency?.code ?: "USD"
    val baseSymbol = baseCurrency?.symbol ?: "$"
    val secondaryCurrencies = remember(currencies) { currencies.filter { !it.isBase } }
    var selectedSecondaryIndex by remember { mutableIntStateOf(0) }
    val activeSecondaryCurrency = if (secondaryCurrencies.isNotEmpty()) {
        secondaryCurrencies[selectedSecondaryIndex.coerceIn(0, secondaryCurrencies.lastIndex)]
    } else null
    val equivalentSec = if (activeSecondaryCurrency != null) totalUsd * activeSecondaryCurrency.rateToUsd else 0.0

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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // --- 2. TARJETA PRINCIPAL: PATRIMONIO TOTAL CONSOLIDADO ---
            item {
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth(),
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

                        // Monto gigante en Lima Neón
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

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isIncognito) {
                            Text(
                                text = "≈ •••••••",
                                style = BrexaTypography.labelMedium.copy(
                                    color = AccentPurple,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        } else if (activeSecondaryCurrency != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(enabled = secondaryCurrencies.size > 1) {
                                    selectedSecondaryIndex = (selectedSecondaryIndex + 1) % secondaryCurrencies.size
                                }
                            ) {
                                Text(
                                    text = "≈ ${formatter.format(equivalentSec)} ${activeSecondaryCurrency.code}",
                                    style = BrexaTypography.labelMedium.copy(
                                        color = AccentPurple,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (secondaryCurrencies.size > 1) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "⇄",
                                        style = BrexaTypography.labelMedium.copy(
                                            color = AccentLime,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "MONEDA PRINCIPAL: ${baseCurrency?.name ?: "Dólar"}",
                                style = BrexaTypography.labelMedium.copy(
                                    color = AccentPurple,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // --- 3. BOTONES DE ACCIÓN RÁPIDA (100% RESPONSIVE) ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = "${recentTransactions.size} REGISTROS",
                        style = BrexaTypography.labelMedium.copy(color = TextSecondary)
                    )
                }
            }

            if (recentTransactions.isEmpty()) {
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
                items(recentTransactions) { tx ->
                    val acc = accounts.find { it.id == tx.accountId }
                    val destAcc = tx.destinationAccountId?.let { id -> accounts.find { it.id == id } }
                    val cat = tx.categoryId?.let { categoryMap[it] }

                    val badgeColor = when (tx.type) {
                        "EXPENSE" -> ExpenseRed
                        "INCOME" -> IncomeGreen
                        "EXCHANGE" -> ExchangeBlue
                        else -> AccentPurple
                    }

                    val typeLabel = when (tx.type) {
                        "EXPENSE" -> "GASTO"
                        "INCOME" -> "INGRESO"
                        "EXCHANGE" -> "CAMBIO"
                        "BALANCE_ADJUSTMENT" -> "AJUSTE"
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
                                val prefix = if (tx.type == "EXPENSE") "− " else if (tx.type == "INCOME") "+ " else "⇄ "
                                Text(
                                    text = if (isIncognito) "••••••" else "$prefix${formatter.format(tx.nativeAmount)} ${tx.currencyCode}",
                                    style = BrexaTypography.labelLarge.copy(
                                        color = if (tx.type == "EXPENSE") ExpenseRed else if (tx.type == "INCOME") IncomeGreen else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isIncognito) "≈ •••• $baseCode" else "≈ $baseSymbol ${formatter.format(tx.usdEquivalent)} $baseCode",
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

    // Modal de Detalle de Movimiento
    if (selectedTxDetail != null) {
        val tx = selectedTxDetail!!
        val acc = accounts.find { it.id == tx.accountId }
        val destAcc = tx.destinationAccountId?.let { id -> accounts.find { it.id == id } }
        val cat = tx.categoryId?.let { categoryMap[it] }

        val badgeColor = when (tx.type) {
            "EXPENSE" -> ExpenseRed
            "INCOME" -> IncomeGreen
            "EXCHANGE" -> ExchangeBlue
            else -> AccentPurple
        }

        val typeLabel = when (tx.type) {
            "EXPENSE" -> "GASTO"
            "INCOME" -> "INGRESO"
            "EXCHANGE" -> "TRANSFERENCIA"
            "BALANCE_ADJUSTMENT" -> "AJUSTE DE SALDO"
            else -> tx.type
        }

        val prefix = if (tx.type == "EXPENSE") "− " else if (tx.type == "INCOME") "+ " else "⇄ "

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
                        style = BrexaTypography.titleLarge.copy(color = AccentLime, fontSize = 15.sp)
                    )
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
                                color = badgeColor,
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
}
