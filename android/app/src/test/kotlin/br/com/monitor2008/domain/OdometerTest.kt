package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerTest {
    @Test fun readsThePanelNumber() {
        // Texto típico do quadro: hora, temperatura externa, parcial com decimal e o hodômetro.
        val lines = listOf("12:17", "24°C", "TRIP 123.4 km", "45231 km")
        assertEquals(45231, OdometerRead.pick(lines, expectedKm = null))
        assertEquals(45231, OdometerRead.pick(listOf("45.231 km"), expectedKm = null))
        assertEquals(45231, OdometerRead.pick(listOf("45 231"), expectedKm = null))
    }

    @Test fun prefersTheNumberNearTheAppKm() {
        // O app marca 44.900; na foto aparece também a autonomia "520" e outro número grande.
        val lines = listOf("520 km", "45231", "99999")
        assertEquals(45231, OdometerRead.pick(lines, expectedKm = 44_900.0))
        // Nada plausível perto do km do app.
        assertNull(OdometerRead.pick(listOf("520", "12:17"), expectedKm = 44_900.0))
    }

    @Test fun ignoresTripWithDecimal() {
        assertTrue(OdometerRead.candidates(listOf("123.4")).isEmpty())
        assertTrue(OdometerRead.candidates(listOf("1234,5")).isEmpty())
    }

    @Test fun monthlyReminder() {
        val day = 24 * 3_600_000L
        assertTrue(OdometerReminder.due(31 * day, lastCheckMs = day, odometerSet = true, moving = false))
        assertFalse(OdometerReminder.due(20 * day, lastCheckMs = day, odometerSet = true, moving = false))
        assertFalse(OdometerReminder.due(31 * day, lastCheckMs = day, odometerSet = true, moving = true))
        assertFalse(OdometerReminder.due(31 * day, lastCheckMs = day, odometerSet = false, moving = false))
        assertFalse(OdometerReminder.due(31 * day, lastCheckMs = 0, odometerSet = true, moving = false))
    }
}
