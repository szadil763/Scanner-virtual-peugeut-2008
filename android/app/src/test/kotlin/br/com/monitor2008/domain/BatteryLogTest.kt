package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryLogTest {
    private fun p(t: Long, v: Double, speed: Double, load: Double?, rpm: Double = 1800.0, thr: Double? = null) =
        BatteryPoint(t, v, rpm, speed, load, thr)

    @Test fun regimeFollowsSpeedAndEngineLoad() {
        assertEquals(BatteryRegime.PARADO, BatteryLog.regime(0.0, 5.0, null))
        assertEquals(BatteryRegime.SEM_ACELERAR, BatteryLog.regime(60.0, 10.0, null))
        assertEquals(BatteryRegime.CRUZEIRO, BatteryLog.regime(80.0, 35.0, null))
        assertEquals(BatteryRegime.ACELERANDO, BatteryLog.regime(70.0, 75.0, null))
        // Sem carga, usa a borboleta; sem nenhum dos dois, só se sabe que está rodando.
        assertEquals(BatteryRegime.SEM_ACELERAR, BatteryLog.regime(60.0, null, 5.0))
        assertEquals(BatteryRegime.RODANDO, BatteryLog.regime(60.0, null, null))
    }

    @Test fun recordsAtMostOnePointEveryTwoSeconds() {
        val a = p(0, 13.8, 50.0, 30.0)
        assertTrue(BatteryLog.shouldRecord(null, a))
        assertFalse(BatteryLog.shouldRecord(a, p(1_500, 13.8, 50.0, 30.0)))
        assertTrue(BatteryLog.shouldRecord(a, p(2_000, 13.8, 50.0, 30.0)))
    }

    @Test fun statsGroupByRegimeWithMinAvgMax() {
        val pts = listOf(
            p(0, 13.7, 60.0, 70.0), p(2_000, 13.9, 60.0, 75.0),
            p(4_000, 12.5, 60.0, 8.0), p(6_000, 12.4, 55.0, 5.0), p(8_000, 12.6, 50.0, 9.0),
        )
        val st = BatteryLog.stats(pts)
        assertEquals(listOf(BatteryRegime.SEM_ACELERAR, BatteryRegime.ACELERANDO), st.map { it.regime })
        val coast = st.first { it.regime == BatteryRegime.SEM_ACELERAR }
        assertEquals(3, coast.count)
        assertEquals(12.5, coast.avgV, 1e-9); assertEquals(12.4, coast.minV, 1e-9); assertEquals(12.6, coast.maxV, 1e-9)
        assertEquals(13.8, st.first { it.regime == BatteryRegime.ACELERANDO }.avgV, 1e-9)
    }

    @Test fun csvLineRoundTrips() {
        val a = p(1234, 12.456, 61.4, 8.0, 1500.0, null)
        val line = BatteryLog.csvLine(a)
        assertEquals("1234,12.46,1500,61,8,,SEM_ACELERAR", line)
        val b = BatteryLog.parseCsvLine(line)!!
        assertEquals(1234L, b.timeMs); assertEquals(12.46, b.volts, 1e-9); assertEquals(8.0, b.loadPct!!, 1e-9); assertNull(b.throttlePct)
        assertNull(BatteryLog.parseCsvLine("lixo"))
    }

    @Test fun reportHasSummaryAndSampleLines() {
        val pts = (0 until 10).map { p(it * 2_000L, 13.0 + it * 0.1, 60.0, 30.0) }
        val r = BatteryLog.report("Citroën C4 Lounge", pts, maxLines = 5)
        assertTrue(r.contains("Citroën C4 Lounge"))
        assertTrue(r.contains("10 leituras"))
        assertTrue(r.contains(BatteryLog.CSV_HEADER))
        assertEquals(5, r.lines().count { it.startsWith("0,") || it.matches(Regex("^\\d+,.*CRUZEIRO$")) })
    }
}
