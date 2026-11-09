package com.jarvisblue.feature.studio.gold

/**
 * Optional, conservative confirmation from completed, timestamp-checked higher timeframe bars.
 * These remain price-pattern heuristics, not bank order flow, probability, or trading advice.
 * Fail closed: no reference Entry/SL/TP when context is missing, contradictory, or unavailable.
 */
data class HigherTimeframeContext(val timeframe: String, val analysis: GoldAnalysis)
data class GoldConfirmation(val analysis: GoldAnalysis, val explanation: String)

object GoldMultiTimeframeGate {
    fun requiredFor(timeframe: String): List<String> = when (timeframe) {
        "15min" -> listOf("1h", "4h")
        "1h" -> listOf("4h", "1day")
        "4h" -> listOf("1day")
        "1day" -> emptyList()
        else -> throw IllegalArgumentException("Unsupported Gold timeframe")
    }

    fun evaluate(primary: GoldAnalysis, timeframe: String, contexts: List<HigherTimeframeContext>): GoldConfirmation {
        val expected = requiredFor(timeframe)
        if (primary.bias == GoldBias.WAIT) {
            return GoldConfirmation(primary, "Primary timeframe: WAIT. No higher-timeframe requests required.")
        }
        if (expected.isEmpty()) {
            return blocked(primary, "Daily is the highest available timeframe; higher-timeframe confirmation is unavailable.")
        }
        if (contexts.size != expected.size || contexts.map { it.timeframe }.toSet() != expected.toSet()) {
            return blocked(primary, "Higher-timeframe market data missing or invalid (${expected.joinToString()}); WAIT.")
        }
        val unaligned = contexts.filterNot { isAligned(it.analysis, primary.bias) }
        if (unaligned.isNotEmpty()) {
            return blocked(primary, "Higher timeframe disagreement (${unaligned.joinToString { it.timeframe }}); WAIT.")
        }
        val text = expected.joinToString(" + ") + " aligned on EMA20/50, RSI and MACD. Research only; spread/news not checked."
        return GoldConfirmation(primary.copy(reasons = primary.reasons + text), text)
    }

    private fun isAligned(a: GoldAnalysis, desired: GoldBias): Boolean = when (desired) {
        GoldBias.STRONG_BUY -> a.price > a.ema20 && a.ema20 > a.ema50 &&
            a.rsi14 >= 50.0 && a.macdHistogram > 0.0
        GoldBias.STRONG_SELL -> a.price < a.ema20 && a.ema20 < a.ema50 &&
            a.rsi14 <= 50.0 && a.macdHistogram < 0.0
        GoldBias.WAIT -> false
    }

    private fun blocked(primary: GoldAnalysis, why: String): GoldConfirmation {
        val safe = primary.copy(
            bias = GoldBias.WAIT,
            entry = null,
            closeLevel = null,
            stop = null,
            target = null,
            takeProfit1 = null,
            takeProfit2 = null,
            reasons = primary.reasons + why,
        )
        return GoldConfirmation(safe, why)
    }
}
