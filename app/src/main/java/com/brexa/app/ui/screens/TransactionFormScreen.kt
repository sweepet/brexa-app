package com.brexa.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.platform.LocalContext
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showNoteModalDialog by remember { mutableStateOf(false) }
    var tempModalNoteText by remember { mutableStateOf("") }

    val context = LocalContext.current
    var selectedDateTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val isToday = remember(selectedDateTimestamp) {
        val calNow = Calendar.getInstance()
        val calSelected = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
        calNow.get(Calendar.YEAR) == calSelected.get(Calendar.YEAR) &&
                calNow.get(Calendar.DAY_OF_YEAR) == calSelected.get(Calendar.DAY_OF_YEAR)
    }

    val isYesterday = remember(selectedDateTimestamp) {
        val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val calSelected = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
        calYesterday.get(Calendar.YEAR) == calSelected.get(Calendar.YEAR) &&
                calYesterday.get(Calendar.DAY_OF_YEAR) == calSelected.get(Calendar.DAY_OF_YEAR)
    }

    val formattedDateText = remember(selectedDateTimestamp, isToday, isYesterday) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
        val timeStr = SimpleDateFormat("hh:mm a", Locale("es", "ES")).format(cal.time).uppercase()
        val dateFull = SimpleDateFormat("dd MMM yyyy", Locale("es", "ES")).format(cal.time)

        when {
            isToday -> "HOY, $dateFull • $timeStr"
            isYesterday -> "AYER, $dateFull • $timeStr"
            else -> "$dateFull • $timeStr"
        }
    }

    val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val accountCurrency = currencies.find { it.id == selectedAccount?.currencyId }
    val isForeignCurrency = accountCurrency?.isBase == false

    // Estado de la tasa: texto editable iniciado con la tasa configurada de la moneda
    var rateText by remember(selectedAccount) {
        val defaultRate = accountCurrency?.rateToUsd ?: 1.0
        mutableStateOf(CurrencyFormat.formatForInput(defaultRate))
    }

    val rateNum = CurrencyFormat.parseNumber(rateText) ?: 0.0

    // Cálculo reactivo instantáneo del costo en moneda base
    val amountNum = CurrencyFormat.parseNumber(amountText) ?: 0.0
    val equivalentUsd = if (isForeignCurrency && rateNum > 0.0) {
        try {
            BigDecimal(amountNum).divide(BigDecimal(rateNum), 2, RoundingMode.HALF_EVEN).toDouble()
        } catch (e: Exception) {
            0.0
        }
    } else {
        amountNum
    }

    val currencyFormatter = remember { CurrencyFormat.standardFormatter }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .imePadding()
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
                            curr?.rateToUsd?.let { r ->
                                rateText = CurrencyFormat.formatForInput(r)
                            }
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
        // TASA DE COTIZACIÓN MANUAL PRECISA EN TIEMPO REAL
        // =========================================================================
        if (selectedType != "EXCHANGE" && isForeignCurrency) {
            Spacer(modifier = Modifier.height(20.dp))

            val officialRate = accountCurrency?.rateToUsd ?: 1.0
            val officialRateStr = CurrencyFormat.formatRate(officialRate)
            val baseCode = baseCurrency?.code ?: "USD"
            val currCode = accountCurrency?.code ?: ""

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
                            text = "TASA DE COTIZACIÓN",
                            style = BrexaTypography.labelMedium.copy(color = TextSecondary, fontSize = 11.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BrutalistBadge(
                            text = "OFICIAL: $officialRateStr",
                            backgroundColor = BackgroundBase,
                            contentColor = AccentLime,
                            borderColor = AccentLime,
                            modifier = Modifier.clickable {
                                rateText = CurrencyFormat.formatForInput(officialRate)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Campo de entrada manual numérica con precisión decimal
                    BrutalistTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = "TASA APLICADA (1 $baseCode =)",
                        placeholder = officialRateStr,
                        suffix = currCode,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        borderColor = AccentLime,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

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
                            val displayEquivalent = if (rateNum > 0.0) {
                                "${baseCurrency?.symbol ?: "$"}${currencyFormatter.format(equivalentUsd)} ${baseCurrency?.code ?: "USD"}"
                            } else {
                                "---"
                            }
                            Text(
                                text = "⚡ $displayEquivalent",
                                style = BrexaTypography.displayMedium.copy(
                                    color = AccentLime,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

                item {
                    Box(
                        modifier = Modifier
                            .border(
                                width = 2.dp,
                                color = AccentLime,
                                shape = RectangleShape
                            )
                            .background(SurfaceCard, RectangleShape)
                            .clickable { showCreateCategoryDialog = true }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "+ NUEVA",
                                style = BrexaTypography.labelMedium.copy(
                                    color = AccentLime,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // NOTA / CONCEPTO (Modal interactivo para evitar que el teclado lo tape)
        Text(
            text = "NOTA / CONCEPTO",
            style = BrexaTypography.labelMedium.copy(color = TextSecondary),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        BrutalistCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    tempModalNoteText = noteText
                    showNoteModalDialog = true
                },
            backgroundColor = SurfaceCard,
            borderColor = if (noteText.isNotBlank()) AccentLime else BorderSolid
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (noteText.isNotBlank()) "CONCEPTO ASIGNADO" else "CONCEPTO (OPCIONAL)",
                        style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.sp, letterSpacing = 0.5.sp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (noteText.isNotBlank()) noteText else "Toca para escribir una nota o concepto...",
                        style = BrexaTypography.bodyMedium.copy(
                            color = if (noteText.isNotBlank()) TextPrimary else TextMuted,
                            fontWeight = if (noteText.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (noteText.isNotBlank()) {
                        BrutalistBadge(
                            text = "✕",
                            backgroundColor = BackgroundBase,
                            contentColor = ExpenseRed,
                            borderColor = ExpenseRed,
                            fontSize = 9.5.sp,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.clickable {
                                noteText = ""
                            }
                        )
                    }

                    BrutalistBadge(
                        text = if (noteText.isNotBlank()) "✏ EDITAR" else "+ NOTA",
                        backgroundColor = BackgroundBase,
                        contentColor = if (noteText.isNotBlank()) AccentLime else TextPrimary,
                        borderColor = if (noteText.isNotBlank()) AccentLime else BorderSolid,
                        fontSize = 9.5.sp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.clickable {
                            tempModalNoteText = noteText
                            showNoteModalDialog = true
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Selector Neobrutalista de Fecha y Hora de la Operación
        Text(
            text = "FECHA DE LA OPERACIÓN",
            style = BrexaTypography.labelMedium.copy(color = TextSecondary),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        BrutalistCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = SurfaceCard,
            borderColor = if (!isToday) AccentPurple else BorderSolid
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isToday) "REGISTRANDO HOY" else if (isYesterday) "REGISTRANDO AYER" else "FECHA PERSONALIZADA",
                            style = BrexaTypography.labelSmall.copy(color = TextMuted, fontSize = 9.sp, letterSpacing = 0.5.sp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedDateText,
                            style = BrexaTypography.titleMedium.copy(
                                color = if (isToday) AccentLime else AccentPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    BrutalistBadge(
                        text = "📅 CAMBIAR",
                        backgroundColor = BackgroundBase,
                        contentColor = TextPrimary,
                        borderColor = BorderSolid,
                        fontSize = 9.5.sp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val updatedCal = Calendar.getInstance().apply {
                                        timeInMillis = selectedDateTimestamp
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    selectedDateTimestamp = updatedCal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Accesos Rápidos: [ HOY ] [ AYER ] [ ⏱ HORA ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        BrutalistBadge(
                            text = "HOY",
                            backgroundColor = if (isToday) AccentLime else BackgroundBase,
                            contentColor = if (isToday) Color.Black else TextSecondary,
                            borderColor = if (isToday) AccentLime else BorderSolid,
                            fontSize = 9.5.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val calNow = Calendar.getInstance()
                                    val updatedCal = Calendar.getInstance().apply {
                                        timeInMillis = selectedDateTimestamp
                                        set(Calendar.YEAR, calNow.get(Calendar.YEAR))
                                        set(Calendar.MONTH, calNow.get(Calendar.MONTH))
                                        set(Calendar.DAY_OF_MONTH, calNow.get(Calendar.DAY_OF_MONTH))
                                    }
                                    selectedDateTimestamp = updatedCal.timeInMillis
                                }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        BrutalistBadge(
                            text = "AYER",
                            backgroundColor = if (isYesterday) AccentPurple else BackgroundBase,
                            contentColor = if (isYesterday) Color.Black else TextSecondary,
                            borderColor = if (isYesterday) AccentPurple else BorderSolid,
                            fontSize = 9.5.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                                    val updatedCal = Calendar.getInstance().apply {
                                        timeInMillis = selectedDateTimestamp
                                        set(Calendar.YEAR, calYesterday.get(Calendar.YEAR))
                                        set(Calendar.MONTH, calYesterday.get(Calendar.MONTH))
                                        set(Calendar.DAY_OF_MONTH, calYesterday.get(Calendar.DAY_OF_MONTH))
                                    }
                                    selectedDateTimestamp = updatedCal.timeInMillis
                                }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        BrutalistBadge(
                            text = "⏱ HORA",
                            backgroundColor = BackgroundBase,
                            contentColor = TextPrimary,
                            borderColor = BorderSolid,
                            fontSize = 9.5.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            val updatedCal = Calendar.getInstance().apply {
                                                timeInMillis = selectedDateTimestamp
                                                set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                set(Calendar.MINUTE, minute)
                                            }
                                            selectedDateTimestamp = updatedCal.timeInMillis
                                        },
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                }
                        )
                    }
                }
            }
        }

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

                if (isForeignCurrency && selectedType != "EXCHANGE" && rateNum <= 0.0) {
                    errorMessage = "Ingresa una tasa de cotización válida mayor a 0."
                    return@BrutalistButton
                }

                when (selectedType) {
                    "EXPENSE" -> {
                        viewModel.registerExpense(
                            accountId = selectedAccountId,
                            categoryId = selectedCategoryId.ifEmpty { "CAT_EXP_1" },
                            amount = amountNum,
                            rate = if (isForeignCurrency) rateNum else 1.0,
                            note = noteText,
                            dateTimestamp = selectedDateTimestamp,
                            onSuccess = onDismiss,
                            onError = { errorMessage = it }
                        )
                    }
                    "INCOME" -> {
                        viewModel.registerIncome(
                            accountId = selectedAccountId,
                            categoryId = selectedCategoryId.ifEmpty { "CAT_INC_1" },
                            amount = amountNum,
                            rate = if (isForeignCurrency) rateNum else 1.0,
                            note = noteText,
                            dateTimestamp = selectedDateTimestamp,
                            onSuccess = onDismiss,
                            onError = { errorMessage = it }
                        )
                    }
                    "EXCHANGE" -> {
                        val destNum = CurrencyFormat.parseNumber(destinationAmountText) ?: 0.0
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
                            dateTimestamp = selectedDateTimestamp,
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

    // Modal: Crear Categoría al vuelo desde el formulario
    if (showCreateCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var selectedIconId by remember { mutableStateOf("ic_cat_general") }
        var dialogError by remember { mutableStateOf<String?>(null) }
        val catType = if (selectedType == "INCOME") "INCOME" else "EXPENSE"
        val typeAccent = if (catType == "EXPENSE") ExpenseRed else IncomeGreen

        val iconOptions = listOf(
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

        fun getIconRes(id: String): Int = when (id) {
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

        AlertDialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "NUEVA CATEGORÍA DE ${if (catType == "EXPENSE") "GASTO" else "INGRESO"}",
                    style = BrexaTypography.titleLarge.copy(color = typeAccent)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrutalistTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = "NOMBRE DE LA CATEGORÍA",
                        placeholder = "ej. Mascotas, Proyectos, Freelance..."
                    )

                    Text(text = "SELECCIONA UN ÍCONO", style = BrexaTypography.labelMedium.copy(color = TextSecondary))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        iconOptions.take(5).forEach { (iconKey, label) ->
                            val isSel = selectedIconId == iconKey
                            Box(
                                modifier = Modifier
                                    .border(1.5.dp, if (isSel) typeAccent else BorderSolid, RectangleShape)
                                    .background(if (isSel) SurfaceCardElevated else BackgroundBase, RectangleShape)
                                    .clickable { selectedIconId = iconKey }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = getIconRes(iconKey)),
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
                        iconOptions.drop(5).forEach { (iconKey, label) ->
                            val isSel = selectedIconId == iconKey
                            Box(
                                modifier = Modifier
                                    .border(1.5.dp, if (isSel) typeAccent else BorderSolid, RectangleShape)
                                    .background(if (isSel) SurfaceCardElevated else BackgroundBase, RectangleShape)
                                    .clickable { selectedIconId = iconKey }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = getIconRes(iconKey)),
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
                            type = catType,
                            iconId = selectedIconId,
                            colorHex = if (catType == "EXPENSE") "#FF4D4D" else "#00FF66",
                            onSuccess = {
                                showCreateCategoryDialog = false
                            },
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

    // Modal Neobrutalista para Escribir Nota / Concepto sin que el teclado la tape
    if (showNoteModalDialog) {
        val quickNotes = listOf(
            "Supermercado", "Almuerzo", "Cena", "Transporte",
            "Farmacia", "Servicios", "Combustible", "Pago Móvil",
            "Repuestos", "Salida", "Ropa", "Transferencia"
        )

        AlertDialog(
            onDismissRequest = { showNoteModalDialog = false },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONCEPTO // NOTA",
                        style = BrexaTypography.titleLarge.copy(color = AccentLime, fontSize = 16.sp)
                    )
                    if (tempModalNoteText.isNotBlank()) {
                        BrutalistBadge(
                            text = "BORRAR ✕",
                            backgroundColor = BackgroundBase,
                            contentColor = ExpenseRed,
                            borderColor = ExpenseRed,
                            fontSize = 8.5.sp,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.clickable { tempModalNoteText = "" }
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Escribe el detalle de la compra o pago:",
                        style = BrexaTypography.bodySmall.copy(color = TextSecondary)
                    )

                    BrutalistTextField(
                        value = tempModalNoteText,
                        onValueChange = { tempModalNoteText = it },
                        label = "CONCEPTO O DESCRIPCIÓN",
                        placeholder = "ej. Supermercado, Almuerzo, Repuestos...",
                        isMonospace = false,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "SUGERENCIAS RÁPIDAS EN 1 TOQUE:",
                        style = BrexaTypography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        )
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickNotes) { suggestion ->
                            val isSel = tempModalNoteText.equals(suggestion, ignoreCase = true)
                            BrutalistBadge(
                                text = suggestion,
                                backgroundColor = if (isSel) AccentLime else BackgroundBase,
                                contentColor = if (isSel) Color.Black else TextPrimary,
                                borderColor = if (isSel) AccentLime else BorderSolid,
                                fontSize = 9.5.sp,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.clickable {
                                    tempModalNoteText = suggestion
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "LISTO ✓",
                    backgroundColor = AccentLime,
                    contentColor = Color.Black,
                    borderColor = AccentLime,
                    fontSize = 11.sp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.clickable {
                        noteText = tempModalNoteText.trim()
                        showNoteModalDialog = false
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
                    modifier = Modifier.clickable { showNoteModalDialog = false }
                )
            }
        )
    }
}
