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
    val badge: String? = null
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
     * Devuelve las tasas cacheadas de inmediato para render instantáneo al abrir la pantalla.
     */
    fun loadCachedRates(context: Context?): List<MarketRateItem> {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val bcvUsd = prefs?.getFloat("rate_BCV_USD", 914.02f)?.toDouble() ?: 914.02
        val bcvEur = prefs?.getFloat("rate_BCV_EUR", 1010.56f)?.toDouble() ?: 1010.56
        val usdt = prefs?.getFloat("rate_USDT", 1076.84f)?.toDouble() ?: 1076.84
        val bcvAvg = prefs?.getFloat("rate_BCV_AVG", ((bcvUsd + usdt) / 2.0).toFloat())?.toDouble() ?: ((bcvUsd + usdt) / 2.0)
        val copTrm = prefs?.getFloat("rate_COP_TRM", 3316.70f)?.toDouble() ?: 3316.70

        val pctUsd = prefs?.getFloat("pct_BCV_USD", 0.2629f)?.toDouble() ?: 0.2629
        val pctEur = prefs?.getFloat("pct_BCV_EUR", 0.1208f)?.toDouble() ?: 0.1208
        val pctUsdt = prefs?.getFloat("pct_USDT", 0.3119f)?.toDouble() ?: 0.3119
        val pctCop = prefs?.getFloat("pct_COP_TRM", 0.4479f)?.toDouble() ?: 0.4479

        return buildList {
            add(
                MarketRateItem(
                    id = "BCV_USD",
                    title = "BCV Dólar",
                    subtitle = "Oficial Banco Central",
                    currencySymbol = "Bs.",
                    rate = bcvUsd,
                    changePercent = pctUsd,
                    formattedRate = formatRate(bcvUsd),
                    badge = "OFICIAL"
                )
            )
            add(
                MarketRateItem(
                    id = "BCV_EUR",
                    title = "BCV Euro",
                    subtitle = "Oficial Banco Central",
                    currencySymbol = "Bs.",
                    rate = bcvEur,
                    changePercent = pctEur,
                    formattedRate = formatRate(bcvEur),
                    badge = "OFICIAL"
                )
            )
            add(
                MarketRateItem(
                    id = "USDT",
                    title = "Promedio USDT",
                    subtitle = "Binance P2P / Paralelo",
                    currencySymbol = "Bs.",
                    rate = usdt,
                    changePercent = pctUsdt,
                    formattedRate = formatRate(usdt),
                    badge = "PARALELO"
                )
            )
            add(
                MarketRateItem(
                    id = "BCV_AVG",
                    title = "Promedio BCV",
                    subtitle = "Punto medio BCV y Paralelo",
                    currencySymbol = "Bs.",
                    rate = bcvAvg,
                    changePercent = (pctUsd + pctUsdt) / 2.0,
                    formattedRate = formatRate(bcvAvg),
                    badge = "PROMEDIO"
                )
            )
            add(
                MarketRateItem(
                    id = "COP_TRM",
                    title = "Banco de la Republica TRM",
                    subtitle = "COP - Pesos Colombianos",
                    currencySymbol = "$",
                    rate = copTrm,
                    changePercent = pctCop,
                    formattedRate = formatRate(copTrm),
                    badge = "TRM"
                )
            )
        }
    }

    /**
     * Consulta en vivo las APIs de DolarApi y actualiza la caché persistente.
     */
    suspend fun fetchLiveRates(context: Context? = null): Result<List<MarketRateItem>> = withContext(Dispatchers.IO) {
        try {
            var bcvUsd: Double? = null
            var usdtRate: Double? = null
            var bcvEur: Double? = null
            var copTrm: Double? = null

            // 1. Dólares VE (Oficial y Paralelo)
            try {
                val req = Request.Builder()
                    .url(venezuelaDolaresUrl)
                    .header("User-Agent", "Brexa-App/1.1")
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
                    .header("User-Agent", "Brexa-App/1.1")
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
                    .header("User-Agent", "Brexa-App/1.1")
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

            val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs?.edit()

            fun calculateAndUpdatePct(key: String, newRate: Double, defaultPct: Double): Double {
                if (prefs == null) return defaultPct
                val oldRate = prefs.getFloat("rate_$key", 0f).toDouble()
                val oldPct = prefs.getFloat("pct_$key", defaultPct.toFloat()).toDouble()
                editor?.putFloat("rate_$key", newRate.toFloat())

                return if (oldRate > 0.0 && oldRate != newRate) {
                    val pct = ((newRate - oldRate) / oldRate) * 100.0
                    editor?.putFloat("pct_$key", pct.toFloat())
                    pct
                } else {
                    oldPct
                }
            }

            val finalBcvUsd = bcvUsd ?: prefs?.getFloat("rate_BCV_USD", 914.02f)?.toDouble() ?: 914.02
            val finalBcvEur = bcvEur ?: prefs?.getFloat("rate_BCV_EUR", 1010.56f)?.toDouble() ?: 1010.56
            val finalUsdt = usdtRate ?: prefs?.getFloat("rate_USDT", 1076.84f)?.toDouble() ?: 1076.84
            val finalBcvAvg = (finalBcvUsd + finalUsdt) / 2.0
            val finalCopTrm = copTrm ?: prefs?.getFloat("rate_COP_TRM", 3316.70f)?.toDouble() ?: 3316.70

            val pctUsd = calculateAndUpdatePct("BCV_USD", finalBcvUsd, 0.2629)
            val pctEur = calculateAndUpdatePct("BCV_EUR", finalBcvEur, 0.1208)
            val pctUsdt = calculateAndUpdatePct("USDT", finalUsdt, 0.3119)
            val pctAvg = (pctUsd + pctUsdt) / 2.0
            val pctCop = calculateAndUpdatePct("COP_TRM", finalCopTrm, 0.4479)

            editor?.apply()

            val list = listOf(
                MarketRateItem(
                    id = "BCV_USD",
                    title = "BCV Dólar",
                    subtitle = "Oficial Banco Central",
                    currencySymbol = "Bs.",
                    rate = finalBcvUsd,
                    changePercent = pctUsd,
                    formattedRate = formatRate(finalBcvUsd),
                    badge = "OFICIAL"
                ),
                MarketRateItem(
                    id = "BCV_EUR",
                    title = "BCV Euro",
                    subtitle = "Oficial Banco Central",
                    currencySymbol = "Bs.",
                    rate = finalBcvEur,
                    changePercent = pctEur,
                    formattedRate = formatRate(finalBcvEur),
                    badge = "OFICIAL"
                ),
                MarketRateItem(
                    id = "USDT",
                    title = "Promedio USDT",
                    subtitle = "Binance P2P / Paralelo",
                    currencySymbol = "Bs.",
                    rate = finalUsdt,
                    changePercent = pctUsdt,
                    formattedRate = formatRate(finalUsdt),
                    badge = "PARALELO"
                ),
                MarketRateItem(
                    id = "BCV_AVG",
                    title = "Promedio BCV",
                    subtitle = "Punto medio BCV y Paralelo",
                    currencySymbol = "Bs.",
                    rate = finalBcvAvg,
                    changePercent = pctAvg,
                    formattedRate = formatRate(finalBcvAvg),
                    badge = "PROMEDIO"
                ),
                MarketRateItem(
                    id = "COP_TRM",
                    title = "Banco de la Republica TRM",
                    subtitle = "COP - Pesos Colombianos",
                    currencySymbol = "$",
                    rate = finalCopTrm,
                    changePercent = pctCop,
                    formattedRate = formatRate(finalCopTrm),
                    badge = "TRM"
                )
            )

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
