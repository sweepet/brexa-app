package com.brexa.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.R
import com.brexa.app.data.sync.DriveBackupInfo
import com.brexa.app.data.sync.GoogleDriveSyncManager
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistButton
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    viewModel: BrexaViewModel,
    onAuthSuccess: () -> Unit,
    onContinueGuest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncManager = remember { GoogleDriveSyncManager(context) }

    var isLoading by remember { mutableStateOf(false) }
    var detectedBackupInfo by remember { mutableStateOf<DriveBackupInfo?>(null) }
    var pendingAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    var showRestorePrompt by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                pendingAccount = account
                isLoading = true
                statusMessage = "Verificando si tienes respaldos en Google Drive..."

                coroutineScope.launch {
                    val backup = syncManager.findBackupFile(account)
                    isLoading = false
                    if (backup != null) {
                        detectedBackupInfo = backup
                        showRestorePrompt = true
                    } else {
                        Toast.makeText(context, "Sesión iniciada: ${account.email}", Toast.LENGTH_SHORT).show()
                        onAuthSuccess()
                    }
                }
            }
        } catch (e: Exception) {
            isLoading = false
            val err = e.localizedMessage ?: "Inicio de sesión cancelado."
            statusMessage = "⚠️ $err"
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Logo Neobrutalista
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(BackgroundBase, RectangleShape)
                .border(2.dp, AccentLime, RectangleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_brexa),
                contentDescription = "Brexa Logo",
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Título de la App
        Text(
            text = "BREXA",
            style = BrexaTypography.displayLarge.copy(
                color = AccentLime,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                letterSpacing = 2.sp
            )
        )

        Text(
            text = "TERMINAL FINANCIERO BIMONETARIO",
            style = BrexaTypography.labelMedium.copy(
                color = AccentPurple,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Tarjetas de propuesta de valor
        BrutalistCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = SurfaceCard,
            borderColor = BorderSolid
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "☁", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RESPALDO PRIVADO EN TU NUBE",
                        style = BrexaTypography.labelLarge.copy(color = AccentLime)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tus finanzas se sincronizan con una carpeta protegida y oculta en tu propio Google Drive. Cero costos y cero servidores de terceros.",
                    style = BrexaTypography.bodyMedium.copy(color = TextSecondary, fontSize = 12.sp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🛡", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PROTECCIÓN ANTE ROBO O DAÑO",
                        style = BrexaTypography.labelLarge.copy(color = AccentPurple)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Si cambias de celular o se te daña el teléfono, recuperarás todo tu saldo e historial contable con un solo toque.",
                    style = BrexaTypography.bodyMedium.copy(color = TextSecondary, fontSize = 12.sp)
                )
            }
        }

        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusMessage!!,
                style = BrexaTypography.labelMedium.copy(color = TextPrimary),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(color = AccentLime)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "CONECTANDO CON GOOGLE...",
                style = BrexaTypography.labelMedium.copy(color = AccentLime)
            )
        } else {
            // Botón de Inicio de Sesión Neobrutalista
            BrutalistButton(
                text = "G   INICIAR SESIÓN CON GOOGLE",
                onClick = {
                    statusMessage = null
                    val client = syncManager.getGoogleSignInClient()
                    signInLauncher.launch(client.signInIntent)
                },
                containerColor = AccentLime,
                contentColor = Color.Black,
                shadowColor = AccentLime,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Botón de Continuar como Invitado / Offline
            BrutalistButton(
                text = "CONTINUAR EN MODO LOCAL (OFFLINE) ▶",
                onClick = onContinueGuest,
                containerColor = SurfaceCardElevated,
                contentColor = TextPrimary,
                borderColor = BorderSolid,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Podrás conectar tu Google Drive en cualquier momento desde Ajustes (⚙).",
                style = BrexaTypography.labelMedium.copy(color = TextMuted, fontSize = 10.sp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Modal si detecta respaldo en Google Drive
    if (showRestorePrompt && pendingAccount != null) {
        AlertDialog(
            onDismissRequest = { /* No cerrar tocando fuera */ },
            shape = RectangleShape,
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "¡RESPALDO ENCONTRADO!",
                    style = BrexaTypography.titleLarge.copy(color = AccentLime)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Detectamos un respaldo existente en el Google Drive de ${pendingAccount?.email}.",
                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                    )
                    Text(
                        text = "¿Deseas restaurar todas tus cuentas, monedas y registros históricos ahora?",
                        style = BrexaTypography.bodyMedium.copy(color = TextSecondary)
                    )
                }
            },
            confirmButton = {
                BrutalistBadge(
                    text = "RESTAURAR AHORA",
                    backgroundColor = AccentLime,
                    contentColor = Color.Black,
                    borderColor = AccentLime,
                    modifier = Modifier.clickable {
                        showRestorePrompt = false
                        isLoading = true
                        statusMessage = "Descargando y reconstruyendo tus datos..."
                        coroutineScope.launch {
                            val downloadRes = syncManager.downloadBackup(pendingAccount!!)
                            isLoading = false
                            downloadRes.onSuccess { content ->
                                if (!content.isNullOrBlank()) {
                                    viewModel.restoreBackupFromJson(content)
                                    Toast.makeText(context, "¡Datos restaurados con éxito!", Toast.LENGTH_SHORT).show()
                                }
                                onAuthSuccess()
                            }.onFailure { err ->
                                Toast.makeText(context, "Error restaurando: ${err.message}", Toast.LENGTH_LONG).show()
                                onAuthSuccess()
                            }
                        }
                    }
                )
            },
            dismissButton = {
                BrutalistBadge(
                    text = "EMPEZAR DE CERO",
                    backgroundColor = BackgroundBase,
                    contentColor = TextSecondary,
                    borderColor = BorderSolid,
                    modifier = Modifier.clickable {
                        showRestorePrompt = false
                        onAuthSuccess()
                    }
                )
            }
        )
    }
}
