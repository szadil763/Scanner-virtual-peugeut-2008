package br.com.monitor2008.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Posto registrado num abastecimento. Postos a menos de [FuelQuality.SAME_STATION_M] são o mesmo. */
data class Station(val id: String, val name: String, val address: String, val lat: Double, val lon: Double)

/** Um abastecimento, do ponto de vista da qualidade do combustível. */
data class FuelStop(
    val timeMs: Long,
    val stationId: String?,
    val full: Boolean,
    val liters: Double,
    val pricePerLiter: Double,
    val ethanol: Boolean,
    /**
     * Litros reais ÷ litros previstos pelo ar admitido, no tanque que TERMINOU neste abastecimento.
     * Só existe de tanque cheio a tanque cheio, sem abastecimento parcial no meio.
     */
    val ratioOfEndedTank: Double? = null,
)

enum class FuelGrade { BOA, NORMAL, SUSPEITA, POUCOS_DADOS }

/** O que o carro mostrou rodando com o combustível de um abastecimento. Diferenças contra o normal do carro. */
data class TankEval(
    val stationId: String,
    val startMs: Long,
    val km: Double,
    val ratioDevPct: Double?,
    val timingDiff: Double?,
    val ltftDiff: Double?,
    val score: Int,
    val notes: List<String>,
) {
    /** Teve pelo menos uma comparação com o normal do carro. */
    val compared: Boolean get() = ratioDevPct != null || timingDiff != null || ltftDiff != null
}

data class StationRating(
    val station: Station,
    val grade: FuelGrade,
    /** Tanques comparados com o normal do carro. */
    val tanks: Int,
    val visits: Int,
    val lastPrice: Double?,
    val lastVisitMs: Long,
    val distanceKm: Double?,
    val summary: String,
)

/**
 * Estima a qualidade do combustível de cada posto pelo que o próprio carro mostrou rodando com ele:
 * - litros reais ÷ previstos pelo ar (tanque cheio a cheio): combustível fraco ou adulterado gasta
 *   mais litros para o mesmo ar; a conta já desconta trânsito e jeito de dirigir;
 * - avanço de ignição: octanagem baixa faz a central atrasar a ignição (batida de pino);
 * - correção longa de combustível: mistura mais pobre que o normal (mais etanol ou água).
 * Tudo comparado com a mediana dos tanques do mesmo combustível. É estimativa, não laudo.
 */
object FuelQuality {
    const val SAME_STATION_M = 150.0
    /** Abaixo disso o tanque rodou pouco para dizer algo. */
    const val MIN_KM = 30.0
    /** Tanques do mesmo combustível necessários para existir um "normal do carro". */
    const val MIN_TANKS = 3

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** Posto já registrado a menos de [maxM] metros, o mais perto. */
    fun nearest(stations: List<Station>, lat: Double, lon: Double, maxM: Double = SAME_STATION_M): Station? =
        stations.map { it to distanceM(lat, lon, it.lat, it.lon) }.filter { it.second <= maxM }.minByOrNull { it.second }?.first

