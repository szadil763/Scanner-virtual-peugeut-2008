package br.com.monitor2008.domain

/**
 * Perfil do carro: o que muda de um modelo para outro nas contas e nas dicas. O que o carro responde
 * pela tomada OBD não fica aqui: o app pergunta ao carro (PIDs suportados) em qualquer modelo.
 *
 * Cada carro tem o próprio app instalado no multimídia, com os próprios dados; o perfil é escolhido
 * uma vez (Ajustes). Para um modelo novo, basta acrescentar um perfil em [Vehicles].
 */
data class Vehicle(
    val id: String,
    /** "Peugeot 2008". */
    val name: String,
    /** "1.6 16V flex (EC5)". */
    val engine: String,
    /** "automático de 4 marchas (família AL4)". */
    val gearbox: String,
    /** Marca, para "confirme no plano de revisões da ...". */
    val brand: String,
    /** Cilindrada em litros, usada no consumo pelo método MAP. */
    val displacementL: Double,
    /** Abaixo disso (km/l na média) o consumo é alto na cidade: gasolina e etanol. */
    val highConsumptionGasoline: Double,
    val highConsumptionEthanol: Double,
    /** O que verificar primeiro quando cada indicador da Saúde sai do normal (id do indicador -> texto). */
    val notes: Map<String, String>,
    /** Dica fixa sobre o câmbio. */
    val gearboxTip: String,
    /** Dica fixa das verificações a cada revisão. */
    val periodicTip: String,
    /** Correia dentada (troca por km); false = corrente de comando, que não tem troca programada. */
    val timingBelt: Boolean = true,
) {
    /** "Peugeot 2008 1.6 16V flex (EC5)". */
    val fullName: String get() = "$name $engine"
}

object Vehicles {
    private val common = mapOf(
        "o2" to "Sonda envelhecida aumenta o consumo; no flex, use a peça com a especificação original.",
        "cat" to "Antes de trocar o catalisador, elimine falha de ignição e mistura rica, que o danificam.",
        "tbi" to "A limpeza do corpo de borboleta é simples; depois, a central reaprende a marcha lenta (deixe ligado parado alguns minutos).",
        "map" to "Verifique mangueiras de vácuo, respiro do cárter e a válvula do cânister.",
        "bat" to "Se o carro fica parado vários dias, tire o adaptador da tomada OBD: ele consome a bateria.",
        "dtc" to "Veja os códigos na aba Diagnóstico.",
    )

    val PEUGEOT_2008 = Vehicle(
        id = "peugeot-2008-16",
        name = "Peugeot 2008",
        engine = "1.6 16V flex (EC5)",
        gearbox = "automático de 4 marchas (família AL4)",
        brand = "Peugeot",
        displacementL = 1.598,
        highConsumptionGasoline = 8.0,
        highConsumptionEthanol = 5.5,
        notes = common + mapOf(
            "mix" to "No motor EC5, comece pelas mangueiras do respiro do cárter (ressecam e racham) e pela junta do coletor de admissão.",
            "idle" to "No EC5 as bobinas individuais são um defeito conhecido; junto, verifique velas e limpeza do corpo de borboleta.",
            "cool" to "Na linha PSA 1.6 é comum a carcaça plástica da válvula termostática vazar: confira o nível do reservatório com o motor frio e procure manchas rosadas.",
        ),
        gearboxTip = "O câmbio automático do 2008 1.6 de 2016 (4 marchas, família AL4) é sensível ao estado do fluido. Mesmo se constar como vitalício, " +
            "especialistas recomendam trocar o fluido periodicamente (algo entre 30.000 e 60.000 km), só com o fluido especificado. " +
            "Trancos, demora para engatar ou o painel piscando em modo de segurança pedem oficina. Confirme o tipo de câmbio no manual.",
        periodicTip = "A cada revisão: nível e cor do líquido de arrefecimento (atenção à carcaça da válvula termostática), " +
            "coxins do motor (vibração em marcha lenta ao engatar D), mangueiras do respiro do cárter e limpeza do corpo de borboleta.",
    )

