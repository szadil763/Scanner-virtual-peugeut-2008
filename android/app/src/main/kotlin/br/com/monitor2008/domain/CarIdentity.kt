package br.com.monitor2008.domain

/** De onde veio a decisão sobre qual é o carro. */
enum class CarSource {
    /** O multimídia do carro (escolha fixa do dono) disse qual é. */
    PAINEL,
    /** Chassi já confirmado (pelo multimídia ou pelo dono). */
    VIN_CONFIRMED,
    /** Chassi com palpite pela fábrica ou carro novo sem perfil (ainda não confirmado). */
    VIN_GUESS,
    /** Só o endereço do adaptador. */
    ADAPTER,
    /** O multimídia e um chassi confirmado discordam: vale o chassi e o app avisa. */
    CONFLICT,
    /** Nenhuma pista: mantém o carro atual. */
    NONE,
}

data class CarDecision(
    val carId: String,
    val source: CarSource,
    /** Gravar (e confirmar) que este chassi é deste carro. */
    val confirmVin: Boolean = false,
    /** Gravar que este adaptador é deste carro. */
    val learnAdapter: Boolean = false,
)

/**
 * Qual é o carro, juntando três pistas, da mais forte para a mais fraca:
 * 1. o multimídia do carro (escolha fixa do dono, mandada ao celular);
 * 2. o chassi lido no OBD (único de cada carro), confirmado ou só um palpite;
 * 3. o endereço do adaptador (adaptadores baratos repetem o endereço, por isso o mais fraco).
 * Quando o multimídia diz um carro e um chassi JÁ CONFIRMADO diz outro, vale o chassi (é o carro que está sendo lido)
 * e o app avisa. Quando o multimídia fala e o chassi/adaptador ainda não eram confirmados, eles aprendem com o multimídia.
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
            if (vinCar != null && vinConfirmed && vinCar != painel) return CarDecision(vinCar, CarSource.CONFLICT)
            return CarDecision(painel, CarSource.PAINEL, confirmVin = vinCar != null, learnAdapter = true)
        }
        if (vinCar != null) return CarDecision(vinCar, if (vinConfirmed) CarSource.VIN_CONFIRMED else CarSource.VIN_GUESS)
        if (adapterCar != null) return CarDecision(adapterCar, CarSource.ADAPTER)
        return CarDecision(current, CarSource.NONE)
    }
}
