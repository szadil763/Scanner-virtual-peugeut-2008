package br.com.monitor2008.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Estimativa do combustível no tanque. O Peugeot não informa o nível pela tomada OBD, então o app parte do
 * que o dono diz: tanque cheio = capacidade. A partir daí desconta o que o app estima ter gasto. É só uma
 * estimativa: o erro do consumo estimado se acumula, e a bomba pode desarmar antes de encher de verdade.
 */
object FuelTank {
    /** Depois de abastecer: cheio = capacidade; parcial soma os litros (sem passar da capacidade). Sem base, parcial não diz nada. */
    fun afterRefuel(currentL: Double?, capacityL: Double, liters: Double, full: Boolean): Double? =
        if (full) capacityL else currentL?.let { min(capacityL, it + liters) }

    /** Depois de rodar: desconta o gasto estimado; nunca fica abaixo de zero. */
    fun afterDriving(currentL: Double?, consumedL: Double): Double? =
        currentL?.let { max(0.0, it - max(0.0, consumedL)) }

    /** Percentual do tanque (0 a 100). */
    fun percent(currentL: Double, capacityL: Double): Int =
        if (capacityL <= 0.0) 0 else (currentL / capacityL * 100.0).coerceIn(0.0, 100.0).toInt()

    /** Quantos km ainda dá para rodar com o que sobrou, na média do trajeto (km/l). Sem média, não dá para dizer. */
    fun rangeKm(currentL: Double?, avgKmPerL: Double?): Double? =
        if (currentL == null || avgKmPerL == null || avgKmPerL <= 0.0) null else currentL * avgKmPerL

    /** Litros a partir de uma porcentagem do tanque (ajuste manual). */
    fun litersFromPercent(percent: Double, capacityL: Double): Double =
        (percent.coerceIn(0.0, 100.0) / 100.0) * capacityL

    /** Reserva: até este percentual, ou com autonomia menor que [RESERVE_RANGE_KM], o app avisa. */
    const val RESERVE_PERCENT = 15
    const val CRITICAL_PERCENT = 7
    const val RESERVE_RANGE_KM = 60.0

    /** Aviso de reserva (null = tanque bem, ou sem estimativa). Usa o mesmo formato dos alertas graves. */
    fun reserveAlert(currentL: Double?, capacityL: Double, rangeKm: Double?): CriticalAlert? {
        if (currentL == null || capacityL <= 0.0) return null
        val pct = percent(currentL, capacityL)
        val litros = String.format(java.util.Locale("pt", "BR"), "%.0f", currentL)
        val km = rangeKm?.let { String.format(java.util.Locale("pt", "BR"), "%.0f", it) }
        val autonomia = if (km != null) " Autonomia de uns $km km." else ""
        return when {
            pct <= CRITICAL_PERCENT ->
                CriticalAlert("tank", "COMBUSTÍVEL NO FIM: cerca de $litros L ($pct%).$autonomia Abasteça agora.",
                    "Atenção: combustível quase acabando, cerca de $litros litros.$autonomia Abasteça agora.")
            pct <= RESERVE_PERCENT || (rangeKm != null && rangeKm < RESERVE_RANGE_KM) ->
                CriticalAlert("tank", "COMBUSTÍVEL NA RESERVA: cerca de $litros L ($pct%).$autonomia Procure um posto.",
                    "Atenção: combustível na reserva, cerca de $litros litros.$autonomia Procure um posto.")
            else -> null
        }
    }

    /** Limites da capacidade que o dono pode informar (L). */
    const val MIN_CAPACITY_L = 20.0
    const val MAX_CAPACITY_L = 120.0
}

/** Um ponto do histórico do tanque. [kind]: "rodando", "abasteceu" ou "ajuste" (nível corrigido à mão). */
data class TankPoint(val timeMs: Long, val liters: Double, val kind: String)

object TankHistory {
    const val MAX_POINTS = 600
    /** Só grava quando o nível mexeu o bastante, ou passou muito tempo desde o último ponto. */
    const val MIN_STEP_L = 0.5
    const val MAX_GAP_MS = 30 * 60_000L

    fun shouldRecord(last: TankPoint?, liters: Double, nowMs: Long): Boolean =
        last == null || kotlin.math.abs(last.liters - liters) >= MIN_STEP_L || nowMs - last.timeMs >= MAX_GAP_MS

    /** Para o gráfico: no máximo [max] pontos, sempre com o primeiro e o último e todos os abastecimentos e ajustes. */
    fun downsample(points: List<TankPoint>, max: Int): List<TankPoint> {
        if (points.size <= max) return points
        val step = points.size.toDouble() / max
        val keep = sortedSetOf<Int>(0, points.lastIndex)
        points.forEachIndexed { i, p -> if (p.kind != "rodando") keep += i }
        var x = 0.0
        while (x < points.size) { keep += x.toInt(); x += step }
        return keep.map { points[it] }
    }
}
