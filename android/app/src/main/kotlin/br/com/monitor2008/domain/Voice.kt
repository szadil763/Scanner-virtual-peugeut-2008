package br.com.monitor2008.domain

import java.text.Normalizer
import java.util.Locale

/** O que o dono perguntou pelo comando de voz (primeira versão: só consultas, nada muda no app). */
enum class VoiceIntent { CONSUMO, VIAGEM, TEMPERATURA, BATERIA, OLEO, FALHAS, SAUDE, VELOCIDADE, ROTACAO, AJUDA, DESCONHECIDO }

/** Indicador da Saúde fora do normal, para a resposta falada. */
data class VoiceAlert(val title: String, val alert: Boolean)

/** O que o app sabe agora, em números simples (montado a partir da tela). */
data class VoiceSnapshot(
    /** Lendo o adaptador real agora (false = desconectado ou simulação). */
    val connected: Boolean,
    val kmPerLNow: Double? = null,
    val tripAvgKmPerL: Double? = null,
    val tripKm: Double = 0.0,
    val tripMinutes: Int = 0,
    val coolantC: Double? = null,
    val batteryV: Double? = null,
    val speedKmh: Double? = null,
    val rpm: Double? = null,
    /** Null = troca de óleo ainda não informada. */
    val oilKmLeft: Double? = null,
    val oilDaysLeft: Long? = null,
    /** Indicadores em atenção ou alerta. */
    val alerts: List<VoiceAlert> = emptyList(),
    /** Null = falhas ainda não lidas. */
    val dtcs: List<String>? = null,
)

/**
 * Entende a frase reconhecida e monta a resposta falada. Regras fixas por palavras-chave, sem internet;
 * números com vírgula (o leitor de voz em português lê "doze vírgula três").
 */
object VoiceCommands {

