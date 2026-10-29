package br.com.monitor2008.domain

import java.net.URLEncoder
import java.text.Normalizer

/** Rádio que transmite pela internet (catálogo aberto radio-browser.info). */
data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val favicon: String? = null,
    val tags: String = "",
    val state: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Boolean = false,
    val popularity: Int = 0,
) {
    /** Linha de baixo na lista: estado e os dois primeiros estilos. */
    val subtitle: String
        get() = listOf(state, tags.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(2).joinToString(", "))
            .filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * Regras puras do catálogo de rádios: endereço da busca e limpeza da lista (o catálogo tem a mesma
 * rádio cadastrada várias vezes, links quebrados e nomes com espaços sobrando).
 */
object RadioCatalog {
    /** Servidores espelho do catálogo; se um cair, tenta o próximo. */
    val SERVERS = listOf("de1", "de2", "fi1", "at1")

    fun searchUrl(server: String, name: String? = null, state: String? = null, limit: Int = 40): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val q = buildList {
            add("countrycode=BR")
            add("hidebroken=true")
            add("order=clickcount")
            add("reverse=true")
            add("limit=$limit")
            name?.trim()?.takeIf { it.isNotEmpty() }?.let { add("name=${enc(it)}") }
            state?.trim()?.takeIf { it.isNotEmpty() }?.let { add("state=${enc(it)}") }
        }
        return "https://$server.api.radio-browser.info/json/stations/search?" + q.joinToString("&")
    }

    /** Minúsculas, sem acento e só letras e números: "Rádio Jovem Pan FM " e "radio jovem pan fm" são a mesma. */
    fun normalize(name: String): String =
        Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9]"), "")

    /** Tira as sem nome ou sem link, e as repetidas (fica a mais ouvida). Mantém a ordem recebida. */
    fun clean(list: List<RadioStation>): List<RadioStation> {
        val valid = list
            .map { it.copy(name = it.name.trim().replace(Regex("\\s+"), " ")) }
            .filter { it.name.isNotBlank() && (it.url.startsWith("http://") || it.url.startsWith("https://")) }
        val best = valid.groupBy { normalize(it.name) }
            .mapValues { (_, same) -> same.maxWith(compareBy<RadioStation> { it.popularity }.thenBy { it.url.startsWith("https") }) }
        val seen = HashSet<String>()
        return valid.mapNotNull { st ->
            val key = normalize(st.name)
            if (key.isEmpty() || !seen.add(key)) null else best.getValue(key)
        }
    }

    /** Junta duas listas sem repetir (a primeira tem prioridade). */
    fun merge(first: List<RadioStation>, second: List<RadioStation>): List<RadioStation> = clean(first + second)

    /** Próxima (ou anterior) rádio da lista, dando a volta. Null se a lista estiver vazia. */
    fun step(list: List<RadioStation>, currentId: String?, forward: Boolean): RadioStation? {
        if (list.isEmpty()) return null
        val i = list.indexOfFirst { it.id == currentId }
        if (i < 0) return list.first()
        val n = list.size
        return list[(i + (if (forward) 1 else -1) + n) % n]
    }
}
