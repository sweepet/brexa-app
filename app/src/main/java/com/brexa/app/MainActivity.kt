package com.brexa.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.brexa.app.data.local.BrexaDatabase
import com.brexa.app.data.repository.BrexaRepository
import com.brexa.app.data.sync.AppUpdateManager
import com.brexa.app.data.sync.GoogleDriveSyncManager
import com.brexa.app.data.sync.UpdateInfo
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.presentation.BrexaViewModelFactory
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.screens.*
import com.brexa.app.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización de persistencia y casos de uso
        val database = BrexaDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = BrexaRepository(database)
        val syncManagerInstance = GoogleDriveSyncManager(applicationContext)
        val viewModel = ViewModelProvider(
            this,
            BrexaViewModelFactory(repository, syncManagerInstance)
        )[BrexaViewModel::class.java]

        setContent {
            BrexaTheme {
                val syncManager = remember { syncManagerInstance }
                val updateManager = remember { AppUpdateManager(applicationContext) }
                val prefs = remember { getSharedPreferences("brexa_prefs", Context.MODE_PRIVATE) }
                val isUserSignedIn = remember { syncManager.getLastSignedInAccount() != null }
                val hasChosenMode = remember { prefs.getBoolean("has_chosen_mode", false) }

                var currentScreen by remember {
                    mutableStateOf(if (isUserSignedIn || hasChosenMode) "HOME" else "AUTH")
                }
                var transactionTypeToOpen by remember { mutableStateOf("EXPENSE") }

                var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
                var isDownloadingUpdate by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableFloatStateOf(0f) }

                // Comprobación automática de actualización en segundo plano al iniciar la app
                LaunchedEffect(Unit) {
                    val result = updateManager.checkForUpdate()
                    result.onSuccess { update ->
                        if (update != null) {
                            availableUpdate = update
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundBase)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = BackgroundBase
                ) {
                    when (currentScreen) {
                        "AUTH" -> {
                            AuthScreen(
                                viewModel = viewModel,
                                onAuthSuccess = {
                                    prefs.edit().putBoolean("has_chosen_mode", true).apply()
                                    currentScreen = "HOME"
                                },
                                onContinueGuest = {
                                    prefs.edit().putBoolean("has_chosen_mode", true).apply()
                                    currentScreen = "HOME"
                                }
                            )
                        }
                        "HOME" -> {
                            HomeScreen(
                                viewModel = viewModel,
                                onOpenTransactionForm = { type ->
                                    transactionTypeToOpen = type
                                    currentScreen = "TRANSACTION_FORM"
                                },
                                onOpenAccountsManagement = { currentScreen = "ACCOUNTS" },
                                onOpenAnalytics = { currentScreen = "ANALYTICS" },
                                onOpenSettings = { currentScreen = "SETTINGS" }
                            )
                        }
                        "TRANSACTION_FORM" -> {
                            TransactionFormScreen(
                                viewModel = viewModel,
                                initialType = transactionTypeToOpen,
                                onDismiss = { currentScreen = "HOME" }
                            )
                        }
                        "ANALYTICS" -> {
                            AnalyticsScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "HOME" }
                            )
                        }
                        "ACCOUNTS" -> {
                            AccountsCurrenciesScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "HOME" }
                            )
                        }
                        "SETTINGS" -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "HOME" }
                            )
                        }
                    }

                    // Cartel Neobrutalista de Actualización Automática In-App
                    if (availableUpdate != null) {
                        val update = availableUpdate!!
                        AlertDialog(
                            onDismissRequest = { if (!isDownloadingUpdate) availableUpdate = null },
                            shape = RectangleShape,
                            containerColor = SurfaceCard,
                            title = {
                                Text(
                                    text = "⚡ NUEVA VERSIÓN // ${update.versionName}",
                                    style = BrexaTypography.titleLarge.copy(color = AccentLime)
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Hay una nueva actualización disponible en GitHub con mejoras y correcciones.",
                                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                                    )
                                    if (update.releaseNotes.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 140.dp)
                                                .background(BackgroundBase, RectangleShape)
                                                .border(1.dp, BorderSolid, RectangleShape)
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = update.releaseNotes,
                                                style = BrexaTypography.labelMedium.copy(color = TextSecondary, fontSize = 11.sp)
                                            )
                                        }
                                    }
                                    if (isDownloadingUpdate) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "DESCARGANDO APK: ${(downloadProgress * 100).toInt()}%",
                                            style = BrexaTypography.labelLarge.copy(color = AccentLime, fontWeight = FontWeight.Bold)
                                        )
                                        // Barra de progreso neobrutalista
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp)
                                                .background(BackgroundBase, RectangleShape)
                                                .border(1.5.dp, BorderSolid, RectangleShape)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(fraction = downloadProgress.coerceIn(0.01f, 1f))
                                                    .background(AccentLime, RectangleShape)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                if (!isDownloadingUpdate) {
                                    BrutalistBadge(
                                        text = "ACTUALIZAR AHORA",
                                        backgroundColor = AccentLime,
                                        contentColor = Color.Black,
                                        borderColor = AccentLime,
                                        modifier = Modifier.clickable {
                                            isDownloadingUpdate = true
                                            lifecycleScope.launch {
                                                val res = updateManager.downloadAndInstall(update) { progress ->
                                                    downloadProgress = progress
                                                }
                                                isDownloadingUpdate = false
                                                res.onFailure { err ->
                                                    Toast.makeText(
                                                        applicationContext,
                                                        "Error al actualizar: ${err.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            },
                            dismissButton = {
                                if (!isDownloadingUpdate) {
                                    BrutalistBadge(
                                        text = "MÁS TARDE",
                                        backgroundColor = BackgroundBase,
                                        contentColor = TextSecondary,
                                        borderColor = BorderSolid,
                                        modifier = Modifier.clickable { availableUpdate = null }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
