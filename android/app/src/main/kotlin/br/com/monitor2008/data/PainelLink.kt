package br.com.monitor2008.data

import android.app.Application
import android.os.Handler
import android.os.Looper
import br.com.monitor2008.ui.UiState
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    private val main by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var lastSentMs = 0L

    fun maybeSend(app: Application, address: String, ui: UiState) {
        val target = normalize(address) ?: return
        val now = System.currentTimeMillis()
        if (now - lastSentMs < 1000) return
        lastSentMs = now
        val body = toJson(app, ui).toString().toByteArray(Charsets.UTF_8)
        pool.execute { post(app, target, body) }
    }

    /** "10.12.38.252" ou "10.12.38.252:8080" viram "10.12.38.252:8080"; vazio vira null. */
    fun normalize(address: String): String? {
        val a = address.trim().removePrefix("http://").trimEnd('/')
        if (a.isBlank()) return null
        return if (a.contains(':')) a else "$a:8080"
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
            if (c.responseCode == 200) {
                val txt = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val arr = JSONObject(txt).optJSONArray("comandos")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val acao = arr.optString(i)
                        main.post { execute(app, acao) }
                    }
                }
            }
            c.disconnect()
        } catch (_: Exception) {
            // Multimídia desligado ou fora da rede: tenta de novo no próximo envio.
        }
    }

    /** Comandos vindos dos botões do painel. Música só com o acesso às notificações (multimídia). */
    private fun execute(app: Application, acao: String) {
        when (acao) {
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
