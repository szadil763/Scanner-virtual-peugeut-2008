package br.com.monitor2008.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRulesTest {
    private val t = 1_000_000_000L

    @Test fun playingUntilTheCarWasTurnedOff() =
        assertTrue(ResumeRules.shouldResume(aliveAtMs = t, lastPlayingAtMs = t - 20_000, lastPlayingPkg = "com.spotify.music"))

    @Test fun pausedLongBeforeTurningOff() =
        assertFalse(ResumeRules.shouldResume(aliveAtMs = t, lastPlayingAtMs = t - 10 * 60_000, lastPlayingPkg = "com.spotify.music"))

    @Test fun nothingEverPlayed() {
        assertFalse(ResumeRules.shouldResume(aliveAtMs = t, lastPlayingAtMs = 0, lastPlayingPkg = null))
        assertFalse(ResumeRules.shouldResume(aliveAtMs = t, lastPlayingAtMs = t, lastPlayingPkg = null))
    }

    @Test fun mapComesBackOnlyIfItWasTheFrontAppAndRecent() {
        val h = 3_600_000L
        assertTrue(ResumeRules.shouldResumeMap("MAPA", t - 2 * h, t))
        assertFalse(ResumeRules.shouldResumeMap("PAINEL", t - 2 * h, t))   // o Monitor era a tela da frente
        assertFalse(ResumeRules.shouldResumeMap("MUSICA", t - 2 * h, t))
        assertFalse(ResumeRules.shouldResumeMap("MAPA", t - 13 * h, t))    // rota velha demais
        assertFalse(ResumeRules.shouldResumeMap("MAPA", 0, t))             // nunca abriu
        assertFalse(ResumeRules.shouldResumeMap(null, t, t))
    }

    @Test fun arrivedWhenParkedNearTheDestination() {
        assertTrue(ResumeRules.arrived(120.0))
        assertTrue(ResumeRules.arrived(300.0))
        assertFalse(ResumeRules.arrived(4_500.0))  // parou no meio do caminho: segue a rota
        assertFalse(ResumeRules.arrived(null))     // sem posição: não dá para saber, segue a rota
    }

    @Test fun wakingAfterLongSilenceMeansTheCarWasOff() {
        assertTrue(ResumeRules.wokeFromOff(aliveAtMs = t - 10 * 60_000, nowMs = t))
        assertFalse(ResumeRules.wokeFromOff(aliveAtMs = t - 40_000, nowMs = t))   // batimento normal
        assertFalse(ResumeRules.wokeFromOff(aliveAtMs = 0, nowMs = t))            // primeira vez
    }
}
