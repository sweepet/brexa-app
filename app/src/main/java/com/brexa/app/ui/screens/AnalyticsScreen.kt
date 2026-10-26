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
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.theme.*
import java.text.DecimalFormat

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

    val monthNames = listOf(
        "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
        "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"
    )

    val formatter = remember { DecimalFormat("#,##0.00") }
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

        // Navegador de Mes (100% Responsive)
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
                modifier = Modifier.clickable { viewModel.previousMonth() }
            )

            Text(
                text = "${monthNames[currentMonth]} $currentYear",
                style = BrexaTypography.labelLarge.copy(
                    color = AccentLime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            BrutalistBadge(
                text = "SIGUIENTE ▶",
                backgroundColor = BackgroundBase,
                contentColor = TextPrimary,
                borderColor = BorderSolid,
                modifier = Modifier.clickable { viewModel.nextMonth() }
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
                    Text(
                        text = if (currentType == "EXPENSE") "TOTAL GASTADO EN $baseCode" else "TOTAL INGRESADO EN $baseCode",
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
                    text = "SIN REGISTROS PARA ESTE MES",
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
                                    text = "${String.format("%.1f", item.percentage)} % DEL TOTAL",
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
