package br.com.monitor2008.data

import br.com.monitor2008.ui.UiState
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Envia os números do carro ao painel do multimídia (PainelServerActivity): POST /estado, no máximo
 * uma vez por segundo, em segundo plano. Sem endereço configurado em Ajustes, não envia nada.
 */
object PainelLink {
    private val pool: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "painel-envio").apply { isDaemon = true }
    }
    @Volatile private var lastSentMs = 0L

    fun maybeSend(address: String, ui: UiState) {
        val target = normalize(address) ?: return
        val now = System.currentTimeMillis()
        if (now - lastSentMs < 1000) return
        lastSentMs = now
        val body = toJson(ui).toString().toByteArray(Charsets.UTF_8)
        pool.execute { post(target, body) }
    }

    /** "10.12.38.252" ou "10.12.38.252:8080" viram "10.12.38.252:8080"; vazio vira null. */
    fun normalize(address: String): String? {
        val a = address.trim().removePrefix("http://").trimEnd('/')
        if (a.isBlank()) return null
        return if (a.contains(':')) a else "$a:8080"
    }

    fun toJson(ui: UiState): JSONObject {
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
        }
    }

    private fun num(d: Double?): Any = if (d != null && d.isFinite()) d else JSONObject.NULL

    private fun post(target: String, body: ByteArray) {
        try {
            val c = URL("http://$target/estado").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 1500
            c.readTimeout = 1500
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body) }
            c.responseCode
            c.disconnect()
        } catch (_: Exception) {
            // Multimídia desligado ou fora da rede: tenta de novo no próximo envio.
        }
    }
}
