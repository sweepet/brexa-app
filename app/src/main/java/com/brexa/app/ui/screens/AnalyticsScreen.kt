package com.brexa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.R
import com.brexa.app.presentation.AnalyticsPeriod
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.theme.*
import com.brexa.app.ui.util.CurrencyFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AnalyticsScreen(
    viewModel: BrexaViewModel,
    onBack: () -> Unit
) {
    val distribution by viewModel.categoryDistribution.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val currentMonth by viewModel.analyticsMonth.collectAsState()
    val currentYear by viewModel.analyticsYear.collectAsState()
    val currentType by viewModel.analyticsType.collectAsState()
    val currentPeriod by viewModel.analyticsPeriod.collectAsState()
    val weekCalendar by viewModel.analyticsWeekCalendar.collectAsState()

    val monthNames = listOf(
        "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
        "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"
    )

    val periodDisplayLabel = remember(currentPeriod, currentMonth, currentYear, weekCalendar) {
        if (currentPeriod == AnalyticsPeriod.MONTH) {
            "${monthNames[currentMonth]} $currentYear"
        } else {
            val cal = weekCalendar.clone() as Calendar
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
            cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
            val startDay = cal.get(Calendar.DAY_OF_MONTH)
            val startMonth = monthNames[cal.get(Calendar.MONTH)].take(3)

            cal.add(Calendar.DAY_OF_MONTH, 6)
            val endDay = cal.get(Calendar.DAY_OF_MONTH)
            val endMonth = monthNames[cal.get(Calendar.MONTH)].take(3)
            val endYear = cal.get(Calendar.YEAR)

            if (startMonth == endMonth) {
                "$startDay - $endDay $startMonth $endYear"
            } else {
                "$startDay $startMonth - $endDay $endMonth $endYear"
            }
        }
    }

    val formatter = remember { CurrencyFormat.standardFormatter }
    val totalPeriodUsd = distribution.sumOf { it.totalUsd }

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
                text = "ANALÍTICA Y REPORTES",
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

        // 1. Selector de Período: SEMANA vs MES (Neobrutalista)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BorderSolid, RectangleShape)
        ) {
            val isWeek = currentPeriod == AnalyticsPeriod.WEEK
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isWeek) AccentLime else SurfaceCard, RectangleShape)
                    .clickable { viewModel.setAnalyticsPeriod(AnalyticsPeriod.WEEK) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SEMANA (LUN - DOM)",
                    style = BrexaTypography.labelLarge.copy(
                        color = if (isWeek) Color.Black else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (!isWeek) AccentLime else SurfaceCard, RectangleShape)
                    .clickable { viewModel.setAnalyticsPeriod(AnalyticsPeriod.MONTH) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MES COMPLETO",
                    style = BrexaTypography.labelLarge.copy(
                        color = if (!isWeek) Color.Black else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Navegador de Período (100% Responsive)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RectangleShape)
                .border(2.dp, BorderSolid, RectangleShape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrutalistBadge(
                text = "◀ ANTERIOR",
                backgroundColor = BackgroundBase,
                contentColor = TextPrimary,
                borderColor = BorderSolid,
                modifier = Modifier.clickable { viewModel.previousPeriod() }
            )

            Text(
                text = periodDisplayLabel,
                style = BrexaTypography.labelLarge.copy(
                    color = AccentLime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            BrutalistBadge(
                text = "SIGUIENTE ▶",
                backgroundColor = BackgroundBase,
                contentColor = TextPrimary,
                borderColor = BorderSolid,
                modifier = Modifier.clickable { viewModel.nextPeriod() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle: GASTOS vs INGRESOS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BorderSolid, RectangleShape)
        ) {
            val isExpense = currentType == "EXPENSE"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isExpense) ExpenseRed else SurfaceCard, RectangleShape)
                    .clickable { viewModel.setAnalyticsType("EXPENSE") }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "GASTOS (TOTAL)",
                    style = BrexaTypography.labelLarge.copy(
                        color = if (isExpense) Color.Black else TextSecondary,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (!isExpense) IncomeGreen else SurfaceCard, RectangleShape)
                    .clickable { viewModel.setAnalyticsType("INCOME") }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "INGRESOS (TOTAL)",
                    style = BrexaTypography.labelLarge.copy(
                        color = if (!isExpense) Color.Black else TextSecondary,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta de Total del Período
        BrutalistCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = SurfaceCard,
            borderColor = if (currentType == "EXPENSE") ExpenseRed else IncomeGreen,
            borderWidth = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val baseCode = baseCurrency?.code ?: "USD"
                    val baseSymbol = baseCurrency?.symbol ?: "$"
                    val periodSuffix = if (currentPeriod == AnalyticsPeriod.WEEK) "(ESTA SEMANA)" else "(ESTE MES)"
                    Text(
                        text = if (currentType == "EXPENSE") "TOTAL GASTADO $periodSuffix" else "TOTAL INGRESADO $periodSuffix",
                        style = BrexaTypography.labelMedium.copy(color = TextSecondary)
                    )
                    Text(
                        text = "$baseSymbol ${formatter.format(totalPeriodUsd)} $baseCode",
                        style = BrexaTypography.displayMedium.copy(
                            color = if (currentType == "EXPENSE") ExpenseRed else IncomeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                BrutalistBadge(
                    text = "${distribution.size} CATEGORÍAS",
                    backgroundColor = BackgroundBase,
                    contentColor = TextPrimary,
                    borderColor = BorderSolid
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "DESGLOSE PORCENTUAL POR CATEGORÍA",
            style = BrexaTypography.labelLarge.copy(color = TextPrimary),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (distribution.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RectangleShape)
                    .border(1.5.dp, BorderSolid, RectangleShape)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (currentPeriod == AnalyticsPeriod.WEEK) "SIN REGISTROS PARA ESTA SEMANA" else "SIN REGISTROS PARA ESTE MES",
                    style = BrexaTypography.labelMedium.copy(color = TextMuted)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(distribution) { item ->
                    val iconRes = when (item.categoryIconId) {
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

                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = SurfaceCard,
                        borderColor = BorderSolid
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                        contentDescription = item.categoryName,
                                        tint = AccentLime,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.categoryName.uppercase(),
                                        style = BrexaTypography.labelLarge.copy(color = TextPrimary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "${baseCurrency?.symbol ?: "$"} ${formatter.format(item.totalUsd)} ${baseCurrency?.code ?: "USD"}",
                                    style = BrexaTypography.labelLarge.copy(
                                        color = AccentLime,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Barra de Progreso Neobrutalista
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .background(BackgroundBase, RectangleShape)
                                    .border(1.5.dp, BorderSolid, RectangleShape)
                            ) {
                                val fillFraction = (item.percentage.toFloat() / 100f).coerceIn(0.01f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = fillFraction)
                                        .background(
                                            if (currentType == "EXPENSE") ExpenseRed else IncomeGreen,
                                            RectangleShape
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "${String.format(Locale("es", "ES"), "%.1f", item.percentage)} % DEL TOTAL",
                                    style = BrexaTypography.labelMedium.copy(
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