    private fun median(v: List<Double>): Double? {
        if (v.isEmpty()) return null
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Média ponderada pelo tempo de motor ligado. */
    private fun weighted(trips: List<TripHealth>, f: (TripHealth) -> Double?): Double? {
        val pairs = trips.mapNotNull { t -> f(t)?.let { it to t.runningMin } }
        val w = pairs.sumOf { it.second }
        return if (w <= 0) null else pairs.sumOf { it.first * it.second } / w
    }

    private class Raw(val stop: FuelStop, val km: Double, val ratio: Double?, val timing: Double?, val ltft: Double?)

    /**
     * Avalia cada abastecimento de tanque cheio num posto conhecido: o tanque vai dele até o próximo
     * abastecimento (ou até agora, se for o atual).
     */
    fun tanks(stops: List<FuelStop>, trips: List<TripHealth>, nowMs: Long): List<TankEval> {
        val sorted = stops.sortedBy { it.timeMs }
        val raws = sorted.mapIndexedNotNull { i, s ->
            if (!s.full || s.stationId == null) return@mapIndexedNotNull null
            val next = sorted.getOrNull(i + 1)
            val end = next?.timeMs ?: nowMs
            val tankTrips = trips.filter { it.startMs >= s.timeMs && it.startMs < end }
            val km = tankTrips.sumOf { it.km }
            if (km < MIN_KM) return@mapIndexedNotNull null
            // Litros reais ÷ previstos só vale se o próximo abastecimento fechou este tanque (cheio).
            val ratio = next?.takeIf { it.full }?.ratioOfEndedTank
            Raw(s, km, ratio, weighted(tankTrips) { it.timingAvg }, weighted(tankTrips) { it.ltftAvg })
        }
        return raws.map { r -> evaluate(r, raws.filter { it.stop.ethanol == r.stop.ethanol }) }
    }

    private fun evaluate(r: Raw, sameFuel: List<Raw>): TankEval {
        fun baseline(f: (Raw) -> Double?): Double? {
            val v = sameFuel.mapNotNull(f)
            return if (v.size >= MIN_TANKS) median(v) else null
        }
        val ratioBase = baseline { it.ratio }
        val timingBase = baseline { it.timing }
        val ltftBase = baseline { it.ltft }
        val ratioDev = if (r.ratio != null && ratioBase != null) (r.ratio / ratioBase - 1) * 100 else null
        val timingDiff = if (r.timing != null && timingBase != null) r.timing - timingBase else null
        val ltftDiff = if (r.ltft != null && ltftBase != null) r.ltft - ltftBase else null

        var score = 0
        val notes = mutableListOf<String>()
        fun pct(v: Double) = String.format(java.util.Locale("pt", "BR"), "%.0f", kotlin.math.abs(v))
        ratioDev?.let {
            when {
                it > 6 -> { score -= 2; notes += "gastou ${pct(it)}% mais combustível que o normal do carro" }
                it > 3 -> { score -= 1; notes += "gastou ${pct(it)}% mais combustível que o normal" }
                it < -3 -> { score += 1; notes += "rendeu ${pct(it)}% melhor que o normal" }
            }
        }
        timingDiff?.let {
            when {
                it < -3 -> { score -= 2; notes += "ignição bem atrasada: sinal de octanagem baixa (batida de pino)" }
                it < -1.5 -> { score -= 1; notes += "ignição um pouco atrasada" }
                it > 1.5 -> { score += 1; notes += "ignição adiantada: combustível com boa octanagem" }
            }
        }
        ltftDiff?.let {
            when {
                it > 10 -> { score -= 2; notes += "mistura bem mais pobre que o normal (combustível fora do padrão?)" }
                it > 6 -> { score -= 1; notes += "mistura mais pobre que o normal" }
            }
        }
        return TankEval(r.stop.stationId!!, r.stop.timeMs, r.km, ratioDev, timingDiff, ltftDiff, score, notes)
    }

    fun grade(evals: List<TankEval>): FuelGrade {
        val compared = evals.filter { it.compared }
        if (compared.isEmpty()) return FuelGrade.POUCOS_DADOS
        val avg = compared.map { it.score }.average()
        return when {
            avg <= -1.5 -> FuelGrade.SUSPEITA
            avg >= 0.5 -> FuelGrade.BOA
            else -> FuelGrade.NORMAL
        }
    }

    /** Postos com nota, os bons e mais perto primeiro; os suspeitos por último. */
    fun rank(stations: List<Station>, stops: List<FuelStop>, evals: List<TankEval>, here: Pair<Double, Double>?): List<StationRating> {
        val order = listOf(FuelGrade.BOA, FuelGrade.NORMAL, FuelGrade.POUCOS_DADOS, FuelGrade.SUSPEITA)
        return stations.map { st ->
            val mine = evals.filter { it.stationId == st.id }
            val visits = stops.filter { it.stationId == st.id }.sortedBy { it.timeMs }
            val grade = grade(mine)
            val compared = mine.filter { it.compared }
            val summary = when {
                compared.isEmpty() -> "Poucos dados: a nota sai depois de rodar uns ${MIN_KM.toInt()} km com o tanque e ter $MIN_TANKS tanques para comparar."
                else -> (compared.last().notes.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Rodou dentro do normal do carro") +
                    " · ${compared.size} tanque(s) avaliado(s)"
            }
            StationRating(
                station = st, grade = grade, tanks = compared.size, visits = visits.size,
                lastPrice = visits.lastOrNull()?.pricePerLiter, lastVisitMs = visits.lastOrNull()?.timeMs ?: 0L,
                distanceKm = here?.let { distanceM(it.first, it.second, st.lat, st.lon) / 1000 },
                summary = summary,
            )
        }.sortedWith(compareBy<StationRating> { order.indexOf(it.grade) }.thenBy { it.distanceKm ?: Double.MAX_VALUE })
    }
}
