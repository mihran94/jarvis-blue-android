package com.jarvisblue.feature.studio.gold

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.sin

/** User-initiated research only; the API key is kept in RAM, never logged or persisted. */
data class GoldMarketUiState(
    val apiKey: String = "",
    val timeframe: String = "1h",
    val requireHigherTimeframes: Boolean = true,
    val confirmation: String = "Broker-free analysis ready: connect Twelve Data or paste CSV.",
    val csv: String = "",
    val equityUsd: String = "1000",
    val riskPercent: String = "1",
    val analysis: GoldAnalysis? = null,
    val source: String = "No data loaded",
    val sourceTimezone: String = "",
    val fetchedAtUtc: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GoldMarketViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(GoldMarketUiState())
    val state = _state.asStateFlow()
    private val client = OkHttpClient.Builder()
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    fun setApiKey(value: String) { _state.value = _state.value.copy(apiKey = value.trim(), error = null) }
    fun setTimeframe(value: String) {
        if (value in VALID_INTERVALS && !_state.value.busy) _state.value = _state.value.copy(
            timeframe = value, analysis = null, source = "Timeframe changed: refresh data",
            confirmation = "New timeframe selected; previous signal cleared.", error = null,
        )
    }
    fun setRequireHigherTimeframes(enabled: Boolean) {
        if (!_state.value.busy) _state.value = _state.value.copy(
            requireHigherTimeframes = enabled, analysis = null,
            confirmation = if (enabled) "Higher-timeframe guard enabled; refresh to confirm."
                           else "Single-timeframe research; NOT higher-timeframe confirmed.",
            source = "Configuration changed: refresh data", error = null,
        )
    }
    fun setCsv(value: String) { _state.value = _state.value.copy(csv = value.take(110_000), error = null) }
    fun setEquity(value: String) { _state.value = _state.value.copy(equityUsd = value.take(20)) }
    fun setRisk(value: String) { _state.value = _state.value.copy(riskPercent = value.take(8)) }

    fun analyzeCsv() {
        val csv = _state.value.csv
        try {
            val parsed = GoldSignalEngine.parseCsv(csv)
            val result = GoldSignalEngine.analyze(parsed)
            _state.value = _state.value.copy(analysis = result, source = "MANUAL CSV (not live)", sourceTimezone = "User supplied", fetchedAtUtc = "",
                confirmation = "Unverified manual candles: NOT live, higher timeframe NOT confirmed.", error = null)
        } catch (e: IllegalArgumentException) {
            _state.value = _state.value.copy(analysis = null, source = "Invalid CSV", error = e.message)
        }
    }

    fun loadDemo(mode: GoldBias) {
        // Carefully labelled SYNTHETIC data; not evidence of live accuracy or profitability.
        val (slope, phase, jump) = when (mode) {
            GoldBias.STRONG_BUY -> Triple(0.05, 0.0, 2.0)
            GoldBias.STRONG_SELL -> Triple(-0.05, 3.0, -2.0)
            GoldBias.WAIT -> Triple(0.0, 0.0, 0.0)
        }
        val start = LocalDateTime.of(2026, 1, 1, 0, 0)
        val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val demo = (0 until 130).map { i ->
            val base = if (mode == GoldBias.WAIT) 2700.0 else 2700.0 + slope * i + sin(i * 0.42 + phase)
            val next = if (mode == GoldBias.WAIT) 2700.0 else 2700.0 + slope * (i + 1) +
                sin((i + 1) * 0.42 + phase) + (if (i == 129) jump else 0.0)
            GoldCandle(start.plusHours(i.toLong()).format(timeFormat), base,
                maxOf(base, next) + (if (mode == GoldBias.WAIT) 0.0 else 0.3),
                minOf(base, next) - (if (mode == GoldBias.WAIT) 0.0 else 0.3), next)
        }
        val result = GoldSignalEngine.analyze(demo)
        _state.value = _state.value.copy(
            analysis = result,
            source = "SYNTHETIC ${mode.label} EXAMPLE — NOT LIVE MARKET DATA",
            sourceTimezone = "Fictional timestamps", fetchedAtUtc = "",
            confirmation = "SYNTHETIC DEMO ONLY. No real quotes or timeframe confirmation.", error = null,
        )
    }

    fun fetch() {
        val snapshot = _state.value
        if (snapshot.busy) return
        if (snapshot.apiKey.isBlank()) {
            _state.value = snapshot.copy(error = "Enter your Twelve Data API key first.")
            return
        }
        _state.value = snapshot.copy(busy = true, error = null, analysis = null, source = "Fetching XAU/USD…")
        viewModelScope.launch {
            try {
                // Exness login/region/API access is not a dependency for market analysis.
                // Each response is independently checked for time, timezone and completed bars.
                val fetched = withContext(Dispatchers.IO) { loadTwelveData(snapshot.apiKey, snapshot.timeframe) }
                val primary = GoldSignalEngine.analyze(fetched.candles)
                val confirmation = if (snapshot.requireHigherTimeframes && primary.bias != GoldBias.WAIT) {
                    val required = GoldMultiTimeframeGate.requiredFor(snapshot.timeframe)
                    val contexts = withContext(Dispatchers.IO) {
                        required.mapNotNull { timeframe ->
                            try {
                                val higher = loadTwelveData(snapshot.apiKey, timeframe)
                                HigherTimeframeContext(timeframe, GoldSignalEngine.analyze(higher.candles))
                            } catch (_: Exception) {
                                // Never weaken the gate when provider quota/permissions/time checks fail.
                                null
                            }
                        }
                    }
                    GoldMultiTimeframeGate.evaluate(primary, snapshot.timeframe, contexts)
                } else if (snapshot.requireHigherTimeframes) {
                    GoldMultiTimeframeGate.evaluate(primary, snapshot.timeframe, emptyList())
                } else {
                    GoldConfirmation(primary, "Single-timeframe only. Higher timeframes not checked; not confirmed.")
                }
                _state.value = _state.value.copy(
                    busy = false, analysis = confirmation.analysis,
                    confirmation = confirmation.explanation,
                    source = "Twelve Data XAU/USD (${snapshot.timeframe}; newest possibly-forming candle excluded)",
                    sourceTimezone = fetched.timezone, fetchedAtUtc = Instant.now().toString(), error = null,
                )
            } catch (e: Exception) {
                // Do not expose request/exception internals: they can contain an API key in a URL.
                _state.value = _state.value.copy(
                    busy = false, analysis = null, source = "Live fetch failed",
                    confirmation = "WAIT: provider data unavailable or cannot be verified.",
                    error = when (e) {
                        is IllegalArgumentException -> e.message ?: "Invalid response from data provider."
                        else -> "Unable to fetch data. Check internet, API plan, or symbol access."
                    },
                )
            }
        }
    }

    private data class Feed(val candles: List<GoldCandle>, val timezone: String)

    private fun loadTwelveData(key: String, timeframe: String): Feed {
        require(timeframe in VALID_INTERVALS)
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.twelvedata.com")
            .addPathSegment("time_series")
            .addQueryParameter("symbol", "XAU/USD")
            .addQueryParameter("interval", timeframe)
            // Intraday bars are requested in UTC to avoid interpreting forex timestamps
            // using meta.exchange_timezone, which is not necessarily the output timezone.
            // Twelve Data ignores timezone for 1day bars.
            .addQueryParameter("timezone", "UTC")
            .addQueryParameter("outputsize", "130")
            .addQueryParameter("apikey", key)
            .build()
        val request = Request.Builder().url(url).get().build()
        // Never log URL: it contains the user-supplied API key.
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalArgumentException("Data provider HTTP ${response.code}; check API access.")
            response.body?.string()?.takeIf { it.length <= 800_000 }
                ?: throw IllegalArgumentException("Empty or unexpectedly large market response.")
        }
        val json = JSONObject(body)
        if (json.optString("status") == "error" || !json.has("values")) {
            val code = json.optString("code", "unknown")
            throw IllegalArgumentException("Market API returned an error (code: $code). Check plan/credits/XAU/USD availability.")
        }
        val arr = json.getJSONArray("values")
        val candles = (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            GoldCandle(
                time = item.getString("datetime"),
                open = item.getString("open").toDouble(),
                high = item.getString("high").toDouble(),
                low = item.getString("low").toDouble(),
                close = item.getString("close").toDouble(),
            )
        }.sortedBy { it.time }
        // Provider may return the current incomplete candle. Always discard it.
        require(candles.size >= GoldSignalEngine.MIN_CANDLES + 1) { "Not enough candles returned; need at least ${GoldSignalEngine.MIN_CANDLES + 1}." }
        val finished = candles.dropLast(1)
        GoldSignalEngine.validate(finished)
        val exchangeTz = json.optJSONObject("meta")?.optString("exchange_timezone", "") ?: ""
        val tz = if (timeframe == "1day") exchangeTz else "UTC"
        // Fail closed: no live directional call if timestamp/timezone cannot be verified.
        val zone = try { ZoneId.of(tz) } catch (_: Exception) {
            throw IllegalArgumentException("Provider timezone missing/unknown. Cannot verify live candle freshness.")
        }
        val timestamp = try {
            LocalDateTime.parse(finished.last().time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } catch (_: Exception) {
            try { LocalDateTime.parse(finished.last().time, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
            catch (_: Exception) { throw IllegalArgumentException("Candle timestamp format not recognised; cannot verify freshness.") }
        }
        val asOf = timestamp.atZone(zone).toInstant()
        val maxAgeMinutes = when (timeframe) { "15min" -> 60L; "1h" -> 240L; "4h" -> 900L; "1day" -> 4600L; else -> 0L }
        val age = Duration.between(asOf, Instant.now()).toMinutes()
        require(age >= -15L && age <= maxAgeMinutes) {
            "Market candles are stale or future-dated (latest: ${finished.last().time} $tz). WAIT; refresh later."
        }
        return Feed(finished, tz)
    }

    companion object { val VALID_INTERVALS = listOf("15min", "1h", "4h", "1day") }
}
