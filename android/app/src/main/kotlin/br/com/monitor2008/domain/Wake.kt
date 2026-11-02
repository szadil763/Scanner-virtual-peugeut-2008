package br.com.monitor2008.domain

/**
 * Palavra para chamar o comando de voz sem tocar na tela ("Monitor"). O reconhecimento offline
 * (Vosk, só com as palavras "monitor" e "desconhecido") devolve JSON; aqui só as regras.
 */
object WakeRules {
    const val WORD = "monitor"

    /** Lista de palavras do reconhecedor offline: só "monitor"; o resto vira "[unk]". */
    const val GRAMMAR = "[\"monitor\", \"[unk]\"]"

    private val textField = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"")

    /** Resultado final do reconhecedor ({"text" : "monitor"}) tem a palavra? */
    fun isWake(json: String?): Boolean {
        val text = json?.let { textField.find(it)?.groupValues?.get(1) } ?: return false
        return VoiceCommands.normalize(text).split(' ').any { it == WORD }
    }

    /** Escuta a palavra só quando está tudo pronto e o microfone está livre. */
    fun shouldListen(enabled: Boolean, modelReady: Boolean, micAllowed: Boolean, assistantBusy: Boolean, inCall: Boolean): Boolean =
        enabled && modelReady && micAllowed && !assistantBusy && !inCall
}
