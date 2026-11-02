package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatRulesTest {
    @Test fun itemsAreSavedAndToggled() {
        assertEquals(FloatRules.DEFAULT, FloatRules.parse(null))
        assertEquals(FloatRules.DEFAULT, FloatRules.parse("LIXO"))
        val items = FloatRules.parse("TEMPERATURA,VELOCIDADE")
        assertEquals(listOf(FloatItem.VELOCIDADE, FloatItem.TEMPERATURA), items)
        assertEquals("VELOCIDADE,TEMPERATURA", FloatRules.serialize(items))
        assertEquals(listOf(FloatItem.VELOCIDADE, FloatItem.TEMPERATURA, FloatItem.BATERIA), FloatRules.toggle(items, FloatItem.BATERIA))
        // Nunca fica vazia.
        assertEquals(listOf(FloatItem.VELOCIDADE), FloatRules.toggle(listOf(FloatItem.VELOCIDADE), FloatItem.VELOCIDADE))
    }

    @Test fun sizesStayInRange() {
        assertEquals("Média", FloatRules.size(1).label)
        assertEquals(2, FloatRules.stepSize(2, true))
        assertEquals(0, FloatRules.stepSize(0, false))
        assertEquals("Grande", FloatRules.size(9).label)
    }

    @Test fun texts() {
        val v = FloatValues(speedKmh = 62.4, kmPerL = 13.27, avgKmPerL = 11.9, rpm = 2100.0, coolantC = 91.0, batteryV = 14.12)
        assertEquals("62", FloatRules.text(FloatItem.VELOCIDADE, v))
        assertEquals("13,3", FloatRules.text(FloatItem.CONSUMO, v))
        assertEquals("—", FloatRules.text(FloatItem.CONSUMO, v.copy(speedKmh = 0.0)))
        assertEquals("—", FloatRules.text(FloatItem.BATERIA, FloatValues()))
        assertEquals("14,1", FloatRules.text(FloatItem.BATERIA, v))
    }

    @Test fun warnings() {
        val v = FloatValues(rpm = 800.0, coolantC = 107.0, batteryV = 12.6)
        assertTrue(FloatRules.warn(FloatItem.TEMPERATURA, v))
        assertTrue(FloatRules.warn(FloatItem.BATERIA, v))
        assertFalse(FloatRules.warn(FloatItem.BATERIA, v.copy(rpm = 0.0)))
        assertFalse(FloatRules.warn(FloatItem.VELOCIDADE, v))
    }
}
