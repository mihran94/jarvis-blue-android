package com.jarvisblue.feature.studio.gold

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class GoldSignalEngineTest {
    private fun candles(count: Int = 110, move: Double = 0.25): List<GoldCandle> = (0 until count).map { i ->
        val open = 2500.0 + move * i + 2.5 * sin(i * 0.4)
        val close = 2500.0 + move * (i + 1) + 2.5 * sin((i + 1) * 0.4)
        GoldCandle("%04d".format(i), open, maxOf(open, close) + 1.5, minOf(open, close) - 1.5, close)
    }

    // Synthetic examples deliberately engineered to trigger the confluence algorithm.
    // These are NOT claims about backtested win rates or future performance.
    private fun demo(slope: Double, phase: Double, jump: Double): List<GoldCandle> = (0 until 130).map { i ->
        val open = 2700.0 + slope * i + sin(i * 0.42 + phase)
        val close = 2700.0 + slope * (i + 1) + sin((i + 1) * 0.42 + phase) + if (i == 129) jump else 0.0
        GoldCandle("%04d".format(i), open, maxOf(open, close) + 0.3, minOf(open, close) - 0.3, close)
    }

    @Test fun rejectsTooFewCandles() {
        assertThrows(IllegalArgumentException::class.java) { GoldSignalEngine.analyze(candles(69)) }
    }

    @Test fun finiteIndicatorsAndWaitHasNoLevels() {
        val a = GoldSignalEngine.analyze(candles())
        assertTrue(a.rsi14 in 0.0..100.0)
        assertTrue(a.ema20 > 0.0)
        assertTrue(a.ema50 > 0.0)
        assertTrue(a.atr14 > 0.0)
        assertEquals(60, a.closes.size)
        if (a.bias == GoldBias.WAIT) {
            assertNull(a.entry)
            assertNull(a.closeLevel)
            assertNull(a.takeProfit1)
            assertNull(a.takeProfit2)
        }
    }

    @Test fun rejectsOutOfOrderTimestamps() {
        val input = candles().toMutableList()
        input[10] = input[10].copy(time = input[9].time)
        assertThrows(IllegalArgumentException::class.java) { GoldSignalEngine.analyze(input) }
    }

    @Test fun parsesCsvAndRejectsImpossibleHigh() {
        val csv = "timestamp,open,high,low,close\n2026-01-01,10,12,9,11\n2026-01-02,11,12,10,11.5"
        assertEquals(2, GoldSignalEngine.parseCsv(csv).size)
        assertThrows(IllegalArgumentException::class.java) { GoldSignalEngine.parseCsv("2026-01-01,10,9,8,11") }
    }

    @Test fun sizingInOuncesNotLots() {
        assertEquals(2.0, GoldSignalEngine.indicativeOunces(1000.0, 1.0, 2700.0, 2695.0), 1e-10)
        assertThrows(IllegalArgumentException::class.java) {
            GoldSignalEngine.indicativeOunces(1000.0, 50.0, 2700.0, 2695.0)
        }
    }

    @Test fun flatMarketIsWait() {
        val flat = (0 until 85).map { i -> GoldCandle("%03d".format(i), 2700.0, 2700.0, 2700.0, 2700.0) }
        val a = GoldSignalEngine.analyze(flat)
        assertEquals(GoldBias.WAIT, a.bias)
        assertNull(a.entry)
        assertNull(a.stop)
    }

    @Test fun strongBuyHasOrderedEntryExitStopAndTargets() {
        val a = GoldSignalEngine.analyze(demo(.05, .0, 2.0))
        assertEquals(GoldBias.STRONG_BUY, a.bias)
        assertEquals(4, a.bullishVotes)
        assertEquals(0, a.bearishVotes)
        assertTrue(a.price > a.resistance)
        assertTrue(a.stop!! < a.closeLevel!!)
        assertTrue(a.closeLevel!! < a.entry!!)
        assertTrue(a.entry!! < a.takeProfit1!!)
        assertTrue(a.takeProfit1!! < a.takeProfit2!!)
        assertEquals(a.takeProfit2!!, a.target!!, 1e-9)
    }

    @Test fun strongSellHasOrderedEntryExitStopAndTargets() {
        val a = GoldSignalEngine.analyze(demo(-.05, 3.0, -2.0))
        assertEquals(GoldBias.STRONG_SELL, a.bias)
        assertEquals(4, a.bearishVotes)
        assertEquals(0, a.bullishVotes)
        assertTrue(a.price < a.support)
        assertTrue(a.takeProfit2!! < a.takeProfit1!!)
        assertTrue(a.takeProfit1!! < a.entry!!)
        assertTrue(a.entry!! < a.closeLevel!!)
        assertTrue(a.closeLevel!! < a.stop!!)
    }

    @Test fun unconfirmedBreakoutIsNotStrong() {
        val a = GoldSignalEngine.analyze(demo(.05, .0, .0))
        assertEquals(GoldBias.WAIT, a.bias)
        assertNull(a.entry)
        assertNull(a.takeProfit2)
    }
}
