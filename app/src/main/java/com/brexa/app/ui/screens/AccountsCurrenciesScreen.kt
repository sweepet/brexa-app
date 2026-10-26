package com.brexa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import com.brexa.app.data.local.entity.CurrencyEntity
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistButton
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.components.BrutalistTextField
import com.brexa.app.ui.theme.*
import java.text.DecimalFormat

@Composable
fun AccountsCurrenciesScreen(
    viewModel: BrexaViewModel,
    onBack: () -> Unit
) {
    val accounts by viewModel.activeAccounts.collectAsState()
    val currencies by viewModel.allCurrencies.collectAsState()

    var activeTab by remember { mutableStateOf("ACCOUNTS") } // "ACCOUNTS" o "CURRENCIES"

    // Modales
    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showCreateCurrencyDialog by remember { mutableStateOf(false) }
    var editingRateCurrency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var adjustingAccount by remember { mutableStateOf<AccountEntity?>(null) }

    val formatter = remember { DecimalFormat("#,##0.00") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .padding(16.dp)
    ) {
        // Encabezado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GESTIÓN FINANCIERA",
                style = BrexaTypography.titleLarge.copy(color = TextPrimary)
            )
            BrutalistBadge(
                text = "VOLVER ✕",
                backgroundColor = SurfaceCard,
                contentColor = TextSecondary,
                borderColor = BorderSolid,
                modifier = Modifier.clickable { onBack() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pestañas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BorderSolid, RectangleShape)
        ) {
            val isAcc = activeTab == "ACCOUNTS"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isAcc) AccentLime else SurfaceCard, RectangleShape)
                    .clickable { activeTab = "ACCOUNTS" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CUENTAS (${accounts.size}/11)",
                    style = BrexaTypography.labelLarge.copy(
                        color = if (isAcc) Color.Black else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (!isAcc) AccentPurple else SurfaceCard, RectangleShape)
                    .clickable { activeTab = "CURRENCIES" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MONEDAS (${currencies.size}/11)",
                    style = BrexaTypography.labelLarge.copy(
                        color = if (!isAcc) Color.Black else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeTab == "ACCOUNTS") {
            // Botón Crear Cuenta
            BrutalistButton(
                text = "+ CREAR NUEVA CUENTA",
                onClick = { showCreateAccountDialog = true },
                containerColor = AccentLime,
                contentColor = Color.Black,
                enabled = accounts.size < 11,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(accounts) { acc ->
                    val curr = currencies.find { it.id == acc.currencyId }
                    val iconRes = when (acc.iconType) {
                        "CASH" -> R.drawable.ic_account_cash
                        "BANK" -> R.drawable.ic_account_bank
                        "CRYPTO" -> R.drawable.ic_account_crypto
                        else -> R.drawable.ic_account_general
                    }

                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = SurfaceCard,
                        borderColor = if (acc.isDefault) AccentLime else BorderSolid
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = acc.name,
                                        tint = if (acc.isDefault) AccentLime else AccentPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = acc.name.uppercase(),
                                            style = BrexaTypography.labelLarge.copy(color = TextPrimary)
                                        )
                                        Text(
                                            text = "${formatter.format(acc.currentBalance)} ${curr?.code ?: "USD"}",
                                            style = BrexaTypography.titleLarge.copy(
                                                color = if (acc.currentBalance < 0.0) ExpenseRed else TextPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                BrutalistBadge(
                                    text = if (acc.includeInTotal) "INCLUIDA EN TOTAL" else "EXCLUIDA",
                                    backgroundColor = if (acc.includeInTotal) BackgroundBase else SurfaceCardElevated,
                                    contentColor = if (acc.includeInTotal) AccentLime else TextMuted,
                                    borderColor = if (acc.includeInTotal) AccentLime else BorderSolid,
                                    modifier = Modifier.clickable {
                                        viewModel.toggleIncludeInTotal(acc.id, acc.includeInTotal)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BrutalistBadge(
                                    text = "AJUSTAR SALDO",
                                    backgroundColor = SurfaceCardElevated,
                                    contentColor = AccentLime,
                                    borderColor = AccentLime,
                                    modifier = Modifier.clickable { adjustingAccount = acc }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // TAB MONEDAS
            BrutalistButton(
                text = "+ CREAR NUEVA MONEDA",
                onClick = { showCreateCurrencyDialog = true },
                containerColor = AccentPurple,
                contentColor = Color.Black,
                enabled = currencies.size < 11,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(currencies) { curr ->
                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = SurfaceCard,
                        borderColor = if (curr.isBase) AccentLime else BorderSolid
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BrutalistBadge(
                                        text = curr.code,
                                        backgroundColor = BackgroundBase,
                                        contentColor = if (curr.isBase) AccentLime else AccentPurple,
                                        borderColor = if (curr.isBase) AccentLime else AccentPurple
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = curr.name.uppercase(),
                                        style = BrexaTypography.labelLarge.copy(color = TextPrimary)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (curr.isBase) "1 USD = 1.00 USD (MONEDA BASE)" else "1 USD = ${String.format("%.2f", curr.rateToUsd)} ${curr.code}",
                                    style = BrexaTypography.labelMedium.copy(
                                        color = if (curr.isBase) AccentLime else TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            if (!curr.isBase) {
                                BrutalistBadge(
                                    text = "EDITAR TASA ✎",
                                    backgroundColor = BackgroundBase,
                                    contentColor = AccentPurple,
                                    borderColor = AccentPurple,
                                    modifier = Modifier.clickable { editingRateCurrency = curr }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Crear Cuenta
    if (showCreateAccountDialog) {
        var accountName by remember { mutableStateOf("") }
        var selectedCurrId by remember { mutableStateOf(currencies.firstOrNull()?.id ?: "") }
        var selectedIconType by remember { mutableStateOf("BANK") }
        var initialBalText by remember { mutableStateOf("0.00") }
        var includeInTotal by remember { mutableStateOf(true) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        val iconOptions = listOf(
            Triple("CASH", R.drawable.ic_account_cash, "EFECTIVO"),
            Triple("BANK", R.drawable.ic_account_bank, "BANCO"),
            Triple("CRYPTO", R.drawable.ic_account_crypto, "CRIPTO"),
            Triple("GENERAL", R.drawable.ic_account_general, "GENERAL")
        )

        AlertDialog(
            onDismissRequest = { showCreateAccountDialog = false },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(text = "NUEVA CUENTA", style = BrexaTypography.titleLarge.copy(color = AccentLime))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrutalistTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = "NOMBRE DE LA CUENTA",
                        placeholder = "ej. Banesco Pago Móvil"
                    )

                    Text(text = "MONEDA", style = BrexaTypography.labelMedium.copy(color = TextSecondary))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        currencies.forEach { c ->
                            val isSel = c.id == selectedCurrId
                            BrutalistBadge(
                                text = c.code,
                                backgroundColor = if (isSel) AccentLime else BackgroundBase,
                                contentColor = if (isSel) Color.Black else TextPrimary,
                                borderColor = if (isSel) AccentLime else BorderSolid,
                                modifier = Modifier.clickable { selectedCurrId = c.id }
                            )
                        }
                    }

                    Text(text = "ÍCONO DE CUENTA (4 DISPONIBLES)", style = BrexaTypography.labelMedium.copy(color = TextSecondary))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        iconOptions.forEach { (typeKey, iconRes, label) ->
                            val isSel = selectedIconType == typeKey
                            Box(
                                modifier = Modifier
                                    .border(1.5.dp, if (isSel) AccentLime else BorderSolid, RectangleShape)
                                    .background(if (isSel) SurfaceCardElevated else BackgroundBase, RectangleShape)
                                    .clickable { selectedIconType = typeKey }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = label,
                                    tint = if (isSel) AccentLime else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    BrutalistTextField(
                        value = initialBalText,
                        onValueChange = { initialBalText = it },
                        label = "SALDO INICIAL",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    if (dialogError != null) {
                        Text(text = "⚠️ $dialogError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "GUARDAR CUENTA",
                    backgroundColor = AccentLime,
                    contentColor = Color.Black,
                    borderColor = AccentLime,
                    modifier = Modifier.clickable {
                        val initBal = initialBalText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        viewModel.addCustomAccount(
                            name = accountName,
                            currencyId = selectedCurrId,
                            iconType = selectedIconType,
                            colorHex = "#D4FF00",
                            initialBalance = initBal,
                            includeInTotal = includeInTotal,
                            onSuccess = { showCreateAccountDialog = false },
                            onError = { dialogError = it }
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
                    modifier = Modifier.clickable { showCreateAccountDialog = false }
                )
            }
        )
    }

    // Modal: Crear Moneda
    if (showCreateCurrencyDialog) {
        var codeText by remember { mutableStateOf("") }
        var nameText by remember { mutableStateOf("") }
        var rateText by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCreateCurrencyDialog = false },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(text = "NUEVA MONEDA", style = BrexaTypography.titleLarge.copy(color = AccentPurple))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrutalistTextField(
                        value = codeText,
                        onValueChange = { if (it.length <= 4) codeText = it.uppercase() },
                        label = "SIGLA (HASTA 4 LETRAS)",
                        placeholder = "ej. USDT, EUR, COP"
                    )

                    BrutalistTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = "NOMBRE DE LA MONEDA",
                        placeholder = "ej. Tether USD, Euro"
                    )

                    BrutalistTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = "TASA DE CAMBIO: 1 USD = [ X ] SIGLA",
                        placeholder = "ej. 72.50",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    if (dialogError != null) {
                        Text(text = "⚠️ $dialogError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "GUARDAR MONEDA",
                    backgroundColor = AccentPurple,
                    contentColor = Color.Black,
                    borderColor = AccentPurple,
                    modifier = Modifier.clickable {
                        val rate = rateText.replace(",", ".").toDoubleOrNull() ?: 1.0
                        viewModel.addCustomCurrency(
                            code = codeText,
                            name = nameText,
                            symbol = codeText,
                            rateToUsd = rate,
                            iconId = "ic_account_bank",
                            onSuccess = { showCreateCurrencyDialog = false },
                            onError = { dialogError = it }
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
                    modifier = Modifier.clickable { showCreateCurrencyDialog = false }
                )
            }
        )
    }

    // Modal: Editar Tasa de Cambio
    if (editingRateCurrency != null) {
        val curr = editingRateCurrency!!
        var newRateText by remember { mutableStateOf(curr.rateToUsd.toString()) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingRateCurrency = null },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(text = "ACTUALIZAR TASA // ${curr.code}", style = BrexaTypography.titleLarge.copy(color = AccentLime))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Introduce cuántas unidades de ${curr.code} equivalen a 1 USD hoy:",
                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                    )
                    BrutalistTextField(
                        value = newRateText,
                        onValueChange = { newRateText = it },
                        label = "1 USD = [ X ] ${curr.code}",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    if (dialogError != null) {
                        Text(text = "⚠️ $dialogError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "ACTUALIZAR",
                    backgroundColor = AccentLime,
                    contentColor = Color.Black,
                    borderColor = AccentLime,
                    modifier = Modifier.clickable {
                        val rate = newRateText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        viewModel.updateCurrencyRate(
                            currencyId = curr.id,
                            newRate = rate,
                            onSuccess = { editingRateCurrency = null },
                            onError = { dialogError = it }
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
                    modifier = Modifier.clickable { editingRateCurrency = null }
                )
            }
        )
    }

    // Modal: Ajustar Saldo Real (Conciliación Rápida)
    if (adjustingAccount != null) {
        val acc = adjustingAccount!!
        var newBalText by remember { mutableStateOf(acc.currentBalance.toString()) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { adjustingAccount = null },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(text = "AJUSTAR SALDO REAL", style = BrexaTypography.titleLarge.copy(color = AccentLime))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Indica el saldo real que tienes en ${acc.name}. Brexa creará un movimiento de conciliación automática:",
                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                    )
                    BrutalistTextField(
                        value = newBalText,
                        onValueChange = { newBalText = it },
                        label = "SALDO EXACTO ACTUAL",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    if (dialogError != null) {
                        Text(text = "⚠️ $dialogError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "CONCILIAR",
                    backgroundColor = AccentLime,
                    contentColor = Color.Black,
                    borderColor = AccentLime,
                    modifier = Modifier.clickable {
                        val nb = newBalText.replace(",", ".").toDoubleOrNull() ?: acc.currentBalance
                        viewModel.adjustAccountBalance(
                            accountId = acc.id,
                            newBalance = nb,
                            onSuccess = { adjustingAccount = null },
                            onError = { dialogError = it }
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
                    modifier = Modifier.clickable { adjustingAccount = null }
                )
            }
        )
    }
}
