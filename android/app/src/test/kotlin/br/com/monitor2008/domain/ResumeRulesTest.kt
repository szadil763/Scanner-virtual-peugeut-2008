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
}
