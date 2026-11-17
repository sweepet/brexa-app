package com.brexa.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.brexa.app.data.local.BrexaDatabase
import com.brexa.app.data.reminder.DailyReminderManager
import com.brexa.app.data.repository.BrexaRepository
import com.brexa.app.data.sync.AppUpdateManager
import com.brexa.app.data.sync.GoogleDriveSyncManager
import com.brexa.app.data.sync.UpdateInfo
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.presentation.BrexaViewModelFactory
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrexamanOverlay
import com.brexa.app.ui.components.BrexamanChatSheet
import com.brexa.app.ui.screens.*
import com.brexa.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

                val extraScreen = remember { intent?.getStringExtra("EXTRA_SCREEN") }
                var currentScreen by remember {
                    mutableStateOf(
                        extraScreen ?: if (isUserSignedIn || hasChosenMode) "HOME" else "AUTH"
                    )
                }
                var preselectedRateId by remember { mutableStateOf<String?>(null) }
                var controlInitialTab by remember { mutableIntStateOf(0) }
                var transactionTypeToOpen by remember { mutableStateOf("EXPENSE") }
                var shouldForceTour by remember { mutableStateOf(false) }

                var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
                var isDownloadingUpdate by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableFloatStateOf(0f) }

                // Brexaman IA State
                var showBrexamanChat by remember { mutableStateOf(false) }
                val brexamanMessages by viewModel.brexamanMessages.collectAsState()
                val isBrexamanThinking by viewModel.isBrexamanThinking.collectAsState()
                val brexamanQueriesRemaining by viewModel.brexamanDailyQueriesRemaining.collectAsState()

                // Permiso de notificaciones para Android 13+ (API 33+)
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        DailyReminderManager.scheduleDailyReminder(applicationContext)
                        DailyReminderManager.scheduleMorningDuePaymentsCheck(applicationContext)
                    }
                }

                // Comprobaciones iniciales (actualización en GitHub y recordatorios programados)
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    DailyReminderManager.scheduleDailyReminder(applicationContext)
                    DailyReminderManager.scheduleMorningDuePaymentsCheck(applicationContext)

                    // Si ya son las 8:00 AM o más hoy, verificar si hay pagos para hoy (máx 1 vez al día)
                    val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    if (currentHour >= 8) {
                        DailyReminderManager.checkAndNotifyDuePayments(applicationContext)
                    }

                    // Limpieza proactiva de archivos APK residuales para recuperar almacenamiento
                    withContext(Dispatchers.IO) {
                        updateManager.cleanOldApks()
                    }

                    val result = updateManager.checkForUpdate()
                    result.onSuccess { update ->
                        if (update != null) {
                            availableUpdate = update
                        }
                    }

                    // Sincronización silenciosa de tasas de cambio (DolarApi)
                    viewModel.refreshMarketRates()
                }

                // Control de navegación atrás por hardware y gestos
                var lastBackPressTime by remember { mutableLongStateOf(0L) }

                // 1. Si el chat con Brexaman está abierto, cerrarlo con atrás
                BackHandler(enabled = showBrexamanChat) {
                    showBrexamanChat = false
                }

                // 2. Si el diálogo de actualización está abierto (sin descarga activa), cerrarlo con atrás
                BackHandler(enabled = availableUpdate != null && !isDownloadingUpdate && !showBrexamanChat) {
                    availableUpdate = null
                }

                // 3. Si el usuario está en cualquier sub-pantalla o pestaña distinta a HOME, atrás lo regresa a HOME
                val isSecondaryScreen = currentScreen != "HOME" && currentScreen != "AUTH"
                BackHandler(enabled = isSecondaryScreen && !showBrexamanChat && availableUpdate == null) {
                    currentScreen = "HOME"
                }

                // 4. Si el usuario está en HOME, doble toque de atrás para salir (evita salir por accidente)
                BackHandler(enabled = currentScreen == "HOME" && !showBrexamanChat && availableUpdate == null) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime < 2000L) {
                        finish()
                    } else {
                        lastBackPressTime = currentTime
                        Toast.makeText(applicationContext, "Presiona atrás de nuevo para salir", Toast.LENGTH_SHORT).show()
                    }
                }

                val isMainTabScreen = currentScreen in listOf("HOME", "RATES", "CALCULATOR", "PAYMENTS")

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundBase)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding(),
                    containerColor = BackgroundBase,
                    bottomBar = {
                        if (isMainTabScreen) {
                            NavigationBar(
                                containerColor = SurfaceCard,
                                contentColor = TextSecondary,
                                tonalElevation = 6.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == "HOME",
                                    onClick = { currentScreen = "HOME" },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = "Gastos"
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Gastos",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (currentScreen == "HOME") FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentGold,
                                        selectedTextColor = AccentGold,
                                        indicatorColor = AccentGold.copy(alpha = 0.18f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == "RATES",
                                    onClick = { currentScreen = "RATES" },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.CurrencyExchange,
                                            contentDescription = "Tasas"
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Tasas",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (currentScreen == "RATES") FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentGold,
                                        selectedTextColor = AccentGold,
                                        indicatorColor = AccentGold.copy(alpha = 0.18f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == "CALCULATOR",
                                    onClick = { currentScreen = "CALCULATOR" },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Calculate,
                                            contentDescription = "Calculadora"
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Calculadora",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (currentScreen == "CALCULATOR") FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentGold,
                                        selectedTextColor = AccentGold,
                                        indicatorColor = AccentGold.copy(alpha = 0.18f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentScreen == "PAYMENTS",
                                    onClick = { currentScreen = "PAYMENTS" },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.PendingActions,
                                            contentDescription = "Pagos"
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Pagos",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (currentScreen == "PAYMENTS") FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentGold,
                                        selectedTextColor = AccentGold,
                                        indicatorColor = AccentGold.copy(alpha = 0.18f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
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
                                    onOpenAnalytics = {
                                        currentScreen = "ANALYTICS"
                                    },
                                    onOpenSettings = { currentScreen = "SETTINGS" },
                                    onOpenPendingExpenses = {
                                        currentScreen = "PAYMENTS"
                                    },
                                    onOpenTransactionHistory = {
                                        currentScreen = "TRANSACTION_HISTORY"
                                    },
                                    forceStartTour = shouldForceTour,
                                    onTourDismissed = { shouldForceTour = false }
                                )
                            }
                            "RATES" -> {
                                RatesScreen(
                                    viewModel = viewModel,
                                    onNavigateToCalculatorWithRate = { rateItem ->
                                        preselectedRateId = rateItem.id
                                        currentScreen = "CALCULATOR"
                                    }
                                )
                            }
                            "CALCULATOR" -> {
                                CalculatorScreen(
                                    viewModel = viewModel,
                                    preselectedRateId = preselectedRateId,
                                    onNavigateToTransactionWithAmount = { amount, rate, curr ->
                                        transactionTypeToOpen = "EXPENSE"
                                        currentScreen = "TRANSACTION_FORM"
                                    }
                                )
                            }
                            "PAYMENTS", "PENDING_EXPENSES", "CONTROL" -> {
                                PendingExpensesScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = "HOME" }
                                )
                            }
                            "TRANSACTION_HISTORY" -> {
                                TransactionHistoryScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = "HOME" }
                                )
                            }
                            "ANALYTICS" -> {
                                AnalyticsScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = "HOME" }
                                )
                            }
                            "TRANSACTION_FORM" -> {
                                TransactionFormScreen(
                                    viewModel = viewModel,
                                    initialType = transactionTypeToOpen,
                                    onDismiss = { currentScreen = "HOME" }
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
                                    onBack = { currentScreen = "HOME" },
                                    onRestartTour = {
                                        prefs.edit().putBoolean("has_seen_onboarding_tour", false).apply()
                                        shouldForceTour = true
                                        currentScreen = "HOME"
                                    }
                                )
                            }
                        }

                        // Mascota y Asistente Brexaman interactivo
                        if (currentScreen == "HOME") {
                            BrexamanOverlay(
                                onOpenAssistant = {
                                    viewModel.refreshBrexamanDailyUsageSync()
                                    showBrexamanChat = true
                                }
                            )
                        }
                    }
                }

                    // Modal de Conversacion con Brexaman
                    BrexamanChatSheet(
                        isOpen = showBrexamanChat,
                        onDismiss = { showBrexamanChat = false },
                        messages = brexamanMessages,
                        isThinking = isBrexamanThinking,
                        queriesRemaining = brexamanQueriesRemaining,
                        dailyLimit = viewModel.brexamanDailyLimit,
                        onClearChat = { viewModel.clearBrexamanChat() },
                        onSendMessage = { prompt: String -> viewModel.sendBrexamanMessage(prompt) },
                        onSendQuickPrompt = { promptType: String -> viewModel.sendBrexamanQuickPrompt(promptType) }
                    )

                    // Cartel Elegante de Actualización Automática In-App
                    if (availableUpdate != null) {
                        val update = availableUpdate!!
                        AlertDialog(
                            onDismissRequest = { if (!isDownloadingUpdate) availableUpdate = null },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = SurfaceCard,
                            title = {
                                Text(
                                    text = "⚡ NUEVA VERSIÓN // ${update.versionName}",
                                    style = BrexaTypography.titleLarge.copy(color = AccentGold)
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Hay una nueva actualización disponible en GitHub con mejoras y correcciones.",
                                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                                    )
                                    val notes = if (update.releaseNotes.isNotBlank() && !update.releaseNotes.trim().equals("null", ignoreCase = true)) {
                                        update.releaseNotes
                                    } else {
                                        "Mejora en la navegación y retorno con gestos, optimizaciones de estabilidad y rendimiento."
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 140.dp)
                                            .background(BackgroundBase, RoundedCornerShape(10.dp))
                                            .border(1.dp, BorderSolid, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = notes,
                                            style = BrexaTypography.labelMedium.copy(color = TextSecondary, fontSize = 13.sp)
                                        )
                                    }
                                    if (isDownloadingUpdate) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "DESCARGANDO APK: ${(downloadProgress * 100).toInt()}%",
                                            style = BrexaTypography.labelLarge.copy(color = AccentGold, fontWeight = FontWeight.Bold)
                                        )
                                        // Barra de progreso
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp)
                                                .background(BackgroundBase, RoundedCornerShape(7.dp))
                                                .border(1.dp, BorderSolid, RoundedCornerShape(7.dp))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(fraction = downloadProgress.coerceIn(0.01f, 1f))
                                                    .background(AccentGold, RoundedCornerShape(7.dp))
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                if (!isDownloadingUpdate) {
                                    BrutalistBadge(
                                        text = "ACTUALIZAR AHORA",
                                        backgroundColor = AccentGold,
                                        contentColor = Color(0xFF0D1117),
                                        borderColor = AccentGold,
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
