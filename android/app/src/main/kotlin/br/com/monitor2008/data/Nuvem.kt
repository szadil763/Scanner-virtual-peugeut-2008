package br.com.monitor2008.data

import android.content.Context
import br.com.monitor2008.domain.MaintCatalog
import br.com.monitor2008.domain.Station
import br.com.monitor2008.ui.MonitorEngine
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dados de cada CARRO na nuvem (Firebase Realtime Database), em /carros/<carro>.
 * Quem está na lista de membros do carro lê e grava. Quem é convidado (pelo e-mail, em
 * "convites") entra sozinho ao sincronizar. Os dados são do carro, não da pessoa.
 * Sobe tudo do carro: abastecimentos, postos, manutenção, odômetro, configurações e as
 * viagens da Saúde. Regras: ver docs/FIREBASE.md.
 */
object Nuvem {
    private fun carro(id: String) = FirebaseDatabase.getInstance().reference.child("carros").child(id)

    fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /** Sincroniza o carro: entra na lista, traz o que falta no aparelho e envia o que ele tem. */
    fun sincronizar(ctx: Context, store: Store, depois: (Boolean, String) -> Unit) {
        val uid = uid() ?: return depois(false, "Entre com a conta Google primeiro.")
        val id = store.carKey
        carro(id).child("membros").child(uid).setValue(true)
            .addOnSuccessListener {
                receber(ctx, store, id) { ok, msg ->
                    if (ok) { store.nuvemPronta = true; ultimoEnvioMs = System.currentTimeMillis(); sujo = false }
                    depois(ok, msg)
                }
            }
            .addOnFailureListener { depois(false, "Não consegui entrar no carro na nuvem: ${it.message ?: "sem permissão"}") }
    }

    /** Traz da nuvem o que o aparelho ainda não tem e depois envia o que ele tem. */
    private fun receber(ctx: Context, store: Store, id: String, depois: (Boolean, String) -> Unit) {
        carro(id).get()
            .addOnSuccessListener { snap ->
                var trouxe = false
                if (snap.exists()) {
                    val abast = JSONArray(snap.child("abastecimentos").getValue(String::class.java) ?: "[]")
                    val postos = JSONArray(snap.child("postos").getValue(String::class.java) ?: "[]")
                    val manut = JSONObject(snap.child("manutencao").getValue(String::class.java) ?: "{}")
                    val odo = snap.child("odometro").getValue(Double::class.java) ?: 0.0
                    val tudo = snap.child("tudo").getValue(String::class.java)
                    val viagens = snap.child("viagensSaude").getValue(String::class.java)
                    val trajetos = snap.child("trajetos").getValue(String::class.java)
                    val tanqueRaw = snap.child("tanque").getValue(String::class.java)

                    // Aparelho novo para este carro: traz tudo (configurações, calibração, óleo e viagens da Saúde).
                    if (!store.carroTemDados() && !tudo.isNullOrBlank()) {
                        store.importarCarro(JSONObject(tudo))
                        trouxe = true
                    }
                    if (!viagens.isNullOrBlank()) HealthStore(ctx, id).restaurarRaw(viagens)
                    // Aparelho sem trajetos guardados: traz os da nuvem.
                    if (store.tripArchive().isEmpty() && !trajetos.isNullOrBlank()) {
                        store.importTripArchive(trajetos)
                        trouxe = true
                    }

                    // Histórico e nível do tanque: só se este aparelho ainda não tem.
                    if (!tanqueRaw.isNullOrBlank()) try {
                        val t = JSONObject(tanqueRaw)
                        if (store.tankHistory().isEmpty() && t.optJSONArray("historico")?.length() ?: 0 > 0) {
                            store.importTankHistory(t.getJSONArray("historico").toString())
                            trouxe = true
                        }
                        if (store.tankEstimateL == null && t.has("nivelL") && !t.isNull("nivelL")) {
                            store.tankEstimateL = t.getDouble("nivelL")
                            trouxe = true
                        }
                    } catch (_: Exception) { }

                    if (store.refuels().isEmpty()) {
                        for (i in 0 until abast.length()) {
                            val o = abast.getJSONObject(i)
                            store.addRefuel(
                                Refuel(
                                    epochDay = o.getLong("dia"),
                                    liters = o.getDouble("litros"),
                                    pricePerLiter = o.getDouble("preco"),
                                    full = o.getBoolean("cheio"),
                                    odometerKm = o.optDouble("odometro", 0.0),
                                    realKmPerL = if (o.isNull("kmL")) null else o.getDouble("kmL"),
                                    timeMs = o.optLong("hora", 0L).takeIf { it > 0 } ?: (o.getLong("dia") * 86_400_000L + 43_200_000L),
                                    stationId = if (o.isNull("posto")) null else o.getString("posto"),
                                    ethanol = o.optBoolean("etanol", false),
                                    tankRatio = if (o.isNull("tanque")) null else o.getDouble("tanque"),
                                ),
                            )
                            trouxe = true
                        }
                    }
                    if (store.stations().isEmpty() && postos.length() > 0) {
                        store.saveStations(
                            (0 until postos.length()).map { i ->
                                val p = postos.getJSONObject(i)
                                Station(p.getString("id"), p.getString("name"), p.optString("address", ""), p.getDouble("lat"), p.getDouble("lon"))
                            },
                        )
                        trouxe = true
                    }
                    if (store.odometerKm == 0.0 && odo > 0) {
                        store.saveProgress(odo, store.uncalLiters, store.kmSinceFull)
                        trouxe = true
                    }
                    for (item in MaintCatalog.items) {
                        val m = manut.optJSONObject(item.id) ?: continue
                        if (!store.maintConfigured(item.id)) {
                            store.setMaintDone(item.id, m.getDouble("km"), m.getLong("dia"))
                            trouxe = true
                        }
                    }
                }
                if (trouxe) MonitorEngine.get(ctx.applicationContext as android.app.Application).recarregarCarro()
                enviar(ctx, store, id, depois)
            }
            .addOnFailureListener { depois(false, "Não consegui ler o carro na nuvem: ${it.message ?: "sem permissão"}") }
    }

