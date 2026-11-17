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
        // Tensão baixa por pouco tempo (ventoinha ligada) não é alarme.
        assertTrue(CriticalAlerts.check(coolantC = 100.0, batteryV = 12.4, rpm = 800.0, batteryLowSustained = false).isEmpty())
        assertTrue(CriticalAlerts.shouldSpeak("temp", 1_000_000L, emptyMap()))
        assertFalse(CriticalAlerts.shouldSpeak("temp", 1_000_000L, mapOf("temp" to 900_000L)))
    }

    @Test fun lowBatteryOnlyAlarmsWhenItLasts() {
        val w = LowBatteryWatch()
        // Ventoinha ligada por 90 s: cai para 12,4 V e volta para 13,8 V. Sem alarme.
        assertFalse(w.update(0, 12.4, 800.0)); assertFalse(w.update(90_000, 12.4, 800.0))
        assertFalse(w.update(92_000, 13.8, 800.0)); assertFalse(w.update(200_000, 12.5, 800.0))
        // Uma leitura solta de 12,7 V não zera a contagem; 2 min seguidos abaixo de 12,6 V dão alarme.
        assertFalse(w.update(260_000, 12.7, 800.0))
        assertTrue(w.update(320_000, 12.5, 800.0))
        // Motor desligado: zera.
        assertFalse(w.update(330_000, 12.3, 0.0)); assertFalse(w.update(340_000, 12.3, 800.0))
    }

    @Test fun veryLowBatteryAlarmsAfter20Seconds() {
        val w = LowBatteryWatch()
        assertFalse(w.update(0, 11.8, 900.0)); assertFalse(w.update(10_000, 11.9, 900.0))
        assertTrue(w.update(20_000, 11.8, 900.0))
    }
}
