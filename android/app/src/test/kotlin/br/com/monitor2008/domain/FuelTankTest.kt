package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelTankTest {
    @Test fun percentToLiters() {
        assertEquals(25.0, FuelTank.litersFromPercent(50.0, 50.0), 1e-9)
        assertEquals(50.0, FuelTank.litersFromPercent(150.0, 50.0), 1e-9)
        assertEquals(0.0, FuelTank.litersFromPercent(-5.0, 50.0), 1e-9)
    }

    @Test fun reserveAlertLevels() {
        assertNull(FuelTank.reserveAlert(null, 50.0, null))
        assertNull(FuelTank.reserveAlert(30.0, 50.0, 300.0))
        assertEquals("tank", FuelTank.reserveAlert(7.0, 50.0, 80.0)!!.id) // 14 % = reserva
        assertTrue(FuelTank.reserveAlert(7.0, 50.0, 80.0)!!.text.startsWith("COMBUSTÍVEL NA RESERVA"))
        assertTrue(FuelTank.reserveAlert(3.0, 50.0, 40.0)!!.text.startsWith("COMBUSTÍVEL NO FIM"))
        // Autonomia curta avisa mesmo com percentual maior.
        assertTrue(FuelTank.reserveAlert(20.0, 50.0, 50.0)!!.text.startsWith("COMBUSTÍVEL NA RESERVA"))
    }

    @Test fun historyRecordsOnlyWhenWorthIt() {
        val p = TankPoint(0, 30.0, "rodando")
        assertTrue(TankHistory.shouldRecord(null, 30.0, 0))
        assertFalse(TankHistory.shouldRecord(p, 29.8, 60_000))
        assertTrue(TankHistory.shouldRecord(p, 29.4, 60_000))
        assertTrue(TankHistory.shouldRecord(p, 30.0, TankHistory.MAX_GAP_MS))
    }

    @Test fun downsampleKeepsEndsAndEvents() {
        val pts = (0 until 100).map { TankPoint(it * 1000L, 50.0 - it * 0.1, if (it == 40) "abasteceu" else "rodando") }
        val out = TankHistory.downsample(pts, 10)
        assertTrue(out.size < 30)
        assertEquals(pts.first(), out.first())
        assertEquals(pts.last(), out.last())
        assertTrue(out.any { it.kind == "abasteceu" })
        assertEquals(pts.take(5), TankHistory.downsample(pts.take(5), 10))
    }

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
