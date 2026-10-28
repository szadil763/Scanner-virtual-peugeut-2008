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

/**
 * Item de manutenção por km e/ou tempo. Intervalos são valores comuns para o 2008 1.6 flex
 * automático: confirme no manual e no plano de revisões da Peugeot. Null = não conta por isso.
 */
data class MaintItem(val id: String, val name: String, val intervalKm: Int?, val intervalMonths: Int?, val why: String)

data class ItemStatus(val kmLeft: Double?, val daysLeft: Long?, val state: MaintState)

object MaintCatalog {
    val items = listOf(
        MaintItem("filtro_ar", "Filtro de ar do motor", 20_000, 24, "Filtro sujo aumenta o consumo e tira potência."),
        MaintItem("filtro_comb", "Filtro de combustível", 20_000, 24, "No flex, o etanol solta sujeira do tanque e entope o filtro."),
        MaintItem("filtro_cabine", "Filtro de cabine (ar-condicionado)", 10_000, 12, "Ar-condicionado fraco e com cheiro quando está sujo."),
        MaintItem("velas", "Velas de ignição", 30_000, 36, "Vela gasta causa falha de ignição, marcha lenta irregular e mais consumo."),
        MaintItem("correia", "Correia dentada e tensor", 60_000, 60, "Se romper, danifica o motor. Item crítico: confirme o intervalo no manual."),
        MaintItem("correia_acess", "Correia de acessórios", 40_000, 48, "Aciona alternador e ar-condicionado; ressecada, pode romper."),
        MaintItem("arrefecimento", "Líquido de arrefecimento", 40_000, 36, "O aditivo perde a proteção contra corrosão com o tempo."),
        MaintItem("fluido_freio", "Fluido de freio", null, 24, "Absorve umidade com o tempo e o freio perde eficiência."),
        MaintItem("cambio", "Óleo do câmbio automático", 60_000, 60, "Pode constar como vitalício; trocar preserva o câmbio. Use só o óleo especificado."),
    )
}

object Maintenance {
    private const val DAYS_PER_MONTH = 30.4375
    private const val SOON_KM = 1_000.0
    private const val SOON_DAYS = 30L

    /** Situação de um item: vence pelo que acontecer primeiro, km ou data. */
    fun itemStatus(item: MaintItem, lastKm: Double, lastEpochDay: Long, currentKm: Double, todayEpochDay: Long): ItemStatus {
        val kmLeft = item.intervalKm?.let { it - (currentKm - lastKm) }
        val daysLeft = item.intervalMonths?.let { (it * DAYS_PER_MONTH).roundToLong() - (todayEpochDay - lastEpochDay) }
        val soonKm = item.intervalKm?.let { maxOf(SOON_KM, it * 0.1) }
        val state = when {
            (kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0) -> MaintState.DUE
            (kmLeft != null && kmLeft <= soonKm!!) || (daysLeft != null && daysLeft <= SOON_DAYS) -> MaintState.SOON
            else -> MaintState.OK
        }
        return ItemStatus(kmLeft, daysLeft, state)
    }

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
