package br.com.monitor2008.domain

import java.text.Normalizer
import java.util.Locale

/** O que o dono perguntou pelo comando de voz (primeira versão: só consultas, nada muda no app). */
enum class VoiceIntent { ABRIR, ABASTECER, IR_PARA, TOCAR_RADIO, TOCAR_MUSICA, CONSUMO, VIAGEM, TEMPERATURA, BATERIA, OLEO, FALHAS, SAUDE, VELOCIDADE, ROTACAO, AJUDA, DESCONHECIDO }

/** Telas do app que o comando de voz abre (MAPA abre o app de mapa; MUSICA e RADIO, a aba Mídias nesse modo). */
enum class VoiceNav(val spoken: String) {
    PAINEL("o Painel"), MIDIAS("Mídias"), MUSICA("a Música"), RADIO("o Rádio"), CONSUMO("Consumo"), POSTOS("Postos"),
    SAUDE("Saúde"), MANUTENCAO("Manutenção"), DIAGNOSTICO("Diagnóstico"), AJUSTES("Ajustes"), MAPA("o mapa"),
}

/** Abastecimento falado: o que deu para entender (null = não disse). [liters] já vem calculado do valor, se der. */
data class VoiceRefuel(
    val amount: Double? = null,
    val liters: Double? = null,
    val pricePerLiter: Double? = null,
    val full: Boolean? = null,
    val ethanol: Boolean? = null,
)

