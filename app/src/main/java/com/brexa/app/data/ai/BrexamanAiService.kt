package com.brexa.app.data.ai

import android.util.Base64
import com.brexa.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class BrexamanAiService(
    private val defaultApiKey: String = BuildConfig.GEMINI_API_KEY
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Clave predeterminada embebida para que la app siempre funcione de fábrica
    private val embeddedFallbackKey: String by lazy {
        try {
            String(Base64.decode("QVEuQWI4Uk42SVdyNmpkZXlVc1FHdkROZkV6NUNIX19RM0htaVFGMXc0WEUzNHd1ZmhTYVE=", Base64.DEFAULT)).trim()
        } catch (_: Exception) {
            ""
        }
    }

    // Modelos rápidos y probados activamente en Google AI Studio
    private val candidateModels = listOf(
        "gemini-3-flash-preview",
        "gemini-flash-lite-latest",
        "gemini-3.1-flash-lite"
    )

    suspend fun askBrexaman(
        userMessage: String,
        snapshot: BrexamanFinancialSnapshot,
        conversationHistory: List<BrexamanChatMessage>,
        apiKeyOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() }
            ?: defaultApiKey.takeIf { it.isNotBlank() }
            ?: embeddedFallbackKey.takeIf { it.isNotBlank() }

        if (apiKey.isNullOrBlank()) {
            val localResponse = generateOfflineResponse(userMessage, snapshot)
            return@withContext Result.success(localResponse)
        }

        val systemPrompt = """
            Eres Brexaman, la mascota interactiva e inteligencia artificial experta en finanzas personales de la app Brexa.
            
            ${snapshot.toContextString()}
            
            TUS REGLAS Y PERSONALIDAD:
            1. Eres un asesor financiero inteligente, perspicaz, amigable y empático. Hablas con naturalidad en español latinoamericano.
            2. Tienes acceso completo a los datos reales del usuario de arriba (tasa oficial BCV: ${String.format("%.2f", snapshot.bcvRate)} Bs, patrimonio, cuentas y presupuesto).
            3. Si el usuario te pregunta por precios, compras o tasas en la calle (ejemplo: si le cobran el dólar a un monto X en una bodega o comercio):
               - Compara INMEDIATAMENTE contra la tasa oficial del BCV registrada (${String.format("%.2f", snapshot.bcvRate)} Bs).
               - Calcula matemáticamente la diferencia porcentual de sobreprecio o brecha cambiaria: ((precio_cobrado - tasa_bcv) / tasa_bcv) * 100%.
               - Explica con claridad cuántos bolívares de más está pagando por cada dólar y dale una recomendación financiera práctica y directa para proteger su dinero.
            4. Si te preguntan sobre presupuestos, cuánto le queda libre, cuentas o balances, consulta sus números reales de arriba y responde con total transparencia y precisión.
            5. Sé conciso, dinámico y amigable: no des rodeos innecesarios, ve al grano con los números destacados y una conclusión útil.
        """.trimIndent()

        for (modelName in candidateModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val rootJson = JsonObject()

                // System instruction
                val systemInstruction = JsonObject()
                val sysParts = JsonArray()
                val sysPartText = JsonObject()
                sysPartText.addProperty("text", systemPrompt)
                sysParts.add(sysPartText)
                systemInstruction.add("parts", sysParts)
                rootJson.add("systemInstruction", systemInstruction)

                // Historial de mensajes
                val contentsArray = JsonArray()
                val recentHistory = conversationHistory.takeLast(6)
                for (msg in recentHistory) {
                    val msgObj = JsonObject()
                    msgObj.addProperty("role", if (msg.sender == MessageSender.USER) "user" else "model")
                    val parts = JsonArray()
                    val partObj = JsonObject()
                    partObj.addProperty("text", msg.content)
                    parts.add(partObj)
                    msgObj.add("parts", parts)
                    contentsArray.add(msgObj)
                }

                // Mensaje actual
                val currentMsgObj = JsonObject()
                currentMsgObj.addProperty("role", "user")
                val currentParts = JsonArray()
                val currentPartObj = JsonObject()
                currentPartObj.addProperty("text", userMessage)
                currentParts.add(currentPartObj)
                currentMsgObj.add("parts", currentParts)
                contentsArray.add(currentMsgObj)
                rootJson.add("contents", contentsArray)

                // Configuración
                val genConfig = JsonObject()
                genConfig.addProperty("temperature", 0.7)
                genConfig.addProperty("maxOutputTokens", 650)
                rootJson.add("generationConfig", genConfig)

                val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val jsonResponse = gson.fromJson(bodyStr, JsonObject::class.java)
                        val candidates = jsonResponse.getAsJsonArray("candidates")
                        if (candidates != null && candidates.size() > 0) {
                            val candidate = candidates[0].asJsonObject
                            val contentObj = candidate.getAsJsonObject("content")
                            val parts = contentObj?.getAsJsonArray("parts")
                            val text = parts?.get(0)?.asJsonObject?.get("text")?.asString
                            if (!text.isNullOrBlank()) {
                                return@withContext Result.success(text.trim())
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Prueba el siguiente modelo
            }
        }

        // Si falló toda la red, análisis local inteligente
        Result.success(generateOfflineResponse(userMessage, snapshot))
    }

    private fun generateOfflineResponse(userMessage: String, snapshot: BrexamanFinancialSnapshot): String {
        val lower = userMessage.lowercase()

        // Detección matemática inteligente de consultas sobre tasas de cambio callejeras
        if ((lower.contains("dólar") || lower.contains("dolar") || lower.contains("tasa") || lower.contains("bodega") || lower.contains("cobrando")) && snapshot.bcvRate > 0) {
            val matcher = Pattern.compile("(\\d{2,6}(?:[.,]\\d+)?)").matcher(userMessage)
            val numbers = mutableListOf<Double>()
            while (matcher.find()) {
                val numStr = matcher.group(1)?.replace(",", ".") ?: ""
                numStr.toDoubleOrNull()?.let { numbers.add(it) }
            }
            // Si el usuario mencionó una tasa en bolívares (ej. 1200)
            val askedRate = numbers.firstOrNull { it > 100.0 }
            if (askedRate != null) {
                val diffBs = askedRate - snapshot.bcvRate
                val pct = ((askedRate - snapshot.bcvRate) / snapshot.bcvRate) * 100.0
                return if (diffBs > 0) {
                    """
                    ¡Sí, es bastante! Te están cobrando un sobreprecio del **${String.format("%.1f", pct)}%** sobre la tasa oficial BCV de la app (${String.format("%.2f", snapshot.bcvRate)} Bs).
                    
                    • Tasa oficial BCV: ${String.format("%.2f", snapshot.bcvRate)} Bs/USD
                    • Tasa que te cobran: ${String.format("%.2f", askedRate)} Bs/USD
                    • Diferencia en contra: **${String.format("%.2f", diffBs)} Bs** por cada dólar.
                    
                    💡 Consejo de Brexaman: Si puedes, paga en bolívares a la tasa oficial o busca un comercio sin recargo para proteger tu dinero.
                    """.trimIndent()
                } else {
                    """
                    La tasa que te ofrecen (${String.format("%.2f", askedRate)} Bs) está por debajo o igual a la tasa oficial BCV (${String.format("%.2f", snapshot.bcvRate)} Bs). Es una cotización favorable para ti.
                    """.trimIndent()
                }
            }
        }

        return when {
            lower.contains("mes") || lower.contains("cómo voy") || lower.contains("resumen") || lower.contains("balance") ->
                BrexamanFinancialAnalyzer.answerQuickPromptLocally("MONTH_STATUS", snapshot)
            lower.contains("gasto") || lower.contains("categoría") || lower.contains("en qué") ->
                BrexamanFinancialAnalyzer.answerQuickPromptLocally("TOP_EXPENSES", snapshot)
            lower.contains("deuda") || lower.contains("pendiente") || lower.contains("pago") || lower.contains("vence") ->
                BrexamanFinancialAnalyzer.answerQuickPromptLocally("PENDING_DEBTS", snapshot)
            lower.contains("consejo") || lower.contains("tip") || lower.contains("recomienda") || lower.contains("ahorro") ->
                BrexamanFinancialAnalyzer.answerQuickPromptLocally("DAILY_TIP", snapshot)
            else -> {
                "¡Hola! Soy Brexaman. La tasa oficial BCV hoy es de ${String.format("%.2f", snapshot.bcvRate)} Bs/USD y tu patrimonio total es de $${String.format("%.2f", snapshot.totalNetWorthUsd)} USD. Puedes consultarme cualquier duda sobre tus gastos, compras o tasas de cambio."
            }
        }
    }
}
