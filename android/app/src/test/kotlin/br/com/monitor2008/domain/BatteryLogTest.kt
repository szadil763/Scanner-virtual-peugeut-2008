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
        assertEquals(listOf(BatteryRegime.SEM_ACELERAR.label, BatteryRegime.ACELERANDO.label), st.map { it.label })
        val coast = st.first { it.label == BatteryRegime.SEM_ACELERAR.label }
        assertEquals(3, coast.count)
        assertEquals(12.5, coast.avgV, 1e-9); assertEquals(12.4, coast.minV, 1e-9); assertEquals(12.6, coast.maxV, 1e-9)
        assertEquals(13.8, st.first { it.label == BatteryRegime.ACELERANDO.label }.avgV, 1e-9)
    }

    @Test fun csvLineRoundTrips() {
        val a = p(1234, 12.456, 61.4, 8.0, 1500.0, null)
        val line = BatteryLog.csvLine(a)
        assertEquals("1234,12.46,1500,61,8,,SEM_ACELERAR,", line)
        val b = BatteryLog.parseCsvLine(line)!!
        assertEquals(1234L, b.timeMs); assertEquals(12.46, b.volts, 1e-9); assertEquals(8.0, b.loadPct!!, 1e-9); assertNull(b.throttlePct)
        assertNull(BatteryLog.parseCsvLine("lixo"))
        // Com a temperatura do motor, e linha antiga (sem a coluna) ainda é lida.
        val quente = BatteryPoint(5, 12.5, 800.0, 0.0, 20.0, null, 101.0)
        assertEquals(101.0, BatteryLog.parseCsvLine(BatteryLog.csvLine(quente))!!.coolantC!!, 1e-9)
        assertNull(BatteryLog.parseCsvLine("1234,12.46,1500,61,8,,SEM_ACELERAR")!!.coolantC)
    }

    @Test fun hotEngineIsSeparatedToShowTheFanEffect() {
        val pts = listOf(
            BatteryPoint(0, 13.8, 800.0, 0.0, 20.0, null, 90.0), BatteryPoint(2_000, 13.6, 800.0, 0.0, 20.0, null, 92.0),
            BatteryPoint(4_000, 12.5, 800.0, 0.0, 20.0, null, 99.0), BatteryPoint(6_000, 12.7, 800.0, 0.0, 20.0, null, 100.0),
            BatteryPoint(8_000, 13.0, 800.0, 0.0, 20.0, null, null), // sem temperatura: fica fora da separação
        )
        val h = BatteryLog.heatStats(pts)
        assertEquals(listOf(BatteryLog.COOL_LABEL, BatteryLog.HOT_LABEL), h.map { it.label })
        assertEquals(13.7, h[0].avgV, 1e-9); assertEquals(12.6, h[1].avgV, 1e-9); assertEquals(2, h[1].count)
        assertEquals(BatteryLog.stats(pts).size + 2, BatteryLog.allStats(pts).size)
    }

    @Test fun reportHasSummaryAndSampleLines() {
        val pts = (0 until 10).map { p(it * 2_000L, 13.0 + it * 0.1, 60.0, 30.0) }
        val r = BatteryLog.report("Citroën C4 Lounge", pts, maxLines = 5)
        assertTrue(r.contains("Citroën C4 Lounge"))
        assertTrue(r.contains("10 leituras"))
        assertTrue(r.contains(BatteryLog.CSV_HEADER))
        assertEquals(5, r.lines().count { it.startsWith("0,") || it.matches(Regex("^\\d+,.*CRUZEIRO,[^,]*$")) })
    }
}
