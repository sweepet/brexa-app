package com.brexa.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.brexa.app.data.local.BrexaDatabase
import com.brexa.app.data.repository.BrexaRepository
import com.brexa.app.data.sync.GoogleDriveSyncManager
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.presentation.BrexaViewModelFactory
import com.brexa.app.ui.screens.*
import com.brexa.app.ui.theme.BackgroundBase
import com.brexa.app.ui.theme.BrexaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización de persistencia y casos de uso
        val database = BrexaDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = BrexaRepository(database)
        val viewModel = ViewModelProvider(
            this,
            BrexaViewModelFactory(repository)
        )[BrexaViewModel::class.java]

        setContent {
            BrexaTheme {
                val syncManager = remember { GoogleDriveSyncManager(applicationContext) }
                val prefs = remember { getSharedPreferences("brexa_prefs", Context.MODE_PRIVATE) }
                val isUserSignedIn = remember { syncManager.getLastSignedInAccount() != null }
                val hasChosenMode = remember { prefs.getBoolean("has_chosen_mode", false) }

                var currentScreen by remember {
                    mutableStateOf(if (isUserSignedIn || hasChosenMode) "HOME" else "AUTH")
                }
                var transactionTypeToOpen by remember { mutableStateOf("EXPENSE") }

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
                }
            }
        }
    }
}
