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

    @Test fun opensScreens() {
        assertEquals(VoiceNav.SAUDE, VoiceCommands.parse("abrir a saúde").nav)
        assertEquals(VoiceNav.RADIO, VoiceCommands.parse("tocar rádio").nav)
        assertEquals(VoiceNav.RADIO, VoiceCommands.parse("rádio").nav)
        assertEquals(VoiceNav.MUSICA, VoiceCommands.parse("vai para a música").nav)
        assertEquals(VoiceNav.MIDIAS, VoiceCommands.parse("abre mídias").nav)
        assertEquals(VoiceNav.MAPA, VoiceCommands.parse("abrir o mapa").nav)
        assertEquals(VoiceNav.POSTOS, VoiceCommands.parse("mostra os postos").nav)
        assertEquals(VoiceNav.PAINEL, VoiceCommands.parse("voltar para o painel").nav)
        // Perguntas continuam perguntas.
        assertEquals(VoiceIntent.SAUDE, VoiceCommands.parse("como está a saúde do carro").intent)
        assertEquals(VoiceIntent.CONSUMO, VoiceCommands.parse("qual o consumo").intent)
        assertEquals(VoiceIntent.OLEO, VoiceCommands.parse("quanto falta pra troca de óleo").intent)
    }

    @Test fun numbersInWords() {
        assertEquals(listOf("abasteci", "100", "reais", "a", "6", "e", "11"), VoiceCommands.numbers("Abasteci cem reais a seis e onze"))
        assertEquals(listOf("125"), VoiceCommands.numbers("cento e vinte e cinco"))
        assertEquals(listOf("2000"), VoiceCommands.numbers("dois mil"))
        assertEquals(listOf("r", "100", "a", "6.11"), VoiceCommands.numbers("R$ 100 a 6,11"))
    }

    @Test fun refuelByMoney() {
        val r = VoiceCommands.parse("abasteci cem reais a seis e onze").refuel!!
        assertEquals(100.0, r.amount!!, 1e-9)
        assertEquals(6.11, r.pricePerLiter!!, 1e-9)
        assertEquals(16.367, r.liters!!, 1e-9)
        val r2 = VoiceCommands.refuel("Abasteci R$ 150,00 de etanol, o litro a 4,29, tanque cheio")
        assertEquals(150.0, r2.amount!!, 1e-9)
        assertEquals(4.29, r2.pricePerLiter!!, 1e-9)
        assertEquals(true, r2.full)
        assertEquals(true, r2.ethanol)
        val r3 = VoiceCommands.refuel("coloquei 80 reais de gasolina a 6 reais e 19 centavos")
        assertEquals(80.0, r3.amount!!, 1e-9)
        assertEquals(6.19, r3.pricePerLiter!!, 1e-9)
        assertEquals(false, r3.ethanol)
    }

    @Test fun refuelByLiters() {
        val r = VoiceCommands.refuel("abasteci 40 litros a 6,11, não enchi o tanque")
        assertEquals(40.0, r.liters!!, 1e-9)
        assertEquals(6.11, r.pricePerLiter!!, 1e-9)
        assertEquals(null, r.amount)
        assertEquals(false, r.full)
        val a = VoiceCommands.answer(VoiceCommands.parse("abasteci 40 litros a 6,11"), live.copy(speedKmh = 0.0))
        assertEquals("40 litros a 6,11 o litro: 244 reais e 40 centavos. Confira o posto e toque em Salvar.", a)
    }

    @Test fun refuelNeedsTheCarStopped() {
        assertEquals("Pare o carro para registrar o abastecimento.",
            VoiceCommands.answer(VoiceCommands.parse("abasteci cem reais"), live.copy(moving = true)))
        assertEquals(VoiceIntent.ABASTECER, VoiceCommands.parse("registrar abastecimento").intent)
    }

    @Test fun routeToAnAddress() {
        val c = VoiceCommands.parse("ir para Rua Augusta, 500")
        assertEquals(VoiceIntent.IR_PARA, c.intent)
        assertEquals("rua augusta 500", c.query)
        assertEquals("shopping ibirapuera", VoiceCommands.parse("navegar até o Shopping Ibirapuera").query)
        assertEquals("posto shell", VoiceCommands.parse("como chegar no posto Shell").query)
        assertEquals("casa", VoiceCommands.parse("me leva pra casa").query)
        // Aba continua aba.
        assertEquals(VoiceNav.SAUDE, VoiceCommands.parse("ir para a saúde").nav)
        assertEquals(VoiceIntent.ABRIR, VoiceCommands.parse("vai para os postos").intent)
    }

    @Test fun savedPlaces() {
        val places = listOf("Casa" to "Rua das Flores, 10", "Trabalho" to "Av. Paulista, 1000")
        assertEquals("Rua das Flores, 10", VoiceCommands.placeFor("minha casa", places))
        assertEquals("Av. Paulista, 1000", VoiceCommands.placeFor("o trabalho", places))
        assertEquals(null, VoiceCommands.placeFor("rua augusta 500", places))
    }

    @Test fun radioByName() {
        val c = VoiceCommands.parse("tocar rádio Jovem Pan")
        assertEquals(VoiceIntent.TOCAR_RADIO, c.intent)
        assertEquals("jovem pan", c.query)
        assertEquals("band fm", VoiceCommands.parse("toca a Band FM").query)
        assertEquals("transamerica", VoiceCommands.parse("liga a rádio Transamérica").query)
        val list = listOf(
            RadioStation("1", "Band FM 96.1", "u1"), RadioStation("2", "Jovem Pan FM 100.9 São Paulo", "u2"),
            RadioStation("3", "Rádio Jovem Brasil", "u3"),
        )
        assertEquals("2", VoiceCommands.bestRadio("jovem pan", list)?.id)
        assertEquals("1", VoiceCommands.bestRadio("band fm", list)?.id)
        assertEquals(null, VoiceCommands.bestRadio("fm", list))
    }

    @Test fun musicSearch() {
        val c = VoiceCommands.parse("tocar Péricles no Spotify")
        assertEquals(VoiceIntent.TOCAR_MUSICA, c.intent)
        assertEquals("pericles", c.query)
        assertEquals("com.spotify.music", c.app)
        assertEquals("separacao", VoiceCommands.parse("toca a música Separação").query)
        assertEquals("ate que durou", VoiceCommands.parse("quero ouvir Até que durou").query)
        assertEquals(null, VoiceCommands.parse("toca a música Separação").app)
        // Sem nome: só dá play.
        val play = VoiceCommands.parse("tocar música")
        assertEquals(VoiceIntent.ABRIR, play.intent)
        assertEquals(VoiceNav.MUSICA, play.nav)
        assertTrue(play.play)
    }
}
