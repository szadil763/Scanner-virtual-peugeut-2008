package br.com.monitor2008.domain

/**
 * Dicas de manutenção preventiva para o Peugeot 2008 1.6 16V flex automático (motor EC5), a partir
 * dos dados coletados: viagens, indicadores da Saúde, falhas, itens por km/data e consumo.
 *
 * Regras fixas, sem internet: a mesma situação gera sempre a mesma dica. Conhecimento do modelo vem
 * de problemas conhecidos da linha PSA; onde há dúvida, a dica manda confirmar no manual.
 */
enum class TipLevel { ALTA, MEDIA, BAIXA }

data class Tip(val level: TipLevel, val title: String, val text: String)

data class AdvisorInput(
    val trips: List<TripHealth>,
    val health: List<HealthItem>,
    /** Itens por km/data e a situação (null = última troca não informada). */
    val maint: List<Pair<MaintItem, ItemStatus?>>,
    val tripKmPerL: Double?,
    val tripKm: Double,
    /** Fração de etanol no tanque (0,30 = gasolina E30; 0,95 = etanol). */
    val ethanolFraction: Double,
    /** Já houve abastecimento de tanque cheio a tanque cheio (consumo calibrado). */
    val calibrated: Boolean,
    val storedDtcs: List<String> = emptyList(),
    val pendingDtcs: List<String> = emptyList(),
)

object Advisor {

    fun tips(input: AdvisorInput): List<Tip> {
        val out = mutableListOf<Tip>()
        dataAmount(input, out)
        codes(input, out)
        healthItems(input, out)
        usage(input, out)
        consumption(input, out)
        maintenance(input, out)
        modelChecks(out)
        return out.sortedBy { it.level.ordinal }
    }

    // ---------- quantidade de dados ----------

    private fun dataAmount(input: AdvisorInput, out: MutableList<Tip>) {
        val n = input.trips.size
        if (n < 5) {
            out += Tip(
                TipLevel.BAIXA, "Poucos dados ainda",
                "Só $n viagem(ns) gravada(s). As dicas ficam mais precisas depois de uma semana de uso com o adaptador " +
                    "conectado; a comparação com o normal do seu carro começa na 6ª viagem.",
            )
        }
    }

    // ---------- códigos de falha ----------

    private fun codes(input: AdvisorInput, out: MutableList<Tip>) {
        input.storedDtcs.forEach { c ->
            out += Tip(TipLevel.ALTA, "Falha armazenada $c", (DtcInfo.hint(c)?.let { "Costuma indicar $it. " } ?: "") +
                "Anote o código e leve a um mecânico; não apague a falha antes do diagnóstico.")
        }
        input.pendingDtcs.forEach { c ->
            out += Tip(TipLevel.MEDIA, "Falha pendente $c", (DtcInfo.hint(c)?.let { "Costuma indicar $it. " } ?: "") +
                "A central viu o problema mas ainda não acendeu a luz: vale verificar antes que vire falha.")
        }
    }

    // ---------- indicadores da Saúde, com o contexto do modelo ----------

    /** O que verificar primeiro no 2008 1.6 EC5 quando cada indicador sai do normal. */
    private val MODEL_NOTE = mapOf(
        "mix" to "No motor EC5, comece pelas mangueiras do respiro do cárter (ressecam e racham) e pela junta do coletor de admissão.",
        "o2" to "Sonda envelhecida aumenta o consumo; no flex, use a peça com a especificação original.",
        "cat" to "Antes de trocar o catalisador, elimine falha de ignição e mistura rica, que o danificam.",
        "idle" to "No EC5 as bobinas individuais são um defeito conhecido; junto, verifique velas e limpeza do corpo de borboleta.",
        "tbi" to "A limpeza do corpo de borboleta é simples; depois, a central reaprende a marcha lenta (deixe ligado parado alguns minutos).",
        "map" to "Verifique mangueiras de vácuo, respiro do cárter e a válvula do cânister.",
        "cool" to "Na linha PSA 1.6 é comum a carcaça plástica da válvula termostática vazar: confira o nível do reservatório com o motor frio e procure manchas rosadas.",
        "bat" to "Se o carro fica parado vários dias, tire o adaptador da tomada OBD: ele consome a bateria.",
        "dtc" to "Veja os códigos na aba Diagnóstico.",
    )

    private fun healthItems(input: AdvisorInput, out: MutableList<Tip>) {
        input.health.forEach { h ->
            val level = when (h.status) {
                HealthStatus.ALERTA -> TipLevel.ALTA
                HealthStatus.ATENCAO -> TipLevel.MEDIA
                else -> return@forEach
            }
            if (h.id == "dtc" && (input.storedDtcs.isNotEmpty() || input.pendingDtcs.isNotEmpty())) return@forEach // já virou dica por código
            out += Tip(level, "${h.title}: ${h.value}", h.advice + (MODEL_NOTE[h.id]?.let { " $it" } ?: ""))
        }
    }