    /** Ordem importa: o primeiro grupo que casar vence ("luz de injeção" é falha, não saúde). */
    private val keywords: List<Pair<VoiceIntent, List<String>>> = listOf(
        VoiceIntent.AJUDA to listOf("ajuda", "o que voce faz", "o que posso", "comandos", "o que eu posso"),
        VoiceIntent.FALHAS to listOf("falha", "codigo", "luz de injecao", "injecao eletronica", "erro"),
        VoiceIntent.OLEO to listOf("oleo", "troca"),
        VoiceIntent.TEMPERATURA to listOf("temperatura", "aquecendo", "esquentando", "quente", "agua do motor", "arrefecimento"),
        VoiceIntent.BATERIA to listOf("bateria", "voltagem", "tensao", "alternador"),
        VoiceIntent.CONSUMO to listOf("consumo", "por litro", "gastando", "fazendo quantos", "media", "economia"),
        VoiceIntent.VIAGEM to listOf("viagem", "trajeto", "rodei", "quantos quilometros", "quanto andei", "distancia"),
        VoiceIntent.VELOCIDADE to listOf("velocidade", "quantos por hora"),
        VoiceIntent.ROTACAO to listOf("rotacao", "giro", "rpm"),
        VoiceIntent.SAUDE to listOf("saude", "como esta o carro", "como ta o carro", "alerta", "problema", "tudo bem com o carro", "estado do carro"),
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun intent(text: String): VoiceIntent {
        val t = normalize(text)
        if (t.isEmpty()) return VoiceIntent.DESCONHECIDO
        return keywords.firstOrNull { (_, words) -> words.any { t.contains(it) } }?.first ?: VoiceIntent.DESCONHECIDO
    }

    /** O reconhecedor devolve várias versões da frase: vale a primeira que o app entende. */
    fun intent(candidates: List<String>): Pair<VoiceIntent, String> {
        candidates.forEach { c -> intent(c).let { if (it != VoiceIntent.DESCONHECIDO) return it to c } }
        return VoiceIntent.DESCONHECIDO to (candidates.firstOrNull() ?: "")
    }

    fun answer(intent: VoiceIntent, s: VoiceSnapshot): String = when (intent) {
        VoiceIntent.CONSUMO -> consumo(s)
        VoiceIntent.VIAGEM -> viagem(s)
        VoiceIntent.TEMPERATURA -> live(s, s.coolantC) { c ->
            "Motor a ${n(c, 0)} graus." + when {
                c >= 110 -> " Está muito quente: pare em local seguro e desligue o motor."
                c >= 105 -> " Está acima do normal. Fique de olho no ponteiro."
                c < 70 -> " Ainda está esquentando."
                else -> " Temperatura normal."
            }
        }
        VoiceIntent.BATERIA -> live(s, s.batteryV) { v ->
            val running = (s.rpm ?: 0.0) > 400
            "Bateria com ${n(v, 1)} volts." + when {
                running && v < 13.2 -> " Com o motor ligado está baixa: o alternador pode não estar carregando."
                running && v > 15.0 -> " Está alta: vale verificar o regulador do alternador."
                !running && v < 12.2 -> " Com o motor desligado está baixa."
                else -> " Normal."
            }
        }
        VoiceIntent.OLEO -> oleo(s)
        VoiceIntent.FALHAS -> when {
            s.dtcs == null -> "As falhas ainda não foram lidas. Conecte ao carro e veja na aba Diagnóstico."
            s.dtcs.isEmpty() -> "Nenhuma falha gravada na central."
            else -> (if (s.dtcs.size == 1) "Uma falha gravada: " else "${s.dtcs.size} falhas gravadas: ") +
                s.dtcs.take(4).joinToString(", ") { spell(it) } + ". Veja a explicação na aba Diagnóstico."
        }
        VoiceIntent.SAUDE -> saude(s)
        VoiceIntent.VELOCIDADE -> live(s, s.speedKmh) { "${n(it, 0)} quilômetros por hora." }
        VoiceIntent.ROTACAO -> live(s, s.rpm) { "${n(it, 0)} rotações por minuto." }
        VoiceIntent.AJUDA -> HELP
        VoiceIntent.DESCONHECIDO -> "Não entendi. $HELP"
    }

    const val HELP = "Pergunte, por exemplo: qual o consumo, como está a viagem, temperatura do motor, bateria, " +
        "troca de óleo, tem alguma falha, ou como está o carro."

    private fun live(s: VoiceSnapshot, v: Double?, text: (Double) -> String): String = when {
        !s.connected -> "O carro não está conectado agora."
        v == null -> "O carro não está informando esse dado agora."
        else -> text(v)
    }

    private fun consumo(s: VoiceSnapshot): String {
        val parts = mutableListOf<String>()
        s.tripAvgKmPerL?.let { parts += "Média de ${n(it, 1)} quilômetros por litro" + if (s.tripKm >= 0.1) " em ${n(s.tripKm, 0)} quilômetros." else "." }
        if (s.connected && (s.speedKmh ?: 0.0) > 3) s.kmPerLNow?.let { parts += "Agora, ${n(it, 1)}." }
        return if (parts.isEmpty()) "Ainda não há consumo calculado. Ande alguns quilômetros com o carro conectado." else parts.joinToString(" ")
    }

    private fun viagem(s: VoiceSnapshot): String {
        if (s.tripKm < 0.1) return "O trajeto ainda não tem quilômetros registrados."
        val avg = s.tripAvgKmPerL?.let { ", média de ${n(it, 1)} quilômetros por litro" } ?: ""
        val time = if (s.tripMinutes > 0) " em ${duration(s.tripMinutes)}" else ""
        return "Trajeto com ${n(s.tripKm, 0)} quilômetros$time$avg."
    }

    private fun oleo(s: VoiceSnapshot): String {
        val km = s.oilKmLeft ?: return "A última troca de óleo ainda não foi informada. Informe na aba Manutenção, com o carro parado."
        val days = s.oilDaysLeft
        return when {
            km <= 0 || (days != null && days <= 0) -> "A troca de óleo está vencida. Agende a troca."
            else -> "Faltam ${n(km, 0)} quilômetros" + (days?.let { " ou $it dias" } ?: "") + " para a troca de óleo." +
                if (km < 1000) " Já dá para agendar." else ""
        }
    }

    private fun saude(s: VoiceSnapshot): String {
        val alerts = s.alerts.filter { it.alert }
        val attention = s.alerts.filter { !it.alert }
        val dtc = s.dtcs?.takeIf { it.isNotEmpty() }?.let { " A central tem ${if (it.size == 1) "uma falha gravada" else "${it.size} falhas gravadas"}." } ?: ""
        if (alerts.isEmpty() && attention.isEmpty()) return "Nenhum indicador fora do normal.$dtc"
        val parts = mutableListOf<String>()
        if (alerts.isNotEmpty()) parts += count(alerts.size, "alerta", "alertas") + ": " + alerts.joinToString(", ") { it.title } + "."
        if (attention.isNotEmpty()) parts += count(attention.size, "ponto de atenção", "pontos de atenção") + ": " + attention.joinToString(", ") { it.title } + "."
        return parts.joinToString(" ") + dtc + " Veja os detalhes na aba Saúde."
    }

    private fun count(k: Int, one: String, many: String) = if (k == 1) "Um $one" else "$k $many"

    private fun duration(min: Int): String = when {
        min < 60 -> "$min minutos"
        min % 60 == 0 -> "${min / 60} ${if (min / 60 == 1) "hora" else "horas"}"
        else -> "${min / 60} ${if (min / 60 == 1) "hora" else "horas"} e ${min % 60} minutos"
    }

    /** Número com vírgula, sem casas quando [d] = 0. */
    fun n(v: Double, d: Int): String = String.format(Locale("pt", "BR"), "%.${d}f", v)

    /** "P0420" -> "P 0 4 2 0": o leitor fala letra e dígitos um a um. */
    fun spell(code: String): String = code.toCharArray().joinToString(" ")
}
