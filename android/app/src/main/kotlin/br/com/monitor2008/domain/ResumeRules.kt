package br.com.monitor2008.domain

/**
 * Ao ligar o carro, continuar a música ou a rádio só se ela estava tocando quando o multimídia
 * desligou. O app grava a cada 30 s que está vivo ([aliveAtMs]) e, se algo toca, o que toca
 * ([lastPlayingAtMs]). Tocando até o fim = as duas marcas juntas; pausado antes de desligar =
 * a de "tocando" ficou para trás.
 */
object ResumeRules {
    /** Folga entre as marcas: o intervalo de gravação (30 s) com sobra. */
    const val MAX_GAP_MS = 90_000L

    fun shouldResume(aliveAtMs: Long, lastPlayingAtMs: Long, lastPlayingPkg: String?): Boolean =
        lastPlayingPkg != null && lastPlayingAtMs > 0 && aliveAtMs - lastPlayingAtMs in 0..MAX_GAP_MS
}
