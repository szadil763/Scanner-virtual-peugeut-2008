package br.com.monitor2008.domain

import br.com.monitor2008.data.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelCalcTest {

    @Test fun blendE30() {
        val b = FuelBlend.GASOLINA_E30
        assertEquals(12.99, b.afr, 1e-9)
        assertEquals(758.2, b.densityGPerL, 1e-9)
    }

    @Test fun blendEthanol() = assertEquals(9.285, FuelBlend.ETANOL.afr, 1e-9)

    @Test fun litersPerHourFromMaf() =
        assertEquals(1.8276, FuelCalc.litersPerHourFromMaf(5.0, FuelBlend.GASOLINA_E30), 1e-3)

    @Test fun mafFromMap() =
        assertEquals(3.92, FuelCalc.mafFromMap(40.0, 800.0, 29.85, EngineConfig(1.598, 0.8)), 0.02)

    @Test fun kmPerLInstant() = assertEquals(12.0, FuelCalc.kmPerL(60.0, 5.0)!!, 1e-9)

    @Test fun kmPerLNullWhenStopped() = assertNull(FuelCalc.kmPerL(0.0, 1.0))

    @Test fun kmPerLCappedAt50() = assertEquals(50.0, FuelCalc.kmPerL(60.0, 0.06)!!, 1e-9)

    @Test fun calibrationFactor() {
        assertEquals(1.1014, FuelCalc.calibrationFactor(38.0, 34.5), 1e-3)
        assertEquals(1.5, FuelCalc.calibrationFactor(100.0, 10.0), 1e-9)
        assertEquals(0.5, FuelCalc.calibrationFactor(1.0, 10.0), 1e-9)
    }

    @Test fun estimatePrefersFuelRateThenMafThenMap() {
        val blend = FuelBlend.GASOLINA_E30
        val engine = EngineConfig()
        val base = Sample(timeMs = 0, rpm = 800.0, speedKmh = 0.0, mapKpa = 40.0, intakeC = 29.85)

        assertEquals(FlowMethod.MAP, FuelCalc.estimate(base, blend, engine)!!.method)
        assertEquals(FlowMethod.MAF, FuelCalc.estimate(base.copy(mafGs = 5.0), blend, engine)!!.method)
        val rate = FuelCalc.estimate(base.copy(mafGs = 5.0, fuelRateLph = 1.2), blend, engine)!!
        assertEquals(FlowMethod.FUEL_RATE, rate.method)
        assertEquals(1.2, rate.litersPerHour, 1e-9)
    }

    @Test fun estimateAppliesCalibration() {
        val s = Sample(timeMs = 0, mafGs = 5.0)
        val a = FuelCalc.estimate(s, FuelBlend.GASOLINA_E30, EngineConfig(), 1.0)!!.litersPerHour
        val b = FuelCalc.estimate(s, FuelBlend.GASOLINA_E30, EngineConfig(), 1.1)!!.litersPerHour
        assertEquals(a * 1.1, b, 1e-9)
    }

    @Test fun estimateNullWithoutData() = assertNull(FuelCalc.estimate(Sample(timeMs = 0), FuelBlend.GASOLINA_E30, EngineConfig()))

    @Test fun fullToFullRefuelRecalibrates() {
        val state = RefuelState(baselineValid = true, uncalLiters = 34.5, pendingRealLiters = 0.0, kmSinceFull = 400.0, factor = 1.0)
        val r = FuelCalc.applyRefuel(state, liters = 38.0, full = true)
        assertEquals(1.1014, r.state.factor, 1e-3)
        assertEquals(400.0 / 38.0, r.realKmPerL!!, 1e-9)
        assertEquals(0.0, r.state.uncalLiters, 0.0)
        assertTrue(r.state.baselineValid)
    }

    @Test fun partialRefuelsCountTowardNextFullTank() {
        val start = RefuelState(baselineValid = true, uncalLiters = 40.0, kmSinceFull = 450.0, factor = 1.0)
        val partial = FuelCalc.applyRefuel(start, liters = 10.0, full = false)
        assertNull(partial.realKmPerL)
        val full = FuelCalc.applyRefuel(partial.state, liters = 32.0, full = true)
        assertEquals(42.0 / 40.0, full.state.factor, 1e-9) // 10 + 32 litros reais contra 40 estimados
    }

    @Test fun firstFullTankOnlyStartsTheBaseline() {
        val r = FuelCalc.applyRefuel(RefuelState(), liters = 36.0, full = true)
        assertTrue(r.state.baselineValid)
        assertEquals(1.0, r.state.factor, 0.0)
        assertNull(r.realKmPerL)
    }
}
