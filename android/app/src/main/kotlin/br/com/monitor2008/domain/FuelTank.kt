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

    /** Limites da capacidade que o dono pode informar (L). */
    const val MIN_CAPACITY_L = 20.0
    const val MAX_CAPACITY_L = 120.0
}
