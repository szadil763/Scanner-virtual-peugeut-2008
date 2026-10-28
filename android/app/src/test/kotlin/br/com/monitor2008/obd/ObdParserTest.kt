package br.com.monitor2008.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {

    @Test fun rpm() = assertEquals(1726.0, ObdParser.parse(Pid.RPM, "41 0C 1A F8 \r\r>")!!, 1e-9)

    @Test fun speed() = assertEquals(50.0, ObdParser.parse(Pid.SPEED, "41 0D 32")!!, 1e-9)

    @Test fun coolantIgnoresSearchingLine() =
        assertEquals(83.0, ObdParser.parse(Pid.COOLANT_TEMP, "SEARCHING...\r41 05 7B\r\r>")!!, 1e-9)

    @Test fun maf() = assertEquals(5.0, ObdParser.parse(Pid.MAF, "41 10 01 F4")!!, 1e-9)

    @Test fun battery() = assertEquals(14.5, ObdParser.parse(Pid.MODULE_VOLTAGE, "41 42 38 A4")!!, 1e-9)

    @Test fun fuelLevel() = assertEquals(50.196078, ObdParser.parse(Pid.FUEL_LEVEL, "41 2F 80")!!, 1e-5)

    @Test fun fuelRate() = assertEquals(10.0, ObdParser.parse(Pid.FUEL_RATE, "41 5E 00 C8")!!, 1e-9)

    @Test fun responseWithoutSpaces() = assertEquals(1726.0, ObdParser.parse(Pid.RPM, "410C1AF8")!!, 1e-9)

    @Test fun noDataAndQuestionMarkGiveNull() {
        assertNull(ObdParser.parse(Pid.RPM, "NO DATA\r\r>"))
        assertNull(ObdParser.parse(Pid.RPM, "?\r\r>"))
    }

    @Test fun incompleteResponseGivesNull() = assertNull(ObdParser.parse(Pid.RPM, "41 0C 1A"))

    @Test fun supportedPids() {
        val s = ObdParser.supportedFrom(0x00, "41 00 BE 3F A8 13")
        assertTrue(s.containsAll(listOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0F, 0x10, 0x11)))
        assertTrue(0x20 in s) // há outra faixa disponível
        assertTrue(0x02 !in s)
    }

    // Resposta real do 2008 automático: o câmbio responde primeiro e quase não declara nada.
    @Test fun supportedPidsJoinsAllEcus() {
        val s = ObdParser.supportedFrom(0x00, "41 00 80 00 00 00 \r41 00 BE 3E B8 11 \r\r>")
        assertTrue(s.containsAll(listOf(0x01, 0x04, 0x05, 0x0B, 0x0C, 0x0D, 0x0F, 0x11, 0x20)))
    }

    // As duas respostas grudadas na mesma linha, como podem chegar pelo BLE.
    @Test fun supportedPidsSplitsEcusOnSameLine() {
        val s = ObdParser.supportedFrom(0x00, "41 00 80 00 00 00 41 00 BE 3E B8 11 \r\r>")
        assertTrue(s.containsAll(listOf(0x01, 0x04, 0x05, 0x0B, 0x0C, 0x0D, 0x0F, 0x11, 0x20)))
    }

    @Test fun rpmWithTwoEcusOnSameLine() =
        assertEquals(758.5, ObdParser.parse(Pid.RPM, "41 0C 0B DA 41 0C 0B DA\r\r>")!!, 1e-9)

    @Test fun parseSkipsIncompleteEcuLine() =
        assertEquals(1726.0, ObdParser.parse(Pid.RPM, "41 0C 1A\r41 0C 1A F8\r\r>")!!, 1e-9)

    @Test fun adapterVoltage() {
        assertEquals(12.4, ObdParser.voltage("12.4V\r\r>")!!, 1e-9)
        assertEquals(14.1, ObdParser.voltage("14,1 V")!!, 1e-9)
        assertNull(ObdParser.voltage("?\r\r>"))
    }

    @Test fun fuelTrims() {
        assertEquals(0.0, ObdParser.parse(Pid.STFT, "41 06 80")!!, 1e-9)
        assertEquals(12.5, ObdParser.parse(Pid.LTFT, "41 07 90")!!, 1e-9)
        assertEquals(-25.0, ObdParser.parse(Pid.LTFT, "41 07 60")!!, 1e-9)
    }

    @Test fun timingAdvance() = assertEquals(10.0, ObdParser.parse(Pid.TIMING, "41 0E 94")!!, 1e-9)

    @Test fun o2Sensor() = assertEquals(0.45, ObdParser.parse(Pid.O2_S1, "41 14 5A FF")!!, 1e-9)

    // Motor e câmbio respondem ao PID 01: a luz acesa do motor não pode sumir atrás da resposta do câmbio.
    @Test fun monitorStatusJoinsEcus() {
        val a = ObdParser.monitorStatus("41 01 00 04 00 00\r41 01 82 07 65 04\r\r>")!!.toInt()
        assertTrue((a and 0x80) != 0)
        assertEquals(2, a and 0x7F)
        assertEquals(0.0, ObdParser.monitorStatus("41 01 00 04 00 00 41 01 00 07 65 04")!!, 1e-9)
        assertNull(ObdParser.monitorStatus("NO DATA"))
    }

    @Test fun pendingDtcs() = assertEquals(listOf("P0171"), ObdParser.dtcs("47 01 01 71 00 00 00 00", "47"))

    @Test fun dtcsInCanFormat() =
        assertEquals(listOf("P0133", "P0200"), ObdParser.dtcs("43 02 01 33 02 00 00 00"))

    @Test fun noDtcs() = assertEquals(emptyList<String>(), ObdParser.dtcs("43 00 00 00 00 00 00 00"))

    @Test fun networkDtcLetter() = assertEquals(listOf("U0100"), ObdParser.dtcs("43 01 C1 00 00 00 00 00"))
}
