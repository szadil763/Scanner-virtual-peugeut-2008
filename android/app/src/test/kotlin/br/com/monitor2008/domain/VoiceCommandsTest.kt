package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {

    private val live = VoiceSnapshot(
        connected = true, kmPerLNow = 14.2, tripAvgKmPerL = 12.34, tripKm = 23.6, tripMinutes = 75,
        coolantC = 91.0, batteryV = 14.1, speedKmh = 60.0, rpm = 2100.0, oilKmLeft = 3200.0, oilDaysLeft = 140,
        alerts = listOf(VoiceAlert("Catalisador", true), VoiceAlert("Marcha lenta", true), VoiceAlert("Bateria", false)),
        dtcs = listOf("P0420"),
    )

    @Test fun understandsCommonQuestions() {
        assertEquals(VoiceIntent.CONSUMO, VoiceCommands.intent("Qual é o consumo?"))
        assertEquals(VoiceIntent.CONSUMO, VoiceCommands.intent("quantos km por litro tô fazendo"))
        assertEquals(VoiceIntent.TEMPERATURA, VoiceCommands.intent("Temperatura do motor"))
        assertEquals(VoiceIntent.BATERIA, VoiceCommands.intent("como está a bateria"))
        assertEquals(VoiceIntent.OLEO, VoiceCommands.intent("Quanto falta pra troca de óleo?"))
        assertEquals(VoiceIntent.FALHAS, VoiceCommands.intent("tem alguma falha?"))
        assertEquals(VoiceIntent.FALHAS, VoiceCommands.intent("por que a luz de injeção acendeu"))
        assertEquals(VoiceIntent.SAUDE, VoiceCommands.intent("Como está o carro?"))
        assertEquals(VoiceIntent.VIAGEM, VoiceCommands.intent("quanto andei hoje"))
        assertEquals(VoiceIntent.AJUDA, VoiceCommands.intent("ajuda"))
        assertEquals(VoiceIntent.DESCONHECIDO, VoiceCommands.intent("bom dia"))
        assertEquals(VoiceIntent.DESCONHECIDO, VoiceCommands.intent(""))
    }

    @Test fun picksTheFirstCandidateItUnderstands() {
        val (intent, text) = VoiceCommands.intent(listOf("qual o con sumo", "qual o consumo"))
        assertEquals(VoiceIntent.CONSUMO, intent)
        assertEquals("qual o consumo", text)
    }

    @Test fun answersWithCommaDecimals() {
        val a = VoiceCommands.answer(VoiceIntent.CONSUMO, live)
        assertTrue(a, a.contains("12,3 quilômetros por litro em 24 quilômetros"))
        assertTrue(a, a.contains("Agora, 14,2"))
        assertEquals("Trajeto com 24 quilômetros em 1 hora e 15 minutos, média de 12,3 quilômetros por litro.",
            VoiceCommands.answer(VoiceIntent.VIAGEM, live))
    }

    @Test fun liveDataNeedsTheCar() {
        val off = live.copy(connected = false)
        assertEquals("O carro não está conectado agora.", VoiceCommands.answer(VoiceIntent.TEMPERATURA, off))
        assertTrue(VoiceCommands.answer(VoiceIntent.TEMPERATURA, live).startsWith("Motor a 91 graus. Temperatura normal"))
        assertTrue(VoiceCommands.answer(VoiceIntent.TEMPERATURA, live.copy(coolantC = 112.0)).contains("pare em local seguro"))
    }

    @Test fun batteryDependsOnEngineRunning() {
        assertTrue(VoiceCommands.answer(VoiceIntent.BATERIA, live.copy(batteryV = 12.6)).contains("alternador"))
        assertTrue(VoiceCommands.answer(VoiceIntent.BATERIA, live.copy(batteryV = 12.6, rpm = 0.0)).endsWith("Normal."))
    }

    @Test fun healthListsAlertsAndCodes() {
        val a = VoiceCommands.answer(VoiceIntent.SAUDE, live)
        assertTrue(a, a.startsWith("2 alertas: Catalisador, Marcha lenta. Um ponto de atenção: Bateria."))
        assertTrue(a, a.contains("uma falha gravada"))
        assertEquals("Nenhum indicador fora do normal.", VoiceCommands.answer(VoiceIntent.SAUDE, live.copy(alerts = emptyList(), dtcs = emptyList())))
        assertEquals("Uma falha gravada: P 0 4 2 0. Veja a explicação na aba Diagnóstico.", VoiceCommands.answer(VoiceIntent.FALHAS, live))
    }

    @Test fun oil() {
        assertEquals("Faltam 3200 quilômetros ou 140 dias para a troca de óleo.", VoiceCommands.answer(VoiceIntent.OLEO, live))
        assertTrue(VoiceCommands.answer(VoiceIntent.OLEO, live.copy(oilKmLeft = -50.0)).contains("vencida"))
        assertTrue(VoiceCommands.answer(VoiceIntent.OLEO, live.copy(oilKmLeft = null)).contains("ainda não foi informada"))
    }
}
