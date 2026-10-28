package br.com.monitor2008.domain

import br.com.monitor2008.data.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {

    /** Viagem simulada: [driveS] segundos a 60 km/h e depois [idleS] segundos em marcha lenta, a cada 1 s. */
    private fun trip(
        idleS: Int = 60,
        driveS: Int = 300,
        coolantStart: Double = 30.0,
        ltft: Double = 2.0,
        stft: Double = 0.0,
        idleThrottle: Double = 12.0,
        idleMap: Double = 32.0,
        restV: Double = 12.6,
        runV: Double = 14.2,
        o2Low: Double = 0.1,
        o2High: Double = 0.85,
        o2RearSwing: Double = 0.1,
    ): TripHealth {
        val r = HealthRecorder()
        var t = 0L
        // Antes da partida: tensão de repouso.
        r.add(Sample(timeMs = t, rpm = 0.0, batteryV = restV), 1.0)
        var coolant = coolantStart
        repeat(idleS + driveS) { i ->
            t += 1000
            coolant = minOf(90.0, coolant + 0.5)
            val idle = i >= driveS
            val o2 = if (i % 2 == 0) o2Low else o2High
            r.add(
                Sample(
                    timeMs = t,
                    rpm = if (idle) 800.0 + (if (i % 2 == 0) 10 else -10) else 2200.0,
                    speedKmh = if (idle) 0.0 else 60.0,
                    coolantC = coolant,
                    mapKpa = if (idle) idleMap else 50.0,
                    throttlePct = if (idle) idleThrottle else 20.0,
                    stftPct = stft,
                    ltftPct = ltft,
                    timingDeg = 12.0,
                    o2s1V = o2,
                    o2s2V = 0.6 + (if (i % 2 == 0) o2RearSwing / 2 else -o2RearSwing / 2),
                    batteryV = runV,
                    milOn = false,
                ),
                1.0,
            )
        }
        return r.finish()!!
    }

    private fun item(trips: List<TripHealth>, id: String, live: Sample? = null) =
        HealthRules.evaluate(trips, live).first { it.id == id }

    @Test fun shortTripIsNotSaved() {
        val r = HealthRecorder()
        r.add(Sample(timeMs = 0, rpm = 800.0, speedKmh = 0.0), 1.0)
        assertNull(r.finish())
    }

    @Test fun recorderSummarizesTrip() {
        val t = trip()
        assertEquals(6.0, t.runningMin, 1e-9)
        assertEquals(5.0, t.km, 1e-6) // 300 s a 60 km/h
        assertEquals(800.0, t.idleRpmAvg!!, 1e-9)
        assertEquals(10.0, t.idleRpmSd!!, 1e-9)
        assertEquals(12.6, t.batteryRestV!!, 1e-9)
        assertEquals(14.2, t.batteryRunAvgV!!, 1e-9)
        assertNotNull(t.warmupMin)
        assertEquals(0.75, t.o2s1Max!! - t.o2s1Min!!, 1e-9)
        assertTrue(t.o2s1CrossPerMin!! > 30)
    }

    @Test fun healthyCarIsAllOk() {
        val trips = List(6) { trip() }
        HealthRules.evaluate(trips, null).forEach {
            assertTrue("${it.id} = ${it.status}", it.status == HealthStatus.OK)
        }
    }

    @Test fun leanMixtureWarns() {
        assertEquals(HealthStatus.ATENCAO, item(listOf(trip(ltft = 12.0)), "mix").status)
        assertEquals(HealthStatus.ALERTA, item(listOf(trip(ltft = 22.0)), "mix").status)
    }

    @Test fun idleOnlyLeanPointsToAirLeak() {
        val i = item(listOf(trip(ltft = 2.0, stft = 12.0)), "mix")
        assertTrue(i.advice.contains("entrada falsa de ar"))
    }

    @Test fun lazyO2Sensor() =
        assertEquals(HealthStatus.ALERTA, item(listOf(trip(o2Low = 0.4, o2High = 0.6)), "o2").status)

    @Test fun weakCatalyst() =
        assertEquals(HealthStatus.ALERTA, item(listOf(trip(o2RearSwing = 0.7)), "cat").status)

    @Test fun dirtyThrottleBodyAgainstBaseline() {
        val trips = List(5) { trip(idleThrottle = 12.0) } + List(3) { trip(idleThrottle = 16.5) }
        assertEquals(HealthStatus.ALERTA, item(trips, "tbi").status)
        assertEquals(HealthStatus.APRENDENDO, item(trips.take(3), "tbi").status)
    }

    @Test fun vacuumLossAgainstBaseline() {
        val trips = List(5) { trip(idleMap = 32.0) } + List(3) { trip(idleMap = 38.0) }
        assertEquals(HealthStatus.ATENCAO, item(trips, "map").status)
    }

    @Test fun weakBattery() {
        assertEquals(HealthStatus.ATENCAO, item(listOf(trip(restV = 12.0)), "bat").status)
        assertEquals(HealthStatus.ALERTA, item(listOf(trip(runV = 12.8)), "bat").status)
    }

    @Test fun overheatingFromLiveReading() =
        assertEquals(HealthStatus.ALERTA, item(listOf(trip()), "cool", Sample(timeMs = 0, coolantC = 107.0)).status)

    @Test fun milFromLiveReading() =
        assertEquals(HealthStatus.ALERTA, item(listOf(trip()), "dtc", Sample(timeMs = 0, milOn = true, dtcCount = 1)).status)

    @Test fun noTripsMeansLearning() =
        assertEquals(HealthStatus.APRENDENDO, item(emptyList(), "mix").status)
}
