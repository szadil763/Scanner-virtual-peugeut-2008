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
    /**
     * O multimídia diz um carro, mas o chassi ou o adaptador conectado é de OUTRO. O adaptador só conecta de verdade
     * quando o motor está ligado, então vale o carro dele; o multimídia pode ser o do carro ao lado, esquecido ligado.
     * O app usa o carro do adaptador e avisa.
     */
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
    /** Em [CarSource.CONFLICT]: de qual carro é o adaptador (ou chassi) conectado. */
    val otherCar: String? = null,
)

/**
 * Qual é o carro, juntando três pistas, da mais forte para a mais fraca:
 * 1. o multimídia do carro (escolha fixa do dono, mandada ao celular);
 * 2. o chassi lido no OBD (único de cada carro), confirmado ou só um palpite;
 * 3. o endereço do adaptador (adaptadores baratos repetem o endereço, por isso o mais fraco).
 * Quando o multimídia diz um carro e o chassi (ou o adaptador conhecido) é de outro, é CONFLITO: vale o carro do
 * adaptador conectado (o que está com o motor ligado) e o app avisa. Sem conflito, o multimídia ensina ao chassi e ao
 * adaptador.
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
            // Carro "sem perfil" (chave vin-...) não conflita: o multimídia é quem o identifica.
            val vinOther = vinCar != null && vinCar != painel && !Vehicles.isCustomKey(vinCar)
            val adapterOther = vinCar == null && adapterCar != null && adapterCar != painel
            if (vinOther || adapterOther) {
                val doAdaptador = if (vinOther) vinCar else adapterCar
                return CarDecision(doAdaptador!!, CarSource.CONFLICT, otherCar = doAdaptador)
            }
            return CarDecision(painel, CarSource.PAINEL, confirmVin = vinCar != null, learnAdapter = true)
        }
        if (vinCar != null) return CarDecision(vinCar, if (vinConfirmed) CarSource.VIN_CONFIRMED else CarSource.VIN_GUESS)
        if (adapterCar != null) return CarDecision(adapterCar, CarSource.ADAPTER)
        return CarDecision(current, CarSource.NONE)
    }
}
