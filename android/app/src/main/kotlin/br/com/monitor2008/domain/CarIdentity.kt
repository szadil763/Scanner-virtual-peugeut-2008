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
     * O multimídia diz um carro, mas o chassi ou o adaptador conectado é de OUTRO (ex.: o celular pegou o adaptador do
     * carro estacionado ao lado). Os dados lidos não são do carro em que se está: o app não troca de carro,
     * desconecta e procura o adaptador certo.
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
 * Quando o multimídia diz um carro e o chassi (ou o adaptador conhecido) é de outro, é CONFLITO: o adaptador conectado
 * é o errado. O app não mistura os dados (continua no carro atual) e procura o adaptador certo. Quando não há conflito,
 * o multimídia ensina ao chassi e ao adaptador.
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
            if (vinOther || adapterOther) return CarDecision(current, CarSource.CONFLICT, otherCar = if (vinOther) vinCar else adapterCar)
            return CarDecision(painel, CarSource.PAINEL, confirmVin = vinCar != null, learnAdapter = true)
        }
        if (vinCar != null) return CarDecision(vinCar, if (vinConfirmed) CarSource.VIN_CONFIRMED else CarSource.VIN_GUESS)
        if (adapterCar != null) return CarDecision(adapterCar, CarSource.ADAPTER)
        return CarDecision(current, CarSource.NONE)
    }
}
