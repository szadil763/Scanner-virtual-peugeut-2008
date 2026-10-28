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

    /** [litersPerHour] nulo = sem estimativa: conta distância e tempo, mas não entra na média de consumo. */
    fun add(dtSeconds: Double, speedKmh: Double, litersPerHour: Double?) {
        if (dtSeconds <= 0.0) return
        val km = speedKmh * dtSeconds / 3600.0
        distanceKm += km
        seconds += dtSeconds
        if (litersPerHour != null) {
            liters += litersPerHour * dtSeconds / 3600.0
            kmWithFuel += km
        }
    }

    val avgKmPerL: Double? get() = if (liters > 0.01 && kmWithFuel > 0.1) kmWithFuel / liters else null
    val avgLPer100Km: Double? get() = avgKmPerL?.let { 100.0 / it }
    val avgSpeedKmh: Double get() = if (seconds > 0) distanceKm / seconds * 3600.0 else 0.0

    fun restore(distanceKm: Double, liters: Double, seconds: Double, kmWithFuel: Double) {
        this.distanceKm = distanceKm; this.liters = liters; this.seconds = seconds; this.kmWithFuel = kmWithFuel
    }

    fun reset() {
        distanceKm = 0.0; liters = 0.0; seconds = 0.0; kmWithFuel = 0.0
    }
}
