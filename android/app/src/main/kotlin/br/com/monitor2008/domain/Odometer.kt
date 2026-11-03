package br.com.monitor2008.domain

import kotlin.math.abs

/**
 * O hodômetro do 2008 não sai pela tomada OBD (sem PID A6; ele fica no quadro de instrumentos).
 * Para corrigir o km do app sem digitar, o dono tira uma foto do painel e o app lê o número
 * (reconhecimento de texto do aparelho). Aqui só as regras, testadas.
 */
object OdometerRead {
    // Número com milhar separado ("45.231", "45 231") ou corrido ("45231"), sem casa decimal depois
    // (o parcial "123.4" não é o hodômetro).
    private val number = Regex("(?<![\\d.,])(\\d{1,3}(?:[. ]\\d{3})+|\\d{4,7})(?![.,]?\\d)")

    fun candidates(lines: List<String>): List<Int> = lines.flatMap { line ->
        number.findAll(line).mapNotNull { m -> m.value.replace(".", "").replace(" ", "").toIntOrNull() }.toList()
    }.filter { it in 100..999_999 }.distinct()

    /**
     * O km do painel no texto lido. Com o km que o app já calcula ([expectedKm]), fica com o número
     * mais perto dele (entre 2.000 a menos e 50.000 a mais); sem ele, o maior número (o hodômetro é o
     * maior número do painel). Null se não achou nenhum plausível.
     */
    fun pick(lines: List<String>, expectedKm: Double?): Int? {
        val c = candidates(lines)
        if (c.isEmpty()) return null
        if (expectedKm == null) return c.filter { it >= 1_000 }.maxOrNull() ?: c.maxOrNull()
        return c.filter { it >= expectedKm - 2_000 && it <= expectedKm + 50_000 }.minByOrNull { abs(it - expectedKm) }
    }
}

/** Lembrete mensal de conferir o km do painel com o do app. */
object OdometerReminder {
    const val INTERVAL_MS = 30L * 24 * 3_600_000

    /** Mostra o lembrete: km já informado uma vez, carro parado e 30 dias desde a última conferência. */
    fun due(nowMs: Long, lastCheckMs: Long, odometerSet: Boolean, moving: Boolean): Boolean =
        odometerSet && !moving && lastCheckMs > 0 && nowMs - lastCheckMs >= INTERVAL_MS
}
