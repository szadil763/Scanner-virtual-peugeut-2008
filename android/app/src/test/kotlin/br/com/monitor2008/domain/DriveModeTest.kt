package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveModeTest {
    private val auto = DriveModeSetting.AUTO

    @Test fun entersAfterTenSecondsMoving() {
        val t = DriveModeTracker()
        assertFalse(t.update(0, 40.0, auto))
        assertFalse(t.update(9_000, 40.0, auto))
        assertTrue(t.update(10_000, 40.0, auto))
    }

    @Test fun quickStartDoesNotSwitch() {
        val t = DriveModeTracker()
        t.update(0, 30.0, auto)
        t.update(5_000, 0.0, auto) // parou antes dos 10 s
        assertFalse(t.update(12_000, 30.0, auto))
    }

    @Test fun trafficLightKeepsDriveMode() {
        val t = DriveModeTracker()
        t.update(0, 50.0, auto); t.update(10_000, 50.0, auto)
        assertTrue(t.update(20_000, 0.0, auto))  // semáforo
        assertTrue(t.update(70_000, 0.0, auto))  // 50 s parado: continua
        assertTrue(t.update(75_000, 10.0, auto)) // andou de novo
        assertTrue(t.update(80_000, 0.0, auto))
        assertFalse(t.update(140_000, 0.0, auto)) // 1 min parado: volta ao completo
    }

    @Test fun settingsOverride() {
        val t = DriveModeTracker()
        assertTrue(t.update(0, 0.0, DriveModeSetting.ALWAYS_SIMPLE))
        assertFalse(t.update(0, 120.0, DriveModeSetting.ALWAYS_FULL))
    }

    @Test fun criticalAlerts() {
        assertEquals(listOf("temp"), CriticalAlerts.check(coolantC = 112.0, batteryV = 14.0, rpm = 2000.0).map { it.id })
        assertEquals(listOf("bat"), CriticalAlerts.check(coolantC = 90.0, batteryV = 12.1, rpm = 900.0).map { it.id })
        // Bateria baixa com o motor desligado não é alerta grave (é normal ficar perto de 12,4 V).
        assertTrue(CriticalAlerts.check(coolantC = 90.0, batteryV = 12.1, rpm = 0.0).isEmpty())
        assertTrue(CriticalAlerts.shouldSpeak("temp", 1_000_000L, emptyMap()))
        assertFalse(CriticalAlerts.shouldSpeak("temp", 1_000_000L, mapOf("temp" to 900_000L)))
    }
}
