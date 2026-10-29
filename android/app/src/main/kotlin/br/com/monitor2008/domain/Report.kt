package br.com.monitor2008.domain

import java.util.Locale

/** Tudo que entra no relatório para o mecânico. */
data class ReportInput(
    val dateText: String,
    val appVersion: String,
    val carKm: Double?,
    val tripsRecorded: Int,
    val storedDtcs: List<String>?,
    val pendingDtcs: List<String>?,
    val health: List<HealthItem>,
    val maint: List<Pair<MaintItem, ItemStatus?>>,
    val tripKm: Double,
    val tripKmPerL: Double?,
    val fuelName: String,
    val tips: List<Tip>,
)

/**
 * Relatório em texto simples, para mostrar ou mandar ao mecânico (WhatsApp, e-mail). Texto puro
 * porque abre em qualquer app; os asteriscos viram negrito no WhatsApp.
 */
object Report {
    private val BR = Locale("pt", "BR")

    fun build(r: ReportInput): String = buildString {
        appendLine("*Relatório do carro - Peugeot 2008 1.6 flex automático*")
        appendLine("Data: ${r.dateText} · Monitor 2008 ${r.appVersion}")
        appendLine("Hodômetro: " + (r.carKm?.let { "${String.format(BR, "%,d", Math.round(it))} km" } ?: "não informado"))
        appendLine("Base: ${r.tripsRecorded} viagem(ns) gravada(s) pelo adaptador OBD2")
        appendLine()

        appendLine("*Falhas*")
        when {
            r.storedDtcs == null -> appendLine("- Não lidas nesta sessão")
            r.storedDtcs.isEmpty() -> appendLine("- Nenhuma falha armazenada")
            else -> r.storedDtcs.forEach { appendLine("- $it: ${DtcInfo.describe(it)}") }
        }
        r.pendingDtcs?.forEach { appendLine("- Pendente $it: ${DtcInfo.describe(it)}") }
        appendLine()

        appendLine("*Indicadores*")
        val order = listOf(HealthStatus.ALERTA, HealthStatus.ATENCAO, HealthStatus.OK, HealthStatus.APRENDENDO)
        r.health.sortedBy { order.indexOf(it.status) }.forEach { h ->
            val st = when (h.status) {
                HealthStatus.ALERTA -> "ALERTA"
                HealthStatus.ATENCAO -> "ATENÇÃO"
                HealthStatus.OK -> "ok"
                HealthStatus.APRENDENDO -> "sem dados suficientes"
            }
            appendLine("- ${h.title}: ${h.value} ($st)")
        }
        appendLine()

        appendLine("*Consumo*")
        appendLine("- ${r.fuelName}: " + (r.tripKmPerL?.let { String.format(BR, "%.1f km/l", it) } ?: "sem dados") +
            String.format(BR, " em %.0f km (estimado pelo OBD2)", r.tripKm))
        appendLine()

        val maintLines = r.maint.mapNotNull { (item, st) ->
            when (st?.state) {
                MaintState.DUE -> "- ${item.name}: vencido"
                MaintState.SOON -> "- ${item.name}: vence em breve"
                null -> "- ${item.name}: última troca não informada"
                else -> null
            }
        }
        if (maintLines.isNotEmpty()) {
            appendLine("*Manutenção por km/data*")
            maintLines.forEach { appendLine(it) }
            appendLine()
        }

        val important = r.tips.filter { it.level != TipLevel.BAIXA }
        if (important.isNotEmpty()) {
            appendLine("*Pontos a verificar*")
            important.forEach { appendLine("- ${it.title}: ${it.text}") }
            appendLine()
        }
        append("Indicadores calculados a partir do OBD2; não substituem o diagnóstico com scanner da marca.")
    }
}
