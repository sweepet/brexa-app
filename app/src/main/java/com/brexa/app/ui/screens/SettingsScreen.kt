package com.brexa.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.data.sync.AppUpdateManager
import com.brexa.app.data.sync.GoogleDriveSyncManager
import com.brexa.app.data.sync.UpdateInfo
import com.brexa.app.presentation.BrexaViewModel
import com.brexa.app.ui.components.BrutalistBadge
import com.brexa.app.ui.components.BrutalistButton
import com.brexa.app.ui.components.BrutalistCard
import com.brexa.app.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: BrexaViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val syncManager = remember { GoogleDriveSyncManager(context) }
    val updateManager = remember { AppUpdateManager(context) }
    val lastBackupTimestamp by viewModel.lastBackupTimestamp.collectAsState()
    var signedInAccount by remember { mutableStateOf(syncManager.getLastSignedInAccount()) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var availableUpdateInSettings by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingInSettings by remember { mutableStateOf(false) }
    var settingsDownloadProgress by remember { mutableFloatStateOf(0f) }
    var updateResultText by remember { mutableStateOf<String?>(null) }
    var isDriveSyncing by remember { mutableStateOf(false) }
    var isDriveRestoring by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var driveSyncErrorMessage by remember { mutableStateOf<String?>(null) }

    fun formatBackupTimestamp(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return "Sin respaldo registrado"
        val calNow = Calendar.getInstance()
        val calBackup = Calendar.getInstance().apply { timeInMillis = timestamp }

        val timeFormat = SimpleDateFormat("hh:mm a", Locale("es", "ES"))
        val formattedTime = timeFormat.format(Date(timestamp)).uppercase()

        val isToday = calNow.get(Calendar.YEAR) == calBackup.get(Calendar.YEAR) &&
                calNow.get(Calendar.DAY_OF_YEAR) == calBackup.get(Calendar.DAY_OF_YEAR)

        calNow.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = calNow.get(Calendar.YEAR) == calBackup.get(Calendar.YEAR) &&
                calNow.get(Calendar.DAY_OF_YEAR) == calBackup.get(Calendar.DAY_OF_YEAR)

        return when {
            isToday -> "Hoy a las $formattedTime"
            isYesterday -> "Ayer a las $formattedTime"
            else -> {
                val fullFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("es", "ES"))
                fullFormat.format(Date(timestamp))
            }
        }
    }

    // Consulta automática a Google Drive si no hay fecha local registrada
    LaunchedEffect(signedInAccount) {
        val acc = signedInAccount ?: return@LaunchedEffect
        if (lastBackupTimestamp == null || lastBackupTimestamp == 0L) {
            val backupInfo = syncManager.findBackupFile(acc)
            if (backupInfo != null && backupInfo.modifiedTime.isNotBlank()) {
                try {
                    val time = java.time.Instant.parse(backupInfo.modifiedTime).toEpochMilli()
                    viewModel.setLastBackupTimestamp(time)
                } catch (_: Exception) {}
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            coroutineScope.launch {
                val backupInfo = syncManager.findBackupFile(account)
                if (backupInfo != null && backupInfo.modifiedTime.isNotBlank()) {
                    try {
                        val time = java.time.Instant.parse(backupInfo.modifiedTime).toEpochMilli()
                        viewModel.setLastBackupTimestamp(time)
                    } catch (_: Exception) {}
                }
            }
            Toast.makeText(context, "Sesión iniciada con Google", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            driveSyncErrorMessage = "Error al iniciar sesión: ${e.localizedMessage ?: "Cancelado"}"
            Toast.makeText(context, driveSyncErrorMessage ?: "Error", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Encabezado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONFIGURACIÓN Y NUBE",
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

        Spacer(modifier = Modifier.height(20.dp))

        // SECCIÓN 1: PROTOCOLO DE CERO PÉRDIDA DE DATOS
        Text(
            text = "RESPALDO Y SEGURIDAD DE DATOS",
            style = BrexaTypography.labelLarge.copy(color = AccentLime),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        BrutalistCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = SurfaceCard,
            borderColor = BorderSolid
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "COPIA DE SEGURIDAD LOCAL MANUAL",
                    style = BrexaTypography.labelLarge.copy(color = TextPrimary)
                )
                Text(
                    text = "Exporta tus cuentas, tasas y transacciones reales en un archivo .json / .brexa para guardarlo en tu computadora o enviártelo por WhatsApp:",
                    style = BrexaTypography.bodyMedium.copy(color = TextSecondary),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                BrutalistButton(
                    text = "EXPORTAR COPIA (.BREXA)",
                    onClick = {
                        coroutineScope.launch {
                            val json = viewModel.exportBackupJson()
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, json)
                                putExtra(Intent.EXTRA_TITLE, "Copia de Seguridad Brexa - ${System.currentTimeMillis()}")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Guardar o Enviar Copia de Brexa")
                            context.startActivity(shareIntent)
                        }
                    },
                    containerColor = AccentLime,
                    contentColor = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECCIÓN 2: GOOGLE DRIVE APPDATA (100% NATIVO Y PRIVADO)
        BrutalistCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = SurfaceCard,
            borderColor = if (signedInAccount != null) AccentLime else BorderSolid
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESPALDO GOOGLE DRIVE",
                        style = BrexaTypography.labelLarge.copy(color = TextPrimary),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BrutalistBadge(
                        text = if (signedInAccount != null) "CONECTADO" else "NO CONECTADO",
                        backgroundColor = BackgroundBase,
                        contentColor = if (signedInAccount != null) AccentLime else TextSecondary,
                        borderColor = if (signedInAccount != null) AccentLime else BorderSolid,
                        fontSize = 8.5.sp,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (signedInAccount != null) {
                    Text(
                        text = "Cuenta activa: ${signedInAccount?.email ?: ""}",
                        style = BrexaTypography.labelMedium.copy(color = AccentPurple, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tarjeta Informativa de Respaldo Automático y Última Fecha
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundBase, RectangleShape)
                            .border(1.dp, BorderSolid, RectangleShape)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ESTADO DE RESPALDO",
                                style = BrexaTypography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            BrutalistBadge(
                                text = "AUTOMÁTICO ACTIVO ✓",
                                backgroundColor = SurfaceCard,
                                contentColor = AccentLime,
                                borderColor = AccentLime,
                                fontSize = 8.sp,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (lastBackupTimestamp != null && lastBackupTimestamp!! > 0L) {
                                "Último guardado: ${formatBackupTimestamp(lastBackupTimestamp)}"
                            } else {
                                "Aún no se ha realizado el primer respaldo."
                            },
                            style = BrexaTypography.bodyMedium.copy(
                                color = if (lastBackupTimestamp != null && lastBackupTimestamp!! > 0L) AccentLime else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Se sincroniza automáticamente en tu Google Drive privado ante cualquier cambio (gastos, ingresos, transferencias, cuentas o monedas).",
                            style = BrexaTypography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp)
                        )
                    }

                    if (driveSyncErrorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = driveSyncErrorMessage!!,
                            style = BrexaTypography.labelSmall.copy(color = ExpenseRed, fontSize = 10.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Text(
                        text = "Inicia sesión con tu cuenta de Google para respaldar de forma automática y privada en tu Google Drive.",
                        style = BrexaTypography.bodyMedium.copy(color = TextSecondary),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                if (signedInAccount == null) {
                    BrutalistButton(
                        text = "CONECTAR CUENTA DE GOOGLE",
                        onClick = {
                            signInLauncher.launch(syncManager.getGoogleSignInClient().signInIntent)
                        },
                        containerColor = AccentLime,
                        contentColor = Color.Black,
                        shadowColor = AccentLime,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Subir a Drive Manual
                        Box(modifier = Modifier.weight(1f)) {
                            BrutalistButton(
                                text = if (isDriveSyncing) "SUBIENDO..." else "SUBIR A DRIVE ☁",
                                onClick = {
                                    val acc = signedInAccount ?: return@BrutalistButton
                                    isDriveSyncing = true
                                    driveSyncErrorMessage = null
                                    coroutineScope.launch {
                                        val json = viewModel.exportBackupJson()
                                        val uploadResult = syncManager.uploadBackup(acc, json)
                                        isDriveSyncing = false
                                        uploadResult.onSuccess {
                                            viewModel.setLastBackupTimestamp(System.currentTimeMillis())
                                            Toast.makeText(context, "Respaldo sincronizado en Google Drive con éxito", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            driveSyncErrorMessage = "Error: ${err.message}"
                                            Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                containerColor = SurfaceCardElevated,
                                contentColor = AccentLime,
                                borderColor = AccentLime,
                                enabled = !isDriveSyncing && !isDriveRestoring,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Restaurar desde Drive
                        Box(modifier = Modifier.weight(1f)) {
                            BrutalistButton(
                                text = if (isDriveRestoring) "RESTAURANDO..." else "RESTAURAR ⬇",
                                onClick = {
                                    showRestoreConfirmDialog = true
                                },
                                containerColor = SurfaceCardElevated,
                                contentColor = AccentPurple,
                                borderColor = AccentPurple,
                                enabled = !isDriveSyncing && !isDriveRestoring,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        BrutalistBadge(
                            text = "DESCONECTAR CUENTA ✕",
                            backgroundColor = BackgroundBase,
                            contentColor = ExpenseRed,
                            borderColor = ExpenseRed,
                            modifier = Modifier.clickable {
                                syncManager.signOut {
                                    signedInAccount = null
                                    viewModel.setLastBackupTimestamp(0L)
                                    Toast.makeText(context, "Sesión de Google cerrada", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Diálogo de Confirmación para Restaurar
        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                shape = RectangleShape,
                containerColor = SurfaceCard,
                title = {
                    Text(text = "RESTAURAR DESDE GOOGLE DRIVE", style = BrexaTypography.titleLarge.copy(color = AccentPurple))
                },
                text = {
                    Text(
                        text = "Esta acción descargará el respaldo guardado en tu Google Drive y restaurará el 100% de tus cuentas, monedas, categorías y movimientos exactamente como los tenías. ¿Deseas continuar?",
                        style = BrexaTypography.bodyMedium.copy(color = TextPrimary)
                    )
                },
                confirmButton = {
                    BrutalistBadge(
                        text = "RESTAURAR AHORA",
                        backgroundColor = AccentPurple,
                        contentColor = Color.Black,
                        borderColor = AccentPurple,
                        modifier = Modifier.clickable {
                            showRestoreConfirmDialog = false
                            val acc = signedInAccount ?: return@clickable
                            isDriveRestoring = true
                            coroutineScope.launch {
                                val downloadRes = syncManager.downloadBackup(acc)
                                isDriveRestoring = false
                                downloadRes.onSuccess { content ->
                                    if (content.isNullOrBlank()) {
                                        Toast.makeText(context, "No se encontró ningún respaldo en tu Google Drive.", Toast.LENGTH_LONG).show()
                                    } else {
                                        val restoreRes = viewModel.restoreBackupFromJson(content)
                                        restoreRes.onSuccess {
                                            viewModel.setLastBackupTimestamp(System.currentTimeMillis())
                                            Toast.makeText(context, "¡Respaldo restaurado con éxito!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Error restaurando datos: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }.onFailure { err ->
                                    Toast.makeText(context, "Error descargando de Drive: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                },
                dismissButton = {
                    BrutalistBadge(
                        text = "CANCELAR",
                        backgroundColor = BackgroundBase,
                        contentColor = TextSecondary,
                        borderColor = BorderSolid,
                        modifier = Modifier.clickable { showRestoreConfirmDialog = false }
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECCIÓN 3: ACTUALIZACIONES DESDE GITHUB RELEASES
        Text(
            text = "ACTUALIZACIONES DE LA APLICACIÓN",
            style = BrexaTypography.labelLarge.copy(color = AccentPurple),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        BrutalistCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = SurfaceCard,
            borderColor = BorderSolid
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VERSIÓN INSTALADA",
                        style = BrexaTypography.labelLarge.copy(color = TextPrimary)
                    )
                    BrutalistBadge(
                        text = updateManager.getInstalledVersionName(),
                        backgroundColor = BackgroundBase,
                        contentColor = AccentLime,
                        borderColor = AccentLime
                    )
                }

                Text(
                    text = "Consulta si hay una nueva versión disponible para disfrutar de las últimas mejoras y funciones en tu dispositivo con un solo toque:",
                    style = BrexaTypography.bodyMedium.copy(color = TextSecondary),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (availableUpdateInSettings == null) {
                    BrutalistButton(
                        text = if (isCheckingUpdates) "CONSULTANDO GITHUB..." else "BUSCAR ACTUALIZACIONES",
                        onClick = {
                            isCheckingUpdates = true
                            updateResultText = null
                            availableUpdateInSettings = null
                            coroutineScope.launch {
                                val currentVer = updateManager.getInstalledVersionName()
                                val result = updateManager.checkForUpdate()
                                isCheckingUpdates = false
                                result.onSuccess { update ->
                                    if (update != null) {
                                        availableUpdateInSettings = update
                                    } else {
                                        updateResultText = "✅ Brexa está al día ($currentVer). No hay nuevas versiones."
                                    }
                                }.onFailure { err ->
                                    updateResultText = "✅ Brexa está al día ($currentVer). No hay nuevas versiones."
                                }
                            }
                        },
                        containerColor = AccentPurple,
                        contentColor = Color.Black,
                        enabled = !isCheckingUpdates,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val update = availableUpdateInSettings!!
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "⚡ ¡NUEVA VERSIÓN DETECTADA: ${update.versionName}!",
                            style = BrexaTypography.labelLarge.copy(color = AccentLime, fontWeight = FontWeight.Bold)
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
                                    style = BrexaTypography.bodyMedium.copy(color = TextSecondary, fontSize = 11.sp)
                                )
                            }
                        }

                        if (isDownloadingInSettings) {
                            Text(
                                text = "DESCARGANDO APK: ${(settingsDownloadProgress * 100).toInt()}%",
                                style = BrexaTypography.labelLarge.copy(color = AccentLime)
                            )
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
                                        .fillMaxWidth(fraction = settingsDownloadProgress.coerceIn(0.01f, 1f))
                                        .background(AccentLime, RectangleShape)
                                )
                            }
                        } else {
                            BrutalistButton(
                                text = "DESCARGAR E INSTALAR AHORA",
                                onClick = {
                                    isDownloadingInSettings = true
                                    coroutineScope.launch {
                                        val res = updateManager.downloadAndInstall(update) { progress ->
                                            settingsDownloadProgress = progress
                                        }
                                        isDownloadingInSettings = false
                                        res.onFailure { err ->
                                            Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                containerColor = AccentLime,
                                contentColor = Color.Black,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (updateResultText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = updateResultText!!,
                        style = BrexaTypography.labelMedium.copy(
                            color = AccentLime,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECCIÓN 4: INFORMACIÓN DE LICENCIA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RectangleShape)
                .border(1.5.dp, BorderSolid, RectangleShape)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BREXA // CONTROL FINANCIERO BIMONETARIO",
                    style = BrexaTypography.labelLarge.copy(color = TextPrimary, fontSize = 11.sp)
                )
                Text(
                    text = "Desarrollado para entornos inflacionarios • Caracas, Venezuela",
                    style = BrexaTypography.labelMedium.copy(color = TextMuted, fontSize = 10.sp),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
