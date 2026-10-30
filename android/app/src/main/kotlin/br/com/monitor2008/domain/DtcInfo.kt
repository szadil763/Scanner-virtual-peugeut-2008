package br.com.monitor2008.domain

/**
 * O que os códigos de falha costumam indicar nos motores flex (escrito a partir da linha PSA: Peugeot e Citroën; vale para a maioria dos carros). Usado nas dicas da
 * Saúde e na tela Diagnóstico. Não é diagnóstico: é por onde o mecânico costuma começar.
 */
object DtcInfo {

    private val HINTS = mapOf(
        "P0171" to "mistura pobre: entrada falsa de ar (mangueiras do respiro do cárter, junta do coletor), bicos ou bomba fraca",
        "P0172" to "mistura rica: bico pingando, sonda lambda ou pressão de combustível alta",
        "P0300" to "falha de ignição em vários cilindros: velas, bobinas ou combustível ruim",
        "P0301" to "falha de ignição no cilindro 1: bobina ou vela (com bobinas individuais, como no 2008 1.6, troque-a de lugar para confirmar)",
        "P0302" to "falha de ignição no cilindro 2: bobina ou vela do cilindro 2",
        "P0303" to "falha de ignição no cilindro 3: bobina ou vela do cilindro 3",
        "P0304" to "falha de ignição no cilindro 4: bobina ou vela do cilindro 4",
        "P0128" to "motor esquentando devagar: válvula termostática travada aberta",
        "P0130" to "sonda lambda antes do catalisador", "P0133" to "sonda lambda lenta (envelhecida)",
        "P0134" to "sonda lambda sem sinal", "P0136" to "sonda lambda depois do catalisador",
        "P0420" to "eficiência do catalisador baixa",
        "P0115" to "sensor de temperatura do motor", "P0117" to "sensor de temperatura do motor",
        "P0118" to "sensor de temperatura do motor",
        "P0106" to "sensor de pressão do coletor (MAP): conector, mangueira ou o sensor",
        "P0120" to "sensor de posição da borboleta", "P0122" to "sensor de posição da borboleta",
        "P0335" to "sensor de rotação do motor", "P0340" to "sensor de fase (comando)",
        "P0443" to "válvula do cânister (vapores de combustível)",
        "P0480" to "comando da ventoinha do radiador: verifique a ventoinha antes de o motor esquentar",
        "P0560" to "tensão do sistema elétrico: bateria ou alternador",
        "P0505" to "controle de marcha lenta: limpeza do corpo de borboleta",
        "P0500" to "sensor de velocidade",
        "P0700" to "o câmbio automático registrou falha: leve a uma oficina especializada em câmbio automático",
    )

    /** Explicação do código, se for um dos comuns; null se não houver. */
    fun hint(code: String): String? = HINTS[code.uppercase()]

    /** Sempre devolve algo: a explicação conhecida ou, pelo menos, o sistema e se é código padrão ou do fabricante. */
    fun describe(code: String): String {
        hint(code)?.let { return it.replaceFirstChar { c -> c.uppercase() } }
        val c = code.uppercase()
        val system = when (c.firstOrNull()) {
            'P' -> "motor ou câmbio"
            'C' -> "chassi (freios, suspensão)"
            'B' -> "carroceria (airbag, conforto)"
            'U' -> "comunicação entre módulos"
            else -> return "Código não reconhecido"
        }
        val kind = if (c.getOrNull(1) == '1' || c.getOrNull(1) == '3') "código do fabricante (específico da marca)" else "código padrão"
        return "Falha de $system, $kind: consulte um mecânico com scanner da marca"
    }
}
