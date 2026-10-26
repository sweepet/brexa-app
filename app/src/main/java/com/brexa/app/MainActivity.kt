package com.brexa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.brexa.app.data.local.BrexaDatabase
import com.brexa.app.data.repository.BrexaRepository
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.presentation.BrexaViewModelFactory
import com.brexa.app.ui.screens.*
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
                var currentScreen by remember { mutableStateOf("HOME") }
                var transactionTypeToOpen by remember { mutableStateOf("EXPENSE") }

                when (currentScreen) {
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
