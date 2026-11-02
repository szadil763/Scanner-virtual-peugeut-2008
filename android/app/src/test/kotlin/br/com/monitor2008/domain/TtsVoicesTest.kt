package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoicesTest {
    private val voices = listOf(
        TtsVoiceInfo("pt-br-x-afs-network", 400, network = true),
        TtsVoiceInfo("pt-br-x-ptd-local", 400, network = false),
        TtsVoiceInfo("pt-BR-language", 300, network = false),
        TtsVoiceInfo("pt-br-x-pte-local", 400, network = false, notInstalled = true),
        TtsVoiceInfo("pt-br-x-ptd-local", 400, network = false),
    )

    @Test fun bestIsInstalledOfflineHighQuality() {
        assertEquals("pt-br-x-ptd-local", TtsVoices.best(voices)?.name)
        assertEquals(listOf("pt-br-x-ptd-local", "pt-BR-language", "pt-br-x-afs-network", "pt-br-x-pte-local"),
            TtsVoices.rank(voices).map { it.name })
    }

    @Test fun friendlyNames() {
        val o = TtsVoices.options(voices)
        assertEquals(listOf("Voz 1", "Voz 2", "Voz 3", "Voz 4"), o.map { it.label })
        assertTrue(o[0].detail.startsWith("alta qualidade · funciona sem internet"))
        assertTrue(o[2].detail.contains("usa internet"))
        assertTrue(o[3].detail.endsWith("baixa na primeira vez"))
    }

    @Test fun rateAndPitchSteps() {
        assertEquals(1.1f, TtsVoices.step(1.0f, true), 1e-6f)
        assertEquals(0.9f, TtsVoices.step(1.0f, false), 1e-6f)
        assertEquals(1.6f, TtsVoices.step(1.6f, true), 1e-6f)
        assertEquals(0.6f, TtsVoices.step(0.6f, false), 1e-6f)
    }
}
