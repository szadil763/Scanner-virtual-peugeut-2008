package br.com.monitor2008.domain

/**
 * Acumula distância, combustível e tempo do trajeto para as médias. Soma todas as viagens até o
 * usuário zerar; o estado é gravado com [restore] e os quatro valores públicos.
 */
class TripAccumulator {
    var distanceKm = 0.0; private set
    var liters = 0.0; private set
    var seconds = 0.0; private set
    var kmWithFuel = 0.0; private set
    /** Combustível gasto só com o carro andando (parado e marcha lenta ficam de fora). */
    var litersMoving = 0.0; private set

    /** [litersPerHour] nulo = sem estimativa: conta distância e tempo, mas não entra na média de consumo. */
    fun add(dtSeconds: Double, speedKmh: Double, litersPerHour: Double?) {
        if (dtSeconds <= 0.0) return
        val km = speedKmh * dtSeconds / 3600.0
        distanceKm += km
        seconds += dtSeconds
        if (litersPerHour != null) {
            val l = litersPerHour * dtSeconds / 3600.0
            liters += l
            kmWithFuel += km
            if (speedKmh >= MOVING_KMH) litersMoving += l
        }
    }

    /** Média do trajeto inteiro: inclui o combustível gasto parado. */
    val avgKmPerL: Double? get() = if (liters > 0.01 && kmWithFuel > 0.1) kmWithFuel / liters else null
    /** Média só em movimento: o mesmo que o consumo instantâneo mostra com o carro andando. */
    val avgKmPerLMoving: Double? get() = if (litersMoving > 0.01 && kmWithFuel > 0.1) kmWithFuel / litersMoving else null
    val avgLPer100Km: Double? get() = avgKmPerL?.let { 100.0 / it }
    val avgSpeedKmh: Double get() = if (seconds > 0) distanceKm / seconds * 3600.0 else 0.0

    /** [litersMoving] ausente (trajeto gravado antes desta versão): usa o total, até o próximo zerar. */
    fun restore(distanceKm: Double, liters: Double, seconds: Double, kmWithFuel: Double, litersMoving: Double = liters) {
        this.distanceKm = distanceKm; this.liters = liters; this.seconds = seconds; this.kmWithFuel = kmWithFuel
        this.litersMoving = litersMoving
    }

    fun reset() {
        distanceKm = 0.0; liters = 0.0; seconds = 0.0; kmWithFuel = 0.0; litersMoving = 0.0
    }

    companion object {
        /** Abaixo disso o carro conta como parado (marcha lenta, fila). */
        const val MOVING_KMH = 5.0
    }
}
