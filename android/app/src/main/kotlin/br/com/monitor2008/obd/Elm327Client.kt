package br.com.monitor2008.obd

import java.io.InputStream
import java.io.OutputStream

/**
 * Conversa com o ELM327 por um par de streams (o socket Bluetooth fornece os dois).
 * Chamadas bloqueantes: use sempre fora da thread principal (Dispatchers.IO).
 *
 * Somente leitura: o app envia apenas comandos AT de configuração e leitura (ATRV) e consultas dos modos 01, 03 e 07.
 */
class Elm327Client(
    private val input: InputStream,
    private val output: OutputStream,
    private val onExchange: (String, String) -> Unit = { _, _ -> },
) {
    /** Envia [command] e devolve o texto recebido até o prompt ">" ou o fim do tempo. */
    fun send(command: String, timeoutMs: Long = 2_000): String {
        val response = exchange(command, timeoutMs)
        onExchange(command, response)
        return response
    }

    private fun exchange(command: String, timeoutMs: Long): String {
        while (input.available() > 0) input.read() // descarta sobras de respostas antigas
        output.write((command + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()

        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val c = input.read()
                if (c < 0) break
                if (c.toChar() == '>') return sb.toString()
                sb.append(c.toChar())
            } else {
                Thread.sleep(5)
            }
        }
        return sb.toString()
    }

    /** Configura o adaptador e deixa o ELM327 procurar o protocolo do carro. Devolve o protocolo (ATDPN). */
    fun initialize(): String {
        initResponses = listOf(
            send("ATZ", 3_000),
            send("ATE0"),   // sem eco
            send("ATL0"),   // sem line feed
            send("ATH0"),   // sem cabeçalhos CAN na resposta
            send("ATS1"),   // com espaços entre os bytes
            send("ATSP0"),  // protocolo automático
        )
        firstSupport = send("0100", 8_000) // primeira consulta dispara a busca de protocolo
        return send("ATDPN").trim()
    }

    /** Respostas da configuração (ATZ...ATSP0): todas vazias = adaptador conectado mas travado. */
    var initResponses: List<String> = emptyList()
        private set

    /** Resposta do primeiro 0100 (na busca do protocolo): também conta para os PIDs suportados. */
    private var firstSupport = ""

    /** PIDs que o carro declara suportar, consultando as faixas 00, 20, 40 e 60. */
    fun supportedPids(): Set<Int> = SupportedPids.collect(firstSupport) { send(it, 4_000) }

    fun query(pid: Pid): Double? = ObdParser.parse(pid, send(pid.command))

    /** Tensão da bateria medida pelo adaptador (ATRV). Somente leitura. */
    fun adapterVoltage(): Double? = ObdParser.voltage(send("ATRV"))

    /** Modo 03: falhas armazenadas. Somente leitura. */
    fun readDtcs(): List<String> = ObdParser.dtcs(send("03", 4_000))

    /** Modo 07: falhas pendentes (a central viu o problema, mas ainda não acendeu a luz). Somente leitura. */
    fun readPendingDtcs(): List<String> = ObdParser.dtcs(send("07", 4_000), "47")

    /** Modo 09 PID 02: chassi (VIN) do veículo, para identificar o modelo sozinho. Somente leitura. */
    fun readVin(): String? {
        // Como nos PIDs do modo 01: só pede o chassi se o carro declarar (0900, informação 02).
        if (ObdParser.infoTypeSupported(send("0900", 3_000), 2) != true) return null
        return ObdParser.vin(send("0902", 4_000))
    }
}

/**
 * No 2008 o motor e o câmbio respondem juntos ao 0100. Às vezes só o câmbio responde a tempo
 * ("41 00 80 00 00 00", só o PID 01): o app achava que o carro não tinha rotação, temperatura etc.
 * e parava de ler esses dados (foto do dono, 0.1.59). Agora junta as respostas de várias tentativas
 * (e a do primeiro 0100) e só desiste de uma faixa depois de perguntar de novo.
 */
object SupportedPids {
    /** Dados básicos que todo motor declara: sem eles, a resposta veio incompleta. */
    private val ENGINE_BASICS = setOf(0x05, 0x0C)

    fun collect(firstResponse: String, ask: (String) -> String): Set<Int> {
        val all = mutableSetOf<Int>()
        all += ObdParser.supportedFrom(0x00, firstResponse)
        for (base in listOf(0x00, 0x20, 0x40, 0x60)) {
            if (base != 0x00 && base !in all) break // o carro não tem esta faixa
            val tries = if (base == 0x00) 3 else 2
            for (attempt in 0 until tries) {
                all += ObdParser.supportedFrom(base, ask("01" + Pid.hex2(base)))
                val complete = if (base == 0x00) all.containsAll(ENGINE_BASICS) else (base + 0x20) in all || attempt > 0
                if (complete) break
            }
        }
        return all
    }
}