    // ---------- padrão de uso ----------

    private fun usage(input: AdvisorInput, out: MutableList<Tip>) {
        val t = input.trips.takeLast(20)
        if (t.size < 5) return
        val short = t.count { it.runningMin < 10 || (it.coolantMaxC ?: 99.0) < 80 }
        if (short * 2 >= t.size) {
            out += Tip(
                TipLevel.MEDIA, "Uso severo: muitas viagens curtas",
                "$short de ${t.size} viagens terminaram antes de o motor esquentar. Isso acumula umidade e combustível no óleo: " +
                    "use o plano de \"uso severo\" (troca de óleo em metade do intervalo, em geral 5.000 km ou 6 meses; confirme no manual) " +
                    "e faça de vez em quando um trajeto de 20 minutos ou mais.",
            )
        }
        val idleHeavy = t.count { trip -> trip.km / trip.runningMin.coerceAtLeast(1.0) * 60 < 15 }
        if (idleHeavy * 2 >= t.size) {
            out += Tip(
                TipLevel.BAIXA, "Muito trânsito parado",
                "A média das viagens ficou abaixo de 15 km/h. No câmbio automático o fluido esquenta mais no anda e para: " +
                    "considere a troca do fluido do câmbio antes do prazo normal.",
            )
        }
    }

    // ---------- consumo ----------

    private fun consumption(input: AdvisorInput, out: MutableList<Tip>) {
        val kmL = input.tripKmPerL ?: return
        if (input.tripKm < 50) return
        val etanol = input.ethanolFraction >= 0.9
        val low = if (etanol) 5.5 else 8.0 // abaixo disso, consumo alto para o 2008 1.6 automático na cidade
        if (!input.calibrated) {
            out += Tip(
                TipLevel.BAIXA, "Calibre o consumo",
                "O consumo ainda é estimado. Registre dois abastecimentos de tanque cheio seguidos (aba Consumo) para o app ajustar o cálculo.",
            )
        }
        if (kmL < low) {
            out += Tip(
                TipLevel.MEDIA, "Consumo alto: ${String.format(java.util.Locale("pt", "BR"), "%.1f", kmL)} km/l",
                "Para o 2008 1.6 automático com ${if (etanol) "etanol" else "gasolina"}, abaixo de ${low.toInt()} km/l na média é alto. " +
                    "Confira a calibragem dos pneus, o filtro de ar, as velas e a sonda lambda" +
                    (if (!input.calibrated) "; antes, calibre o consumo com tanque cheio para confirmar." else "."),
            )
        }
    }

    // ---------- itens por km/data ----------

    private fun maintenance(input: AdvisorInput, out: MutableList<Tip>) {
        input.maint.forEach { (item, st) ->
            when (st?.state) {
                MaintState.DUE -> out += Tip(TipLevel.ALTA, "${item.name}: vencido", item.why)
                MaintState.SOON -> out += Tip(TipLevel.MEDIA, "${item.name}: vence em breve", item.why)
                else -> {}
            }
        }
        val unknown = input.maint.filter { it.second == null }.map { it.first.name }
        if (unknown.isNotEmpty()) {
            out += Tip(
                TipLevel.BAIXA, "Informe as últimas trocas",
                "Sem a data da última troca, o app não avisa: ${unknown.joinToString(", ")}. " +
                    "Na aba Manutenção, informe a última troca de cada um (\"Fiz hoje\" ou \"Informar\" com data e km).",
            )
        }
        if (unknown.any { it.startsWith("Correia dentada") }) {
            out += Tip(
                TipLevel.MEDIA, "Correia dentada: confirme quando foi trocada",
                "Item mais crítico do motor: se romper, danifica o motor. Se não souber quando foi trocada, vale verificar já. " +
                    "Troque junto o tensor e a bomba d'água.",
            )
        }
    }

    // ---------- verificações do modelo (sempre) ----------

    private fun modelChecks(out: MutableList<Tip>) {
        out += Tip(
            TipLevel.BAIXA, "Câmbio automático",
            "O câmbio automático do 2008 1.6 de 2016 (4 marchas, família AL4) é sensível ao estado do fluido. Mesmo se constar como vitalício, " +
                "especialistas recomendam trocar o fluido periodicamente (algo entre 30.000 e 60.000 km), só com o fluido especificado. " +
                "Trancos, demora para engatar ou o painel piscando em modo de segurança pedem oficina. Confirme o tipo de câmbio no manual.",
        )
        out += Tip(
            TipLevel.BAIXA, "Verificações periódicas do modelo",
            "A cada revisão: nível e cor do líquido de arrefecimento (atenção à carcaça da válvula termostática), " +
                "coxins do motor (vibração em marcha lenta ao engatar D), mangueiras do respiro do cárter e limpeza do corpo de borboleta.",
        )
    }
}
