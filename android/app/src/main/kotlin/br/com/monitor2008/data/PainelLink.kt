package br.com.monitor2008.data

import android.app.Application
import android.os.Handler
import android.os.Looper
import br.com.monitor2008.domain.RefuelMath
import br.com.monitor2008.ui.MonitorEngine
import br.com.monitor2008.ui.UiState
import org.json.JSONArray
import org.json.JSONObject
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
    @Volatile private var lastSentMs = 0L
    /** Endereço achado pela busca (ou o último que funcionou). */
    @Volatile private var discovered: String? = null
    private var failCount = 0
    @Volatile private var lastScanMs = 0L
    private val scanning = AtomicBoolean(false)

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
        val preco = o.optDouble("preco", Double.NaN)
        if (!preco.isFinite() || preco <= 0) return
        val litros: Double? = if (o.has("litros")) {
            o.optDouble("litros", Double.NaN).takeIf { it.isFinite() && it > 0 }
        } else {
            o.optDouble("valor", Double.NaN).takeIf { it.isFinite() && it > 0 }
                ?.let { RefuelMath.litersFromAmount(it, preco) }
        }
        if (litros == null) return
        MonitorEngine.get(app).registerRefuel(litros, preco, o.optBoolean("cheio", true), o.optBoolean("etanol", false))
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
            "radio.next" -> RadioPlayer.get(app).step(true)
            "radio.prev" -> RadioPlayer.get(app).step(false)
        }
    }
}
