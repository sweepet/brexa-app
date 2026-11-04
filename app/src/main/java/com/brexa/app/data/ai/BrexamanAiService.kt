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

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val rootJson = JsonObject()

            val systemInstruction = JsonObject()
            val sysParts = JsonArray()
            val sysPartText = JsonObject()

            val systemPrompt = """
                Eres Brexaman, la mascota y asistente de inteligencia artificial amigable, tierno, pero disciplinado y protector de la app financiera Brexa.
                
                TUS REGLAS DE ORO:
                1. Basa tus respuestas en los números reales del siguiente resumen financiero del usuario.
                2. No inventes cifras. Si te preguntan algo que no aparece en los datos, dilo con amabilidad.
                3. Sé conciso, cálido y utiliza viñetas o frases directas.
                4. Usa un tono optimista, motivador y con humor ligero cuando sea oportuno.
                5. Destaca siempre los montos y divisas (USD y Bs).
                
                ${snapshot.toContextString()}
            """.trimIndent()

            sysPartText.addProperty("text", systemPrompt)
            sysParts.add(sysPartText)
            systemInstruction.add("parts", sysParts)
            rootJson.add("systemInstruction", systemInstruction)

            val contentsArray = JsonArray()

            val recentHistory = conversationHistory.takeLast(4)
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

            val currentMsgObj = JsonObject()
            currentMsgObj.addProperty("role", "user")
            val currentParts = JsonArray()
            val currentPartObj = JsonObject()
            currentPartObj.addProperty("text", userMessage)
            currentParts.add(currentPartObj)
            currentMsgObj.add("parts", currentParts)
            contentsArray.add(currentMsgObj)

            rootJson.add("contents", contentsArray)

            val genConfig = JsonObject()
            genConfig.addProperty("temperature", 0.7)
            genConfig.addProperty("maxOutputTokens", 500)
            rootJson.add("generationConfig", genConfig)

            val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.success(
                        generateOfflineResponse(userMessage, snapshot)
                    )
                }

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
                Result.success(generateOfflineResponse(userMessage, snapshot))
            }
        } catch (e: Exception) {
            Result.success(generateOfflineResponse(userMessage, snapshot))
        }
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
                """
                ¡Hola! Soy Brexaman. Estoy monitoreando tus finanzas en tiempo real.
                
                Actualmente tu patrimonio neto es de $${String.format("%.2f", snapshot.totalNetWorthUsd)} USD.
                Este mes has gastado $${String.format("%.2f", snapshot.currentMonthExpenseUsd)} USD frente a $${String.format("%.2f", snapshot.currentMonthIncomeUsd)} USD de ingresos.
                
                Usa las sugerencias rápidas de abajo o pregúntame sobre tus gastos, ahorros o deudas.
                """.trimIndent()
            }
        }
    }
}
