package com.brexa.app.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileSize: Long = 0L
)

class AppUpdateManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val releasesApiUrl = "https://api.github.com/repos/sweepet/brexa-app/releases/latest"

    /**
     * Retorna la versión actualmente instalada en el dispositivo (ej. v1.0.1)
     */
    fun getInstalledVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${pInfo.versionName ?: "1.0.0"}"
        } catch (e: Exception) {
            "v1.0.0"
        }
    }

    /**
     * Consulta la API de GitHub Releases para verificar si hay una versión superior a la instalada.
     */
    suspend fun checkForUpdate(currentVersion: String = getInstalledVersionName()): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(releasesApiUrl)
                .header("User-Agent", "Brexa-Android-App")
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.success(null) // No hay releases o es privado sin auth
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.success(null)
                val json = JSONObject(bodyStr)
                val tagName = json.optString("tag_name", "").trim()
                val bodyNotes = json.optString("body", "Mejoras y correcciones generales.")

                // Comprobar si la versión de GitHub es más reciente que la instalada
                if (isNewerVersion(tagName, currentVersion)) {
                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                val downloadUrl = asset.getString("browser_download_url")
                                val size = asset.optLong("size", 0L)
                                return@withContext Result.success(
                                    UpdateInfo(
                                        versionName = tagName,
                                        releaseNotes = cleanReleaseNotes(bodyNotes, tagName),
                                        downloadUrl = downloadUrl,
                                        fileSize = size
                                    )
                                )
                            }
                        }
                    }
                }
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compara semánticamente dos versiones (ej. v1.0.1 > v1.0.0)
     */
    private fun isNewerVersion(remoteTag: String, currentTag: String): Boolean {
        if (remoteTag.isEmpty()) return false
        val remoteClean = remoteTag.removePrefix("v").removePrefix("V")
        val currentClean = currentTag.removePrefix("v").removePrefix("V")

        val remoteParts = remoteClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Limpia y formatea las notas de la versión para mostrar solo los cambios de esta versión
     * sin enlaces técnicos de git ni acumulación de versiones anteriores.
     */
    fun cleanReleaseNotes(raw: String, tagName: String): String {
        if (raw.isBlank()) return "Mejoras de estabilidad y rendimiento."

        val lines = raw.lines()
        val cleanedLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("**Full Changelog**", ignoreCase = true) ||
                trimmed.startsWith("Full Changelog:", ignoreCase = true) ||
                trimmed.startsWith("https://github.com", ignoreCase = true) ||
                trimmed.startsWith("## Novedades", ignoreCase = true) ||
                trimmed.startsWith("## What's Changed", ignoreCase = true) ||
                trimmed.startsWith("⚡ Brexa", ignoreCase = true)
            ) {
                continue
            }
            val cleanLine = trimmed
                .replace("**", "")
                .replace("`", "")

            if (cleanLine.isNotBlank()) {
                cleanedLines.add(cleanLine)
            }
        }

        return if (cleanedLines.isEmpty()) {
            "Mejoras y correcciones en $tagName."
        } else {
            cleanedLines.take(6).joinToString("\n")
        }
    }

    /**
     * Descarga el archivo APK y lanza el instalador nativo de Android.
     */
    suspend fun downloadAndInstall(
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "Brexa-Android-App")
                .get()
                .build()

            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val apkFile = File(downloadDir, "Brexa-${updateInfo.versionName}.apk")
            if (apkFile.exists()) apkFile.delete()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Error al descargar el APK: ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(IOException("Cuerpo de descarga vacío"))
                val totalLength = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalLength > 0) {
                                val progress = downloadedBytes.toFloat() / totalLength.toFloat()
                                onProgress(progress.coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Iniciar instalación nativa en el hilo principal
            withContext(Dispatchers.Main) {
                launchInstaller(apkFile)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Dispara el Intent nativo de Android para instalar el APK sobre la aplicación actual sin perder datos.
     */
    fun launchInstaller(apkFile: File) {
        // En Android 8.0+ comprobar permiso de fuentes desconocidas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val manageIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(manageIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}
