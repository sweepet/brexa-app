package com.brexa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.theme.*

@Composable
fun ControlScreen(
    viewModel: BrexaViewModel,
    initialTab: Int = 0,
    onNavigateToWallet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
    ) {
        // --- SELECTOR DE PESTAÑAS SUPERIOR DE CONTROL ---
        Surface(
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceCard,
                    contentColor = AccentGold,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = AccentGold,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = BorderSolid, thickness = 1.dp)
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "PAGOS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) AccentGold else TextSecondary
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.PendingActions,
                                contentDescription = "Pagos Pendientes",
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedTab == 0) AccentGold else TextSecondary
                            )
                        }
                    )

                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "HISTORIAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) AccentGold else TextSecondary
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Historial",
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedTab == 1) AccentGold else TextSecondary
                            )
                        }
                    )

                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                text = "ANÁLISIS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 2) AccentGold else TextSecondary
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Estadísticas",
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedTab == 2) AccentGold else TextSecondary
                            )
                        }
                    )
                }
            }
        }

        // --- CONTENIDO DE LA SUB-PESTAÑA ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> PendingExpensesScreen(
                    viewModel = viewModel,
                    onBack = onNavigateToWallet
                )
                1 -> TransactionHistoryScreen(
                    viewModel = viewModel,
                    onBack = onNavigateToWallet
                )
                2 -> AnalyticsScreen(
                    viewModel = viewModel,
                    onBack = onNavigateToWallet
                )
            }
        }
    }
}
