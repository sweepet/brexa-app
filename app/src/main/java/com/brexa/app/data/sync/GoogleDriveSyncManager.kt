package com.brexa.app.data.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DriveBackupInfo(
    val fileId: String,
    val modifiedTime: String
)

class GoogleDriveSyncManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val driveScope = "https://www.googleapis.com/auth/drive.appdata"
    private val fileName = "brexa_backup.json"

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(driveScope))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun signOut(onComplete: () -> Unit) {
        getGoogleSignInClient().signOut().addOnCompleteListener {
            onComplete()
        }
    }

    /**
     * Obtiene el token OAuth 2.0 Bearer para llamadas a la API de Google Drive
     */
    private suspend fun getAuthToken(account: GoogleSignInAccount): String = withContext(Dispatchers.IO) {
        val googleAccount = account.account
            ?: throw IllegalStateException("La cuenta de Google seleccionada no es válida.")
        GoogleAuthUtil.getToken(context, googleAccount, "oauth2:$driveScope")
    }

    /**
     * Busca si ya existe un archivo de respaldo previo en appDataFolder
     */
    suspend fun findBackupFile(account: GoogleSignInAccount): DriveBackupInfo? = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken(account)
            val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$fileName' and trashed=false&fields=files(id,name,modifiedTime)"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val first = files.getJSONObject(0)
                    DriveBackupInfo(
                        fileId = first.getString("id"),
                        modifiedTime = first.optString("modifiedTime", "")
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sube el JSON de respaldo a Google Drive en la carpeta privada appDataFolder.
     * Si ya existe un archivo previo, lo actualiza (PATCH). Si no, lo crea (POST).
     */
    suspend fun uploadBackup(account: GoogleSignInAccount, jsonContent: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken(account)
            val existing = findBackupFile(account)

            val fileId = if (existing != null) {
                existing.fileId
            } else {
                // Paso 1: Crear el metadato del archivo en appDataFolder
                val createUrl = "https://www.googleapis.com/drive/v3/files"
                val metaJson = JSONObject().apply {
                    put("name", fileName)
                    put("parents", JSONArray().apply { put("appDataFolder") })
                }.toString()

                val metaRequest = Request.Builder()
                    .url(createUrl)
                    .header("Authorization", "Bearer $token")
                    .post(metaJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(metaRequest).execute().use { res ->
                    if (!res.isSuccessful) {
                        return@withContext Result.failure(IOException("Error creando archivo en Google Drive: ${res.code}"))
                    }
                    val body = res.body?.string() ?: ""
                    JSONObject(body).getString("id")
                }
            }

            // Paso 2: Subir o actualizar el contenido binario/JSON del archivo
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
            val contentRequest = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $token")
                .patch(jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(contentRequest).execute().use { res ->
                if (!res.isSuccessful) {
                    Result.failure(IOException("Error actualizando contenido en Drive: ${res.code}"))
                } else {
                    Result.success(fileId)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Descarga el JSON de respaldo desde la carpeta appDataFolder de Google Drive.
     */
    suspend fun downloadBackup(account: GoogleSignInAccount): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val existing = findBackupFile(account)
                ?: return@withContext Result.success(null) // No hay respaldo aún

            val token = getAuthToken(account)
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/${existing.fileId}?alt=media"

            val request = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) {
                    Result.failure(IOException("Error descargando respaldo de Drive: ${res.code}"))
                } else {
                    val content = res.body?.string()
                    Result.success(content)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
