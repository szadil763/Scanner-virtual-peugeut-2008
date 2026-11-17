package br.com.monitor2008.domain

/** De onde veio a decisão sobre qual é o carro. */
enum class CarSource {
    /** O multimídia do carro (escolha fixa do dono) disse qual é. Sempre vence. */
    PAINEL,
    /** Chassi já confirmado (pelo multimídia ou pelo dono). */
    VIN_CONFIRMED,
    /** Chassi com palpite pela fábrica ou carro novo sem perfil (ainda não confirmado). */
    VIN_GUESS,
    /** Só o endereço do adaptador. */
    ADAPTER,
    /** Nenhuma pista: mantém o carro atual. */
    NONE,
}

data class CarDecision(
    val carId: String,
    val source: CarSource,
    /** Gravar (e confirmar) que este chassi é deste carro. */
    val confirmVin: Boolean = false,
    /** Gravar que este adaptador é deste carro (por cima do que estava gravado). */
    val learnAdapter: Boolean = false,
    /** O chassi ou o adaptador estava gravado como deste outro carro; o multimídia corrigiu. */
    val correctedFrom: String? = null,
)

/**
 * Qual é o carro.
 *
 * O multimídia é a pista que manda: ele fica preso no carro e só liga com a ignição daquele carro, e o dono escolheu
 * o carro dele uma vez. Quando ele responde, vale o que ele diz, sempre, e ele corrige o que estiver gravado errado
 * para o chassi e para o adaptador (adaptadores baratos repetem o endereço, então um cadastro de adaptador pode estar
 * errado; antes, esse cadastro errado brigava com o multimídia e o celular ficava conectando e desconectando).
 *
 * Sem o multimídia (celular sozinho): chassi > adaptador > mantém o carro atual.
 */
object CarIdentity {
    fun decide(
        painel: String?,
        vinCar: String?,
        vinConfirmed: Boolean,
        adapterCar: String?,
        current: String,
    ): CarDecision {
        if (painel != null) {
            val wrong = vinCar?.takeIf { it != painel && !Vehicles.isCustomKey(it) } ?: adapterCar?.takeIf { it != painel }
            return CarDecision(painel, CarSource.PAINEL, confirmVin = vinCar != null, learnAdapter = true, correctedFrom = wrong)
        }
        if (vinCar != null) return CarDecision(vinCar, if (vinConfirmed) CarSource.VIN_CONFIRMED else CarSource.VIN_GUESS)
        if (adapterCar != null) return CarDecision(adapterCar, CarSource.ADAPTER)
        return CarDecision(current, CarSource.NONE)
    }
}
