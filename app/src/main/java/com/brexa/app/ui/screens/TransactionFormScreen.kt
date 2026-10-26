package com.brexa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.R
import com.brexa.app.data.local.entity.AccountEntity
import com.brexa.app.data.local.entity.CategoryEntity
import com.brexa.app.data.local.entity.CurrencyEntity
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistButton
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.components.BrutalistTextField
import com.brexa.app.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    viewModel: BrexaViewModel,
    initialType: String = "EXPENSE",
    onDismiss: () -> Unit
) {
    val accounts by viewModel.activeAccounts.collectAsState()
    val currencies by viewModel.allCurrencies.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()

    var selectedType by remember { mutableStateOf(initialType) } // EXPENSE, INCOME, EXCHANGE
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var selectedDestinationAccountId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: "") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var destinationAmountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val accountCurrency = currencies.find { it.id == selectedAccount?.currencyId }
    val isForeignCurrency = accountCurrency?.isBase == false

    // Estado de la tasa: iniciada con la tasa configurada de la moneda
    var currentRate by remember(selectedAccount) {
        mutableFloatStateOf((accountCurrency?.rateToUsd ?: 1.0).toFloat())
    }

    // Centrado del Slider interactivo (+- 25% de sensibilidad)
    val baseRate = (accountCurrency?.rateToUsd ?: 1.0).toFloat()
    val minSlider = (baseRate * 0.75f).coerceAtLeast(0.1f)
    val maxSlider = (baseRate * 1.35f).coerceAtLeast(baseRate + 1f)

    // Cálculo reactivo instantáneo del costo en USD
    val amountNum = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val equivalentUsd = if (isForeignCurrency && currentRate > 0f) {
        BigDecimal(amountNum).divide(BigDecimal(currentRate.toDouble()), 2, RoundingMode.HALF_EVEN).toDouble()
    } else {
        amountNum
    }

    val currencyFormatter = remember { DecimalFormat("#,##0.00") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Encabezado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REGISTRAR OPERACIÓN",
                style = BrexaTypography.titleLarge.copy(color = TextPrimary)
            )
            BrutalistBadge(
                text = "CERRAR ✕",
                backgroundColor = SurfaceCard,
                contentColor = TextSecondary,
                borderColor = BorderSolid,
                modifier = Modifier.clickable { onDismiss() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selector Triple: GASTO | INGRESO | EXCHANGE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BorderSolid, RectangleShape)
        ) {
            val tabs = listOf(
                Triple("EXPENSE", "GASTO", ExpenseRed),
                Triple("INCOME", "INGRESO", IncomeGreen),
                Triple("EXCHANGE", "EXCHANGE", ExchangeBlue)
            )
            tabs.forEach { (typeKey, label, activeColor) ->
                val isSelected = selectedType == typeKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) activeColor else SurfaceCard, RectangleShape)
                        .clickable { selectedType = typeKey }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = BrexaTypography.labelLarge.copy(
                            color = if (isSelected) Color.Black else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Selector de Cuenta Origen
        Text(
            text = if (selectedType == "EXCHANGE") "CUENTA ORIGEN (SALE DINERO)" else "CUENTA",
            style = BrexaTypography.labelMedium.copy(color = TextSecondary),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(accounts) { acc ->
                val isSel = acc.id == selectedAccountId
                val curr = currencies.find { it.id == acc.currencyId }
                Box(
                    modifier = Modifier
                        .border(
                            width = 2.dp,
                            color = if (isSel) AccentLime else BorderSolid,
                            shape = RectangleShape
                        )
                        .background(if (isSel) SurfaceCardElevated else SurfaceCard, RectangleShape)
                        .clickable {
                            selectedAccountId = acc.id
                            curr?.rateToUsd?.let { currentRate = it.toFloat() }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(
                            text = acc.name.uppercase(),
                            style = BrexaTypography.labelLarge.copy(
                                color = if (isSel) AccentLime else TextPrimary
                            )
                        )
                        Text(
                            text = "${currencyFormatter.format(acc.currentBalance)} ${curr?.code ?: "USD"}",
                            style = BrexaTypography.labelMedium.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }

        // Si es EXCHANGE: Selector de Cuenta Destino
        if (selectedType == "EXCHANGE") {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CUENTA DESTINO (RECIBE DINERO)",
                style = BrexaTypography.labelMedium.copy(color = TextSecondary),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(accounts.filter { it.id != selectedAccountId }) { acc ->
                    val isSel = acc.id == selectedDestinationAccountId
                    val curr = currencies.find { it.id == acc.currencyId }
                    Box(
                        modifier = Modifier
                            .border(
                                width = 2.dp,
                                color = if (isSel) ExchangeBlue else BorderSolid,
                                shape = RectangleShape
                            )
                            .background(if (isSel) SurfaceCardElevated else SurfaceCard, RectangleShape)
                            .clickable { selectedDestinationAccountId = acc.id }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                text = acc.name.uppercase(),
                                style = BrexaTypography.labelLarge.copy(
                                    color = if (isSel) ExchangeBlue else TextPrimary
                                )
                            )
                            Text(
                                text = "${currencyFormatter.format(acc.currentBalance)} ${curr?.code ?: (baseCurrency?.code ?: "USD")}",
                                style = BrexaTypography.labelMedium.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Campo de Monto Principal
        BrutalistTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = if (selectedType == "EXCHANGE") "MONTO A TRANSFERIR" else "MONTO",
            placeholder = "0.00",
            suffix = accountCurrency?.code ?: (baseCurrency?.code ?: "USD"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        // Si es EXCHANGE: Campo de Monto Recibido
        if (selectedType == "EXCHANGE") {
            Spacer(modifier = Modifier.height(16.dp))
            val destAcc = accounts.find { it.id == selectedDestinationAccountId }
            val destCurr = currencies.find { it.id == destAcc?.currencyId }
            BrutalistTextField(
                value = destinationAmountText,
                onValueChange = { destinationAmountText = it },
                label = "MONTO RECIBIDO EN DESTINO",
                placeholder = "0.00",
                suffix = destCurr?.code ?: (baseCurrency?.code ?: "USD"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                borderColor = ExchangeBlue,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // =========================================================================
        // COMPONENTE ESTRELLA: SLIDER INTERACTIVO DE TASA DE CAMBIO EN TIEMPO REAL
        // =========================================================================
        if (selectedType != "EXCHANGE" && isForeignCurrency) {
            Spacer(modifier = Modifier.height(20.dp))

            BrutalistCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SurfaceCard,
                borderColor = AccentLime,
                borderWidth = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TASA DE CAMBIO APLICADA",
                            style = BrexaTypography.labelMedium.copy(color = TextSecondary)
                        )
                        BrutalistBadge(
                            text = "1 ${baseCurrency?.code ?: "USD"} = ${String.format("%.2f", currentRate)} ${accountCurrency?.code}",
                            backgroundColor = BackgroundBase,
                            contentColor = AccentLime,
                            borderColor = AccentLime
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Slider ancho interactivo neobrutalista
                    Slider(
                        value = currentRate,
                        onValueChange = { currentRate = it },
                        valueRange = minSlider..maxSlider,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentLime,
                            activeTrackColor = AccentLime,
                            inactiveTrackColor = BorderSolid
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Botones de micro-ajuste rápido (+- 0.50)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BrutalistBadge(
                            text = "− 0.50",
                            backgroundColor = SurfaceCardElevated,
                            contentColor = TextPrimary,
                            borderColor = BorderSolid,
                            modifier = Modifier.clickable { currentRate = (currentRate - 0.50f).coerceAtLeast(0.1f) }
                        )
                        Text(
                            text = "DESLIZA PARA AJUSTAR TASA",
                            style = BrexaTypography.labelMedium.copy(color = TextMuted, fontSize = 9.sp)
                        )
                        BrutalistBadge(
                            text = "+ 0.50",
                            backgroundColor = SurfaceCardElevated,
                            contentColor = TextPrimary,
                            borderColor = BorderSolid,
                            modifier = Modifier.clickable { currentRate = currentRate + 0.50f }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // VISUALIZADOR DE IMPACTO EN TIEMPO REAL EN LIMA NEÓN
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundBase, RectangleShape)
                            .border(1.5.dp, AccentLime, RectangleShape)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "EQUIVALENTE EN TIEMPO REAL",
                                style = BrexaTypography.labelMedium.copy(color = TextSecondary, fontSize = 10.sp)
                            )
                            Text(
                                text = "⚡ ${baseCurrency?.symbol ?: "$"} ${currencyFormatter.format(equivalentUsd)} ${baseCurrency?.code ?: "USD"}",
                                style = BrexaTypography.displayMedium.copy(
                                    color = AccentLime,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        // Selector de Categoría (Solo para Gastos e Ingresos)
        if (selectedType != "EXCHANGE") {
            Spacer(modifier = Modifier.height(20.dp))
            val categories = if (selectedType == "EXPENSE") expenseCategories else incomeCategories
            Text(
                text = "CATEGORÍA",
                style = BrexaTypography.labelMedium.copy(color = TextSecondary),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSel = cat.id == selectedCategoryId
                    val iconRes = when (cat.iconId) {
                        "ic_cat_food" -> R.drawable.ic_cat_food
                        "ic_cat_housing" -> R.drawable.ic_cat_housing
                        "ic_cat_transport" -> R.drawable.ic_cat_transport
                        "ic_cat_health" -> R.drawable.ic_cat_health
                        "ic_cat_leisure" -> R.drawable.ic_cat_leisure
                        "ic_account_cash" -> R.drawable.ic_account_cash
                        "ic_account_bank" -> R.drawable.ic_account_bank
                        "ic_account_crypto" -> R.drawable.ic_account_crypto
                        else -> R.drawable.ic_cat_general
                    }

                    Box(
                        modifier = Modifier
                            .border(
                                width = 2.dp,
                                color = if (isSel) AccentLime else BorderSolid,
                                shape = RectangleShape
                            )
                            .background(if (isSel) SurfaceCardElevated else SurfaceCard, RectangleShape)
                            .clickable { selectedCategoryId = cat.id }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = cat.name,
                                tint = if (isSel) AccentLime else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cat.name.uppercase(),
                                style = BrexaTypography.labelMedium.copy(
                                    color = if (isSel) AccentLime else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Campo de Nota / Descripción
        BrutalistTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = "NOTA / CONCEPTO (OPCIONAL)",
            placeholder = "ej. Supermercado, Almuerzo, Transporte...",
            isMonospace = false,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "⚠️ $errorMessage",
                style = BrexaTypography.labelMedium.copy(color = ExpenseRed)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Botón de Confirmación Neobrutalista con Sombra Dura
        val btnColor = when (selectedType) {
            "EXPENSE" -> ExpenseRed
            "INCOME" -> IncomeGreen
            else -> ExchangeBlue
        }
        val btnText = when (selectedType) {
            "EXPENSE" -> "REGISTRAR GASTO"
            "INCOME" -> "REGISTRAR INGRESO"
            else -> "CONFIRMAR EXCHANGE"
        }

        BrutalistButton(
            text = btnText,
            onClick = {
                errorMessage = null
                if (amountNum <= 0.0) {
                    errorMessage = "Ingresa un monto válido mayor a 0."
                    return@BrutalistButton
                }

                when (selectedType) {
                    "EXPENSE" -> {
                        viewModel.registerExpense(
                            accountId = selectedAccountId,
                            categoryId = selectedCategoryId.ifEmpty { "CAT_EXP_1" },
                            amount = amountNum,
                            rate = if (isForeignCurrency) currentRate.toDouble() else 1.0,
                            note = noteText,
                            onSuccess = onDismiss,
                            onError = { errorMessage = it }
                        )
                    }
                    "INCOME" -> {
                        viewModel.registerIncome(
                            accountId = selectedAccountId,
                            categoryId = selectedCategoryId.ifEmpty { "CAT_INC_1" },
                            amount = amountNum,
                            rate = if (isForeignCurrency) currentRate.toDouble() else 1.0,
                            note = noteText,
                            onSuccess = onDismiss,
                            onError = { errorMessage = it }
                        )
                    }
                    "EXCHANGE" -> {
                        val destNum = destinationAmountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (destNum <= 0.0) {
                            errorMessage = "Ingresa el monto recibido en destino."
                            return@BrutalistButton
                        }
                        viewModel.registerExchange(
                            sourceAccountId = selectedAccountId,
                            targetAccountId = selectedDestinationAccountId,
                            sourceAmount = amountNum,
                            targetReceivedAmount = destNum,
                            note = noteText,
                            onSuccess = onDismiss,
                            onError = { errorMessage = it }
                        )
                    }
                }
            },
            containerColor = btnColor,
            contentColor = Color.Black,
            shadowColor = AccentLime,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
