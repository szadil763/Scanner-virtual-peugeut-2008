package br.com.monitor2008.domain

/**
 * Quando o app considera o carro andando. A leitura de velocidade só vale se for recente: se o adaptador
 * parar de responder (motor desligado, Bluetooth caiu), a última velocidade lida não pode travar o app como
 * "andando" para sempre (botões de zerar o trajeto, abastecer e calibrar bloqueados com o carro parado).
 */
object MovingRules {
    /** Leitura mais velha que isso não conta. */
    const val STALE_MS = 10_000L

    /** Acima disso é movimento (abaixo é ruído do sensor). */
    const val MIN_KMH = 0.5

    fun isMoving(speedKmh: Double?, sampleAgeMs: Long): Boolean =
        (speedKmh ?: 0.0) > MIN_KMH && sampleAgeMs in 0..STALE_MS
}
