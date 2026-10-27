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
import androidx.compose.ui.text.style.TextOverflow
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
import com.brexa.app.ui.util.CurrencyFormat

private val categoryIconOptions = listOf(
    Pair("ic_cat_general", "GENERAL"),
    Pair("ic_cat_food", "COMIDA"),
    Pair("ic_cat_housing", "VIVIENDA"),
    Pair("ic_cat_transport", "TRANSPORTE"),
    Pair("ic_cat_health", "SALUD"),
    Pair("ic_cat_leisure", "OCIO"),
    Pair("ic_account_bank", "BANCO"),
    Pair("ic_account_cash", "EFECTIVO"),
    Pair("ic_account_crypto", "CRIPTO")
)

private fun getCategoryIconResource(iconId: String): Int {
    return when (iconId) {
        "ic_cat_food" -> R.drawable.ic_cat_food
        "ic_cat_housing" -> R.drawable.ic_cat_housing
        "ic_cat_transport" -> R.drawable.ic_cat_transport
        "ic_cat_health" -> R.drawable.ic_cat_health
        "ic_cat_leisure" -> R.drawable.ic_cat_leisure
        "ic_account_bank" -> R.drawable.ic_account_bank
        "ic_account_cash" -> R.drawable.ic_account_cash
        "ic_account_crypto" -> R.drawable.ic_account_crypto
        else -> R.drawable.ic_cat_general
    }
}