    // Envio automático: ao mudar algo importante (abastecer, zerar trajeto, corrigir o tanque) e de tempos em tempos.
    private const val INTERVALO_MUDANCA_MS = 15_000L
    private const val INTERVALO_PERIODICO_MS = 30 * 60_000L
    private const val ENVIO_PRESO_MS = 5 * 60_000L
    @Volatile private var ultimoEnvioMs = 0L
    @Volatile private var sujo = false
    @Volatile private var enviandoDesde = 0L
    @Volatile private var entrando = false

    /** Algo do carro mudou: envia logo (no máximo uma vez a cada 15 s). */
    fun mudou(ctx: Context, store: Store) {
        sujo = true
        try { if (store.nuvemPronta && uid() != null) enviarVivo(store) } catch (_: Exception) { }
        auto(ctx, store)
    }

    // ---- Estado vivo: trajeto, nível do tanque e hodômetro seguem o carro de um celular para o outro ----
    private const val VIVO_INTERVALO_MS = 30_000L
    private const val VIVO_PARADO_MS = 120_000L
    private const val VIVO_ESPERA_MS = 10_000L
    @Volatile private var vivoEnviadoT = 0L
    @Volatile private var vivoEnvioMs = 0L
    @Volatile private var vivoPuxeMs = 0L
    @Volatile private var primeiroPuxe = false
    @Volatile private var inicioVivoMs = 0L

    /**
     * Logo ao abrir: traz o estado do carro da nuvem antes de este celular começar a somar. Enquanto espera
     * (até 10 s), o motor não soma trajeto, para não partir de um estado velho.
     */
    fun iniciarVivo(ctx: Context, store: Store, aplicou: () -> Unit) {
        inicioVivoMs = System.currentTimeMillis()
        puxarVivo(store, aplicou)
        tudoPuxeMs = inicioVivoMs
        puxarTudo(ctx, store, aplicou)
    }

    @Volatile private var nuvemTudoMs = 0L
    @Volatile private var tudoPuxeMs = 0L
    @Volatile private var primeiroTudo = false

    private fun impressao(ctx: Context, store: Store): Int =
        store.hashDados(HealthStore(ctx, store.carKey).rawJson())

    /** Se o conteúdo dos dados do carro mudou desde a última conferência, carimba a hora (vai para a nuvem). */
    private fun detectarMudanca(ctx: Context, store: Store) {
        val h = impressao(ctx, store)
        if (!store.temDadosHash()) { store.dadosHash = h; return } // primeira vez nesta versão: só memoriza
        if (h != store.dadosHash) {
            store.dadosHash = h
            store.dadosMs = System.currentTimeMillis()
            sujo = true
        }
    }

