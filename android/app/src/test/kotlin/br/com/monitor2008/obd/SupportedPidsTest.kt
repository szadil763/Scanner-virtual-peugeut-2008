package br.com.monitor2008.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedPidsTest {
    // Respostas reais do 2008 (fotos do Diagnóstico): câmbio e motor respondem juntos ao 0100.
    private val both = "SEARCHING...|41 00 80 00 00 00 |41 00 BE 3E B8 11"
    private val gearboxOnly = "41 00 80 00 00 00"
    private val r20 = "41 20 80 00 00 01"
    private val r40 = "41 40 02 00 04 00"

    @Test fun gearboxOnlyAnswerIsRetried() {
        // 0.1.59: o segundo 0100 veio só do câmbio e o app perdeu rotação, temperatura etc.
        val asked = mutableListOf<String>()
        var first00 = true
        val pids = SupportedPids.collect(firstResponse = "") { cmd ->
            asked += cmd
            when (cmd) {
                "0100" -> if (first00) { first00 = false; gearboxOnly } else both
                "0120" -> r20
                "0140" -> r40
                else -> "NO DATA"
            }
        }
        assertTrue(0x05 in pids)
        assertTrue(0x0C in pids)
        assertTrue(0x21 in pids)
        assertEquals(listOf("0100", "0100", "0120", "0140", "0140"), asked)
    }

    @Test fun firstResponseCountsToo() {
        // O primeiro 0100 (na busca do protocolo) já tinha o motor: vale mesmo se o segundo vier só do câmbio.
        val pids = SupportedPids.collect(firstResponse = both) { cmd ->
            when (cmd) { "0100" -> gearboxOnly; "0120" -> r20; "0140" -> r40; else -> "NO DATA" }
        }
        assertTrue(pids.containsAll(listOf(0x05, 0x0C, 0x0D, 0x0B)))
    }
}
