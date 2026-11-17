package com.brexa.app.ui.screens

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import java.util.Calendar
import java.util.Locale

@Composable
fun RatesScreen(
    viewModel: BrexaViewModel,
    onNavigateToCalculatorWithRate: (MarketRateItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val liveRates by viewModel.marketRates.collectAsState()
    val isRefreshing by viewModel.isRefreshingMarketRates.collectAsState()
    val headerDate by viewModel.ratesHeaderDate.collectAsState()

    val isHistoricalMode by viewModel.isHistoricalMode.collectAsState()
    val historicalDateStr by viewModel.historicalDisplayDate.collectAsState()
    val historicalRates by viewModel.historicalRates.collectAsState()
    val isLoadingHistorical by viewModel.isLoadingHistorical.collectAsState()

    val context = LocalContext.current

    // Auto-refresh silencioso si las tasas tienen más de 60 minutos
    LaunchedEffect(Unit) {
        viewModel.checkAndAutoRefreshRates()
    }

    // Configurar DatePickerDialog nativo para consultar días pasados
    val calendar = remember { Calendar.getInstance() }
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                viewModel.loadHistoricalRates(
                    year = year,
                    month = month + 1,
                    day = dayOfMonth,
                    onSuccess = {
                        Toast.makeText(context, "Tasas históricas cargadas", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        label = "refresh_rotation"
    )

    val currentDisplayRates = if (isHistoricalMode) historicalRates else liveRates

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
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
                    text = "TASAS",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isHistoricalMode) "Historial consultado" else "Cotizaciones del mercado",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón selector de fecha histórica
                IconButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isHistoricalMode) AccentTurquoise.copy(alpha = 0.2f) else SurfaceCardElevated,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Consultar fecha pasada",
                        tint = if (isHistoricalMode) AccentTurquoise else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Botón refrescar (o volver a hoy si estamos en histórico)
                IconButton(
                    onClick = {
                        if (isHistoricalMode) {
                            viewModel.clearHistoricalMode()
                            Toast.makeText(context, "Regresando a tasas de hoy", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.refreshMarketRates(
                                onSuccess = {
                                    Toast.makeText(context, "Tasas actualizadas", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(SurfaceCardElevated, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isHistoricalMode) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isHistoricalMode) "Volver a hoy" else "Actualizar tasas",
                        tint = AccentTurquoise,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (!isHistoricalMode) rotationAngle else 0f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- SUBHEADER BANNER: FECHA Y FRECUENCIA DE ACTUALIZACIÓN ---
        if (isHistoricalMode) {
            // Banner de Consulta Histórica Activa
            Surface(
                color = SurfaceCardElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentTurquoise),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = AccentTurquoise,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "HISTORIAL CONSULTADO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = AccentTurquoise,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = historicalDateStr ?: "Fecha pasada",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    // Botón para salir del modo histórico
                    Surface(
                        color = AccentTurquoise,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            viewModel.clearHistoricalMode()
                            Toast.makeText(context, "Tasas actuales", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = "HOY ✕",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0E1116),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else {
            // Banner Normal simplificado y con aire (estilo limpio)
            Surface(
                color = SurfaceCardElevated.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderSolid),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { datePickerDialog.show() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = AccentTurquoise,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = headerDate.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FECHA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentTurquoise
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = AccentTurquoise,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- LISTA DE TASAS ---
        if (isLoadingHistorical) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentTurquoise, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Consultando tasas históricas...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else if (currentDisplayRates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentTurquoise)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(currentDisplayRates, key = { it.id }) { item ->
                    MarketRateCard(
                        item = item,
                        onClick = { onNavigateToCalculatorWithRate(item) },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Tasa ${item.title}", item.formattedRate)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "${item.title} copiada: ${item.currencySymbol} ${item.formattedRate}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketRateCard(
    item: MarketRateItem,
    onClick: () -> Unit,
    onCopy: () -> Unit
) {
    // Contador sutil a la próxima hora para la tasa de Binance P2P (USDT)
    val subtitleText = remember(item.id, item.subtitle) {
        if (item.id == "USDT") {
            val cal = Calendar.getInstance()
            val minLeft = 59 - cal.get(Calendar.MINUTE)
            val secLeft = 59 - cal.get(Calendar.SECOND)
            "Binance P2P • Próxima tasa en ${String.format(Locale.US, "%02d:%02d", minLeft, secLeft)}"
        } else {
            item.subtitle
        }
    }

    Surface(
        color = SurfaceCard,
        shape = CardShape,
        border = BorderStroke(1.dp, BorderSolid),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mitad izquierda: Al presionar lleva a la Calculadora
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Mitad derecha: Al presionar el monto o la ficha derecha copia la tasa
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .clickable(onClick = onCopy)
                    .padding(start = 8.dp, top = 14.dp, bottom = 14.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${item.currencySymbol} ${item.formattedRate}",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isPositive = item.changePercent >= 0
                    val icon = if (isPositive) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown
                    val changeColor = if (isPositive) IncomeGreen else ExpenseRed

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = changeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.4f", item.changePercent)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = changeColor
                    )
                }
            }
        }
    }
}
