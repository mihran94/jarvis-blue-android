package com.jarvisblue.feature.studio.gold

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Strict, deterministic market-research setups, never automated broker orders.
 * STRONG means a 4/4 ruleset AND confirmed prior-range breakout, NOT a win probability.
 * All calculations are based on completed OHLC candles; no intrabar execution is assumed.
 */
data class GoldCandle(
    val time: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

enum class GoldBias(val label: String) {
    STRONG_BUY("STRONG BUY · WATCH"),
    STRONG_SELL("STRONG SELL · WATCH"),
    WAIT("WAIT · NO SETUP"),
}

data class GoldAnalysis(
    val bias: GoldBias,
    val price: Double,
    val asOf: String,
    val candleCount: Int,
    val ema20: Double,
    val ema50: Double,
    val rsi14: Double,
    val macdHistogram: Double,
    val atr14: Double,
    val support: Double,
    val resistance: Double,
    val entry: Double?,
    val closeLevel: Double?, // close a hypothetical position on adverse CONFIRMED candle close
    val stop: Double?,       // protective price, not a guaranteed fill
    val target: Double?,     // backwards-compatible alias for TP2
    val takeProfit1: Double?,
    val takeProfit2: Double?,
    val bullishVotes: Int,
    val bearishVotes: Int,
    val reasons: List<String>,
    val closes: List<Double>,
    val institutional: InstitutionalSnapshot,
)

object GoldSignalEngine {
    const val MIN_CANDLES = 70
    private const val BREAKOUT_LOOKBACK = 20

    /** CSV: timestamp,open,high,low,close; timestamps must be oldest -> newest. */
    fun parseCsv(input: String): List<GoldCandle> {
        val lines = input.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
        require(lines.size <= 1501) { "Too many rows; maximum 1,500 candles." }
        val values = if (lines.firstOrNull()?.startsWith("timestamp,", ignoreCase = true) == true ||
            lines.firstOrNull()?.startsWith("datetime,", ignoreCase = true) == true
        ) lines.drop(1) else lines
        require(values.isNotEmpty()) { "Paste CSV: timestamp,open,high,low,close" }
        return values.mapIndexed { index, line ->
            val cells = line.split(',').map { it.trim().trim('"') }
            require(cells.size == 5) { "Row ${index + 1}: expected 5 comma-separated values." }
            val time = cells[0]
            require(time.isNotBlank() && time.length <= 40) { "Row ${index + 1}: invalid date/time." }
            fun num(position: Int): Double = cells[position].toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: throw IllegalArgumentException("Row ${index + 1}: invalid OHLC price.")
            GoldCandle(time, num(1), num(2), num(3), num(4))
        }.also { validate(it) }
    }

    fun validate(candles: List<GoldCandle>) {
        require(candles.size <= 1500) { "Maximum 1,500 candles." }
        candles.forEachIndexed { i, c ->
            require(listOf(c.open, c.high, c.low, c.close).all { it.isFinite() && it > 0 }) {
                "Candle ${i + 1} has an invalid price."
            }
            require(c.high >= max(c.open, c.close) && c.low <= min(c.open, c.close) && c.high >= c.low) {
                "Candle ${i + 1}: high/low inconsistent with open/close."
            }
            require(i == 0 || candles[i - 1].time < c.time) {
                "Candle timestamps must be strictly ascending (oldest first) and unique."
            }
        }
    }

    fun analyze(candles: List<GoldCandle>): GoldAnalysis {
        validate(candles)
        require(candles.size >= MIN_CANDLES) { "Need at least $MIN_CANDLES completed candles." }
        val closes = candles.map { it.close }
        val ema20Series = emaSeries(closes, 20)
        val ema20 = ema20Series.last()
        val ema50 = emaSeries(closes, 50).last()
        val macdLine = emaSeries(closes, 12).zip(emaSeries(closes, 26)) { a, b -> a - b }
        val histogram = macdLine.last() - emaSeries(macdLine, 9).last()
        val rsi14 = rsi(closes, 14)
        val atr14 = atr(candles, 14)
        val latest = candles.last()
        val price = latest.close
        // The last candle is NEVER included in its own breakout threshold.
        val prior = candles.dropLast(1).takeLast(BREAKOUT_LOOKBACK)
        val support = prior.minOf { it.low }
        val resistance = prior.maxOf { it.high }
        val reasons = mutableListOf<String>()
        var bull = 0
        var bear = 0

        if (price > ema20 && ema20 > ema50 && ema20 > ema20Series[ema20Series.lastIndex - 3]) {
            bull++; reasons += "Uptrend: close > EMA20 > EMA50; EMA20 rising."
        } else if (price < ema20 && ema20 < ema50 && ema20 < ema20Series[ema20Series.lastIndex - 3]) {
            bear++; reasons += "Downtrend: close < EMA20 < EMA50; EMA20 falling."
        } else reasons += "EMA alignment/slope is mixed."

        if (rsi14 in 53.0..70.0) {
            bull++; reasons += "RSI14 is bullish without being extreme."
        } else if (rsi14 in 30.0..47.0) {
            bear++; reasons += "RSI14 is bearish without being extreme."
        } else reasons += "RSI14 is neutral or extended."

        if (histogram > 1e-7) {
            bull++; reasons += "MACD histogram positive."
        } else if (histogram < -1e-7) {
            bear++; reasons += "MACD histogram negative."
        } else reasons += "MACD histogram is neutral."

        val body = abs(latest.close - latest.open)
        if (latest.close > latest.open && latest.close > closes[closes.lastIndex - 1]) {
            bull++; reasons += "Latest completed candle closed bullish."
        } else if (latest.close < latest.open && latest.close < closes[closes.lastIndex - 1]) {
            bear++; reasons += "Latest completed candle closed bearish."
        } else reasons += "Latest candle does not confirm direction."

        val volatilityOk = atr14.isFinite() && atr14 > price * 0.00015 && atr14 < price * 0.03
        val candleBodyOk = body >= atr14 * 0.30 && body <= atr14 * 2.0
        val bullishBreakout = price > resistance
        val bearishBreakout = price < support
        val stretchedUp = (price - ema20) > 2.2 * atr14
        val stretchedDown = (ema20 - price) > 2.2 * atr14
        if (!volatilityOk) reasons += "ATR volatility filter failed: WAIT."
        if (!candleBodyOk) reasons += "Last candle too weak or too large relative to ATR: WAIT."
        if (stretchedUp || stretchedDown) reasons += "Price extended too far from EMA20: WAIT."
        if (!bullishBreakout && !bearishBreakout) reasons += "No close beyond the previous 20-candle range: WAIT."
        else reasons += if (bullishBreakout) "Confirmed CLOSE above previous 20-candle high."
                         else "Confirmed CLOSE below previous 20-candle low."

        val rawBias = when {
            !volatilityOk || !candleBodyOk -> GoldBias.WAIT
            bull == 4 && bear == 0 && bullishBreakout && !stretchedUp -> GoldBias.STRONG_BUY
            bear == 4 && bull == 0 && bearishBreakout && !stretchedDown -> GoldBias.STRONG_SELL
            else -> GoldBias.WAIT
        }
        // Entry is reference CLOSE of last completed bar, NOT a quoted executable ask/bid.
        val entry = if (rawBias != GoldBias.WAIT) price else null
        val stop = when (rawBias) {
            GoldBias.STRONG_BUY -> price - 1.5 * atr14
            GoldBias.STRONG_SELL -> price + 1.5 * atr14
            GoldBias.WAIT -> null
        }?.takeIf { it.isFinite() && it > 0.0 }
        // A candle-close invalidation is different from the protective stop.
        // It is located between stop and entry to avoid claiming it guarantees an exit.
        val closeLevel = if (entry != null && stop != null) when (rawBias) {
            GoldBias.STRONG_BUY -> (resistance - 0.10 * atr14).coerceIn(stop + 0.1 * atr14, entry - 0.1 * atr14)
            GoldBias.STRONG_SELL -> (support + 0.10 * atr14).coerceIn(entry + 0.1 * atr14, stop - 0.1 * atr14)
            GoldBias.WAIT -> null
        } else null
        val risk = if (entry != null && stop != null) abs(entry - stop) else 0.0
        val tp1 = if (entry != null && risk > 0) when (rawBias) {
            GoldBias.STRONG_BUY -> entry + 1.5 * risk
            GoldBias.STRONG_SELL -> entry - 1.5 * risk
            GoldBias.WAIT -> null
        } else null
        val tp2 = if (entry != null && risk > 0) when (rawBias) {
            GoldBias.STRONG_BUY -> entry + 2.5 * risk
            GoldBias.STRONG_SELL -> entry - 2.5 * risk
            GoldBias.WAIT -> null
        } else null
        val valid = entry != null && stop != null && closeLevel != null &&
            tp1 != null && tp2 != null && tp1 > 0.0 && tp2 > 0.0 &&
            listOf(entry, stop, closeLevel, tp1, tp2).all { it.isFinite() }
        val bias = if (valid) rawBias else GoldBias.WAIT
        if (bias == GoldBias.WAIT) {
            reasons += "Strong setup absent: NO entry / TP / exit levels."
        } else {
            reasons += "4/4 directional rules + completed-candle breakout. NOT a probability or guarantee."
            reasons += "Research levels only. Spread, slippage, news, liquidity and higher timeframe NOT assessed."
        }
        return GoldAnalysis(
            bias = bias,
            price = price, asOf = latest.time, candleCount = candles.size,
            ema20 = ema20, ema50 = ema50, rsi14 = rsi14, macdHistogram = histogram,
            atr14 = atr14, support = support, resistance = resistance,
            entry = entry.takeIf { valid }, closeLevel = closeLevel.takeIf { valid },
            stop = stop.takeIf { valid }, target = tp2.takeIf { valid },
            takeProfit1 = tp1.takeIf { valid }, takeProfit2 = tp2.takeIf { valid },
            bullishVotes = bull, bearishVotes = bear, reasons = reasons,
            closes = closes.takeLast(60),
            institutional = InstitutionalStructureEngine.analyze(candles),
        )
    }

    /** USD risk / (USD-per-troy-ounce entry-to-stop). NOT broker lot sizing. */
    fun indicativeOunces(equityUsd: Double, riskPercent: Double, entry: Double, stop: Double): Double {
        require(equityUsd.isFinite() && equityUsd > 0.0) { "Account equity must be positive." }
        require(riskPercent.isFinite() && riskPercent > 0.0 && riskPercent <= 2.0) {
            "Risk must be between 0 and 2% (exclusive of 0)."
        }
        require(entry.isFinite() && stop.isFinite() && abs(entry - stop) > 0.0) { "Invalid stop distance." }
        return (equityUsd * riskPercent / 100.0) / abs(entry - stop)
    }

    private fun emaSeries(values: List<Double>, period: Int): List<Double> {
        require(values.isNotEmpty())
        val multiplier = 2.0 / (period + 1.0)
        var last = values.first()
        return values.mapIndexed { index, value ->
            if (index > 0) last += multiplier * (value - last)
            last
        }
    }

    private fun rsi(closes: List<Double>, period: Int): Double {
        require(closes.size > period)
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            gain += max(d, 0.0)
            loss += max(-d, 0.0)
        }
        gain /= period
        loss /= period
        for (i in (period + 1)..closes.lastIndex) {
            val d = closes[i] - closes[i - 1]
            gain = (gain * (period - 1) + max(d, 0.0)) / period
            loss = (loss * (period - 1) + max(-d, 0.0)) / period
        }
        return when {
            gain == 0.0 && loss == 0.0 -> 50.0
            loss == 0.0 -> 100.0
            else -> 100.0 - 100.0 / (1.0 + gain / loss)
        }
    }

    private fun atr(candles: List<GoldCandle>, period: Int): Double {
        val ranges = candles.mapIndexed { i, c ->
            if (i == 0) c.high - c.low
            else max(c.high - c.low, max(abs(c.high - candles[i - 1].close), abs(c.low - candles[i - 1].close)))
        }
        var value = ranges.take(period).average()
        for (i in period..ranges.lastIndex) value = (value * (period - 1) + ranges[i]) / period
        return value
    }
}
