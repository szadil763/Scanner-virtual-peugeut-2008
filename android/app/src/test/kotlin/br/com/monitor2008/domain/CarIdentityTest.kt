package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarIdentityTest {
    private val c4 = Vehicles.C4_LOUNGE_20.id
    private val p2008 = Vehicles.PEUGEOT_2008.id

    @Test fun painelAloneDecidesAndTeachesTheAdapter() {
        val d = CarIdentity.decide(painel = c4, vinCar = null, vinConfirmed = false, adapterCar = null, current = p2008)
        assertEquals(c4, d.carId); assertEquals(CarSource.PAINEL, d.source)
        assertTrue(d.learnAdapter); assertFalse(d.confirmVin)
    }

    @Test fun painelConfirmsAGuessedVin() {
        // O chassi tinha um palpite pela fábrica (2008); o multimídia do C4 diz C4: vale o multimídia e o chassi é confirmado como C4.
        val d = CarIdentity.decide(painel = c4, vinCar = p2008, vinConfirmed = false, adapterCar = p2008, current = p2008)
        assertEquals(c4, d.carId); assertEquals(CarSource.PAINEL, d.source); assertTrue(d.confirmVin); assertTrue(d.learnAdapter)
    }

    @Test fun confirmedVinBeatsADisagreeingPainel() {
        val d = CarIdentity.decide(painel = c4, vinCar = p2008, vinConfirmed = true, adapterCar = null, current = c4)
        assertEquals(p2008, d.carId); assertEquals(CarSource.CONFLICT, d.source)
        assertFalse(d.confirmVin); assertFalse(d.learnAdapter)
    }

    @Test fun painelAgreeingWithConfirmedVinIsNotAConflict() {
        val d = CarIdentity.decide(painel = c4, vinCar = c4, vinConfirmed = true, adapterCar = null, current = c4)
        assertEquals(CarSource.PAINEL, d.source)
    }

    @Test fun withoutPainelTheVinWins() {
        assertEquals(CarSource.VIN_CONFIRMED, CarIdentity.decide(null, c4, true, p2008, p2008).source)
        assertEquals(c4, CarIdentity.decide(null, c4, true, p2008, p2008).carId)
        assertEquals(CarSource.VIN_GUESS, CarIdentity.decide(null, c4, false, p2008, p2008).source)
    }

    @Test fun adapterIsTheLastClueAndNothingKeepsTheCurrentCar() {
        assertEquals(CarSource.ADAPTER, CarIdentity.decide(null, null, false, c4, p2008).source)
        assertEquals(c4, CarIdentity.decide(null, null, false, c4, p2008).carId)
        val none = CarIdentity.decide(null, null, false, null, p2008)
        assertEquals(CarSource.NONE, none.source); assertEquals(p2008, none.carId)
    }
}
