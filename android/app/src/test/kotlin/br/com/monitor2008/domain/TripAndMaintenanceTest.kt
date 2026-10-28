package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripAndMaintenanceTest {

    @Test fun tripAverages() {
        val t = TripAccumulator()
        repeat(3600) { t.add(1.0, 60.0, 5.0) } // 1 hora a 60 km/h gastando 5 L/h
        assertEquals(60.0, t.distanceKm, 1e-6)
        assertEquals(5.0, t.liters, 1e-6)
        assertEquals(12.0, t.avgKmPerL!!, 1e-6)
        assertEquals(100.0 / 12.0, t.avgLPer100Km!!, 1e-6)
        assertEquals(60.0, t.avgSpeedKmh, 1e-6)
    }

    @Test fun tripWithoutFuelDataKeepsDistanceButNoAverage() {
        val t = TripAccumulator()
        repeat(600) { t.add(1.0, 60.0, null) }
        assertEquals(10.0, t.distanceKm, 1e-6)
        assertNull(t.avgKmPerL)
    }

    @Test fun tripResets() {
        val t = TripAccumulator()
        t.add(10.0, 50.0, 4.0)
        t.reset()
        assertEquals(0.0, t.distanceKm, 0.0)
        assertNull(t.avgKmPerL)
    }

    private val plan = OilPlan(intervalKm = 10_000, intervalMonths = 12, lastChangeKm = 8_000.0, lastChangeEpochDay = 20_000L)

    @Test fun oilOk() {
        val s = Maintenance.oilStatus(plan, currentKm = 14_300.0, todayEpochDay = 20_100L)
        assertEquals(3_700.0, s.kmLeft, 1e-9)
        assertEquals(265L, s.daysLeft)
        assertEquals(MaintState.OK, s.state)
    }

    @Test fun oilSoonByKm() =
        assertEquals(MaintState.SOON, Maintenance.oilStatus(plan, 17_500.0, 20_100L).state)

    @Test fun oilSoonByDate() =
        assertEquals(MaintState.SOON, Maintenance.oilStatus(plan, 9_000.0, 20_340L).state)

    @Test fun oilDueByKm() =
        assertEquals(MaintState.DUE, Maintenance.oilStatus(plan, 18_100.0, 20_100L).state)

    @Test fun oilDueByDate() =
        assertEquals(MaintState.DUE, Maintenance.oilStatus(plan, 9_000.0, 20_366L).state)

    private val belt = MaintCatalog.items.first { it.id == "correia" }
    private val brakeFluid = MaintCatalog.items.first { it.id == "fluido_freio" }

    @Test fun itemOk() = assertEquals(MaintState.OK, Maintenance.itemStatus(belt, 0.0, 0, 10_000.0, 100).state)

    // Aviso antecipado: 10% do intervalo (6.000 km para a correia de 60.000 km).
    @Test fun itemSoonByKm() = assertEquals(MaintState.SOON, Maintenance.itemStatus(belt, 0.0, 0, 55_000.0, 100).state)

    @Test fun itemDueByDate() = assertEquals(MaintState.DUE, Maintenance.itemStatus(belt, 0.0, 0, 1_000.0, 1_830).state)

    @Test fun dateOnlyItemIgnoresKm() {
        val s = Maintenance.itemStatus(brakeFluid, 0.0, 0, 90_000.0, 100)
        assertEquals(MaintState.OK, s.state)
        assertEquals(null, s.kmLeft)
    }
}
