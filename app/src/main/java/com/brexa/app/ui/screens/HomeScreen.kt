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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.R
import com.brexa.app.data.local.entity.AccountEntity
import com.brexa.app.data.local.entity.TransactionEntity
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.theme.*
import java.text.DecimalFormat
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

    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    val formatter = remember { DecimalFormat("#,##0.00") }
    val dateFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    // Moneda Base y Primera Moneda Secundaria (si el usuario la ha creado)
    val baseCode = baseCurrency?.code ?: "USD"
    val baseSymbol = baseCurrency?.symbol ?: "$"
    val secondaryCurrency = currencies.find { !it.isBase }
    val hasSecondary = secondaryCurrency != null
    val secRate = secondaryCurrency?.rateToUsd ?: 1.0
    val equivalentSec = totalUsd * secRate

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
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "BREXA // TERMINAL",
                        style = BrexaTypography.labelLarge.copy(color = AccentLime, fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (hasSecondary) "1 $baseCode = ${String.format("%.2f", secRate)} ${secondaryCurrency?.code}" else "MONEDA ANCLA: $baseCode",
                        style = BrexaTypography.labelMedium.copy(color = AccentPurple, fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                                text = "PATRIMONIO TOTAL DISPONIBLE",
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

                        Text(
                            text = if (isIncognito) {
                                "≈ •••••••"
                            } else if (hasSecondary) {
                                "≈ ${formatter.format(equivalentSec)} ${secondaryCurrency?.code} (VALUACIÓN VIVA)"
                            } else {
                                "MONEDA PRINCIPAL: ${baseCurrency?.name ?: "Dólar"}"
                            },
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
                            text = "⇄ EXCHANGE",
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

                    val badgeColor = when (tx.type) {
                        "EXPENSE" -> ExpenseRed
                        "INCOME" -> IncomeGreen
                        "EXCHANGE" -> ExchangeBlue
                        else -> AccentPurple
                    }

                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = SurfaceCard,
                        borderColor = BorderSolid,
                        onClick = { transactionToDelete = tx }
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
                                        text = tx.type,
                                        backgroundColor = BackgroundBase,
                                        contentColor = badgeColor,
                                        borderColor = badgeColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (tx.note.isNotEmpty()) tx.note else (if (tx.type == "EXCHANGE") "Exchange entre cuentas" else "Sin descripción"),
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
                                Text(
                                    text = "${acc?.name ?: "Cuenta"} ${if (destAcc != null) "➔ ${destAcc.name}" else ""} • ${dateFormatter.format(Date(tx.dateTimestamp))}",
                                    style = BrexaTypography.labelMedium.copy(color = TextMuted, fontSize = 10.sp),
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

    // Modal de Confirmación para Reversión / Borrado de Transacción
    if (transactionToDelete != null) {
        val tx = transactionToDelete!!
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "¿ELIMINAR Y REVERTIR MOVIMIENTO?",
                    style = BrexaTypography.titleLarge.copy(color = ExpenseRed)
                )
            },
            text = {
                Text(
                    text = "Se eliminará el registro de ${formatter.format(tx.nativeAmount)} ${tx.currencyCode} y el saldo de la cuenta se recalculará automáticamente de forma inversa.",
                    style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                )
            },
            confirmButton = {
                BrutalistBadge(
                    text = "SÍ, REVERTIR",
                    backgroundColor = ExpenseRed,
                    contentColor = Color.Black,
                    borderColor = ExpenseRed,
                    modifier = Modifier.clickable {
                        viewModel.deleteTransaction(tx.id) {
                            transactionToDelete = null
                        }
                    }
                )
            },
            dismissButton = {
                BrutalistBadge(
                    text = "CANCELAR",
                    backgroundColor = BackgroundBase,
                    contentColor = TextSecondary,
                    borderColor = BorderSolid,
                    modifier = Modifier.clickable { transactionToDelete = null }
                )
            }
        )
    }
}
