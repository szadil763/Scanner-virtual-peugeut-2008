package br.com.monitor2008.domain

import kotlin.math.min

/**
 * Quanto esperar antes de procurar o adaptador de novo. Antes era fixo: 5 s depois de falhar e 60 s
 * com o carro desligado, a noite inteira (o adaptador fica sempre ligado na tomada OBD). Centenas de
 * conexões seguidas travavam o adaptador barato até tirar e pôr de volta (relato do dono, 04/11) e
 * contrariavam a regra 7 (carro desligado = sem consulta). Agora espaça; ao ligar o carro, a tela do
 * multimídia acende e o app reconecta na hora (MonitorEngine.wakeUp), sem esperar.
 */
object ReconnectRules {
    /** Depois de achar o carro desligado: 1, 2, 5, 10 e depois 15 minutos. */
    fun afterEngineOff(times: Int): Long = listOf(60_000L, 120_000L, 300_000L, 600_000L).getOrElse(times) { 900_000L }

    /** Depois de não achar o adaptador: 5, 10, 20, 40 s e depois 1 minuto. */
    fun afterFailure(times: Int): Long = (5_000L shl min(times, 4)).coerceAtMost(60_000L)

    /** O adaptador conectou mas não respondeu a nenhum comando de configuração (nenhum "OK"). */
    fun mute(initResponses: List<String>): Boolean = initResponses.none { it.contains("OK", ignoreCase = true) || it.contains("ELM", ignoreCase = true) }
}
