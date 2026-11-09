package com.jarvisblue.feature.studio.gold

/** Price-action proxies only. This is NOT bank order-flow, Level 2 or proof of institutional trades. */
data class InstitutionalSnapshot(
    val structure: String,
    val swingHigh: Double?,
    val swingLow: Double?,
    val liquidityEvent: String,
    val fvg: String,
    val warnings: List<String>,
)

object InstitutionalStructureEngine {
    fun analyze(candles: List<GoldCandle>): InstitutionalSnapshot {
        GoldSignalEngine.validate(candles)
        require(candles.size >= 25) { "Need at least 25 completed candles." }
        // Swing pivot must have 2 candles on each side. Never use the active candle.
        val highs = (2 until candles.size - 2).filter { i ->
            candles[i].high > candles[i-1].high && candles[i].high > candles[i-2].high &&
                candles[i].high >= candles[i+1].high && candles[i].high >= candles[i+2].high
        }
        val lows = (2 until candles.size - 2).filter { i ->
            candles[i].low < candles[i-1].low && candles[i].low < candles[i-2].low &&
                candles[i].low <= candles[i+1].low && candles[i].low <= candles[i+2].low
        }
        val lastSwingHigh = highs.lastOrNull()?.let { candles[it].high }
        val lastSwingLow = lows.lastOrNull()?.let { candles[it].low }
        val latest = candles.last()
        val previous = candles.dropLast(1).takeLast(20)
        val prevHigh = previous.maxOf { it.high }
        val prevLow = previous.minOf { it.low }
        // Structural confirmation relies on a completed candle close, not just a wick.
        val structure = when {
            lastSwingHigh != null && latest.close > lastSwingHigh -> "BULLISH BOS (proxy)"
            lastSwingLow != null && latest.close < lastSwingLow -> "BEARISH BOS (proxy)"
            else -> "RANGE / UNCONFIRMED"
        }
        // Sweep means taking previous liquidity and closing BACK inside the prior range.
        val sweep = when {
            latest.high > prevHigh && latest.close < prevHigh -> "BUY-SIDE LIQUIDITY SWEEP (proxy)"
            latest.low < prevLow && latest.close > prevLow -> "SELL-SIDE LIQUIDITY SWEEP (proxy)"
            else -> "NONE CONFIRMED"
        }
        // Three-candle displacement proxy; ignore gaps from stale/incomplete bars upstream.
        val a = candles[candles.lastIndex - 2]
        val c = latest
        val fvg = when {
            c.low > a.high -> "BULLISH FVG proxy (${a.high}–${c.low})"
            c.high < a.low -> "BEARISH FVG proxy (${c.high}–${a.low})"
            else -> "NONE CONFIRMED"
        }
        return InstitutionalSnapshot(structure,lastSwingHigh,lastSwingLow,sweep,fvg,
            listOf("Candle-pattern estimates, not institutional positions or real order-book liquidity.",
                "Confirm H4/H1 context separately; spread, news, bid/ask and slippage NOT assessed."))
    }
}
