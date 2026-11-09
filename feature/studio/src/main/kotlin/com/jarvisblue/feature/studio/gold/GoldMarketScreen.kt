package com.jarvisblue.feature.studio.gold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvisblue.core.ui.components.GlowCard
import com.jarvisblue.core.ui.components.NoticeBanner
import com.jarvisblue.core.ui.components.StatusPill
import com.jarvisblue.core.ui.theme.AmberWarn
import com.jarvisblue.core.ui.theme.BlueCore
import com.jarvisblue.core.ui.theme.CyanAccent
import com.jarvisblue.core.ui.theme.GreenOk
import com.jarvisblue.core.ui.theme.RedCritical
import java.util.Locale

@Composable
fun GoldMarketRoute(viewModel: GoldMarketViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analysis = state.analysis
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NoticeBanner(
            "Research only: STRONG BUY/SELL means 4/4 rules + a completed-candle breakout, " +
                "NOT a win probability. No automatic orders. Gold/CFD leverage can cause rapid losses.",
            accent = AmberWarn,
        )
        NoticeBanner(
            "Broker-independent mode: Exness is OPTIONAL and never required for this Gold screen. " +
                "Use your own Twelve Data key, or paste completed OHLC candles. No broker orders.",
            accent = BlueCore,
        )
        GlowCard(title = "GOLD INTELLIGENCE", subtitle = "Spot Gold / USD · multi-indicator confluence", accent = CyanAccent) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GoldMarketViewModel.VALID_INTERVALS.forEach { interval ->
                    FilterChip(
                        selected = state.timeframe == interval,
                        onClick = { viewModel.setTimeframe(interval) },
                        label = { Text(interval) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Require higher-timeframe agreement", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.requireHigherTimeframes,
                    onCheckedChange = viewModel::setRequireHigherTimeframes,
                    enabled = !state.busy,
                )
            }
            Text(
                "ON (recommended): 15m checks H1 + H4; H1 checks H4 + Daily; H4 checks Daily. " +
                    "Missing, stale, or disagreeing data means WAIT with no levels. " +
                    "Daily cannot be higher-timeframe confirmed. Extra API requests may use provider credits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::setApiKey,
                label = { Text("Twelve Data API key (optional)") },
                placeholder = { Text("Your own key; not saved") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::fetch, enabled = !state.busy && state.apiKey.isNotBlank()) {
                    Text(if (state.busy) "Loading…" else "Fetch XAU/USD")
                }

            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { viewModel.loadDemo(GoldBias.STRONG_BUY) }, enabled = !state.busy) { Text("Synthetic BUY") }
                OutlinedButton(onClick = { viewModel.loadDemo(GoldBias.STRONG_SELL) }, enabled = !state.busy) { Text("Synthetic SELL") }
                OutlinedButton(onClick = { viewModel.loadDemo(GoldBias.WAIT) }, enabled = !state.busy) { Text("Synthetic WAIT") }
            }
            Text(
                "Uses Twelve Data time_series (manual refresh). Availability and freshness " +
                    "depend on your API plan. Last possibly-open candle is excluded. " +
                    "Key stays in app memory, but is sent to the provider over HTTPS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlowCard(title = "Or analyse your own completed candles", subtitle = "CSV: timestamp,open,high,low,close · oldest first", accent = BlueCore) {
            OutlinedTextField(
                value = state.csv,
                onValueChange = viewModel::setCsv,
                label = { Text("Paste 70–1500 XAU/USD candles") },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text("2026-01-01 10:00:00,2701.2,2704.0,2698.0,2702.5") },
            )
            OutlinedButton(onClick = viewModel::analyzeCsv, enabled = !state.busy) { Text("Analyse CSV") }
        }
        NoticeBanner(state.confirmation, accent = BlueCore)
        state.error?.let { NoticeBanner(it, accent = RedCritical) }
        if (analysis == null) {
            Text(
                "No signal until you fetch real candles, paste valid CSV, or select labelled demo data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val signalColor = when (analysis.bias) {
                GoldBias.STRONG_BUY -> GreenOk
                GoldBias.STRONG_SELL -> RedCritical
                GoldBias.WAIT -> AmberWarn
            }
            GlowCard(title = "XAU/USD analysis", subtitle = state.source, accent = signalColor) {
                StatusPill(text = analysis.bias.label, color = signalColor)
                Text("Last candle close: \$${format(analysis.price)} · ${analysis.candleCount} candles", style = MaterialTheme.typography.titleMedium)
                Text("Last analysed candle: ${analysis.asOf} (${state.sourceTimezone})", style = MaterialTheme.typography.bodySmall)
                if (state.fetchedAtUtc.isNotBlank()) {
                    Text("Fetched (UTC): ${state.fetchedAtUtc}", style = MaterialTheme.typography.bodySmall)
                }
                GoldCloseChart(analysis.closes)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("EMA20 ${format(analysis.ema20)}")
                    Text("EMA50 ${format(analysis.ema50)}")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RSI14 ${format(analysis.rsi14)}")
                    Text("ATR14 ${format(analysis.atr14)}")
                }
                Text("MACD histogram: ${format(analysis.macdHistogram, 4)}", style = MaterialTheme.typography.bodyMedium)
                Text("Prior 20-candle support ${format(analysis.support)} · resistance ${format(analysis.resistance)}", style = MaterialTheme.typography.bodyMedium)
                Text("Bullish ${analysis.bullishVotes}/4 · Bearish ${analysis.bearishVotes}/4", style = MaterialTheme.typography.bodyMedium)
            }
            GlowCard(title = "Institutional-style price structure", subtitle = "Observed OHLC patterns — not bank order-flow", accent = BlueCore) {
                val structure = analysis.institutional
                Text("Market structure: ${structure.structure}", style = MaterialTheme.typography.titleSmall)
                Text("Confirmed swing high: ${structure.swingHigh?.let { format(it) } ?: "—"}  ·  swing low: ${structure.swingLow?.let { format(it) } ?: "—"}")
                Text("Liquidity sweep: ${structure.liquidityEvent}")
                Text("Fair value gap: ${structure.fvg}")
                structure.warnings.forEach { warning ->
                    Text(warning, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GlowCard(title = "Entry · Close · Take Profit", subtitle = "Strict rule-based study, NOT an AI guarantee", accent = CyanAccent) {
                if (analysis.entry != null && analysis.stop != null && analysis.closeLevel != null &&
                    analysis.takeProfit1 != null && analysis.takeProfit2 != null) {
                    Text("Indicative levels from LAST COMPLETED candle — not executable quotes", style = MaterialTheme.typography.bodySmall)
                    GoldLevelRow("ENTRY (reference close)", "\$${format(analysis.entry)}")
                    GoldLevelRow("CLOSE / EXIT on adverse candle close", "\$${format(analysis.closeLevel)}")
                    GoldLevelRow("PROTECTIVE STOP LOSS", "\$${format(analysis.stop)}")
                    GoldLevelRow("TAKE PROFIT 1 · 1.5R", "\$${format(analysis.takeProfit1)}")
                    GoldLevelRow("TAKE PROFIT 2 · 2.5R", "\$${format(analysis.takeProfit2)}")
                    Text("Close rule: exit if a completed candle closes beyond the close/exit level against the position. Stop is a separate risk cap; execution and fills are NOT guaranteed.", style = MaterialTheme.typography.bodySmall)
                    Text("Illustrative levels exclude bid/ask spread, slippage, fees and macro news.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = state.equityUsd, onValueChange = viewModel::setEquity, label = { Text("Account equity (USD)") }, singleLine = true)
                    OutlinedTextField(value = state.riskPercent, onValueChange = viewModel::setRisk, label = { Text("Risk % (max 2%)") }, singleLine = true)
                    val size = runCatching {
                        GoldSignalEngine.indicativeOunces(
                            state.equityUsd.toDouble(), state.riskPercent.toDouble(), analysis.entry, analysis.stop
                        )
                    }.getOrNull()
                    Text(
                        if (size == null) "Enter valid equity and a risk percentage > 0 and ≤ 2%."
                        else "Research position size: ${format(size, 3)} troy oz (NOT broker lots).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("WAIT — no entry, close, stop or take-profit levels. Do not force a setup.")
                }
                Text("Why:", style = MaterialTheme.typography.titleSmall)
                analysis.reasons.forEach { reason -> Text("• $reason", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun format(value: Double, digits: Int = 2) = String.format(Locale.US, "%.${digits}f", value)

@Composable
private fun GoldLevelRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GoldCloseChart(closes: List<Double>) {
    val chartColor = CyanAccent
    Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
        if (closes.size < 2) return@Canvas
        val low = closes.minOrNull() ?: return@Canvas
        val high = closes.maxOrNull() ?: return@Canvas
        val span = (high - low).coerceAtLeast(0.01)
        val padding = 8.dp.toPx()
        fun point(i: Int): Offset {
            val x = padding + i.toFloat() / (closes.lastIndex) * (size.width - 2 * padding)
            val y = padding + (1f - ((closes[i] - low) / span).toFloat()) * (size.height - 2 * padding)
            return Offset(x, y)
        }
        for (i in 0 until closes.lastIndex) {
            drawLine(color = chartColor, start = point(i), end = point(i + 1), strokeWidth = 2.dp.toPx())
        }
        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = point(closes.lastIndex))
    }
}
