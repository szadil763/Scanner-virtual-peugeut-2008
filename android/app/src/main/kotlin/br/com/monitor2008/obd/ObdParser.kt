package br.com.monitor2008.obd

/**
 * Interpreta o texto devolvido pelo ELM327. Sem dependência de Android, para ser testável.
 *
 * Respostas típicas: "41 0C 1A F8 \r\r>", "SEARCHING...\r41 05 7B", "NO DATA", "?".
 */
object ObdParser {

    /** Devolve os bytes que vêm depois de [prefix] na primeira linha válida, ou null. */
    fun payload(raw: String, prefix: String): IntArray? {
        val lines = raw.split('\r', '\n')
            .map { it.replace(" ", "").replace(">", "").uppercase() }
            .filter { it.isNotEmpty() }
        for (line in lines) {
            if (!line.startsWith(prefix)) continue
            val hex = line.substring(prefix.length)
            if (hex.length % 2 != 0) continue
            val bytes = hex.chunked(2).map { it.toIntOrNull(16) }
            if (bytes.any { it == null }) continue
            return bytes.map { it!! }.toIntArray()
        }
        return null
    }

    /** Decodifica um PID. Null se a resposta for "NO DATA", "?" ou estiver incompleta. */
    fun parse(pid: Pid, raw: String): Double? {
        val bytes = payload(raw, pid.responsePrefix) ?: return null
        if (bytes.size < pid.dataBytes) return null
        return pid.decode(bytes)
    }

    /**
     * Lê a máscara de PIDs suportados (consultas 0100, 0120, 0140, 0160).
     * [base] é o PID consultado (0x00, 0x20...). Devolve os PIDs que o carro declarou suportar.
     */
    fun supportedFrom(base: Int, raw: String): Set<Int> {
        val b = payload(raw, "41" + Pid.hex2(base)) ?: return emptySet()
        if (b.size < 4) return emptySet()
        val mask = (b[0].toLong() shl 24) or (b[1].toLong() shl 16) or (b[2].toLong() shl 8) or b[3].toLong()
        val out = mutableSetOf<Int>()
        for (i in 0 until 32) {
            if (((mask shr (31 - i)) and 1L) == 1L) out += base + 1 + i
        }
        return out
    }

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
