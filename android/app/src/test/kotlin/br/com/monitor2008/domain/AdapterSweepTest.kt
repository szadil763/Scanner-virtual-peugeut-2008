package br.com.monitor2008.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterSweepTest {
    @Test fun anAdapterWhoseCarIsOffIsSkippedForAWhile() {
        val s = AdapterSweep(memoryMs = 1_000)
        s.markOff("81:23:45:67:89:ba", 0)
        assertTrue(s.isOff("81:23:45:67:89:BA", 500)) // não diferencia maiúsculas
        assertFalse(s.isOff("01:23:45:67:89:BA", 500)) // o outro adaptador segue permitido
        assertFalse(s.isOff("81:23:45:67:89:BA", 1_500)) // depois do tempo, volta a valer
    }

    @Test fun anyOffTellsWhenThereIsOnlyOffAdaptersLeft() {
        val s = AdapterSweep(memoryMs = 1_000)
        assertFalse(s.anyOff(0))
        s.markOff("A", 0)
        assertTrue(s.anyOff(10)); assertFalse(s.anyOff(2_000))
    }

    @Test fun triesAnotherAdapterRightAwayOnlyAFewTimes() {
        val s = AdapterSweep()
        repeat(AdapterSweep.MAX_QUICK_SWITCHES) { assertTrue(s.shouldTryAnotherNow()); s.quickSwitches++ }
        assertFalse(s.shouldTryAnotherNow())
        s.clear()
        assertTrue(s.shouldTryAnotherNow()); assertFalse(s.anyOff(0))
    }
}
