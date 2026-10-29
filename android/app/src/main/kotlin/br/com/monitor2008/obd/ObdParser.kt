package br.com.monitor2008.obd

/**
 * Interpreta o texto devolvido pelo ELM327. Sem dependência de Android, para ser testável.
 *
 * Respostas típicas: "41 0C 1A F8 \r\r>", "SEARCHING...\r41 05 7B", "NO DATA", "?".
 */
object ObdParser {

    /**
     * Devolve os bytes que vêm depois de [prefix], um bloco por central que respondeu: no 2008
     * automático, o motor e o câmbio respondem juntos às consultas do modo 01.
     *
     * Com [size] (bytes de dados de cada resposta), também separa respostas que chegam grudadas
     * na mesma linha, como "41 00 80 00 00 00 41 00 BE 3E B8 11". Sem [size], pega o resto da linha.
     */
    fun payloads(raw: String, prefix: String, size: Int? = null): List<IntArray> {
        val lines = raw.split('\r', '\n')
            .map { it.uppercase().replace(NOISE, "").removePrefix("SEARCHING...") }
            .filter { it.isNotEmpty() }
        val out = mutableListOf<IntArray>()
        for (line in lines) {
            if (size == null) {
                if (!line.startsWith(prefix)) continue
                hexBytes(line.substring(prefix.length))?.let { out += it }
                continue
            }
            var pos = 0
            val frame = prefix.length + 2 * size
            while (line.startsWith(prefix, pos) && pos + frame <= line.length) {
                hexBytes(line.substring(pos + prefix.length, pos + frame))?.let { out += it }
                pos += frame
            }
        }
        return out
    }

    private val NOISE = Regex("[^0-9A-Z.?]") // espaços, ">", e qualquer caractere de controle

    private fun hexBytes(hex: String): IntArray? {
        if (hex.length % 2 != 0) return null
        val bytes = hex.chunked(2).map { it.toIntOrNull(16) }
        if (bytes.any { it == null }) return null
        return bytes.map { it!! }.toIntArray()
    }

    /** Bytes da primeira linha válida, ou null. */
    fun payload(raw: String, prefix: String): IntArray? = payloads(raw, prefix).firstOrNull()

    /** Decodifica um PID. Null se a resposta for "NO DATA", "?" ou estiver incompleta. Usa a primeira central com resposta completa. */
    fun parse(pid: Pid, raw: String): Double? {
        val bytes = payloads(raw, pid.responsePrefix, pid.dataBytes).firstOrNull() ?: return null
        return pid.decode(bytes)
    }

    /**
     * Lê a máscara de PIDs suportados (consultas 0100, 0120, 0140, 0160).
     * [base] é o PID consultado (0x00, 0x20...). Junta o que todas as centrais declararam:
     * ler só a primeira linha pegava o câmbio, que quase não declara nada, e o painel ficava vazio.
     */
    fun supportedFrom(base: Int, raw: String): Set<Int> {
        val out = mutableSetOf<Int>()
        for (b in payloads(raw, "41" + Pid.hex2(base), 4)) {
            val mask = (b[0].toLong() shl 24) or (b[1].toLong() shl 16) or (b[2].toLong() shl 8) or b[3].toLong()
            for (i in 0 until 32) {
                if (((mask shr (31 - i)) and 1L) == 1L) out += base + 1 + i
            }
        }
        return out
    }

    /**
     * PID 01 de todas as centrais juntas: devolve o byte A combinado (bit 7 = luz de injeção acesa em
     * alguma central; bits 0..6 = soma das falhas). No 2008 o câmbio também responde ao PID 01, e ler
     * só a primeira resposta podia esconder a luz acesa pelo motor.
     */
    fun monitorStatus(raw: String): Double? {
        val all = payloads(raw, Pid.MONITOR_STATUS.responsePrefix, Pid.MONITOR_STATUS.dataBytes)
        if (all.isEmpty()) return null
        val mil = all.any { (it[0] and 0x80) != 0 }
        val count = all.sumOf { it[0] and 0x7F }.coerceAtMost(0x7F)
        return ((if (mil) 0x80 else 0) or count).toDouble()
    }

    /**
     * Tensão medida pelo próprio adaptador no pino 16 da tomada OBD (comando ATRV), por exemplo
     * "12.4V". Serve de bateria quando o carro não informa o PID 42, como no 2008.
     */
    fun voltage(raw: String): Double? =
        Regex("""(\d{1,2}(?:[.,]\d+)?)\s*V""", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    /**
     * Decodifica a resposta do modo 03 (falhas armazenadas, prefixo "43") ou 07 (pendentes, "47")
     * em CAN (ISO 15765-4): prefixo, "NN" e NN pares de bytes. Pares 00 00 são preenchimento.
     */
    fun dtcs(raw: String, prefix: String = "43"): List<String> {
        // Junta as respostas de todas as centrais: no 2008 o câmbio responde "43 00" primeiro e
        // escondia a falha do motor (Saúde mostrava 1 falha e o Diagnóstico, nenhuma).
        val out = linkedSetOf<String>()
        for (b in payloads(joinMultiFrame(raw), prefix)) {
            if (b.isEmpty()) continue
            val count = b[0]
            for (i in 0 until count) {
                val a = b.getOrNull(1 + 2 * i) ?: break
                val c = b.getOrNull(2 + 2 * i) ?: break
                if (a == 0 && c == 0) continue
                val letter = "PCBU"[(a shr 6) and 3]
                val d1 = (a shr 4) and 3
                val d2 = (a and 0xF).toString(16).uppercase()
                out += "$letter$d1$d2${Pid.hex2(c)}"
            }
        }
        return out.toList()
    }

    /**
     * Respostas longas (mais de 2 falhas) chegam em várias linhas: "00A", "0: 43 03 ...", "1: ...".
     * Junta as linhas numeradas de cada resposta numa linha só.
     */
    fun joinMultiFrame(raw: String): String {
        val frame = Regex("""^([0-9A-Fa-f]):(.*)$""")
        val out = mutableListOf<String>()
        var current: StringBuilder? = null
        for (line in raw.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }) {
            val m = frame.find(line)
            if (m == null) {
                current?.let { out += it.toString() }
                current = null
                out += line
                continue
            }
            if (m.groupValues[1] == "0") {
                current?.let { out += it.toString() }
                current = StringBuilder()
            }
            current?.append(' ')?.append(m.groupValues[2])
        }
        current?.let { out += it.toString() }
        return out.joinToString("\r")
    }
}
