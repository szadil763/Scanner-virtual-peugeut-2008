package br.com.monitor2008.domain

import kotlin.math.roundToLong

enum class MaintState { OK, SOON, DUE }

/** Plano de troca de óleo. O intervalo padrão é chute: confirme no manual do carro. */
data class OilPlan(
    val intervalKm: Int = 10_000,
    val intervalMonths: Int = 12,
    val lastChangeKm: Double,
    val lastChangeEpochDay: Long,
)

data class OilStatus(val kmLeft: Double, val daysLeft: Long, val state: MaintState)

object Maintenance {
    private const val DAYS_PER_MONTH = 30.4375
    private const val SOON_KM = 1_000.0
    private const val SOON_DAYS = 30L

    fun oilStatus(plan: OilPlan, currentKm: Double, todayEpochDay: Long): OilStatus {
        val kmLeft = plan.intervalKm - (currentKm - plan.lastChangeKm)
        val intervalDays = (plan.intervalMonths * DAYS_PER_MONTH).roundToLong()
        val daysLeft = intervalDays - (todayEpochDay - plan.lastChangeEpochDay)
        val state = when {
            kmLeft <= 0 || daysLeft <= 0 -> MaintState.DUE
            kmLeft <= SOON_KM || daysLeft <= SOON_DAYS -> MaintState.SOON
            else -> MaintState.OK
        }
        return OilStatus(kmLeft, daysLeft, state)
    }
}
