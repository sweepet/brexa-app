package com.brexa.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.data.rates.MarketRateItem
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.CardShape
import com.brexa.app.ui.theme.*
import com.brexa.app.ui.util.ConversionShareHelper
import com.brexa.app.ui.util.ShareConversionRow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun CalculatorScreen(
    viewModel: BrexaViewModel,
    preselectedRateId: String? = null,
    onNavigateToTransactionWithAmount: ((amount: Double, rate: Double, currencyCode: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rates by viewModel.marketRates.collectAsState()
    val ratesHeaderDate by viewModel.ratesHeaderDate.collectAsState()
    val context = LocalContext.current

    // Lista de IDs de tasas activas para conversión (por defecto solo 1 tasa para máxima comodidad sin scroll)
    val activeRateIds = remember {
        mutableStateListOf<String>().apply {
            val initial = preselectedRateId ?: "BCV_USD"
            add(initial)
        }
    }

    // Asegurar que si cambia la tasa preseleccionada desde otra pantalla, se active
    LaunchedEffect(preselectedRateId) {
        if (preselectedRateId != null) {
            if (!activeRateIds.contains(preselectedRateId)) {
                activeRateIds.clear()
                activeRateIds.add(preselectedRateId)
            }
        }
    }

    // Casilla activa: "BASE" para USD o el ID de la tasa respectiva (ej: "BCV_USD", "USDT")
    var activeBoxId by remember { mutableStateOf("BASE") }

    // Estado del teclado: INICIA EN TRUE para que al tipear un número reemplace el valor por defecto directamente
    var inputStr by remember { mutableStateOf("1") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var shouldClearOnNextDigit by remember { mutableStateOf(true) }

    val numberFormat = remember {
        val symbols = DecimalFormatSymbols(Locale("es", "VE")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        DecimalFormat("#,##0.##", symbols)
    }

    // Valor numérico actualmente escrito
    val activeAmount = remember(inputStr) {
        val sanitized = inputStr.replace(".", "").replace(",", ".")
        sanitized.toDoubleOrNull() ?: 0.0
    }

    // Monto equivalente normalizado en USD (pivote para todas las casillas simultáneas)
    val normalizedUsdAmount = remember(activeBoxId, activeAmount, rates) {
        if (activeBoxId == "BASE") {
            activeAmount
        } else {
            val editingRate = rates.find { it.id == activeBoxId }?.rate ?: 1.0
            if (editingRate > 0.0) activeAmount / editingRate else 0.0
        }
    }

    // Función para cambiar de casilla activa
    fun switchActiveBox(newBoxId: String) {
        if (activeBoxId != newBoxId) {
            val newAmount = if (newBoxId == "BASE") {
                normalizedUsdAmount
            } else {
                val targetRate = rates.find { it.id == newBoxId }?.rate ?: 1.0
                normalizedUsdAmount * targetRate
            }
            inputStr = if (newAmount > 0.0) {
                String.format(Locale.US, "%.2f", newAmount).replace(".", ",")
            } else {
                if (newBoxId == "BASE") "1" else "1000"
            }
            operand1 = null
            pendingOperator = null
            shouldClearOnNextDigit = true // Reemplaza en la siguiente pulsación
            activeBoxId = newBoxId
        }
    }

    // Manejo de pulsaciones de dígitos
    fun handleDigit(digit: String) {
        if (shouldClearOnNextDigit) {
            inputStr = digit
            shouldClearOnNextDigit = false
        } else {
            if (inputStr == "0" && digit != ",") {
                inputStr = digit
            } else if (inputStr.length < 14) {
                inputStr += digit
            }
        }
    }

    fun handleComma() {
        if (shouldClearOnNextDigit) {
            inputStr = "0,"
            shouldClearOnNextDigit = false
        } else if (!inputStr.contains(",")) {
            inputStr += ","
        }
    }

    fun handleBackspace() {
        if (shouldClearOnNextDigit) {
            inputStr = "0"
            shouldClearOnNextDigit = false
        } else {
            inputStr = if (inputStr.length <= 1) "0" else inputStr.dropLast(1)
        }
    }

    fun handleOperator(op: String) {
        val num = activeAmount
        if (operand1 == null) {
            operand1 = num
        } else if (pendingOperator != null && !shouldClearOnNextDigit) {
            val res = when (pendingOperator) {
                "+" -> operand1!! + num
                "-" -> operand1!! - num
                "×" -> operand1!! * num
                "÷" -> if (num != 0.0) operand1!! / num else 0.0
                else -> num
            }
            operand1 = res
            inputStr = numberFormat.format(res)
        }
        pendingOperator = op
        shouldClearOnNextDigit = true
    }

    fun handleEquals() {
        val op = pendingOperator ?: return
        val op1 = operand1 ?: return
        val num = activeAmount
        val res = when (op) {
            "+" -> op1 + num
            "-" -> op1 - num
            "×" -> op1 * num
            "÷" -> if (num != 0.0) op1 / num else 0.0
            else -> num
        }
        operand1 = null
        pendingOperator = null
        inputStr = numberFormat.format(res)
        shouldClearOnNextDigit = true
    }

    fun handleReset() {
        inputStr = if (activeBoxId == "BASE") "1" else "1000"
        operand1 = null
        pendingOperator = null
        shouldClearOnNextDigit = true // Al reiniciar, el siguiente número reemplaza sin tener que borrar
    }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Monto copiado: $text", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // --- TOP BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CALCULADORA",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Conversor multidivisa simultáneo",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSecondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Invertir
                IconButton(
                    onClick = {
                        val firstRate = activeRateIds.firstOrNull() ?: "BCV_USD"
                        if (activeBoxId == "BASE") switchActiveBox(firstRate) else switchActiveBox("BASE")
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(SurfaceCardElevated, CircleShape)
                        .border(1.dp, BorderSolid, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Invertir casilla activa",
                        tint = AccentTurquoise,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Botón Compartir Ficha Limpia
                IconButton(
                    onClick = {
                        val baseFormatted = "$ ${numberFormat.format(normalizedUsdAmount)} USD"
                        val shareRows = activeRateIds.mapNotNull { rId ->
                            val rItem = rates.find { it.id == rId } ?: return@mapNotNull null
                            val localAmt = normalizedUsdAmount * rItem.rate
                            ShareConversionRow(
                                title = rItem.title,
                                amountFormatted = "${rItem.currencySymbol} ${numberFormat.format(localAmt)}",
                                rateFormatted = "1 USD = ${rItem.currencySymbol} ${rItem.formattedRate}"
                            )
                        }
                        ConversionShareHelper.shareConversion(
                            context = context,
                            baseAmountFormatted = baseFormatted,
                            conversions = shareRows,
                            dateHeader = ratesHeaderDate
                        )
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(SurfaceCardElevated, CircleShape)
                        .border(1.dp, BorderSolid, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartir conversión",
                        tint = AccentTurquoise,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- SELECTOR DE TASAS SIMULTÁNEAS (CHIPS INTERACTIVOS COMPACTOS) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rates.forEach { rateItem ->
                val isShown = activeRateIds.contains(rateItem.id)
                Surface(
                    color = if (isShown) AccentTurquoise.copy(alpha = 0.15f) else SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isShown) AccentTurquoise else BorderSolid),
                    modifier = Modifier.clickable {
                        if (isShown) {
                            if (activeRateIds.size > 1) {
                                if (activeBoxId == rateItem.id) {
                                    activeBoxId = "BASE"
                                }
                                activeRateIds.remove(rateItem.id)
                            } else {
                                Toast.makeText(context, "Debe quedar al menos una tasa activa", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            activeRateIds.add(rateItem.id)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isShown) "✓  ${rateItem.title}" else "+  ${rateItem.title}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = if (isShown) FontWeight.Bold else FontWeight.Medium,
                            color = if (isShown) AccentTurquoise else TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- CASILLA 0: MONEDA BASE (USD) (DISEÑO ULTRA-COMPACTO) ---
        val isBox0Active = activeBoxId == "BASE"
        val box0Display = if (isBox0Active) inputStr else numberFormat.format(normalizedUsdAmount)

        Surface(
            color = if (isBox0Active) SurfaceCardElevated else SurfaceCard,
            shape = CardShape,
            border = BorderStroke(
                if (isBox0Active) 1.5.dp else 1.dp,
                if (isBox0Active) AccentTurquoise else BorderSolid
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { switchActiveBox("BASE") }
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "USD",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        if (isBox0Active) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentTurquoise, CircleShape)
                            )
                        }
                    }
                    Text(
                        text = "$ $box0Display",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = if (box0Display.length > 10) 18.sp else 22.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = if (isBox0Active) AccentTurquoise else IncomeGreen,
                        maxLines = 1,
                        modifier = Modifier.clickable { copyToClipboard(box0Display, "USD") }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Dólar estadounidense (moneda base)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- CASILLAS DE TASAS (PREDETERMINADA 1 SOLA TASA, OTRAS OPCIONALES) ---
        activeRateIds.forEach { rateId ->
            val rateItem = rates.find { it.id == rateId }
            if (rateItem != null) {
                val isThisBoxActive = activeBoxId == rateItem.id
                val calculatedForThisRate = normalizedUsdAmount * rateItem.rate
                val displayAmount = if (isThisBoxActive) inputStr else numberFormat.format(calculatedForThisRate)

                Surface(
                    color = if (isThisBoxActive) SurfaceCardElevated else SurfaceCard,
                    shape = CardShape,
                    border = BorderStroke(
                        if (isThisBoxActive) 1.5.dp else 1.dp,
                        if (isThisBoxActive) AccentTurquoise else BorderSolid
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clickable { switchActiveBox(rateItem.id) }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = rateItem.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                if (isThisBoxActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(AccentTurquoise, CircleShape)
                                    )
                                }
                                if (activeRateIds.size > 1) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            if (activeBoxId == rateItem.id) {
                                                activeBoxId = "BASE"
                                            }
                                            activeRateIds.remove(rateItem.id)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Quitar casilla",
                                            tint = TextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${rateItem.currencySymbol} $displayAmount",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = if (displayAmount.length > 10) 18.sp else 22.sp
                                ),
                                fontWeight = FontWeight.Bold,
                                color = if (isThisBoxActive) AccentTurquoise else TextPrimary,
                                maxLines = 1,
                                modifier = Modifier.clickable { copyToClipboard(displayAmount, rateItem.title) }
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "1 USD = ${rateItem.currencySymbol} ${rateItem.formattedRate}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- TECLADO NUMÉRICO TÁCTIL PERSONALIZADO (DISTRIBUCIÓN 5 FILAS ULTRA-EFICIENTE) ---
        val buttonHeight = 44.dp
        val buttonShape = RoundedCornerShape(10.dp)
        val rowSpacing = 6.dp

        @Composable
        fun KeyButton(
            text: String,
            onClick: () -> Unit,
            modifier: Modifier = Modifier,
            containerColor: Color = SurfaceCard,
            contentColor: Color = TextPrimary,
            icon: @Composable (() -> Unit)? = null
        ) {
            Surface(
                color = containerColor,
                shape = buttonShape,
                border = BorderStroke(1.dp, BorderSolid),
                modifier = modifier
                    .height(buttonHeight)
                    .clickable(onClick = onClick)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        icon()
                    } else {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                }
            }
        }

        // Fila 1: REINICIAR (3 col) + ÷ (1 col)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            KeyButton(
                text = "REINICIAR",
                onClick = { handleReset() },
                modifier = Modifier.weight(3f),
                containerColor = AccentTurquoise,
                contentColor = Color(0xFF0E1116)
            )
            KeyButton(
                text = "÷",
                onClick = { handleOperator("÷") },
                modifier = Modifier.weight(1f),
                containerColor = SurfaceCardElevated,
                contentColor = AccentTurquoise
            )
        }

        Spacer(modifier = Modifier.height(rowSpacing))

        // Fila 2: 7, 8, 9, ×
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            KeyButton(text = "7", onClick = { handleDigit("7") }, modifier = Modifier.weight(1f))
            KeyButton(text = "8", onClick = { handleDigit("8") }, modifier = Modifier.weight(1f))
            KeyButton(text = "9", onClick = { handleDigit("9") }, modifier = Modifier.weight(1f))
            KeyButton(
                text = "×",
                onClick = { handleOperator("×") },
                modifier = Modifier.weight(1f),
                containerColor = SurfaceCardElevated,
                contentColor = AccentTurquoise
            )
        }

        Spacer(modifier = Modifier.height(rowSpacing))

        // Fila 3: 4, 5, 6, −
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            KeyButton(text = "4", onClick = { handleDigit("4") }, modifier = Modifier.weight(1f))
            KeyButton(text = "5", onClick = { handleDigit("5") }, modifier = Modifier.weight(1f))
            KeyButton(text = "6", onClick = { handleDigit("6") }, modifier = Modifier.weight(1f))
            KeyButton(
                text = "−",
                onClick = { handleOperator("-") },
                modifier = Modifier.weight(1f),
                containerColor = SurfaceCardElevated,
                contentColor = AccentTurquoise
            )
        }

        Spacer(modifier = Modifier.height(rowSpacing))

        // Fila 4: 1, 2, 3, +
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            KeyButton(text = "1", onClick = { handleDigit("1") }, modifier = Modifier.weight(1f))
            KeyButton(text = "2", onClick = { handleDigit("2") }, modifier = Modifier.weight(1f))
            KeyButton(text = "3", onClick = { handleDigit("3") }, modifier = Modifier.weight(1f))
            KeyButton(
                text = "+",
                onClick = { handleOperator("+") },
                modifier = Modifier.weight(1f),
                containerColor = SurfaceCardElevated,
                contentColor = AccentTurquoise
            )
        }

        Spacer(modifier = Modifier.height(rowSpacing))

        // Fila 5: 0, ,, ⌫, =
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            KeyButton(text = "0", onClick = { handleDigit("0") }, modifier = Modifier.weight(1f))
            KeyButton(text = ",", onClick = { handleComma() }, modifier = Modifier.weight(1f))
            KeyButton(
                text = "",
                onClick = { handleBackspace() },
                modifier = Modifier.weight(1f),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Borrar",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            KeyButton(
                text = "=",
                onClick = { handleEquals() },
                modifier = Modifier.weight(1f),
                containerColor = AccentTurquoise,
                contentColor = Color(0xFF0E1116)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
