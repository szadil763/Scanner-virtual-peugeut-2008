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

    /** O mapa só volta se foi o último app aberto e há menos de 12 h (depois disso a rota já é velha). */
    const val MAP_MAX_AGE_MS = 12 * 3_600_000L
    fun shouldResumeMap(lastFront: String?, lastFrontAtMs: Long, nowMs: Long): Boolean =
        lastFront == "MAPA" && lastFrontAtMs > 0 && nowMs - lastFrontAtMs in 0..MAP_MAX_AGE_MS

    /** Carro parado a até 300 m do destino = a viagem terminou: não retoma a rota. Sem posição, segue a rota. */
    const val ARRIVED_M = 300.0
    fun arrived(distanceToDestinationM: Double?): Boolean =
        distanceToDestinationM != null && distanceToDestinationM <= ARRIVED_M

    /** A tela acendeu depois de tanto tempo sem sinal de vida: o carro ficou desligado. */
    const val WAKE_GAP_MS = 3 * 60_000L
    fun wokeFromOff(aliveAtMs: Long, nowMs: Long): Boolean = aliveAtMs > 0 && nowMs - aliveAtMs > WAKE_GAP_MS
}
