package com.brexa.app.data.rates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OfficialRateResult(
    val bcvRate: Double? = null,
    val bcvDate: String? = null,
    val copRate: Double? = null,
    val copDate: String? = null,
    val errorMessage: String? = null
)

class OfficialRatesManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Endpoint oficial BCV para Venezuela (DolarApi)
    private val bcvUrl = "https://ve.dolarapi.com/v1/dolares/oficial"

    // Endpoint oficial TRM para Colombia (DolarApi)
    private val copTrmUrl = "https://co.dolarapi.com/v1/trm"

    /**
     * Consulta la tasa oficial del Banco Central de Venezuela (BCV)
     */
    suspend fun fetchBcvRate(): Result<Pair<Double, String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(bcvUrl)
                .header("User-Agent", "Brexa-App/1.0")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error consultando BCV (HTTP ${response.code})"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Respuesta vacía de BCV"))
                val json = JSONObject(body)
                val rate = json.optDouble("promedio", 0.0)
                val date = json.optString("fechaActualizacion", "")
                if (rate > 0.0) {
                    Result.success(Pair(rate, date))
                } else {
                    Result.failure(Exception("Tasa BCV no válida en la respuesta."))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "No se pudo conectar con el servicio de BCV."))
        }
    }

    /**
     * Consulta la Tasa Representativa del Mercado (TRM) oficial de Colombia
     */
    suspend fun fetchCopTrmRate(): Result<Pair<Double, String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(copTrmUrl)
                .header("User-Agent", "Brexa-App/1.0")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error consultando TRM (HTTP ${response.code})"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Respuesta vacía de TRM"))
                val json = JSONObject(body)
                val rate = json.optDouble("valor", 0.0)
                val date = json.optString("fechaActualizacion", "")
                if (rate > 0.0) {
                    Result.success(Pair(rate, date))
                } else {
                    Result.failure(Exception("Tasa TRM no válida en la respuesta."))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "No se pudo conectar con el servicio de TRM."))
        }
    }

    /**
     * Consulta en paralelo/secuencial ambas tasas oficiales
     */
    suspend fun fetchAllOfficialRates(): OfficialRateResult = withContext(Dispatchers.IO) {
        val bcvRes = fetchBcvRate()
        val copRes = fetchCopTrmRate()

        val bcvPair = bcvRes.getOrNull()
        val copPair = copRes.getOrNull()

        var errMsg: String? = null
        if (bcvPair == null && copPair == null) {
            errMsg = bcvRes.exceptionOrNull()?.message ?: copRes.exceptionOrNull()?.message ?: "Error al consultar tasas oficiales"
        }

        OfficialRateResult(
            bcvRate = bcvPair?.first,
            bcvDate = bcvPair?.second,
            copRate = copPair?.first,
            copDate = copPair?.second,
            errorMessage = errMsg
        )
    }
}