    val C4_LOUNGE_20 = Vehicle(
        id = "citroen-c4-lounge-20",
        name = "Citroën C4 Lounge",
        engine = "2.0 16V flex (EW10)",
        gearbox = "automático",
        brand = "Citroën",
        displacementL = 1.997,
        highConsumptionGasoline = 7.0,
        highConsumptionEthanol = 5.0,
        notes = common + mapOf(
            "mix" to "No motor EW10 2.0, comece pelas mangueiras do respiro do cárter, pela junta do coletor de admissão e pela válvula do cânister.",
            "idle" to "No EW10 2.0 a bobina de ignição é um bloco único para os 4 cilindros e costuma falhar com a idade; junto, verifique velas e a limpeza do corpo de borboleta motorizado.",
            "cool" to "Na linha PSA (EW10 inclusive) a carcaça plástica da válvula termostática costuma vazar: confira o nível do reservatório com o motor frio e se a ventoinha do radiador liga com o motor quente.",
        ),
        gearboxTip = "O C4 Lounge 2.0 automático teve câmbio de 4 marchas nos primeiros anos e de 6 marchas depois (confirme o seu no manual). " +
            "Os dois são sensíveis ao estado do fluido: mesmo se constar como vitalício, especialistas recomendam trocar periodicamente " +
            "(algo entre 30.000 e 60.000 km), só com o fluido especificado. Trancos, demora para engatar ou modo de segurança pedem oficina.",
        periodicTip = "A cada revisão: nível e cor do líquido de arrefecimento (atenção à carcaça da válvula termostática e à ventoinha do radiador), " +
            "coxins do motor, bobina de ignição, mangueiras do respiro do cárter e limpeza do corpo de borboleta.",
    )

    /** Carro do irmão do dono (importado do México, ano 2010/2011). */
    val NISSAN_TIIDA_18 = Vehicle(
        id = "nissan-tiida-18",
        name = "Nissan Tiida",
        engine = "1.8 16V flex (MR18DE)",
        gearbox = "manual de 6 marchas ou automático de 4 marchas",
        brand = "Nissan",
        displacementL = 1.798,
        highConsumptionGasoline = 7.5,
        highConsumptionEthanol = 5.2,
        timingBelt = false,
        notes = common + mapOf(
            "mix" to "No MR18DE, comece pelas mangueiras de vácuo, pela válvula do respiro do cárter (PCV) e pela junta do coletor de admissão.",
            "idle" to "No MR18DE cada cilindro tem a sua bobina, e elas costumam falhar com a idade; verifique também velas e a limpeza do corpo de borboleta.",
            "tbi" to "Depois de limpar o corpo de borboleta da Nissan, a oficina precisa fazer o reaprendizado da marcha lenta (\"idle air volume learning\"); sem isso a lenta fica instável.",
            "cool" to "Confira o nível do reservatório com o motor frio e se a ventoinha do radiador liga com o motor quente; mangueiras ressecadas são comuns nessa idade.",
        ),
        gearboxTip = "No Tiida 1.8, se o câmbio for automático (4 marchas), troque o fluido periodicamente (algo entre 40.000 e 60.000 km) só com o fluido Nissan especificado; " +
            "trancos ou demora para engatar pedem oficina. Se for manual, confira o óleo do câmbio e a embreagem (pedal alto ou patinando). Confirme no manual.",
        periodicTip = "A cada revisão: bobinas e velas, corpo de borboleta (com o reaprendizado da marcha lenta), nível do arrefecimento e ventoinha, " +
            "coxins do motor e mangueiras de vácuo. O motor usa corrente de comando: ruído metálico ao ligar frio é sinal para verificar.",
    )

    val all = listOf(PEUGEOT_2008, C4_LOUNGE_20, NISSAN_TIIDA_18)

    fun byId(id: String?): Vehicle? = all.firstOrNull { it.id == id }
}
