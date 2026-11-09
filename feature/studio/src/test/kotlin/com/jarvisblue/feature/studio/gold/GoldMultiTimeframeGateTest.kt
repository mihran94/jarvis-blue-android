package com.jarvisblue.feature.studio.gold

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class GoldMultiTimeframeGateTest {
    private fun example(slope: Double, phase: Double, jump: Double): GoldAnalysis {
        val bars = (0 until 130).map { i ->
            val open = 2700.0 + slope * i + sin(i * .42 + phase)
            val close = 2700.0 + slope * (i+1) + sin((i+1) * .42 + phase) + if(i==129) jump else 0.0
            GoldCandle("%04d".format(i),open,maxOf(open,close)+.3,minOf(open,close)-.3,close)
        }
        return GoldSignalEngine.analyze(bars)
    }
    @Test fun requiredTimeframesCorrect() {
        assertEquals(listOf("1h","4h"),GoldMultiTimeframeGate.requiredFor("15min"))
        assertEquals(listOf("4h","1day"),GoldMultiTimeframeGate.requiredFor("1h"))
        assertEquals(listOf("1day"),GoldMultiTimeframeGate.requiredFor("4h"))
        assertEquals(emptyList<String>(),GoldMultiTimeframeGate.requiredFor("1day"))
    }
    @Test fun missingHigherTimeframeClearsAllLevels() {
        val result = GoldMultiTimeframeGate.evaluate(example(.05,0.0,2.0), "15min", emptyList()).analysis
        assertEquals(GoldBias.WAIT,result.bias)
        assertNull(result.entry); assertNull(result.closeLevel); assertNull(result.stop)
        assertNull(result.target); assertNull(result.takeProfit1); assertNull(result.takeProfit2)
    }
    @Test fun contraryTrendIsBlocked() {
        val buy=example(.05,0.0,2.0); val sell=example(-.05,3.0,-2.0)
        assertEquals(GoldBias.STRONG_BUY,buy.bias)
        val guarded=GoldMultiTimeframeGate.evaluate(buy,"15min",listOf(
            HigherTimeframeContext("1h",sell),HigherTimeframeContext("4h",sell)))
        assertEquals(GoldBias.WAIT,guarded.analysis.bias)
        assertNull(guarded.analysis.entry)
    }
    @Test fun duplicatedHigherTimeframeCannotPass() {
        val buy=example(.05,0.0,2.0)
        val result=GoldMultiTimeframeGate.evaluate(buy,"15min",listOf(
            HigherTimeframeContext("1h",buy),HigherTimeframeContext("1h",buy)))
        assertEquals(GoldBias.WAIT,result.analysis.bias)
    }
    @Test fun matchingHigherTimeframesCanPass() {
        val buy=example(.05,0.0,2.0)
        val result=GoldMultiTimeframeGate.evaluate(buy,"15min",listOf(
            HigherTimeframeContext("1h",buy),HigherTimeframeContext("4h",buy)))
        assertEquals(GoldBias.STRONG_BUY,result.analysis.bias)
        assertNotNull(result.analysis.takeProfit1)
    }
    @Test fun dailyNoContextDefaultsWait() {
        val buy=example(.05,0.0,2.0)
        assertEquals(GoldBias.WAIT, GoldMultiTimeframeGate.evaluate(buy,"1day",emptyList()).analysis.bias)
    }
    @Test fun primaryWaitCannotBecomeBuy() {
        val flat=GoldSignalEngine.analyze((0 until 80).map { i ->
            GoldCandle("%04d".format(i),2700.0,2700.0,2700.0,2700.0)})
        val result=GoldMultiTimeframeGate.evaluate(flat,"15min",emptyList()).analysis
        assertEquals(GoldBias.WAIT,result.bias)
        assertNull(result.stop)
    }
}
