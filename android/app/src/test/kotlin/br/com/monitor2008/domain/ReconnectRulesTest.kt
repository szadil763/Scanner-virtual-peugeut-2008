package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectRulesTest {
    @Test fun spacesOutWithTheCarOff() {
        assertEquals(60_000L, ReconnectRules.afterEngineOff(0))
        assertEquals(300_000L, ReconnectRules.afterEngineOff(2))
        assertEquals(900_000L, ReconnectRules.afterEngineOff(10))
    }

    @Test fun spacesOutWhenNotFound() {
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L), (0..5).map { ReconnectRules.afterFailure(it) })
    }

    @Test fun muteAdapter() {
        // Registro real de um adaptador bom: ATZ às vezes vem vazio, os outros "OK".
        assertFalse(ReconnectRules.mute(listOf("", "ATE0|OK", "OK", "OK")))
        assertFalse(ReconnectRules.mute(listOf("ELM327 v2.1", "", "", "")))
        assertTrue(ReconnectRules.mute(listOf("", "", "", "")))
        assertTrue(ReconnectRules.mute(listOf("?", "", "NO DATA", "")))
    }
}
