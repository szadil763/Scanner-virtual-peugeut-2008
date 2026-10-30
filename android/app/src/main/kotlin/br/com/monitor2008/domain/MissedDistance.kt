package br.com.monitor2008.domain

/**
 * Km rodados sem o app conectado (celular sem Bluetooth, app fechado). O adaptador e a central não
 * guardam a viagem, mas a central conta os km desde que as falhas foram apagadas (PID 31): a
 * diferença entre o que ela contou e o que o app viu desde a última leitura são os km perdidos.
 */
object MissedDistance {
    /** O contador anda de 1 em 1 km: abaixo disso é arredondamento, não viagem perdida. */
    const val MIN_KM = 3.0

    /**
     * [prevCounterKm] e [prevAppKm]: contador da central e hodômetro do app na última leitura.
     * Devolve os km perdidos, ou null se não há o que corrigir.
     */
    fun missedKm(prevCounterKm: Double?, prevAppKm: Double?, counterKm: Double, appKm: Double): Double? {
        if (prevCounterKm == null || prevAppKm == null) return null
        // Falhas apagadas (na oficina ou pela bateria): o contador volta a zero e não dá para comparar.
        if (counterKm < prevCounterKm) return null
        val carKm = counterKm - prevCounterKm
        val seenKm = (appKm - prevAppKm).coerceAtLeast(0.0)
        val missed = carKm - seenKm
        return if (missed >= MIN_KM) missed else null
    }
}
