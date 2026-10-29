package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelQualityTest {

    private val day = 86_400_000L

    private fun trip(startMs: Long, km: Double = 40.0, timing: Double = 12.0, ltft: Double = 2.0) =
        TripHealth(startMs = startMs, endMs = startMs + 3_600_000, km = km, runningMin = 60.0, timingAvg = timing, ltftAvg = ltft)

    /** Tanques de 1 dia cada; [ratios] é o litros reais ÷ previstos de cada tanque (fechado pelo abastecimento seguinte). */
    private fun scenario(stations: List<String>, ratios: List<Double?>, timings: List<Double>): Pair<List<FuelStop>, List<TripHealth>> {
        val stops = stations.mapIndexed { i, st ->
            FuelStop(i * day, st, full = true, liters = 40.0, pricePerLiter = 6.0, ethanol = false,
                ratioOfEndedTank = if (i == 0) null else ratios[i - 1])
        } + FuelStop(stations.size * day, "fim", full = true, liters = 40.0, pricePerLiter = 6.0, ethanol = false,
            ratioOfEndedTank = ratios.last())
        val trips = stations.indices.map { trip(it * day + 1000, timing = timings[it]) }
        return stops to trips
    }

    @Test fun distanceAndNearest() {
        val a = Station("a", "Posto A", "", -23.5505, -46.6333)
        assertEquals(111.0, FuelQuality.distanceM(-23.5505, -46.6333, -23.5515, -46.6333), 1.0)
        assertEquals("a", FuelQuality.nearest(listOf(a), -23.5510, -46.6333)?.id) // ~55 m
        assertNull(FuelQuality.nearest(listOf(a), -23.5550, -46.6333)) // ~500 m
    }

    @Test fun badStationStandsOut() {
        val (stops, trips) = scenario(
            listOf("bom", "bom", "ruim", "bom", "ruim"),
            listOf(1.00, 1.01, 1.09, 0.99, 1.10),
            listOf(12.0, 12.0, 8.0, 12.0, 8.5),
        )
        val evals = FuelQuality.tanks(stops, trips, 10 * day)
        val ruim = evals.filter { it.stationId == "ruim" }
        assertTrue(ruim.all { it.score <= -3 })
        assertEquals(FuelGrade.SUSPEITA, FuelQuality.grade(ruim))
        assertEquals(FuelGrade.NORMAL, FuelQuality.grade(evals.filter { it.stationId == "bom" }))
        assertTrue(ruim.first().notes.any { it.contains("mais combustível") })
    }

    @Test fun fewTanksMeansFewData() {
        val (stops, trips) = scenario(listOf("a", "b"), listOf(1.0, 1.2), listOf(12.0, 8.0))
        val evals = FuelQuality.tanks(stops, trips, 10 * day)
        assertEquals(FuelGrade.POUCOS_DADOS, FuelQuality.grade(evals.filter { it.stationId == "b" }))
    }

    @Test fun partialRefuelHasNoRatio() {
        val stops = listOf(
            FuelStop(0, "a", true, 40.0, 6.0, false),
            FuelStop(day, "b", false, 20.0, 6.0, false), // parcial: o tanque de "a" não foi medido
        )
        val evals = FuelQuality.tanks(stops, listOf(trip(1000)), 2 * day)
        assertNull(evals.single { it.stationId == "a" }.ratioDevPct)
    }

    @Test fun shortTankIsIgnored() {
        val stops = listOf(FuelStop(0, "a", true, 40.0, 6.0, false))
        assertTrue(FuelQuality.tanks(stops, listOf(trip(1000, km = 10.0)), day).isEmpty())
    }

    @Test fun ethanolIsNotComparedWithGasoline() {
        val gas = (0 until 3).map { FuelStop(it * day, "g", true, 40.0, 6.0, ethanol = false, ratioOfEndedTank = if (it == 0) null else 1.0) }
        val eth = FuelStop(3 * day, "e", true, 40.0, 4.0, ethanol = true, ratioOfEndedTank = 1.0)
        val trips = (0 until 4).map { trip(it * day + 1000, timing = if (it == 3) 5.0 else 12.0) }
        val e = FuelQuality.tanks(gas + eth, trips, 10 * day).single { it.stationId == "e" }
        assertNull(e.timingDiff) // só 1 tanque de etanol: sem "normal" para comparar
    }

    @Test fun rankingPutsGoodAndNearFirst() {
        val good = Station("g", "Bom", "", 0.0, 0.0)
        val far = Station("f", "Bom longe", "", 1.0, 1.0)
        val bad = Station("b", "Ruim", "", 0.0, 0.001)
        val evals = listOf(
            TankEval("g", 0, 50.0, -4.0, null, null, 1, listOf("rendeu 4% melhor que o normal")),
            TankEval("f", 0, 50.0, -4.0, null, null, 1, emptyList()),
            TankEval("b", 0, 50.0, 8.0, null, null, -2, emptyList()),
        )
        val r = FuelQuality.rank(listOf(bad, far, good), emptyList(), evals, 0.0 to 0.0)
        assertEquals(listOf("g", "f", "b"), r.map { it.station.id })
        assertEquals(FuelGrade.BOA, r.first().grade)
        assertTrue(r.first().summary.startsWith("Rendeu 4% melhor"))
    }
}
