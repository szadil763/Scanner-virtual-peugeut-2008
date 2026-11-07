package br.com.monitor2008.data

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import br.com.monitor2008.domain.DriveModeSetting
import br.com.monitor2008.domain.DtcInfo
import br.com.monitor2008.domain.MaintCatalog
import br.com.monitor2008.domain.RefuelMath
import br.com.monitor2008.domain.VoiceRefuel
import br.com.monitor2008.ui.MonitorEngine
import br.com.monitor2008.ui.StationDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import br.com.monitor2008.ui.UiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Liga o app do celular ao painel do multimídia (PainelServerActivity). A cada segundo, em segundo
 * plano, manda POST /estado com os números do carro e o que está tocando. A resposta traz os
 * comandos que o multimídia pediu (botões de música e rádio), que o celular executa. Sem endereço
 * em Ajustes, não faz nada.
 */
object PainelLink {
    private val pool: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "painel-envio").apply { isDaemon = true }
    }
    private val scanner: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "painel-busca").apply { isDaemon = true }
    }
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var lastSentMs = 0L
    /** Endereço achado pela busca (ou o último que funcionou). */
    @Volatile private var discovered: String? = null
    private var failCount = 0
    @Volatile private var lastScanMs = 0L
    private val scanning = AtomicBoolean(false)
    /** Chave (app|título|artista) da capa já enviada ao multimídia e se um envio está em curso. */
    @Volatile private var capaSent: String? = null
    @Volatile private var capaSending = false
    /** Pedido feito pelo multimídia: a resposta vai para o painel, que fala pelo som do carro. */
    @Volatile private var vozPainelAte = 0L
    @Volatile private var falaId = 0
    @Volatile private var falaTexto = ""
    /** Abastecimento ditado pela voz do multimídia, esperando a confirmação no painel. */
    @Volatile private var pendente: JSONObject? = null
    @Volatile private var pendenteAteMs = 0L
    @Volatile private var pendenteId = 0

    /** Pedido de voz do abastecimento: guarda para o painel confirmar (não abre tela no celular). */
    fun pedirAbastecimento(r: VoiceRefuel) {
        val preco = r.pricePerLiter
        val litros = r.liters ?: if (r.amount != null && preco != null && preco > 0) RefuelMath.litersFromAmount(r.amount, preco) else null
        if (preco == null || litros == null || litros <= 0) {
            enviarFala("Diga o valor e o preço do litro, por exemplo: abasteci cem reais a seis e onze.")
            return
        }
        pendente = JSONObject().apply {
            put("litros", Math.round(litros * 100) / 100.0)
            put("preco", Math.round(preco * 100) / 100.0)
            put("cheio", r.full ?: true)
            put("etanol", r.ethanol ?: false)
        }
        pendenteAteMs = System.currentTimeMillis() + 120_000
        pendenteId++
    }

    fun cancelarPendente() {
        pendente = null
    }

    /** Vale enquanto o pedido de voz veio do multimídia (até 60 s depois do botão). */
    fun vozNoPainel(): Boolean = System.currentTimeMillis() < vozPainelAte

    /** Resposta de voz que o multimídia deve falar (a próxima leitura do painel a entrega). */
    fun enviarFala(texto: String) {
        falaTexto = texto
        falaId++
    }

    /**
     * Envia os números para o multimídia. Usa o endereço achado pela busca, ou o digitado em
     * Ajustes. Se falhar várias vezes, procura o multimídia de novo na rede do celular.
     */
    fun maybeSend(app: Application, manual: String, ui: UiState) {
        val now = System.currentTimeMillis()
        if (now - lastSentMs < 1000) return
        lastSentMs = now
        val target = discovered ?: normalize(manual)
        if (target == null) {
            scheduleScan()
            return
        }
        val body = toJson(app, ui).toString().toByteArray(Charsets.UTF_8)
        pool.execute { post(app, target, body) }
        val np = if (Features.mediaControl) MediaWatcher.get(app).now.value else null
        val art = np?.art
        val chave = np?.let { chaveDaMusica(it) }
        if (art != null && chave != null && chave != capaSent && !capaSending) {
            capaSending = true
            pool.execute {
                try {
                    if (postCapa(target, art)) capaSent = chave
                } finally {
                    capaSending = false
                }
            }
        }
    }

    /** "10.12.38.252" ou "10.12.38.252:8080" viram "10.12.38.252:8080"; vazio vira null. */
    fun normalize(address: String): String? {
        val a = address.trim().removePrefix("http://").trimEnd('/')
        if (a.isBlank()) return null
        return if (a.contains(':')) a else "$a:8080"
    }

    private fun scheduleScan() {
        val now = System.currentTimeMillis()
        if (now - lastScanMs < 10_000 || !scanning.compareAndSet(false, true)) return
        lastScanMs = now
        scanner.execute {
            try {
                discovered = findPainel()
            } finally {
                scanning.set(false)
            }
        }
    }

    /** Procura o /ping do multimídia em todas as redes do celular (hotspot, Wi-Fi), com 32 conexões ao mesmo tempo. */
    private fun findPainel(): String? {
        val found = AtomicReference<String?>(null)
        val probes = Executors.newFixedThreadPool(32)
        val futures = ArrayList<Future<*>>()
        for (base in localSubnets()) {
            for (h in 1..254) {
                val ip = "$base.$h"
                futures.add(probes.submit {
                    if (found.get() == null && isPainel(ip)) found.compareAndSet(null, "$ip:$PORT")
                })
            }
        }
        futures.forEach { try { it.get() } catch (_: Exception) { } }
        probes.shutdownNow()
        return found.get()
    }

    private fun isPainel(ip: String): Boolean = try {
        val c = URL("http://$ip:$PORT/ping").openConnection() as HttpURLConnection
        c.connectTimeout = 400
        c.readTimeout = 600
        val ok = c.responseCode == 200 &&
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim() == "monitor2008"
        c.disconnect()
        ok
    } catch (_: Exception) {
        false
    }

    /** Redes IPv4 do celular, sem o 4G (a busca não varre a operadora). Ex.: "192.168.43". */
    private fun localSubnets(): List<String> = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback && !isCellular(it.name) }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress?.substringBeforeLast('.') }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }

    private fun isCellular(name: String): Boolean =
        listOf("rmnet", "ccmni", "v4-", "tun", "dummy").any { name.startsWith(it) }

    private fun chaveDaMusica(n: NowPlaying): String = "${n.pkg}|${n.title}|${n.artist}"

    /** Capa reduzida (300 px, JPEG) para o painel. Devolve true se o multimídia recebeu. */
    private fun postCapa(target: String, art: Bitmap): Boolean = try {
        val pequena = Bitmap.createScaledBitmap(art, 300, 300, true)
        val out = ByteArrayOutputStream()
        pequena.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val bytes = out.toByteArray()
        val c = URL("http://$target/capa").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 1500
        c.readTimeout = 1500
        c.doOutput = true
        c.setFixedLengthStreamingMode(bytes.size)
        c.setRequestProperty("Content-Type", "image/jpeg")
        c.outputStream.use { it.write(bytes) }
        val ok = c.responseCode == 204
        c.disconnect()
        ok
    } catch (_: Exception) {
        false
    }

    fun toJson(app: Application, ui: UiState): JSONObject {
        val s = ui.sample
        return JSONObject().apply {
            put("ok", ui.conn.state == ConnState.CONNECTED)
            put("rpm", num(s?.rpm))
            put("speed", num(s?.speedKmh))
            put("coolant", num(s?.coolantC))
            put("battery", num(s?.batteryV))
            put("kmL", num(ui.kmPerL))
            put("lph", num(ui.litersPerHour))
            put("parado", !ui.moving)
            put("tripAvgKmL", num(ui.tripAvgKmPerL))
            put("tripAvgL100", num(ui.tripAvgL100))
            put("tripKm", num(ui.tripKm))
            put("tripMin", ui.tripMinutes)
            put("midia", midia(app))
            put("saude", saude(ui))
            put("postos", postos(ui))
            put("manutencao", manutencao(ui))
            put("consumo", consumo(ui))
            put("diagnostico", diagnostico(app, ui))
            put("falaId", falaId)
            put("pendente", if (pendente != null && System.currentTimeMillis() < pendenteAteMs) pendente!! else JSONObject.NULL)
            put("pendenteId", pendenteId)
            put("fala", falaTexto)
            put("ajustes", JSONObject().apply {
                put("sol", Store(app).highContrast)
                put("direcao", Store(app).driveMode)
            })
        }
    }

    /** Indicadores da Saúde (só leitura no multimídia). */
    private fun saude(ui: UiState): JSONArray = JSONArray().apply {
        ui.health.forEach { h ->
            put(JSONObject().apply {
                put("titulo", h.title)
                put("status", h.status.name)
                put("valor", h.value)
                put("conselho", h.advice)
                put("unidade", h.unit)
                // Gráfico: um ponto por viagem (null = sem dado naquela viagem).
                put("serie", JSONArray().apply { h.series.forEach { put(it?.let { v -> r2(v) } ?: JSONObject.NULL) } })
                put("linhas", JSONArray().apply {
                    h.lines.forEach { l -> put(JSONObject().apply { put("valor", r2(l.value)); put("alerta", l.alert) }) }
                })
                put("abastecimento", h.refuelIndex ?: JSONObject.NULL)
                put("antes", h.beforeRefuel?.let { r2(it) } ?: JSONObject.NULL)
                put("depois", h.afterRefuel?.let { r2(it) } ?: JSONObject.NULL)
                put("viagensDepois", h.tripsAfterRefuel)
            })
        }
    }

    private fun r1(d: Double): Double = Math.round(d * 10) / 10.0
    private fun r2(d: Double): Double = Math.round(d * 100) / 100.0

    /** Diagnóstico: conexão, falhas (só leitura), e o registro do adaptador para conferir. */
    private fun diagnostico(app: Application, ui: UiState): JSONObject = JSONObject().apply {
        put("estado", ui.conn.state.name)
        put("mensagem", ui.conn.message)
        put("protocolo", ui.conn.protocol)
        put("carro", ui.vehicle?.name ?: "")
        put("simulado", ui.simulated)
        put("falhasLidas", ui.dtcs != null)
        put("falhas", JSONArray().apply {
            (ui.dtcs ?: emptyList()).forEach { c ->
                put(JSONObject().apply {
                    put("codigo", c)
                    put("descricao", DtcInfo.describe(c))
                    put("dica", DtcInfo.hint(c) ?: "")
                })
            }
        })
        put("pendentes", JSONArray(ui.pendingDtcs ?: emptyList<String>()))
        put("registro", JSONArray(MonitorEngine.get(app).diagnosticLog().takeLast(60)))
    }

    /** Dados da tela Consumo: gráficos, viagem e últimos abastecimentos (só leitura no multimídia). */
    private fun consumo(ui: UiState): JSONObject = JSONObject().apply {
        put("historico", JSONArray().apply { ui.history.takeLast(120).forEach { put(r2(it)) } })
        put("velocidades", JSONArray().apply { ui.speedHistory.takeLast(240).forEach { put(r1(it)) } })
        put("custo", r2(ui.tripLiters * ui.fuelPrice))
        put("litrosViagem", r2(ui.tripLiters))
        put("velMedia", r1(ui.tripAvgSpeed))
        put("abastecimentos", JSONArray().apply {
            ui.refuels.take(5).forEach { r ->
                put(JSONObject().apply {
                    put("dia", r.epochDay)
                    put("litros", r2(r.liters))
                    put("preco", r2(r.pricePerLiter))
                    put("cheio", r.full)
                    put("etanol", r.ethanol)
                    put("kmL", num(r.realKmPerL))
                })
            }
        })
    }

    /** Itens de manutenção com o estado de cada um (só leitura no multimídia; marcar como feito é comando). */
    private fun manutencao(ui: UiState): JSONArray = JSONArray().apply {
        ui.maint.forEach { row ->
            put(JSONObject().apply {
                put("id", row.item.id)
                put("nome", row.item.name)
                put("porque", row.item.why)
                put("feito", row.status != null)
                put("estado", row.status?.state?.name ?: JSONObject.NULL)
                put("kmRestante", num(row.status?.kmLeft))
                put("diasRestantes", row.status?.daysLeft ?: JSONObject.NULL)
            })
        }
    }

    /** Postos com nota de qualidade, os bons e mais perto primeiro (só leitura no multimídia). */
    private fun postos(ui: UiState): JSONArray = JSONArray().apply {
        ui.stationRatings.forEach { r ->
            put(JSONObject().apply {
                put("nome", r.station.name)
                put("endereco", r.station.address)
                put("nota", r.grade.name)
                put("tanques", r.tanks)
                put("visitas", r.visits)
                put("preco", num(r.lastPrice))
                put("distanciaKm", num(r.distanceKm))
                put("resumo", r.summary)
            })
        }
    }

    /** O que está tocando: música (se o app tem acesso às notificações) e rádio pela internet. */
    private fun midia(app: Application): JSONObject {
        val musica: Any = if (Features.mediaControl) {
            MediaWatcher.get(app).now.value?.let { n ->
                JSONObject().apply {
                    put("app", n.app)
                    put("titulo", n.title ?: "")
                    put("artista", n.artist ?: "")
                    put("tocando", n.playing)
                    put("posMs", n.positionNow())
                    put("durMs", n.durationMs)
                    put("chave", chaveDaMusica(n))
                    put("capa", n.art != null)
                }
            } ?: JSONObject.NULL
        } else JSONObject.NULL
        val rs = RadioPlayer.get(app).state.value
        val st = rs.station
        val radio: Any = if (st != null) {
            JSONObject().apply {
                put("nome", st.name)
                put("titulo", rs.streamTitle ?: "")
                put("tocando", rs.playing)
            }
        } else JSONObject.NULL
        return JSONObject().apply {
            put("musica", musica)
            put("radio", radio)
        }
    }

    private fun num(d: Double?): Any = if (d != null && d.isFinite()) d else JSONObject.NULL

    private const val PORT = 8080

    private fun post(app: Application, target: String, body: ByteArray) {
        try {
            val c = URL("http://$target/estado").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 1500
            c.readTimeout = 1500
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body) }
            failCount = 0
            if (c.responseCode == 200) {
                val txt = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val arr = JSONObject(txt).optJSONArray("comandos")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val cmd = arr.optJSONObject(i) ?: continue
                        main.post { execute(app, cmd) }
                    }
                }
            }
            c.disconnect()
        } catch (_: Exception) {
            // Multimídia desligado ou fora da rede: depois de 3 falhas, procura de novo.
            if (++failCount >= 3) {
                failCount = 0
                discovered = null
                scheduleScan()
            }
        }
    }

    /**
     * Abastecimento pelo painel: mesmo caminho da tela do celular (registerRefuel), que já não
     * registra com o carro andando. Sem posto (o GPS fica no celular): o posto entra depois.
     */
    private fun abastecer(app: Application, o: JSONObject) {
        pendente = null
        val preco = o.optDouble("preco", Double.NaN)
        if (!preco.isFinite() || preco <= 0) return
        val litros: Double? = if (o.has("litros")) {
            o.optDouble("litros", Double.NaN).takeIf { it.isFinite() && it > 0 }
        } else {
            o.optDouble("valor", Double.NaN).takeIf { it.isFinite() && it > 0 }
                ?.let { RefuelMath.litersFromAmount(it, preco) }
        }
        if (litros == null) return
        val cheio = o.optBoolean("cheio", true)
        val etanol = o.optBoolean("etanol", false)
        // Posto pelo GPS do celular (como na tela de abastecimento). Sem GPS, registra sem posto.
        scope.launch {
            val fix = PlaceFinder.current(app, timeoutMs = 10_000)
            val place = fix?.let {
                StationDraft(it.latitude, it.longitude, "", PlaceFinder.address(app, it.latitude, it.longitude) ?: "")
            }
            MonitorEngine.get(app).registerRefuel(litros, preco, cheio, etanol, place)
        }
    }

    /** Marca um item de manutenção como feito hoje (o celular recusa com o carro andando). */
    private fun feitoManutencao(app: Application, id: String) {
        if (MaintCatalog.items.none { it.id == id }) return
        MonitorEngine.get(app).registerMaintDone(id)
    }

    /** Busca de música ou cantor no app que está tocando (ou no app de música padrão). */
    private fun buscar(app: Application, texto: String) {
        val q = texto.trim().take(100)
        if (q.isEmpty()) return
        val media = MediaWatcher.get(app)
        val launcher = AppLauncher(app)
        val pkg = media.now.value?.pkg ?: launcher.musicFor(null) ?: return
        if (!media.playFromSearchQuietly(pkg, q)) launcher.playFromSearch(pkg, q, null)
    }

    /** Comandos vindos dos botões do painel. Música só com o acesso às notificações (multimídia). */
    private fun execute(app: Application, cmd: JSONObject) {
        when (cmd.optString("acao")) {
            "abastecer" -> abastecer(app, cmd)
            "musica.playpause" -> if (Features.mediaControl) MediaWatcher.get(app).playPause()
            "musica.next" -> if (Features.mediaControl) MediaWatcher.get(app).next()
            "musica.prev" -> if (Features.mediaControl) MediaWatcher.get(app).previous()
            "radio.playpause" -> RadioPlayer.get(app).let { r ->
                if (r.state.value.playing) r.pause() else r.resume()
            }
            "musica.buscar" -> if (Features.mediaControl) buscar(app, cmd.optString("texto"))
            "manutencao.feito" -> feitoManutencao(app, cmd.optString("id"))
            "diag.lerFalhas" -> MonitorEngine.get(app).readDtcs()
            "pendente.cancelar" -> cancelarPendente()
            "voz.ouvir" -> {
                vozPainelAte = System.currentTimeMillis() + 60_000
                VoiceAssistant.get(app).listen(beepWhenReady = true)
            }
            "ajuste.sol" -> MonitorEngine.get(app).setHighContrast(cmd.optBoolean("valor", false))
            "ajuste.direcao" -> DriveModeSetting.values().firstOrNull { it.name == cmd.optString("valor") }
                ?.let { MonitorEngine.get(app).setDriveMode(it) }
            "radio.next" -> RadioPlayer.get(app).step(true)
            "radio.prev" -> RadioPlayer.get(app).step(false)
        }
    }
}
