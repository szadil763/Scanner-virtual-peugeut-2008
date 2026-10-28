package br.com.monitor2008.obd

import java.io.InputStream
import java.io.OutputStream

/**
 * Conversa com o ELM327 por um par de streams (o socket Bluetooth fornece os dois).
 * Chamadas bloqueantes: use sempre fora da thread principal (Dispatchers.IO).
 *
 * Somente leitura: o app envia apenas comandos AT de configuração e leitura (ATRV) e consultas dos modos 01 e 03.
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
        send("ATZ", 3_000)
        send("ATE0")   // sem eco
        send("ATL0")   // sem line feed
        send("ATH0")   // sem cabeçalhos CAN na resposta
        send("ATS1")   // com espaços entre os bytes
        send("ATSP0")  // protocolo automático
        send("0100", 8_000) // primeira consulta dispara a busca de protocolo
        return send("ATDPN").trim()
    }

    /** PIDs que o carro declara suportar, consultando as faixas 00, 20, 40 e 60. */
    fun supportedPids(): Set<Int> {
        val all = mutableSetOf<Int>()
        for (base in listOf(0x00, 0x20, 0x40, 0x60)) {
            val found = ObdParser.supportedFrom(base, send("01" + Pid.hex2(base), 4_000))
            all += found
            if ((base + 0x20) !in found) break // o carro não tem a faixa seguinte
        }
        return all
    }

    fun query(pid: Pid): Double? = ObdParser.parse(pid, send(pid.command))

    /** Tensão da bateria medida pelo adaptador (ATRV). Somente leitura. */
    fun adapterVoltage(): Double? = ObdParser.voltage(send("ATRV"))

    /** Modo 03: falhas armazenadas. Somente leitura. */
    fun readDtcs(): List<String> = ObdParser.dtcs(send("03", 4_000))
}