    /**
     * Compara com a nuvem. Se lá a foto geral é mais nova, adota (abastecimentos, manutenção, configurações, trajetos
     * guardados, histórico do tanque e viagens da Saúde). Sem carimbo dos dois lados (dados de antes desta versão),
     * vale quem tem mais abastecimentos.
     */
    private fun puxarTudo(ctx: Context, store: Store, aplicou: () -> Unit) {
        if (uid() == null || !store.nuvemPronta) { primeiroTudo = true; return }
        val ref = carro(store.carKey)
        ref.child("tudoMs").get()
            .addOnSuccessListener { msSnap ->
                val nuvemMs = msSnap.getValue(Long::class.java) ?: 0L
                nuvemTudoMs = nuvemMs
                val local = store.dadosMs
                if (nuvemMs > local || (nuvemMs == 0L && local == 0L)) {
                    ref.get()
                        .addOnSuccessListener { snap ->
                            try {
                                val tudo = snap.child("tudo").getValue(String::class.java)
                                if (tudo.isNullOrBlank()) {
                                    if (local == 0L && store.carroTemDados()) store.dadosMs = System.currentTimeMillis()
                                } else {
                                    val adotar = nuvemMs > local || run {
                                        val n = try { JSONArray(snap.child("abastecimentos").getValue(String::class.java) ?: "[]").length() } catch (e: Exception) { 0 }
                                        n >= store.refuels().size
                                    }
                                    if (adotar) {
                                        store.adotarTudo(JSONObject(tudo), maxOf(nuvemMs, 1L))
                                        snap.child("viagensSaude").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                                            ?.let { HealthStore(ctx, store.carKey).substituirRaw(it) }
                                        store.dadosHash = impressao(ctx, store)
                                        aplicou()
                                    } else {
                                        store.dadosMs = System.currentTimeMillis() // os daqui são mais completos: sobem
                                    }
                                }
                            } catch (_: Exception) { }
                            primeiroTudo = true
                        }
                        .addOnFailureListener { primeiroTudo = true }
                } else primeiroTudo = true
            }
            .addOnFailureListener { primeiroTudo = true }
    }

    fun aguardandoVivo(store: Store): Boolean =
        !primeiroPuxe && inicioVivoMs != 0L && store.nuvemPronta && uid() != null &&
            System.currentTimeMillis() - inicioVivoMs < VIVO_ESPERA_MS

    private fun enviarVivo(store: Store) {
        if (store.vivoMs == 0L) return
        vivoEnviadoT = store.vivoMs
        vivoEnvioMs = System.currentTimeMillis()
        carro(store.carKey).child("vivo").setValue(store.vivoJson())
    }

    /** Traz o estado vivo (trajeto, tanque, hodômetro) se for mais novo que o daqui. */
    private fun puxarVivo(store: Store, aplicou: () -> Unit) {
        if (uid() == null || !store.nuvemPronta) { primeiroPuxe = true; return }
        vivoPuxeMs = System.currentTimeMillis()
        val ref = carro(store.carKey)
        ref.child("vivo").get()
            .addOnSuccessListener { vivoSnap ->
                var mudou = false
                try {
                    val raw = vivoSnap.getValue(String::class.java)
                    if (!raw.isNullOrBlank()) {
                        val o = JSONObject(raw)
                        if (o.getLong("t") > store.vivoMs) { store.aplicarVivo(o); mudou = true }
                    }
                } catch (_: Exception) { }
                primeiroPuxe = true
                if (mudou) aplicou()
            }
            .addOnFailureListener { primeiroPuxe = true }
    }

    /**
     * Chamar de tempos em tempos (a cada 20 s, pelo motor). Em silêncio: sem conta Google ou sem internet não faz
     * nada e tenta de novo depois. Envia a cada 30 min mesmo sem mudança. No celular, a primeira vez entra no carro
     * sozinha (no multimídia continua sendo pelo botão, para não sobrescrever a nuvem com dados velhos).
     */
    fun auto(ctx: Context, store: Store) {
        try {
            if (uid() == null) return
            val agora = System.currentTimeMillis()
            if (!store.nuvemPronta) {
                if (Features.mediaControl || entrando) return
                entrando = true
                sincronizar(ctx, store) { _, _ -> entrando = false }
                return
            }
            // Qualquer mudança nos dados do carro (feita aqui) é carimbada; mudança vinda de outro celular é adotada.
            detectarMudanca(ctx, store)
            if (agora - tudoPuxeMs >= VIVO_INTERVALO_MS) {
                tudoPuxeMs = agora
                puxarTudo(ctx, store) { MonitorEngine.get(ctx.applicationContext as android.app.Application).reloadLive() }
            }
            // Estado vivo: quem está lendo o carro envia; quem está parado busca o que o outro celular mandou.
            if (store.vivoMs > vivoEnviadoT && agora - vivoEnvioMs >= VIVO_INTERVALO_MS) enviarVivo(store)
            else if (agora - vivoPuxeMs >= VIVO_INTERVALO_MS && agora - store.vivoMs > VIVO_PARADO_MS) {
                puxarVivo(store) { MonitorEngine.get(ctx.applicationContext as android.app.Application).reloadLive() }
            }
            if (enviandoDesde != 0L && agora - enviandoDesde < ENVIO_PRESO_MS) return
            // Só envia a foto geral depois de conferir a nuvem, e se os dados daqui não estiverem atrás dos de lá.
            if (!primeiroTudo || store.dadosMs <= 0L || store.dadosMs < nuvemTudoMs) return
            val vencido = agora - ultimoEnvioMs >= INTERVALO_PERIODICO_MS
            if (!vencido && !(sujo && agora - ultimoEnvioMs >= INTERVALO_MUDANCA_MS)) return
            enviandoDesde = agora
            enviar(ctx, store, store.carKey) { ok, _ ->
                enviandoDesde = 0L
                if (ok) { sujo = false; ultimoEnvioMs = System.currentTimeMillis() } else ultimoEnvioMs = System.currentTimeMillis() - INTERVALO_PERIODICO_MS + 60_000L
            }
        } catch (_: Exception) { enviandoDesde = 0L; entrando = false }
    }

