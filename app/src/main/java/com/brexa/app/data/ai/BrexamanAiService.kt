package com.brexa.app.data.ai

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

class BrexamanAiService(
    private val defaultApiKey: String = BuildConfig.GEMINI_API_KEY
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Modelos en orden de preferencia y disponibilidad activa en Google AI Studio
    private val candidateModels = listOf(
        "gemini-3.5-flash",
        "gemini-3.7-flash",
        "gemini-3.8-flash",
        "gemini-flash-latest"
    )

    suspend fun askBrexaman(
        userMessage: String,
        snapshot: BrexamanFinancialSnapshot,
        conversationHistory: List<BrexamanChatMessage>,
        apiKeyOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() }
            ?: defaultApiKey.takeIf { it.isNotBlank() }

        if (apiKey.isNullOrBlank()) {
            val localResponse = generateOfflineResponse(userMessage, snapshot)
            return@withContext Result.success(localResponse)
        }

        val systemPrompt = """
            Eres Brexaman, la mascota interactiva y el asistente de inteligencia artificial de la app de finanzas personales Brexa.
            
            TU PERSONALIDAD:
            - Eres carismático, súper amigable, ocurrente, divertido, empático y servicial.
            - Hablas con soltura, calidez y naturalidad (como un gran amigo o compañero inteligente en tu teléfono).
            - Tienes libertad total para conversar: responde preguntas de conocimiento general, saluda, bromea o ayuda con cualquier duda cotidiana que te haga el usuario (por ejemplo: si te preguntan "¿cuánto es 2 + 2?", responde amablemente con gracia).
            - Siempre que la charla trate sobre dinero, gastos, presupuestos o cuando el usuario te pregunte cómo van sus finanzas, consulta con orgullo los datos reales de su billetera en el siguiente resumen financiero para darle consejos hiperpersonalizados y cifras exactas.
            - Nunca seas sermoneador ni aburrido. Usa un toque de humor cuando sea oportuno y celebra sus ahorros.
            
            ${snapshot.toContextString()}
        """.trimIndent()

        // Intentar con los modelos disponibles hasta que uno responda
        for (modelName in candidateModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val rootJson = JsonObject()

                // 1. System instruction
                val systemInstruction = JsonObject()
                val sysParts = JsonArray()
                val sysPartText = JsonObject()
                sysPartText.addProperty("text", systemPrompt)
                sysParts.add(sysPartText)
                systemInstruction.add("parts", sysParts)
                rootJson.add("systemInstruction", systemInstruction)

                // 2. Historial de mensajes
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
                genConfig.addProperty("temperature", 0.75)
                genConfig.addProperty("maxOutputTokens", 600)
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
                // Si este modelo falla, prueba el siguiente de la lista
            }
        }

        // Si todos los modelos fallaron por algún corte extremo de red
        Result.success(generateOfflineResponse(userMessage, snapshot))
    }

    private fun generateOfflineResponse(userMessage: String, snapshot: BrexamanFinancialSnapshot): String {
        val lower = userMessage.lowercase()
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
                "¡Hola! Soy Brexaman. Actualmente tu patrimonio neto es de $${String.format("%.2f", snapshot.totalNetWorthUsd)} USD. Puedes consultarme tus balances, gastos del mes o pedirme consejos financieros."
            }
        }
    }
}