@Composable
fun AccountsCurrenciesScreen(
    viewModel: BrexaViewModel,
    onBack: () -> Unit
) {
    val accounts by viewModel.activeAccounts.collectAsState()
    val currencies by viewModel.allCurrencies.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()

    var activeTab by remember { mutableStateOf("ACCOUNTS") } // "ACCOUNTS", "CURRENCIES", "CATEGORIES"
    var activeCategoryType by remember { mutableStateOf("EXPENSE") } // "EXPENSE", "INCOME"

    // Modales
    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showCreateCurrencyDialog by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showEditBaseCurrencyDialog by remember { mutableStateOf(false) }
    var editingRateCurrency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var deletingCurrency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var adjustingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    val formatter = remember { CurrencyFormat.standardFormatter }

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

        // Pestañas Principales (3 Pestañas: CUENTAS, MONEDAS, CATEGORÍAS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BorderSolid, RectangleShape)
        ) {
            val tabs = listOf(
                Triple("ACCOUNTS", "CUENTAS (${accounts.size})", AccentLime),
                Triple("CURRENCIES", "MONEDAS (${currencies.size})", AccentPurple),
                Triple("CATEGORIES", "CATEGORÍAS", ExchangeBlue)
            )
            tabs.forEach { (tabKey, title, accentColor) ->
                val isSel = activeTab == tabKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSel) accentColor else SurfaceCard, RectangleShape)
                        .clickable { activeTab = tabKey }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = BrexaTypography.labelMedium.copy(
                            color = if (isSel) Color.Black else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (activeTab) {
            "ACCOUNTS" -> {
                // TAB CUENTAS
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
                                // Fila Superior: Icono + Nombre + Badge de Inclusión
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = acc.name,
                                            tint = if (acc.isDefault) AccentLime else AccentPurple,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = acc.name.uppercase(),
                                            style = BrexaTypography.labelLarge.copy(color = TextPrimary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (acc.isDefault) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "★",
                                                style = BrexaTypography.labelSmall.copy(color = AccentLime)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    BrutalistBadge(
                                        text = if (acc.includeInTotal) "EN TOTAL" else "EXCLUIDA",
                                        backgroundColor = if (acc.includeInTotal) BackgroundBase else SurfaceCardElevated,
                                        contentColor = if (acc.includeInTotal) AccentLime else TextMuted,
                                        borderColor = if (acc.includeInTotal) AccentLime else BorderSolid,
                                        modifier = Modifier.clickable {
                                            viewModel.toggleIncludeInTotal(acc.id, acc.includeInTotal)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Fila Inferior: Saldo + Acciones (Ajustar, Editar, Borrar)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val baseCode = baseCurrency?.code ?: "USD"
                                    Text(
                                        text = "${formatter.format(acc.currentBalance)} ${curr?.code ?: baseCode}",
                                        style = BrexaTypography.titleLarge.copy(
                                            color = if (acc.currentBalance < 0.0) ExpenseRed else TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        BrutalistBadge(
                                            text = "AJUSTAR",
                                            backgroundColor = SurfaceCardElevated,
                                            contentColor = AccentLime,
                                            borderColor = AccentLime,
                                            modifier = Modifier.clickable { adjustingAccount = acc }
                                        )
                                        BrutalistBadge(
                                            text = "✎",
                                            backgroundColor = SurfaceCardElevated,
                                            contentColor = AccentPurple,
                                            borderColor = AccentPurple,
                                            modifier = Modifier.clickable { editingAccount = acc }
                                        )
                                        if (!acc.isDefault) {
                                            BrutalistBadge(
                                                text = "✕",
                                                backgroundColor = SurfaceCardElevated,
                                                contentColor = ExpenseRed,
                                                borderColor = ExpenseRed,
                                                modifier = Modifier.clickable { deletingAccount = acc }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "CURRENCIES" -> {
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
                                Column(modifier = Modifier.weight(1f)) {
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
                                            style = BrexaTypography.labelLarge.copy(color = TextPrimary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val baseCode = baseCurrency?.code ?: "USD"
                                    Text(
                                        text = if (curr.isBase) "1 $baseCode = 1,00 $baseCode (MONEDA BASE ANCLA)" else "1 $baseCode = ${formatter.format(curr.rateToUsd)} ${curr.code}",
                                        style = BrexaTypography.labelMedium.copy(
                                            color = if (curr.isBase) AccentLime else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (curr.isBase) {
                                        BrutalistBadge(
                                            text = "EDITAR ✎",
                                            backgroundColor = BackgroundBase,
                                            contentColor = AccentLime,
                                            borderColor = AccentLime,
                                            modifier = Modifier.clickable { showEditBaseCurrencyDialog = true }
                                        )
                                    } else {
                                        BrutalistBadge(
                                            text = "TASA ✎",
                                            backgroundColor = BackgroundBase,
                                            contentColor = AccentPurple,
                                            borderColor = AccentPurple,
                                            modifier = Modifier.clickable { editingRateCurrency = curr }
                                        )
                                        BrutalistBadge(
                                            text = "BORRAR ✕",
                                            backgroundColor = BackgroundBase,
                                            contentColor = ExpenseRed,
                                            borderColor = ExpenseRed,
                                            modifier = Modifier.clickable { deletingCurrency = curr }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "CATEGORIES" -> {
                // TAB CATEGORÍAS (GASTOS E INGRESOS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, BorderSolid, RectangleShape)
                ) {
                    val isExp = activeCategoryType == "EXPENSE"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isExp) ExpenseRed else SurfaceCard, RectangleShape)
                            .clickable { activeCategoryType = "EXPENSE" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GASTOS (${expenseCategories.size})",
                            style = BrexaTypography.labelMedium.copy(
                                color = if (isExp) Color.Black else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (!isExp) IncomeGreen else SurfaceCard, RectangleShape)
                            .clickable { activeCategoryType = "INCOME" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "INGRESOS (${incomeCategories.size})",
                            style = BrexaTypography.labelMedium.copy(
                                color = if (!isExp) Color.Black else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val currentCats = if (activeCategoryType == "EXPENSE") expenseCategories else incomeCategories
                val btnColor = if (activeCategoryType == "EXPENSE") ExpenseRed else IncomeGreen

                BrutalistButton(
                    text = "+ CREAR CATEGORÍA DE ${if (activeCategoryType == "EXPENSE") "GASTO" else "INGRESO"}",
                    onClick = { showCreateCategoryDialog = true },
                    containerColor = btnColor,
                    contentColor = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentCats) { cat ->
                        val iconRes = getCategoryIconResource(cat.iconId)
                        BrutalistCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = SurfaceCard,
                            borderColor = if (cat.isDefault) BorderSolid else btnColor
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = cat.name,
                                        tint = if (cat.isDefault) TextSecondary else btnColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = cat.name.uppercase(),
                                            style = BrexaTypography.labelLarge.copy(color = TextPrimary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (cat.isDefault) "PREDETERMINADA DEL SISTEMA" else "PERSONALIZADA",
                                            style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                                        )
                                    }
                                }

                                if (!cat.isDefault) {
                                    BrutalistBadge(
                                        text = "BORRAR ✕",
                                        backgroundColor = BackgroundBase,
                                        contentColor = ExpenseRed,
                                        borderColor = ExpenseRed,
                                        modifier = Modifier.clickable { deletingCategory = cat }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // MODALES Y DIÁLOGOS NEOBRUTALISTAS
    // ==========================================

    // Modal: Crear Cuenta
    if (showCreateAccountDialog) {
        var accountName by remember { mutableStateOf("") }
        var selectedCurrId by remember { mutableStateOf(currencies.firstOrNull()?.id ?: "") }
        var selectedIconType by remember { mutableStateOf("BANK") }
        var initialBalText by remember { mutableStateOf("0,00") }
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
                        placeholder = "ej. Billetera, Cuenta Bancaria, Ahorros"
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

                    Text(text = "ÍCONO DE CUENTA", style = BrexaTypography.labelMedium.copy(color = TextSecondary))
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
                        val initBal = CurrencyFormat.parseNumber(initialBalText) ?: 0.0
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

    // Modal: Editar Cuenta
    if (editingAccount != null) {
        val acc = editingAccount!!
        var editName by remember { mutableStateOf(acc.name) }
        var editCurrId by remember { mutableStateOf(acc.currencyId) }
        var editIconType by remember { mutableStateOf(acc.iconType) }
        var editIncludeInTotal by remember { mutableStateOf(acc.includeInTotal) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        val iconOptions = listOf(
            Triple("CASH", R.drawable.ic_account_cash, "EFECTIVO"),
            Triple("BANK", R.drawable.ic_account_bank, "BANCO"),
            Triple("CRYPTO", R.drawable.ic_account_crypto, "CRIPTO"),
            Triple("GENERAL", R.drawable.ic_account_general, "GENERAL")
        )

        AlertDialog(
            onDismissRequest = { editingAccount = null },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(text = "EDITAR CUENTA", style = BrexaTypography.titleLarge.copy(color = AccentPurple))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrutalistTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = "NOMBRE DE LA CUENTA"
                    )

                    Text(text = "MONEDA ASOCIADA", style = BrexaTypography.labelMedium.copy(color = TextSecondary))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        currencies.forEach { c ->
                            val isSel = c.id == editCurrId
                            BrutalistBadge(
                                text = c.code,
                                backgroundColor = if (isSel) AccentPurple else BackgroundBase,
                                contentColor = if (isSel) Color.Black else TextPrimary,
                                borderColor = if (isSel) AccentPurple else BorderSolid,
                                modifier = Modifier.clickable { editCurrId = c.id }
                            )
                        }
                    }

                    Text(text = "ÍCONO DE CUENTA", style = BrexaTypography.labelMedium.copy(color = TextSecondary))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        iconOptions.forEach { (typeKey, iconRes, label) ->
                            val isSel = editIconType == typeKey
                            Box(
                                modifier = Modifier
                                    .border(1.5.dp, if (isSel) AccentPurple else BorderSolid, RectangleShape)
                                    .background(if (isSel) SurfaceCardElevated else BackgroundBase, RectangleShape)
                                    .clickable { editIconType = typeKey }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = label,
                                    tint = if (isSel) AccentPurple else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editIncludeInTotal = !editIncludeInTotal },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrutalistBadge(
                            text = if (editIncludeInTotal) "✓ INCLUIDA EN PATRIMONIO TOTAL" else "✕ EXCLUIDA DEL TOTAL",
                            backgroundColor = if (editIncludeInTotal) BackgroundBase else SurfaceCardElevated,
                            contentColor = if (editIncludeInTotal) AccentLime else TextMuted,
                            borderColor = if (editIncludeInTotal) AccentLime else BorderSolid
                        )
                    }

                    if (dialogError != null) {
                        Text(text = "⚠️ $dialogError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "GUARDAR CAMBIOS",
                    backgroundColor = AccentPurple,
                    contentColor = Color.Black,
                    borderColor = AccentPurple,
                    modifier = Modifier.clickable {
                        viewModel.updateAccount(
                            accountId = acc.id,
                            name = editName,
                            currencyId = editCurrId,
                            iconType = editIconType,
                            colorHex = acc.colorHex,
                            includeInTotal = editIncludeInTotal,
                            onSuccess = { editingAccount = null },
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
                    modifier = Modifier.clickable { editingAccount = null }
                )
            }
        )
    }

    // Modal: Confirmar Borrado de Cuenta
    if (deletingAccount != null) {
        val acc = deletingAccount!!
        var deleteError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(text = "BORRAR CUENTA", style = BrexaTypography.titleLarge.copy(color = ExpenseRed))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "¿Deseas eliminar permanentemente la cuenta '${acc.name.uppercase()}'?",
                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                    )
                    Text(
                        text = "⚠️ Se eliminarán los registros y movimientos asociados a esta cuenta.",
                        style = BrexaTypography.labelMedium.copy(color = ExpenseRed, fontSize = 11.sp)
                    )
                    if (deleteError != null) {
                        Text(text = "⚠️ $deleteError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "CONFIRMAR BORRADO",
                    backgroundColor = ExpenseRed,
                    contentColor = Color.White,
                    borderColor = ExpenseRed,
                    modifier = Modifier.clickable {
                        viewModel.deleteAccount(
                            accountId = acc.id,
                            onSuccess = { deletingAccount = null },
                            onError = { deleteError = it }
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
                    modifier = Modifier.clickable { deletingAccount = null }
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
                        label = "TASA DE CAMBIO: 1 ${baseCurrency?.code ?: "USD"} = [ X ] SIGLA",
                        placeholder = "ej. 72,50",
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
                        val rate = CurrencyFormat.parseNumber(rateText) ?: 1.0
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

    // Modal: Confirmar Borrado de Moneda
    if (deletingCurrency != null) {
        val curr = deletingCurrency!!
        var deleteError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { deletingCurrency = null },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(text = "BORRAR MONEDA // ${curr.code}", style = BrexaTypography.titleLarge.copy(color = ExpenseRed))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "¿Deseas eliminar la moneda ${curr.code} (${curr.name})?",
                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                    )
                    Text(
                        text = "Nota: Solo puedes eliminar monedas que no estén asignadas a ninguna cuenta.",
                        style = BrexaTypography.labelMedium.copy(color = TextSecondary, fontSize = 11.sp)
                    )
                    if (deleteError != null) {
                        Text(text = "⚠️ $deleteError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "ELIMINAR MONEDA",
                    backgroundColor = ExpenseRed,
                    contentColor = Color.White,
                    borderColor = ExpenseRed,
                    modifier = Modifier.clickable {
                        viewModel.deleteCurrency(
                            currencyId = curr.id,
                            onSuccess = { deletingCurrency = null },
                            onError = { deleteError = it }
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
                    modifier = Modifier.clickable { deletingCurrency = null }
                )
            }
        )
    }

    // Modal: Editar Tasa de Cambio
    if (editingRateCurrency != null) {
        val curr = editingRateCurrency!!
        var newRateText by remember { mutableStateOf(CurrencyFormat.formatRate(curr.rateToUsd)) }
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
                    val baseCode = baseCurrency?.code ?: "USD"
                    Text(
                        text = "Introduce cuántas unidades de ${curr.code} equivalen a 1 $baseCode hoy:",
                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                    )
                    BrutalistTextField(
                        value = newRateText,
                        onValueChange = { newRateText = it },
                        label = "1 $baseCode = [ X ] ${curr.code}",
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
                        val rate = CurrencyFormat.parseNumber(newRateText) ?: 0.0
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

    // Modal: Editar Moneda Base
    if (showEditBaseCurrencyDialog && baseCurrency != null) {
        val baseCurr = baseCurrency!!
        var baseCodeText by remember { mutableStateOf(baseCurr.code) }
        var baseNameText by remember { mutableStateOf(baseCurr.name) }
        var baseSymbolText by remember { mutableStateOf(baseCurr.symbol) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditBaseCurrencyDialog = false },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(text = "CONFIGURAR MONEDA BASE", style = BrexaTypography.titleLarge.copy(color = AccentLime))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "La moneda base es la ancla contable para calcular el patrimonio total consolidado y registrar el valor histórico de todas las operaciones.",
                        style = BrexaTypography.bodyMedium.copy(color = TextSecondary, fontSize = 11.sp)
                    )

                    BrutalistTextField(
                        value = baseCodeText,
                        onValueChange = { if (it.length <= 4) baseCodeText = it.uppercase() },
                        label = "SIGLA (HASTA 4 LETRAS)",
                        placeholder = "ej. USD, EUR, USDT"
                    )

                    BrutalistTextField(
                        value = baseNameText,
                        onValueChange = { baseNameText = it },
                        label = "NOMBRE DE LA MONEDA",
                        placeholder = "ej. Dólar Estadounidense"
                    )

                    BrutalistTextField(
                        value = baseSymbolText,
                        onValueChange = { if (it.length <= 3) baseSymbolText = it },
                        label = "SÍMBOLO",
                        placeholder = "ej. $, €, ₮"
                    )

                    if (dialogError != null) {
                        Text(text = "⚠️ $dialogError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "GUARDAR CAMBIOS",
                    backgroundColor = AccentLime,
                    contentColor = Color.Black,
                    borderColor = AccentLime,
                    modifier = Modifier.clickable {
                        viewModel.updateBaseCurrency(
                            code = baseCodeText,
                            name = baseNameText,
                            symbol = baseSymbolText,
                            onSuccess = { showEditBaseCurrencyDialog = false },
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
                    modifier = Modifier.clickable { showEditBaseCurrencyDialog = false }
                )
            }
        )
    }

    // Modal: Ajustar Saldo Real
    if (adjustingAccount != null) {
        val acc = adjustingAccount!!
        var newBalText by remember { mutableStateOf(CurrencyFormat.format(acc.currentBalance)) }
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
                        val nb = CurrencyFormat.parseNumber(newBalText) ?: acc.currentBalance
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

    // Modal: Crear Categoría (Gastos o Ingresos)
    if (showCreateCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var selectedCatType by remember { mutableStateOf(activeCategoryType) }
        var selectedIconId by remember { mutableStateOf("ic_cat_general") }
        var dialogError by remember { mutableStateOf<String?>(null) }

        val typeAccent = if (selectedCatType == "EXPENSE") ExpenseRed else IncomeGreen

        AlertDialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "NUEVA CATEGORÍA",
                    style = BrexaTypography.titleLarge.copy(color = typeAccent)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Selector Gasto vs Ingreso
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSolid, RectangleShape)
                    ) {
                        val isExp = selectedCatType == "EXPENSE"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isExp) ExpenseRed else SurfaceCard, RectangleShape)
                                .clickable { selectedCatType = "EXPENSE" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "GASTO",
                                style = BrexaTypography.labelMedium.copy(
                                    color = if (isExp) Color.Black else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (!isExp) IncomeGreen else SurfaceCard, RectangleShape)
                                .clickable { selectedCatType = "INCOME" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "INGRESO",
                                style = BrexaTypography.labelMedium.copy(
                                    color = if (!isExp) Color.Black else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    BrutalistTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = "NOMBRE DE LA CATEGORÍA",
                        placeholder = "ej. Mascotas, Freelance, Educación..."
                    )

                    Text(text = "SELECCIONA UN ÍCONO", style = BrexaTypography.labelMedium.copy(color = TextSecondary))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoryIconOptions.take(5).forEach { (iconKey, label) ->
                            val isSel = selectedIconId == iconKey
                            Box(
                                modifier = Modifier
                                    .border(1.5.dp, if (isSel) typeAccent else BorderSolid, RectangleShape)
                                    .background(if (isSel) SurfaceCardElevated else BackgroundBase, RectangleShape)
                                    .clickable { selectedIconId = iconKey }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = getCategoryIconResource(iconKey)),
                                    contentDescription = label,
                                    tint = if (isSel) typeAccent else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoryIconOptions.drop(5).forEach { (iconKey, label) ->
                            val isSel = selectedIconId == iconKey
                            Box(
                                modifier = Modifier
                                    .border(1.5.dp, if (isSel) typeAccent else BorderSolid, RectangleShape)
                                    .background(if (isSel) SurfaceCardElevated else BackgroundBase, RectangleShape)
                                    .clickable { selectedIconId = iconKey }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = getCategoryIconResource(iconKey)),
                                    contentDescription = label,
                                    tint = if (isSel) typeAccent else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (dialogError != null) {
                        Text(text = "⚠️ $dialogError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "CREAR CATEGORÍA",
                    backgroundColor = typeAccent,
                    contentColor = Color.Black,
                    borderColor = typeAccent,
                    modifier = Modifier.clickable {
                        if (catName.trim().isEmpty()) {
                            dialogError = "Escribe un nombre para la categoría."
                            return@clickable
                        }
                        viewModel.addCustomCategory(
                            name = catName.trim(),
                            type = selectedCatType,
                            iconId = selectedIconId,
                            colorHex = if (selectedCatType == "EXPENSE") "#FF4D4D" else "#00FF66",
                            onSuccess = { showCreateCategoryDialog = false },
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
                    modifier = Modifier.clickable { showCreateCategoryDialog = false }
                )
            }
        )
    }

    // Modal: Confirmar Borrado de Categoría
    if (deletingCategory != null) {
        val cat = deletingCategory!!
        var deleteError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(text = "BORRAR CATEGORÍA", style = BrexaTypography.titleLarge.copy(color = ExpenseRed))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "¿Deseas eliminar la categoría personalizada '${cat.name.uppercase()}'?",
                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                    )
                    Text(
                        text = "Las transacciones que usaban esta categoría conservarán su registro sin categoría asignada.",
                        style = BrexaTypography.labelMedium.copy(color = TextSecondary, fontSize = 11.sp)
                    )
                    if (deleteError != null) {
                        Text(text = "⚠️ $deleteError", style = BrexaTypography.labelMedium.copy(color = ExpenseRed))
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "ELIMINAR",
                    backgroundColor = ExpenseRed,
                    contentColor = Color.White,
                    borderColor = ExpenseRed,
                    modifier = Modifier.clickable {
                        viewModel.deleteCategory(
                            categoryId = cat.id,
                            onSuccess = { deletingCategory = null },
                            onError = { deleteError = it }
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
                    modifier = Modifier.clickable { deletingCategory = null }
                )
            }
        )
    }
}
