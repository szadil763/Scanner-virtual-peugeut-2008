package br.com.monitor2008.domain

/**
 * Qual adaptador ler quando há mais de um por perto (dois carros lado a lado, os dois adaptadores sempre ligados na
 * tomada): o do carro em que se está é o que tem o MOTOR LIGADO. Um adaptador cujo carro respondeu "sem rotação" fica
 * marcado como "desligado" por um tempo; o app passa na hora para o outro, em vez de esperar minutos e escolher o mesmo
 * de novo. Só quando todos os adaptadores vistos estão com o carro desligado é que o app espera (poupa a bateria).
 */
class AdapterSweep(private val memoryMs: Long = DEFAULT_MEMORY_MS) {
    private val off = HashMap<String, Long>()

    /** Quantas trocas imediatas de adaptador já foram feitas nesta rodada. */
    var quickSwitches = 0

    @Synchronized fun markOff(address: String, nowMs: Long) { off[address.uppercase()] = nowMs }

    @Synchronized fun isOff(address: String, nowMs: Long): Boolean =
        off[address.uppercase()]?.let { nowMs - it < memoryMs } == true

    @Synchronized fun anyOff(nowMs: Long): Boolean = off.values.any { nowMs - it < memoryMs }

    /** Passa para outro adaptador já (até [MAX_QUICK_SWITCHES] vezes por rodada); depois disso, espera. */
    @Synchronized fun shouldTryAnotherNow(): Boolean = quickSwitches < MAX_QUICK_SWITCHES

    @Synchronized fun clear() { off.clear(); quickSwitches = 0 }

    companion object {
        const val DEFAULT_MEMORY_MS = 3 * 60_000L
        const val MAX_QUICK_SWITCHES = 3
    }
}
