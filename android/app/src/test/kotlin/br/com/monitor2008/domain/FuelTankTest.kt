package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelTankTest {
    @Test fun fullRefuelSetsTheTankToCapacityWhateverWasBefore() {
        assertEquals(50.0, FuelTank.afterRefuel(null, 50.0, 32.0, full = true)!!, 1e-9)
        assertEquals(50.0, FuelTank.afterRefuel(12.0, 50.0, 38.0, full = true)!!, 1e-9)
    }

    @Test fun partialRefuelAddsButNeverPassesCapacity() {
        assertEquals(30.0, FuelTank.afterRefuel(20.0, 50.0, 10.0, full = false)!!, 1e-9)
        assertEquals(50.0, FuelTank.afterRefuel(45.0, 50.0, 30.0, full = false)!!, 1e-9)
    }

    @Test fun partialRefuelWithoutAKnownBaseSaysNothing() =
        assertNull(FuelTank.afterRefuel(null, 50.0, 10.0, full = false))

    @Test fun drivingSubtractsAndNeverGoesBelowZero() {
        assertEquals(48.5, FuelTank.afterDriving(50.0, 1.5)!!, 1e-9)
        assertEquals(0.0, FuelTank.afterDriving(1.0, 3.0)!!, 1e-9)
        assertNull(FuelTank.afterDriving(null, 1.0))
        assertEquals(10.0, FuelTank.afterDriving(10.0, -2.0)!!, 1e-9) // consumo negativo não enche o tanque
    }

    @Test fun percentAndRange() {
        assertEquals(62, FuelTank.percent(31.0, 50.0))
        assertEquals(100, FuelTank.percent(60.0, 50.0))
        assertEquals(0, FuelTank.percent(5.0, 0.0))
        assertEquals(310.0, FuelTank.rangeKm(31.0, 10.0)!!, 1e-9)
        assertNull(FuelTank.rangeKm(31.0, null))
        assertNull(FuelTank.rangeKm(null, 10.0))
        assertNull(FuelTank.rangeKm(31.0, 0.0))
    }
}
