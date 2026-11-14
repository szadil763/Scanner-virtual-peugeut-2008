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

    @Test fun painelAgreeingWithTheVinConfirmsIt() {
        val d = CarIdentity.decide(painel = c4, vinCar = c4, vinConfirmed = false, adapterCar = c4, current = p2008)
        assertEquals(c4, d.carId); assertEquals(CarSource.PAINEL, d.source); assertTrue(d.confirmVin); assertTrue(d.learnAdapter)
    }

    @Test fun painelTeachesAVinThatHasNoProfile() {
        val unknown = Vehicles.customKey("9BWZZZ377VT004251")
        val d = CarIdentity.decide(painel = c4, vinCar = unknown, vinConfirmed = false, adapterCar = null, current = c4)
        assertEquals(CarSource.PAINEL, d.source); assertTrue(d.confirmVin)
    }

    @Test fun aVinOfAnotherCarMeansTheWrongAdapterAndKeepsTheCurrentCar() {
        // Multimídia do C4, mas o adaptador conectado é do 2008 (carros lado a lado): não mistura os dados.
        val d = CarIdentity.decide(painel = c4, vinCar = p2008, vinConfirmed = true, adapterCar = null, current = c4)
        assertEquals(CarSource.CONFLICT, d.source); assertEquals(c4, d.carId); assertEquals(p2008, d.otherCar)
        assertFalse(d.confirmVin); assertFalse(d.learnAdapter)
        // Mesmo com o chassi só palpitado pela fábrica: marcas diferentes não combinam.
        assertEquals(CarSource.CONFLICT, CarIdentity.decide(c4, p2008, false, null, c4).source)
    }

    @Test fun anAdapterKnownToBeOfAnotherCarIsAlsoAConflictWhenThereIsNoVin() {
        val d = CarIdentity.decide(painel = c4, vinCar = null, vinConfirmed = false, adapterCar = p2008, current = c4)
        assertEquals(CarSource.CONFLICT, d.source); assertEquals(p2008, d.otherCar); assertEquals(c4, d.carId)
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
