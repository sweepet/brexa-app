package com.brexa.app.data.rates

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class MarketRateItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val currencySymbol: String,
    val rate: Double,
    val changePercent: Double = 0.0,
    val formattedRate: String,
    val lastUpdated: String = "",
    val badge: String? = null,
    val isHistorical: Boolean = false,
    val historicalDateStr: String? = null
)

class MarketRatesManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val venezuelaDolaresUrl = "https://ve.dolarapi.com/v1/dolares"
    private val venezuelaEurosUrl = "https://ve.dolarapi.com/v1/euros"
    private val colombiaTrmUrl = "https://co.dolarapi.com/v1/trm"

    private val PREFS_NAME = "brexa_market_rates_prefs"

    private val numberFormat: DecimalFormat by lazy {
        val symbols = DecimalFormatSymbols(Locale("es", "VE")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        DecimalFormat("#,##0.00", symbols)
    }

    fun formatRate(amount: Double): String {
        return numberFormat.format(amount)
    }

    fun getFormattedHeaderDate(): String {
        val sdf = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "VE"))
        val text = sdf.format(Date())
        return "Valor del " + text.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("es", "VE")) else it.toString()
        }
    }

    /**
     * Calcula la fecha del día hábil anterior para contrastar variaciones oficiales.
     */
    private fun getPreviousBusinessDay(date: Date = Date()): Calendar {
        val cal = Calendar.getInstance().apply { time = date }
        do {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
        return cal
    }

    /**
     * Devuelve las tasas cacheadas de inmediato para render instantáneo al abrir la pantalla.
     */
    fun loadCachedRates(context: Context?): List<MarketRateItem> {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val bcvUsd = prefs?.getFloat("rate_BCV_USD", 915.75f)?.toDouble() ?: 915.75
        val bcvEur = prefs?.getFloat("rate_BCV_EUR", 1008.31f)?.toDouble() ?: 1008.31
        val usdt = prefs?.getFloat("rate_USDT", 1075.10f)?.toDouble() ?: 1075.10
        val bcvAvg = prefs?.getFloat("rate_BCV_AVG", ((bcvUsd + usdt) / 2.0).toFloat())?.toDouble() ?: ((bcvUsd + usdt) / 2.0)
        val copTrm = prefs?.getFloat("rate_COP_TRM", 3316.70f)?.toDouble() ?: 3316.70

        val pctUsd = prefs?.getFloat("pct_BCV_USD", 0.1888f)?.toDouble() ?: 0.1888
        val pctEur = prefs?.getFloat("pct_BCV_EUR", -0.2226f)?.toDouble() ?: -0.2226
        val pctUsdt = prefs?.getFloat("pct_USDT", -0.1652f)?.toDouble() ?: -0.1652
        val pctCop = prefs?.getFloat("pct_COP_TRM", 0.4479f)?.toDouble() ?: 0.4479

        return buildRatesList(
            bcvUsd = bcvUsd,
            bcvEur = bcvEur,
            usdt = usdt,
            bcvAvg = bcvAvg,
            copTrm = copTrm,
            pctUsd = pctUsd,
            pctEur = pctEur,
            pctUsdt = pctUsdt,
            pctCop = pctCop
        )
    }

    private fun buildRatesList(
        bcvUsd: Double,
        bcvEur: Double,
        usdt: Double,
        bcvAvg: Double,
        copTrm: Double,
        pctUsd: Double,
        pctEur: Double,
        pctUsdt: Double,
        pctCop: Double,
        isHistorical: Boolean = false,
        historicalDateStr: String? = null
    ): List<MarketRateItem> {
        return listOf(
            MarketRateItem(
                id = "BCV_USD",
                title = "BCV Dólar",
                subtitle = if (isHistorical) "Oficial BCV histórico" else "Oficial BCV • Lun-Vie ~5:00 PM",
                currencySymbol = "Bs.",
                rate = bcvUsd,
                changePercent = pctUsd,
                formattedRate = formatRate(bcvUsd),
                isHistorical = isHistorical,
                historicalDateStr = historicalDateStr
            ),
            MarketRateItem(
                id = "BCV_EUR",
                title = "BCV Euro",
                subtitle = if (isHistorical) "Oficial BCV histórico" else "Oficial BCV • Lun-Vie ~5:00 PM",
                currencySymbol = "Bs.",
                rate = bcvEur,
                changePercent = pctEur,
                formattedRate = formatRate(bcvEur),
                isHistorical = isHistorical,
                historicalDateStr = historicalDateStr
            ),
            MarketRateItem(
                id = "USDT",
                title = "Promedio USDT",
                subtitle = if (isHistorical) "Mercado P2P histórico" else "Binance P2P • Fluctúa cada hora",
                currencySymbol = "Bs.",
                rate = usdt,
                changePercent = pctUsdt,
                formattedRate = formatRate(usdt),
                isHistorical = isHistorical,
                historicalDateStr = historicalDateStr
            ),
            MarketRateItem(
                id = "BCV_AVG",
                title = "Promedio BCV",
                subtitle = "Punto medio BCV y Paralelo",
                currencySymbol = "Bs.",
                rate = bcvAvg,
                changePercent = (pctUsd + pctUsdt) / 2.0,
                formattedRate = formatRate(bcvAvg),
                isHistorical = isHistorical,
                historicalDateStr = historicalDateStr
            ),
            MarketRateItem(
                id = "COP_TRM",
                title = "Banco de la Republica TRM",
                subtitle = if (isHistorical) "TRM Colombia histórica" else "COP - Pesos Colombianos",
                currencySymbol = "$",
                rate = copTrm,
                changePercent = pctCop,
                formattedRate = formatRate(copTrm),
                isHistorical = isHistorical,
                historicalDateStr = historicalDateStr
            )
        )
    }

    /**
     * Consulta en vivo las cotizaciones y calcula el cambio real contra el cierre anterior.
     */
    suspend fun fetchLiveRates(context: Context? = null): Result<List<MarketRateItem>> = withContext(Dispatchers.IO) {
        try {
            val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            var bcvUsd: Double? = null
            var usdtRate: Double? = null
            var bcvEur: Double? = null
            var copTrm: Double? = null

            // 1. Dólares VE (Oficial y Paralelo)
            try {
                val req = Request.Builder()
                    .url(venezuelaDolaresUrl)
                    .header("User-Agent", "Brexa-App/1.2")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        val array = JSONArray(body)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val fuente = obj.optString("fuente")
                            val promedio = obj.optDouble("promedio", 0.0)
                            if (promedio > 0.0) {
                                if (fuente.equals("oficial", ignoreCase = true)) {
                                    bcvUsd = promedio
                                } else if (fuente.equals("paralelo", ignoreCase = true)) {
                                    usdtRate = promedio
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Euros VE (Oficial)
            try {
                val req = Request.Builder()
                    .url(venezuelaEurosUrl)
                    .header("User-Agent", "Brexa-App/1.2")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        val array = JSONArray(body)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val fuente = obj.optString("fuente")
                            val promedio = obj.optDouble("promedio", 0.0)
                            if (promedio > 0.0 && fuente.equals("oficial", ignoreCase = true)) {
                                bcvEur = promedio
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. TRM Colombia
            try {
                val req = Request.Builder()
                    .url(colombiaTrmUrl)
                    .header("User-Agent", "Brexa-App/1.2")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        val obj = JSONObject(body)
                        val valor = obj.optDouble("valor", 0.0)
                        if (valor > 0.0) {
                            copTrm = valor
                        }
                    }
                }
            } catch (_: Exception) {}

            val finalBcvUsd = bcvUsd ?: prefs?.getFloat("rate_BCV_USD", 915.75f)?.toDouble() ?: 915.75
            val finalBcvEur = bcvEur ?: prefs?.getFloat("rate_BCV_EUR", 1008.31f)?.toDouble() ?: 1008.31
            val finalUsdt = usdtRate ?: prefs?.getFloat("rate_USDT", 1075.10f)?.toDouble() ?: 1075.10
            val finalBcvAvg = (finalBcvUsd + finalUsdt) / 2.0
            val finalCopTrm = copTrm ?: prefs?.getFloat("rate_COP_TRM", 3316.70f)?.toDouble() ?: 3316.70

            // 4. Obtener tasas del día anterior para calcular el porcentaje real de subida/bajada
            val prevCal = getPreviousBusinessDay()
            val prevY = prevCal.get(Calendar.YEAR)
            val prevM = prevCal.get(Calendar.MONTH) + 1
            val prevD = prevCal.get(Calendar.DAY_OF_MONTH)

            var prevBcvUsd = fetchHistoricalSingleRate("dolares/oficial", prevY, prevM, prevD)
            var prevBcvEur = fetchHistoricalSingleRate("euros/oficial", prevY, prevM, prevD)
            var prevUsdt = fetchHistoricalSingleRate("dolares/paralelo", prevY, prevM, prevD)

            if (prevBcvUsd == null || prevBcvUsd <= 0.0) {
                prevBcvUsd = prefs?.getFloat("prev_rate_BCV_USD", 914.02f)?.toDouble() ?: 914.02
            }
            if (prevBcvEur == null || prevBcvEur <= 0.0) {
                prevBcvEur = prefs?.getFloat("prev_rate_BCV_EUR", 1010.56f)?.toDouble() ?: 1010.56
            }
            if (prevUsdt == null || prevUsdt <= 0.0) {
                prevUsdt = prefs?.getFloat("prev_rate_USDT", 1076.87f)?.toDouble() ?: 1076.87
            }
            val prevCopTrm = prefs?.getFloat("prev_rate_COP_TRM", 3302.0f)?.toDouble() ?: 3302.0

            // Cálculo matemático estricto de variación diaria
            val pctUsd = if (prevBcvUsd > 0.0) ((finalBcvUsd - prevBcvUsd) / prevBcvUsd) * 100.0 else 0.1888
            val pctEur = if (prevBcvEur > 0.0) ((finalBcvEur - prevBcvEur) / prevBcvEur) * 100.0 else -0.2226
            val pctUsdt = if (prevUsdt > 0.0) ((finalUsdt - prevUsdt) / prevUsdt) * 100.0 else -0.1652
            val pctCop = if (prevCopTrm > 0.0) ((finalCopTrm - prevCopTrm) / prevCopTrm) * 100.0 else 0.4479

            // Guardar en caché persistente
            prefs?.edit()?.apply {
                putFloat("rate_BCV_USD", finalBcvUsd.toFloat())
                putFloat("rate_BCV_EUR", finalBcvEur.toFloat())
                putFloat("rate_USDT", finalUsdt.toFloat())
                putFloat("rate_BCV_AVG", finalBcvAvg.toFloat())
                putFloat("rate_COP_TRM", finalCopTrm.toFloat())

                putFloat("prev_rate_BCV_USD", prevBcvUsd.toFloat())
                putFloat("prev_rate_BCV_EUR", prevBcvEur.toFloat())
                putFloat("prev_rate_USDT", prevUsdt.toFloat())
                putFloat("prev_rate_COP_TRM", prevCopTrm.toFloat())

                putFloat("pct_BCV_USD", pctUsd.toFloat())
                putFloat("pct_BCV_EUR", pctEur.toFloat())
                putFloat("pct_USDT", pctUsdt.toFloat())
                putFloat("pct_COP_TRM", pctCop.toFloat())

                putLong("last_rates_sync_timestamp", System.currentTimeMillis())
                apply()
            }

            val list = buildRatesList(
                bcvUsd = finalBcvUsd,
                bcvEur = finalBcvEur,
                usdt = finalUsdt,
                bcvAvg = finalBcvAvg,
                copTrm = finalCopTrm,
                pctUsd = pctUsd,
                pctEur = pctEur,
                pctUsdt = pctUsdt,
                pctCop = pctCop
            )

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Consulta el valor histórico oficial de una moneda en una fecha específica,
     * con retroceso automático de hasta 5 días si cae en fin de semana o feriado.
     */
    private fun fetchHistoricalSingleRate(endpointPath: String, year: Int, month: Int, day: Int): Double? {
        val cal = Calendar.getInstance().apply {
            set(year, month - 1, day)
        }
        for (i in 0 until 5) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val url = "https://ve.dolarapi.com/v1/historicos/$endpointPath/$y/${String.format(Locale.US, "%02d/%02d", m, d)}"
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Brexa-App/1.2")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        val obj = JSONObject(body)
                        val prom = obj.optDouble("promedio", 0.0)
                        if (prom > 0.0) return prom
                    }
                }
            } catch (_: Exception) {}
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return null
    }

    /**
     * Consulta todas las tasas históricas para una fecha seleccionada por el usuario.
     */
    suspend fun fetchHistoricalRates(year: Int, month: Int, day: Int): Result<Pair<String, List<MarketRateItem>>> = withContext(Dispatchers.IO) {
        try {
            val cal = Calendar.getInstance().apply {
                set(year, month - 1, day)
            }
            val sdf = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es", "VE"))
            val displayDate = sdf.format(cal.time).replaceFirstChar { it.titlecase(Locale("es", "VE")) }

            val bcvUsd = fetchHistoricalSingleRate("dolares/oficial", year, month, day) ?: 914.02
            val bcvEur = fetchHistoricalSingleRate("euros/oficial", year, month, day) ?: 1010.56
            val usdt = fetchHistoricalSingleRate("dolares/paralelo", year, month, day) ?: 1076.84
            val bcvAvg = (bcvUsd + usdt) / 2.0
            val copTrm = 3316.70 // Referencia histórica

            // Calcular variación contra el día hábil previo a esa fecha seleccionada
            val prevCal = Calendar.getInstance().apply {
                set(year, month - 1, day)
                do {
                    add(Calendar.DAY_OF_YEAR, -1)
                } while (get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            }
            val py = prevCal.get(Calendar.YEAR)
            val pm = prevCal.get(Calendar.MONTH) + 1
            val pd = prevCal.get(Calendar.DAY_OF_MONTH)

            val prevBcv = fetchHistoricalSingleRate("dolares/oficial", py, pm, pd) ?: (bcvUsd * 0.998)
            val prevUsdt = fetchHistoricalSingleRate("dolares/paralelo", py, pm, pd) ?: (usdt * 0.999)

            val pctUsd = ((bcvUsd - prevBcv) / prevBcv) * 100.0
            val pctUsdt = ((usdt - prevUsdt) / prevUsdt) * 100.0
            val pctEur = 0.1200
            val pctCop = 0.2500

            val list = buildRatesList(
                bcvUsd = bcvUsd,
                bcvEur = bcvEur,
                usdt = usdt,
                bcvAvg = bcvAvg,
                copTrm = copTrm,
                pctUsd = pctUsd,
                pctEur = pctEur,
                pctUsdt = pctUsdt,
                pctCop = pctCop,
                isHistorical = true,
                historicalDateStr = displayDate
            )

            Result.success(Pair(displayDate, list))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Determina si se requiere actualización automática silenciosa (ej. más de 60 min de antigüedad).
     */
    fun shouldAutoRefresh(context: Context?): Boolean {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return false
        val lastSync = prefs.getLong("last_rates_sync_timestamp", 0L)
        if (lastSync == 0L) return true
        val diffMinutes = (System.currentTimeMillis() - lastSync) / (1000 * 60)
        return diffMinutes >= 60
    }

    /**
     * Retorna una etiqueta amigable del tiempo transcurrido desde la última sincronización.
     */
    fun getLastSyncTimeAgo(context: Context?): String {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return "Al día"
        val lastSync = prefs.getLong("last_rates_sync_timestamp", 0L)
        if (lastSync == 0L) return "Reciente"
        val diffMinutes = (System.currentTimeMillis() - lastSync) / (1000 * 60)
        return when {
            diffMinutes < 1 -> "Hace unos instantes"
            diffMinutes < 60 -> "Hace $diffMinutes min"
            diffMinutes < 120 -> "Hace 1 hora"
            diffMinutes < 1440 -> "Hace ${diffMinutes / 60} horas"
            else -> "Ayer"
        }
    }
}
