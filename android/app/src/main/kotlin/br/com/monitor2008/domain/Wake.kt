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

    private val partialField = Regex("\"partial\"\\s*:\\s*\"([^\"]*)\"")

    /**
     * Resultado parcial ({"partial" : "monitor"}): chega enquanto a pessoa ainda fala, sem esperar a
     * pausa. Usado para acionar mais rápido; só vale se a palavra for "monitor" sozinha.
     */
    fun isWakePartial(json: String?): Boolean {
        val text = json?.let { partialField.find(it)?.groupValues?.get(1) } ?: return false
        return VoiceCommands.normalize(text) == WORD
    }

    /** Depois que o carro desliga (ou do último uso da voz), quanto tempo ainda escuta "Monitor". */
    const val IDLE_MS = 15 * 60_000L

    /**
     * Como a Alexa do dono: com o carro conectado escuta sempre; desligado, escuta mais [IDLE_MS] desde
     * a última atividade (carro conectado, uso da voz ou abertura do app) e depois desliga sozinho.
     * Antes o celular seguia escutando fora do carro e acionava até no WhatsApp (relato da 0.1.56).
     */
    fun awake(nowMs: Long, carConnected: Boolean, lastActivityMs: Long, idleMs: Long = IDLE_MS): Boolean =
        carConnected || nowMs - lastActivityMs < idleMs

    /** Escuta a palavra só quando está tudo pronto e o microfone está livre. */
    fun shouldListen(enabled: Boolean, modelReady: Boolean, micAllowed: Boolean, assistantBusy: Boolean, inCall: Boolean): Boolean =
        enabled && modelReady && micAllowed && !assistantBusy && !inCall
}
