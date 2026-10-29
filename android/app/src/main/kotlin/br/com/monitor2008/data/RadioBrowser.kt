package br.com.monitor2008.data

import br.com.monitor2008.domain.RadioCatalog
import br.com.monitor2008.domain.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * Busca rádios do Brasil no catálogo aberto radio-browser.info (gratuito, sem cadastro), pela
 * internet do celular. Só lê a lista; o som vem direto do site de cada rádio.
 */
object RadioBrowser {

    /** As mais ouvidas do Brasil, ou as que têm [name] no nome. Lança exceção sem internet. */
    suspend fun search(name: String? = null, state: String? = null, limit: Int = 40): List<RadioStation> =
        withContext(Dispatchers.IO) {
            var last: Exception? = null
            for (server in RadioCatalog.SERVERS) {
                try {
                    return@withContext RadioCatalog.clean(parse(get(RadioCatalog.searchUrl(server, name, state, limit))))
                } catch (e: Exception) {
                    last = e
                }
            }
            throw last ?: IllegalStateException("sem servidor")
        }

    /** Rádios do estado. O catálogo grava o estado com e sem acento ("São Paulo", "Sao Paulo"): busca os dois. */
    suspend fun region(state: String): List<RadioStation> {
        val plain = Normalizer.normalize(state, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val first = search(state = state)
        return if (plain == state) first else RadioCatalog.merge(first, runCatching { search(state = plain) }.getOrDefault(emptyList()))
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "Monitor2008/0.1 (app de carro)")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: String): List<RadioStation> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RadioStation(
                id = o.optString("stationuuid"),
                name = o.optString("name"),
                url = o.optString("url_resolved").ifBlank { o.optString("url") },
                favicon = o.optString("favicon").takeIf { it.startsWith("http") },
                tags = o.optString("tags"),
                state = o.optString("state"),
                codec = o.optString("codec"),
                bitrate = o.optInt("bitrate"),
                hls = o.optInt("hls") == 1,
                popularity = o.optInt("clickcount"),
            )
        }
    }
}