/** Pergunta ou pedido entendido. */
/** [play]: "tocar rádio", "ouvir música": além de abrir, começa a tocar. */
data class VoiceCommand(
    val intent: VoiceIntent,
    val nav: VoiceNav? = null,
    val refuel: VoiceRefuel? = null,
    val play: Boolean = false,
    /** Destino (IR_PARA), nome da rádio (TOCAR_RADIO) ou música/artista (TOCAR_MUSICA). */
    val query: String? = null,
    /** App de música pedido ("no Spotify"): pacote; null = o escolhido em Ajustes. */
    val app: String? = null,
)

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
    /** Carro andando: não abre o registro de abastecimento. */
    val moving: Boolean = false,
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

    // ---------- Abrir telas e registrar abastecimento ----------

    private val navVerbs = listOf("abrir", "abre", "abra", "ir para", "ir pra", "vai para", "vai pra", "va para", "va pra",
        "mostrar", "mostra", "mostre", "mudar para", "mudar pra", "muda para", "muda pra", "trocar para", "trocar pra",
        "troca para", "troca pra", "entrar", "entra", "voltar para", "voltar pra", "volta para", "volta pra", "tela", "aba")
    private val playVerbs = listOf("tocar", "toca", "toque", "ligar", "liga", "ligue", "ouvir", "colocar", "coloca", "bota", "botar")

    /** Ordem importa: "mídias" antes de "música" não importa, mas "rádio" e "música" antes de "mídias" sim. */
    private val navTargets: List<Pair<VoiceNav, List<String>>> = listOf(
        VoiceNav.MAPA to listOf("mapa", "maps", "navegacao", "navegador", "gps", "waze"),
        VoiceNav.RADIO to listOf("radio"),
        VoiceNav.MUSICA to listOf("musica", "spotify"),
        VoiceNav.MIDIAS to listOf("midia"),
        VoiceNav.PAINEL to listOf("painel", "inicio", "tela principal"),
        VoiceNav.CONSUMO to listOf("consumo"),
        VoiceNav.POSTOS to listOf("posto"),
        VoiceNav.SAUDE to listOf("saude"),
        VoiceNav.MANUTENCAO to listOf("manutencao", "revisao"),
        VoiceNav.DIAGNOSTICO to listOf("diagnostico"),
        VoiceNav.AJUSTES to listOf("ajuste", "configurac"),
    )

    private val refuelWords = listOf("abasteci", "abastecer", "abastecimento", "abastece", "coloquei", "botei", "enchi o tanque",
        "completei o tanque", "registrar gasolina", "registrar etanol")

    fun parse(text: String): VoiceCommand {
        val t = normalize(text)
        if (t.isEmpty()) return VoiceCommand(VoiceIntent.DESCONHECIDO)
        if (keywords.first().second.any { t.contains(it) }) return VoiceCommand(VoiceIntent.AJUDA)
        if (refuelWords.any { t.contains(it) }) return VoiceCommand(VoiceIntent.ABASTECER, refuel = refuel(text))
        goTo(t)?.let { return it }
        playRequest(t)?.let { return it }
        val target = navTargets.firstOrNull { (_, w) -> w.any { t.contains(it) } }?.first
        if (target != null) {
            val verb = navVerbs.any { Regex("\\b$it\\b").containsMatchIn(t) }
            val play = (target == VoiceNav.RADIO || target == VoiceNav.MUSICA) && playVerbs.any { Regex("\\b$it\\b").containsMatchIn(t) }
            // Só o nome da tela ("rádio", "mapa") também vale; perguntas ("como está a saúde") não.
            val alone = t.split(' ').size <= 2 && target != VoiceNav.SAUDE && target != VoiceNav.CONSUMO
            if (verb || play || alone) return VoiceCommand(VoiceIntent.ABRIR, nav = target, play = play)
        }
        return VoiceCommand(intent(text))
    }

    // ---------- Rota, rádio pelo nome e música ----------

    private val goVerbs = listOf("navegar ate", "navegar para", "navegar pra", "me leva para", "me leva pra", "me leva ate",
        "me leve para", "me leve pra", "me leve ate", "leva para", "leva pra", "levar ate", "ir para", "ir pra", "ir ate",
        "vai para", "vai pra", "va para", "va pra", "vamos para", "vamos pra", "rota para", "rota pra", "rota ate",
        "como chego em", "como chego no", "como chego na", "como chego ao", "como chegar em", "como chegar no",
        "como chegar na", "como chegar ao", "caminho para", "caminho pra")
    private val articles = setOf("o", "a", "os", "as", "ao", "um", "uma", "no", "na", "de", "do", "da", "e")

    /** O verbo que aparece primeiro na frase (o mais longo, se dois começam juntos) e onde ele termina. */
    private fun firstMatch(t: String, verbs: List<String>): Pair<String, Int>? =
        verbs.mapNotNull { v -> Regex("\\b$v\\b").find(t)?.let { v to it.range } }
            .minWithOrNull(compareBy<Pair<String, IntRange>> { it.second.first }.thenByDescending { it.first.length })
            ?.let { (v, r) -> v to r.last + 1 }

    private fun dropLeading(words: List<String>, drop: Set<String>): List<String> = words.dropWhile { it in drop }

    /** "ir para a saúde" é aba; "ir para a rua augusta 500" é rota. */
    private fun goTo(t: String): VoiceCommand? {
        val (_, end) = firstMatch(t, goVerbs) ?: return null
        val rest = dropLeading(t.substring(end).trim().split(' ').filter { it.isNotEmpty() }, articles)
        if (rest.isEmpty()) return null
        val core = rest.filter { it !in setOf("tela", "aba") && it !in articles }.joinToString(" ")
        val tab = navTargets.firstOrNull { (_, w) -> w.any { core == it || core == it + "s" } }?.first
        if (tab != null) return VoiceCommand(VoiceIntent.ABRIR, nav = tab)
        return VoiceCommand(VoiceIntent.IR_PARA, query = rest.joinToString(" "))
    }

    private val musicApps = listOf(
        "spotify" to "com.spotify.music", "youtube music" to "com.google.android.apps.youtube.music",
        "youtube" to "com.google.android.apps.youtube.music", "deezer" to "deezer.android.app", "amazon music" to "com.amazon.mp3",
    )
    private val playStarts = listOf("quero ouvir", "quero escutar", "tocar", "toca", "toque", "coloca", "colocar",
        "coloque", "bota", "botar", "bote", "ouvir", "escutar", "poe", "ligar", "liga", "ligue")
    private val musicFiller = setOf("a", "o", "as", "os", "musica", "musicas", "cancao", "do", "da", "de", "dos", "das", "artista",
        "cantor", "cantora", "banda", "album", "disco", "um", "uma", "pouco", "alguma", "algo", "som")

    /** "tocar rádio jovem pan", "tocar a band fm", "tocar péricles no spotify", "ouvir música". */
    private fun playRequest(t: String): VoiceCommand? {
        val (verb, end) = firstMatch(t, playStarts) ?: return null
        val words = t.substring(end).trim().split(' ').filter { it.isNotEmpty() }
        val radioOnly = verb in setOf("ligar", "liga", "ligue")
        if ("radio" in words || "fm" in words) {
            val name = dropLeading(words.filter { it != "radio" }, articles).joinToString(" ")
            return if (name.isEmpty()) VoiceCommand(VoiceIntent.ABRIR, nav = VoiceNav.RADIO, play = true)
            else VoiceCommand(VoiceIntent.TOCAR_RADIO, query = name)
        }
        if (radioOnly) return null
        var rest = words.joinToString(" ")
        var app: String? = null
        for ((name, pkg) in musicApps) {
            val r = Regex("\\b(no |na |pelo |pela )?$name\\b")
            if (r.containsMatchIn(rest)) { app = pkg; rest = r.replace(rest, " ").trim(); break }
        }
        val core = dropLeading(rest.split(' ').filter { it.isNotEmpty() }, musicFiller)
        if (core.isEmpty() || core.all { it in musicFiller }) {
            return VoiceCommand(VoiceIntent.ABRIR, nav = VoiceNav.MUSICA, play = true, app = app)
        }
        return VoiceCommand(VoiceIntent.TOCAR_MUSICA, query = core.joinToString(" "), app = app)
    }

    /** Lugar salvo ("casa", "trabalho") pelo nome falado; devolve o endereço. */
    fun placeFor(query: String, places: List<Pair<String, String>>): String? {
        val q = dropLeading(normalize(query).split(' '), articles + setOf("minha", "meu", "pra", "para")).joinToString(" ")
        return places.firstOrNull { (name, _) -> normalize(name) == q }?.second
    }

    /**
     * Rádio pelo nome falado: prefere a que tem todas as palavras no nome, as favoritas antes (vêm
     * primeiro em [candidates]); senão a primeira que tenha alguma palavra que não seja "fm".
     */
    fun bestRadio(query: String, candidates: List<RadioStation>): RadioStation? {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() && it != "fm" && it != "radio" }
        if (words.isEmpty()) return null
        fun name(st: RadioStation) = normalize(st.name)
        return candidates.firstOrNull { st -> words.all { w -> name(st).split(' ').any { it.startsWith(w) } } }
            ?: candidates.firstOrNull { st -> words.any { w -> w.length >= 3 && name(st).contains(w) } }
    }

    /** Várias versões da frase: vale a primeira que o app entende. */
    fun parse(candidates: List<String>): Pair<VoiceCommand, String> {
        candidates.forEach { c -> parse(c).let { if (it.intent != VoiceIntent.DESCONHECIDO) return it to c } }
        return VoiceCommand(VoiceIntent.DESCONHECIDO) to (candidates.firstOrNull() ?: "")
    }

    fun answer(cmd: VoiceCommand, s: VoiceSnapshot): String = when (cmd.intent) {
        VoiceIntent.IR_PARA -> "Abrindo a rota para ${cmd.query}."
        VoiceIntent.TOCAR_RADIO -> "Procurando a rádio ${cmd.query}."
        VoiceIntent.TOCAR_MUSICA -> "Pedindo para tocar ${cmd.query}."
        VoiceIntent.ABRIR -> if (cmd.play && cmd.nav == VoiceNav.RADIO) "Tocando a última rádio." else "Abrindo ${cmd.nav?.spoken ?: "a tela"}."
        VoiceIntent.ABASTECER -> refuelAnswer(cmd.refuel ?: VoiceRefuel(), s)
        else -> answer(cmd.intent, s)
    }

    private fun refuelAnswer(r: VoiceRefuel, s: VoiceSnapshot): String {
        if (s.moving) return "Pare o carro para registrar o abastecimento."
        val confirm = " Confira o posto e toque em Salvar."
        return when {
            r.amount != null && r.pricePerLiter != null && r.liters != null ->
                "${money(r.amount)} a ${n(r.pricePerLiter, 2)} o litro: ${n(r.liters, 2)} litros.$confirm"
            r.liters != null && r.pricePerLiter != null ->
                "${lt(r.liters)} litros a ${n(r.pricePerLiter, 2)} o litro: ${money(r.liters * r.pricePerLiter)}.$confirm"
            r.amount != null -> "${money(r.amount)}. Confira o preço do litro na tela e toque em Salvar."
            r.liters != null -> "${lt(r.liters)} litros. Confira o preço do litro na tela e toque em Salvar."
            else -> "Abrindo o registro de abastecimento. Da próxima vez, diga, por exemplo: abasteci cem reais a seis e onze."
        }
    }

    /** Litros: inteiro quando for ("40 litros"), senão duas casas ("16,37 litros"). */
    private fun lt(v: Double) = if (Math.abs(v - Math.round(v)) < 0.005) n(v, 0) else n(v, 2)

    private fun money(v: Double): String {
        val cents = Math.round(v * 100) % 100
        val reais = Math.round(v * 100) / 100
        return if (cents == 0L) "$reais reais" else "$reais reais e $cents centavos"
    }

    private val numberWords = mapOf(
        "zero" to 0, "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "quatro" to 4, "cinco" to 5, "seis" to 6,
        "sete" to 7, "oito" to 8, "nove" to 9, "dez" to 10, "onze" to 11, "doze" to 12, "treze" to 13, "quatorze" to 14,
        "catorze" to 14, "quinze" to 15, "dezesseis" to 16, "dezessete" to 17, "dezoito" to 18, "dezenove" to 19,
        "vinte" to 20, "trinta" to 30, "quarenta" to 40, "cinquenta" to 50, "sessenta" to 60, "setenta" to 70,
        "oitenta" to 80, "noventa" to 90, "cem" to 100, "cento" to 100, "duzentos" to 200, "trezentos" to 300,
        "quatrocentos" to 400, "quinhentos" to 500, "seiscentos" to 600, "setecentos" to 700, "oitocentos" to 800,
        "novecentos" to 900,
    )

    /**
     * Texto com números por extenso virando dígitos ("cento e vinte" -> "120"); "seis e onze" fica
     * "6 e 11" (não é um número só: é o preço falado). Vírgula decimal vira ponto ("6,11" -> "6.11").
     */
    fun numbers(text: String): List<String> {
        val base = Normalizer.normalize(text.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("r$", " r ")
            .replace(Regex("(\\d)[,.](\\d)"), "$1.$2")
            .replace(Regex("[^a-z0-9. ]"), " ")
            .replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
        val tokens = base.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val w = tokens[i]
            val first = numberWords[w] ?: if (w == "mil") 1000 else null
            if (first == null) { out += w; i++; continue }
            var value: Int = first
            var last: Int = first
            i++
            while (i < tokens.size) {
                val tk = tokens[i]
                if (tk == "mil" && value in 1..999) { value *= 1000; last = value; i++; continue }
                if (tk == "e" && i + 1 < tokens.size) {
                    val b = numberWords[tokens[i + 1]] ?: break
                    val place = when { last >= 1000 -> 1000; last >= 100 -> 100; last >= 10 && last % 10 == 0 -> 10; else -> 0 }
                    if (place == 0 || last % place != 0 || b >= place) break
                    value += b; last = b; i += 2; continue
                }
                val b = numberWords[tk]
                // "vinte cinco" (sem o "e"), como alguns reconhecedores escrevem.
                if (b != null && last >= 20 && last < 100 && last % 10 == 0 && b < 10) { value += b; last = b; i++; continue }
                break
            }
            out += value.toString()
        }
        return out
    }

    private data class Num(val value: Double, val start: Int, val end: Int, val currency: Boolean, val liters: Boolean, val priceMark: Boolean)

    fun refuel(text: String): VoiceRefuel {
        val tk = numbers(text)
        fun num(k: Int) = tk.getOrNull(k)?.toDoubleOrNull()
        val found = mutableListOf<Num>()
        var k = 0
        while (k < tk.size) {
            var v = num(k)
            if (v == null) { k++; continue }
            val start = k
            var end = k
            var currency = tk.getOrNull(k - 1) == "r"
            val isInt = !tk[k].contains('.')
            // "6 reais e 11 centavos", "6 e 11 centavos", "6 e 11" (preço falado)
            var j = k + 1
            if (tk.getOrNull(j) == "reais" || tk.getOrNull(j) == "real") { currency = true; end = j; j++ }
            if (isInt && tk.getOrNull(j) == "e") {
                val c = num(j + 1)
                val centavos = tk.getOrNull(j + 2) == "centavos" || tk.getOrNull(j + 2) == "centavo"
                if (c != null && c == Math.floor(c) && c < 100 && (centavos || (c >= 10 && v < 20))) {
                    v += c / 100
                    end = if (centavos) j + 2 else j + 1
                }
            }
            val next = tk.getOrNull(end + 1)
            val liters = next == "litros" || (next == "litro" && tk.getOrNull(start - 1) != "a") || next == "l"
            val before = tk.getOrNull(start - 1)
            val after = tk.subList(minOf(end + 1, tk.size), minOf(end + 3, tk.size)).joinToString(" ")
            val priceMark = before in setOf("a", "por", "preco", "custando", "litro") ||
                after.startsWith("o litro") || after.startsWith("por litro") || after.startsWith("cada litro") || after.startsWith("no litro")
            found += Num(v, start, end, currency, liters && !priceMark, priceMark)
            k = end + 1
        }
        val liters = found.firstOrNull { it.liters && it.value > 0 && it.value < 200 }?.value
        val rest = found.filter { !it.liters }
        val marked = rest.firstOrNull { it.priceMark && it.value in 1.0..20.0 }
        var price: Double? = null
        var amount: Double? = null
        when {
            marked != null -> {
                price = marked.value
                amount = rest.firstOrNull { it !== marked }?.value
            }
            rest.size >= 2 -> {
                val sorted = rest.sortedBy { it.value }
                if (sorted.first().value in 1.0..20.0) price = sorted.first().value
                amount = sorted.last().value
            }
            rest.size == 1 -> {
                val v = rest.first().value
                if (liters != null && v in 1.0..20.0) price = v else amount = v
            }
        }
        val t = normalize(text)
        val full = when {
            listOf("nao enchi", "nao completei", "parcial", "nao foi cheio", "nao e cheio").any { t.contains(it) } -> false
            listOf("cheio", "completei", "completo", "enchi").any { t.contains(it) } -> true
            else -> null
        }
        val ethanol = when {
            t.contains("etanol") || t.contains("alcool") -> true
            t.contains("gasolina") -> false
            else -> null
        }
        val computed = liters ?: RefuelMath.litersFromAmount(amount, price)
        return VoiceRefuel(amount, computed, price, full, ethanol)
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
        VoiceIntent.ABRIR, VoiceIntent.ABASTECER, VoiceIntent.IR_PARA, VoiceIntent.TOCAR_RADIO, VoiceIntent.TOCAR_MUSICA -> HELP
        VoiceIntent.DESCONHECIDO -> "Não entendi. $HELP"
    }

    const val HELP = "Pergunte, por exemplo: qual o consumo, temperatura do motor, bateria, troca de óleo, " +
        "tem alguma falha, ou como está o carro. Ou peça: abrir a saúde, ir para casa, tocar rádio Jovem Pan, " +
        "tocar Péricles no Spotify, ou abasteci cem reais a seis e onze."

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
