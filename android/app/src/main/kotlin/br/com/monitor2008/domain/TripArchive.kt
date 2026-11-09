package br.com.monitor2008.domain

/**
 * Resumo de um trajeto, guardado quando o dono o zera ("Zerar trajeto"). O trajeto do app soma todas as viagens
 * até ser zerado; guardar cada um permite estudar depois a soma de todos (km, litros, custo e média geral).
 */
data class TripRecord(
    /** Hora em que foi zerado. */
    val endMs: Long,
    val distanceKm: Double,
    val liters: Double,
    val seconds: Double,
    /** Km rodados com estimativa de consumo (a média usa este valor, não a distância total). */
    val kmWithFuel: Double,
    /** Litros gastos só com o carro andando. */
    val litersMoving: Double,
    val pricePerLiter: Double,
    /** Fator de calibração em uso no fim do trajeto (os litros já estão com ele aplicado). */
    val calibration: Double,
    val ethanolFraction: Double,
    val odometerKm: Double,
) {
    val avgKmPerL: Double? get() = if (liters > 0.01 && kmWithFuel > 0.1) kmWithFuel / liters else null
    val avgKmPerLMoving: Double? get() = if (litersMoving > 0.01 && kmWithFuel > 0.1) kmWithFuel / litersMoving else null
    val avgSpeedKmh: Double get() = if (seconds > 0) distanceKm / seconds * 3600.0 else 0.0
    val cost: Double get() = liters * pricePerLiter
}

/** Soma de vários trajetos. A média é a dos totais (km ÷ litros), não a média das médias. */
data class TripTotals(
    val count: Int,
    val distanceKm: Double,
    val liters: Double,
    val seconds: Double,
    val cost: Double,
    val avgKmPerL: Double?,
    val avgKmPerLMoving: Double?,
)

object TripArchive {
    /** Trajeto com menos que isso não vale guardar (zerou sem ter rodado). */
    const val MIN_KM_TO_ARCHIVE = 0.1

    fun shouldArchive(distanceKm: Double): Boolean = distanceKm >= MIN_KM_TO_ARCHIVE

    fun totals(trips: List<TripRecord>): TripTotals {
        val kmWithFuel = trips.sumOf { it.kmWithFuel }
        val liters = trips.sumOf { it.liters }
        val litersMoving = trips.sumOf { it.litersMoving }
        return TripTotals(
            count = trips.size,
            distanceKm = trips.sumOf { it.distanceKm },
            liters = liters,
            seconds = trips.sumOf { it.seconds },
            cost = trips.sumOf { it.cost },
            avgKmPerL = if (liters > 0.01 && kmWithFuel > 0.1) kmWithFuel / liters else null,
            avgKmPerLMoving = if (litersMoving > 0.01 && kmWithFuel > 0.1) kmWithFuel / litersMoving else null,
        )
    }
}
