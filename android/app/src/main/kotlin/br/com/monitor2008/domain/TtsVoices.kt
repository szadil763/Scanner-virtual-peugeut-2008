package br.com.monitor2008.domain

/** Voz em português que o leitor de voz do aparelho oferece (dados de android.speech.tts.Voice). */
data class TtsVoiceInfo(
    val name: String,
    /** 100 (muito baixa) a 500 (muito alta), como Voice.QUALITY_*. */
    val quality: Int,
    val network: Boolean,
    /** Ainda não baixada no aparelho (o leitor baixa ao usar, com internet). */
    val notInstalled: Boolean = false,
)

/** Voz para a tela de Ajustes: nome amigável e descrição. */
data class TtsVoiceOption(val name: String, val label: String, val detail: String)

/**
 * Ordem e nomes das vozes. Os nomes técnicos ("pt-br-x-afs-local") não dizem nada ao dono:
 * vira "Voz 1", "Voz 2"... na ordem de qualidade, com "alta qualidade" e "usa internet" na descrição.
 */
object TtsVoices {

    /** Melhores primeiro: instalada, sem internet, maior qualidade; empate pelo nome (ordem estável). */
    fun rank(voices: List<TtsVoiceInfo>): List<TtsVoiceInfo> =
        voices.distinctBy { it.name }.sortedWith(
            compareBy<TtsVoiceInfo> { it.notInstalled }.thenBy { it.network }.thenByDescending { it.quality }.thenBy { it.name },
        )

    /** A voz que o app usa sem escolha do dono: a primeira de [rank]. */
    fun best(voices: List<TtsVoiceInfo>): TtsVoiceInfo? = rank(voices).firstOrNull()

    fun options(voices: List<TtsVoiceInfo>): List<TtsVoiceOption> = rank(voices).mapIndexed { i, v ->
        val parts = listOfNotNull(
            when {
                v.quality >= 400 -> "alta qualidade"
                v.quality >= 300 -> "qualidade normal"
                else -> "qualidade baixa"
            },
            if (v.network) "usa internet (sem sinal, volta para a voz do aparelho)" else "funciona sem internet",
            if (v.notInstalled) "baixa na primeira vez" else null,
        )
        TtsVoiceOption(v.name, "Voz ${i + 1}", parts.joinToString(" · "))
    }

    /** Velocidade e tom: passos de 0,1 entre 0,6 e 1,6 (1,0 = normal). */
    fun step(value: Float, up: Boolean): Float {
        val next = Math.round((value + if (up) 0.1f else -0.1f) * 10) / 10f
        return next.coerceIn(0.6f, 1.6f)
    }
}
