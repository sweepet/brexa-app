package com.brexa.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.data.rates.MarketRateItem
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistButton
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.components.CardShape
import com.brexa.app.ui.theme.*
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
    val headerDate by viewModel.ratesHeaderDate.collectAsState()
    val context = LocalContext.current

    // Tasa seleccionada actualmente
    var selectedRateId by remember(rates, preselectedRateId) {
        val initialId = preselectedRateId ?: "BCV_USD"
        mutableStateOf(if (rates.any { it.id == initialId }) initialId else (rates.firstOrNull()?.id ?: "BCV_USD"))
    }

    val currentRateItem = rates.find { it.id == selectedRateId } ?: rates.firstOrNull()
    val activeRate = currentRateItem?.rate ?: 914.02

    // Casilla activa para edición: 0 = Moneda Base (USD/EUR/USDT), 1 = Moneda Local (VES/COP)
    var activeBox by remember { mutableIntStateOf(0) }

    // Texto tipeado en la casilla activa
    var inputStr by remember { mutableStateOf("1") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var shouldClearOnNextDigit by remember { mutableStateOf(false) }

    val numberFormat = remember {
        val symbols = DecimalFormatSymbols(Locale("es", "VE")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        DecimalFormat("#,##0.##", symbols)
    }

    // Monedas de las casillas
    val baseCurrency = remember(currentRateItem) {
        when (currentRateItem?.id) {
            "BCV_EUR" -> "EUR"
            "USDT" -> "USDT"
            else -> "USD"
        }
    }

    val localCurrency = remember(currentRateItem) {
        if (currentRateItem?.id == "COP_TRM") "COP" else "VES"
    }

    // Valor numérico de lo que se tipea en la casilla activa
    val activeAmount = remember(inputStr) {
        val sanitized = inputStr.replace(".", "").replace(",", ".")
        sanitized.toDoubleOrNull() ?: 0.0
    }

    // Cálculo dinámico para la otra casilla
    val calculatedAmount = remember(activeBox, activeAmount, activeRate) {
        if (activeBox == 0) {
            // Se está editando Dólares/Euros -> calcular Bolívares/Pesos
            activeAmount * activeRate
        } else {
            // Se está editando Bolívares/Pesos -> calcular cuántos Dólares/Euros son
            if (activeRate > 0.0) activeAmount / activeRate else 0.0
        }
    }

    // Valores a mostrar en cada casilla
    val box0Display = if (activeBox == 0) inputStr else numberFormat.format(calculatedAmount)
    val box1Display = if (activeBox == 1) inputStr else numberFormat.format(calculatedAmount)

    // Función para cambiar de casilla activa preservando el valor actual
    fun switchActiveBox(newBox: Int) {
        if (activeBox != newBox) {
            // Al cambiar, inicializamos el input de la nueva casilla con el valor calculado actual
            val nextVal = calculatedAmount
            inputStr = if (nextVal > 0.0) {
                // Formatear limpio para edición sin separador de miles
                String.format(Locale.US, "%.2f", nextVal).replace(".", ",")
            } else {
                if (newBox == 0) "1" else "1000"
            }
            operand1 = null
            pendingOperator = null
            shouldClearOnNextDigit = false
            activeBox = newBox
        }
    }

    // Manejador del teclado
    fun handleDigit(digit: String) {
        if (shouldClearOnNextDigit) {
            inputStr = digit
            shouldClearOnNextDigit = false
        } else {
            if (inputStr == "0" && digit != ",") {
                inputStr = digit
            } else {
                if (inputStr.length < 14) {
                    inputStr += digit
                }
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
        inputStr = if (activeBox == 0) "1" else "1000"
        operand1 = null
        pendingOperator = null
        shouldClearOnNextDigit = false
    }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copiado: $text", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // --- TOP BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CALCULADORA",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Conversor bidireccional en tiempo real",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Alternar casilla activa rápidamente
            IconButton(
                onClick = { switchActiveBox(if (activeBox == 0) 1 else 0) },
                modifier = Modifier
                    .size(42.dp)
                    .background(SurfaceCardElevated, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Invertir casilla activa",
                    tint = AccentGold,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SELECTOR DE TASAS RÁPIDO (CHIPS DESLIZABLES) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rates.forEach { rateItem ->
                val isSelected = rateItem.id == selectedRateId
                Surface(
                    color = if (isSelected) AccentGold else SurfaceCard,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isSelected) AccentGold else BorderSolid),
                    modifier = Modifier.clickable { selectedRateId = rateItem.id }
                ) {
                    Text(
                        text = rateItem.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF0D1117) else TextPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- CASILLA 1: MONEDA BASE (USD / EUR / USDT) ---
        val isBox0Active = activeBox == 0
        Surface(
            color = if (isBox0Active) SurfaceCardElevated else SurfaceCard,
            shape = CardShape,
            border = BorderStroke(
                if (isBox0Active) 2.dp else 1.dp,
                if (isBox0Active) AccentGold else BorderSolid
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { switchActiveBox(0) }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = baseCurrency,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isBox0Active) {
                            BrutalistBadge(
                                text = "EDITANDO ✎",
                                backgroundColor = AccentGold,
                                contentColor = Color(0xFF0D1117)
                            )
                        } else {
                            Text(
                                text = "Toca para escribir",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = box0Display,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (isBox0Active) AccentGold else IncomeGreen
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = { copyToClipboard(box0Display, baseCurrency) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1 $baseCurrency = ${currentRateItem?.currencySymbol ?: "Bs."} ${currentRateItem?.formattedRate ?: ""} $localCurrency",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- CASILLA 2: MONEDA LOCAL (VES / COP) ---
        val isBox1Active = activeBox == 1
        Surface(
            color = if (isBox1Active) SurfaceCardElevated else SurfaceCard,
            shape = CardShape,
            border = BorderStroke(
                if (isBox1Active) 2.dp else 1.dp,
                if (isBox1Active) AccentGold else BorderSolid
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { switchActiveBox(1) }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = localCurrency,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isBox1Active) {
                            BrutalistBadge(
                                text = "EDITANDO ✎",
                                backgroundColor = AccentGold,
                                contentColor = Color(0xFF0D1117)
                            )
                        } else {
                            Text(
                                text = "Toca para escribir",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = box1Display,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (isBox1Active) AccentGold else IncomeGreen
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = { copyToClipboard(box1Display, localCurrency) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isBox1Active) {
                        "Ingresando en $localCurrency ➔ Convierte a $baseCurrency"
                    } else {
                        "Equivalente en $localCurrency a tasa de mercado"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- BOTÓN ANCHO REINICIAR ---
        Button(
            onClick = { handleReset() },
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGold,
                contentColor = Color(0xFF0D1117)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Text(
                text = "REINICIAR",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- TECLADO NUMÉRICO TÁCTIL PERSONALIZADO ---
        val buttonHeight = 52.dp
        val buttonShape = RoundedCornerShape(12.dp)

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
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                }
            }
        }

        // Fila 1: 7, 8, 9, ÷
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyButton(text = "7", onClick = { handleDigit("7") }, modifier = Modifier.weight(1f))
            KeyButton(text = "8", onClick = { handleDigit("8") }, modifier = Modifier.weight(1f))
            KeyButton(text = "9", onClick = { handleDigit("9") }, modifier = Modifier.weight(1f))
            KeyButton(
                text = "÷",
                onClick = { handleOperator("÷") },
                modifier = Modifier.weight(1f),
                containerColor = SurfaceCardElevated,
                contentColor = AccentGold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fila 2: 4, 5, 6, ×
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyButton(text = "4", onClick = { handleDigit("4") }, modifier = Modifier.weight(1f))
            KeyButton(text = "5", onClick = { handleDigit("5") }, modifier = Modifier.weight(1f))
            KeyButton(text = "6", onClick = { handleDigit("6") }, modifier = Modifier.weight(1f))
            KeyButton(
                text = "×",
                onClick = { handleOperator("×") },
                modifier = Modifier.weight(1f),
                containerColor = SurfaceCardElevated,
                contentColor = AccentGold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fila 3: 1, 2, 3, -
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyButton(text = "1", onClick = { handleDigit("1") }, modifier = Modifier.weight(1f))
            KeyButton(text = "2", onClick = { handleDigit("2") }, modifier = Modifier.weight(1f))
            KeyButton(text = "3", onClick = { handleDigit("3") }, modifier = Modifier.weight(1f))
            KeyButton(
                text = "-",
                onClick = { handleOperator("-") },
                modifier = Modifier.weight(1f),
                containerColor = SurfaceCardElevated,
                contentColor = AccentGold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fila 4: ,, 0, ⌫, +
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyButton(text = ",", onClick = { handleComma() }, modifier = Modifier.weight(1f))
            KeyButton(text = "0", onClick = { handleDigit("0") }, modifier = Modifier.weight(1f))
            KeyButton(
                text = "",
                onClick = { handleBackspace() },
                modifier = Modifier.weight(1f),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Borrar",
                        tint = ExpenseRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
            )
            KeyButton(
                text = "+",
                onClick = { handleOperator("+") },
                modifier = Modifier.weight(1f),
                containerColor = SurfaceCardElevated,
                contentColor = AccentGold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fila 5: =
        Button(
            onClick = { handleEquals() },
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceCardElevated,
                contentColor = AccentGold
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderSolid),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "=",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- SUBHEADER FECHA ---
        Surface(
            color = SurfaceCardElevated.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = headerDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        // --- REGISTRAR EN BREXA ---
        val amountToRegister = if (activeBox == 0) activeAmount else calculatedAmount
        if (onNavigateToTransactionWithAmount != null && amountToRegister > 0.0) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    onNavigateToTransactionWithAmount(amountToRegister, activeRate, baseCurrency)
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentGold),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "REGISTRAR OPERACIÓN EN BREXA",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