    /** Envia só os trajetos guardados (ao zerar o trajeto). Em silêncio: sem conta ou sem sincronizar o carro antes, não faz nada. */
    fun enviarTrajetos(store: Store) {
        if (uid() == null) return
        carro(store.carKey).child("trajetos").setValue(store.tripArchiveJson())
    }

    /** Envia tudo o que o aparelho tem deste carro. */
    private fun enviar(ctx: Context, store: Store, id: String, depois: (Boolean, String) -> Unit) {
        val uid = uid() ?: return depois(false, "Entre com a conta Google primeiro.")
        val abast = JSONArray().apply {
            store.refuels().forEach { r ->
                put(JSONObject().apply {
                    put("dia", r.epochDay)
                    put("litros", r.liters)
                    put("preco", r.pricePerLiter)
                    put("cheio", r.full)
                    put("odometro", r.odometerKm)
                    put("kmL", r.realKmPerL ?: JSONObject.NULL)
                    put("hora", r.timeMs)
                    put("posto", r.stationId ?: JSONObject.NULL)
                    put("etanol", r.ethanol)
                    put("tanque", r.tankRatio ?: JSONObject.NULL)
                })
            }
        }
        val postos = JSONArray().apply {
            store.stations().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("address", s.address)
                    put("lat", s.lat)
                    put("lon", s.lon)
                })
            }
        }
        val manut = JSONObject().apply {
            for (item in MaintCatalog.items) {
                if (store.maintConfigured(item.id)) {
                    put(item.id, JSONObject().apply {
                        put("km", store.maintLastKm(item.id))
                        put("dia", store.maintLastDay(item.id))
                    })
                }
            }
        }
        val dados = hashMapOf<String, Any>(
            "membros/$uid" to true,
            "abastecimentos" to abast.toString(),
            "postos" to postos.toString(),
            "manutencao" to manut.toString(),
            "odometro" to store.odometerKm,
            "tudo" to store.exportarCarro().toString(),
            "viagensSaude" to HealthStore(ctx, id).rawJson(),
            "trajetos" to store.tripArchiveJson(),
            "tudoMs" to store.dadosMs,
            "tanque" to JSONObject().apply {
                put("nivelL", store.tankEstimateL ?: JSONObject.NULL)
                put("historico", JSONArray(store.tankHistoryJson()))
            }.toString(),
            "atualizadoEm" to ServerValue.TIMESTAMP,
        )
        carro(id).updateChildren(dados)
            .addOnSuccessListener { depois(true, "Carro sincronizado com a nuvem.") }
            .addOnFailureListener { depois(false, "Não enviei para a nuvem: ${it.message ?: "sem permissão"}") }
    }

    /** Convida uma pessoa (pelo e-mail Google dela). Ela entra sozinha ao sincronizar. */
    fun convidar(store: Store, email: String, depois: (Boolean, String) -> Unit) {
        val e = email.trim().lowercase()
        if (!e.contains("@")) return depois(false, "Digite um e-mail válido.")
        val ref = carro(store.carKey).child("convites")
        ref.get()
            .addOnSuccessListener { snap ->
                val atual = snap.getValue(String::class.java)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                ref.setValue((atual + e).joinToString(","))
                    .addOnSuccessListener { depois(true, "$e foi convidado. A pessoa entra com a conta Google dela e sincroniza.") }
                    .addOnFailureListener { depois(false, "Não convidei: ${it.message ?: "sem permissão"}") }
            }
            .addOnFailureListener { depois(false, "Não consegui ler os convites: ${it.message ?: "sem permissão"}") }
    }
}
