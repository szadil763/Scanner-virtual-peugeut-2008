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
     * Tensão medida pelo próprio adaptador no pino 16 da tomada OBD (comando ATRV), por exemplo
     * "12.4V". Serve de bateria quando o carro não informa o PID 42, como no 2008.
     */
    fun voltage(raw: String): Double? =
        Regex("""(\d{1,2}(?:[.,]\d+)?)\s*V""", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    /**
     * Decodifica a resposta do modo 03 (falhas armazenadas) em CAN (ISO 15765-4):
     * "43 NN" seguido de NN pares de bytes. Pares 00 00 são preenchimento.
     */
    fun dtcs(raw: String): List<String> {
        val b = payload(raw, "43") ?: return emptyList()
        if (b.isEmpty()) return emptyList()
        val count = b[0]
        val out = mutableListOf<String>()
        for (i in 0 until count) {
            val a = b.getOrNull(1 + 2 * i) ?: break
            val c = b.getOrNull(2 + 2 * i) ?: break
            if (a == 0 && c == 0) continue
            val letter = "PCBU"[(a shr 6) and 3]
            val d1 = (a shr 4) and 3
            val d2 = (a and 0xF).toString(16).uppercase()
            out += "$letter$d1$d2${Pid.hex2(c)}"
        }
        return out
    }
}
