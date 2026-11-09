package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripArchiveTest {
    private fun trip(km: Double, liters: Double, price: Double = 6.0, movingLiters: Double = liters) =
        TripRecord(endMs = 0, distanceKm = km, liters = liters, seconds = km / 30.0 * 3600.0, kmWithFuel = km,
            litersMoving = movingLiters, pricePerLiter = price, calibration = 1.0, ethanolFraction = 0.3, odometerKm = 0.0)

    @Test fun singleTripAverages() {
        val t = trip(km = 24.4, liters = 4.8, movingLiters = 4.0)
        assertEquals(24.4 / 4.8, t.avgKmPerL!!, 1e-9)
        assertEquals(24.4 / 4.0, t.avgKmPerLMoving!!, 1e-9)
        assertEquals(30.0, t.avgSpeedKmh, 1e-9)
        assertEquals(28.8, t.cost, 1e-9)
    }

    @Test fun totalsAverageIsKmOverLitersNotTheMeanOfAverages() {
        // 10 km gastando 1 L (10 km/l) e 90 km gastando 15 L (6 km/l): média geral 100 / 16 = 6,25, não 8.
        val total = TripArchive.totals(listOf(trip(10.0, 1.0), trip(90.0, 15.0)))
        assertEquals(2, total.count)
        assertEquals(100.0, total.distanceKm, 1e-9)
        assertEquals(16.0, total.liters, 1e-9)
        assertEquals(96.0, total.cost, 1e-9)
        assertEquals(6.25, total.avgKmPerL!!, 1e-9)
    }

    @Test fun emptyArchiveHasNoAverage() {
        val total = TripArchive.totals(emptyList())
        assertEquals(0, total.count)
        assertNull(total.avgKmPerL)
        assertNull(total.avgKmPerLMoving)
    }

    @Test fun tripsWithoutFuelDataStayOutOfTheAverage() {
        val semConsumo = TripRecord(0, 20.0, 0.0, 600.0, 0.0, 0.0, 6.0, 1.0, 0.3, 0.0) // rodou sem estimativa de consumo
        val total = TripArchive.totals(listOf(semConsumo, trip(10.0, 1.0)))
        assertEquals(30.0, total.distanceKm, 1e-9)
        assertEquals(10.0, total.avgKmPerL!!, 1e-9)
    }

    @Test fun onlyRealTripsAreArchived() {
        assertTrue(TripArchive.shouldArchive(0.1))
        assertTrue(TripArchive.shouldArchive(24.4))
        assertFalse(TripArchive.shouldArchive(0.0))
        assertFalse(TripArchive.shouldArchive(0.05))
    }
}
