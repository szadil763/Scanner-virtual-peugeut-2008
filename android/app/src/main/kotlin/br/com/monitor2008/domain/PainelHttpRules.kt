package br.com.monitor2008.domain

import java.net.URI
import java.util.Locale

/** Cabeçalho HTTP parseado de uma requisição local recebida por PainelServerActivity. */
data class HttpRequestHead(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, String>,
)

/** Resultado da leitura e validação do cabeçalho Content-Length. */
sealed class ContentLength {
    data class Valid(val size: Int) : ContentLength()
    object TooLarge : ContentLength()
    object Invalid : ContentLength()
}

/**
 * Regras puras para validar e interpretar requisições HTTP recebidas no servidor local do painel.
 *
 * Mantém a lógica de segurança e parsing desacoplada de Android Sockets/WebView para testes JUnit.
 */
object PainelHttpRules {

    private val METHOD_REGEX = Regex("^[A-Z]{2,16}$")
    private val HOST_REGEX = Regex("^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")

    /**
     * Interpreta a linha de requisição e os cabeçalhos HTTP. Retorna null se a linha inicial for
     * malformada, tiver método inválido, alvo sem barra inicial ou tentativa de traversal.
     */
    fun parseRequestHead(rawHead: String): HttpRequestHead? {
        val lines = rawHead.replace("\r\n", "\n").split('\n')
        val firstLine = lines.firstOrNull { it.isNotBlank() } ?: return null
        val parts = firstLine.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null

        val method = parts[0].uppercase(Locale.ROOT)
        if (!METHOD_REGEX.matches(method)) return null

        val rawTarget = parts[1]
        if (!rawTarget.startsWith('/')) return null
        if (rawTarget.any { it.code < 0x20 || it.code == 0x7f || it.isWhitespace() }) return null
        if (rawTarget.contains("..")) return null

        val qIdx = rawTarget.indexOf('?')
        val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
        val query = if (qIdx >= 0) rawTarget.substring(qIdx + 1) else ""

        val headers = LinkedHashMap<String, String>()
        val startIndex = lines.indexOf(firstLine) + 1
        for (i in startIndex until lines.size) {
            val line = lines[i]
            if (line.isBlank()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase(Locale.ROOT)
            val value = line.substring(colon + 1).trim()
            if (name.isNotEmpty()) headers[name] = value
        }
        return HttpRequestHead(method = method, path = path, query = query, headers = headers)
    }

    /** Valida Content-Length evitando inteiros negativos, não numéricos ou corpos acima de [maxBody]. */
    fun parseContentLength(headers: Map<String, String>, maxBody: Int): ContentLength {
        val raw = headers["content-length"] ?: return ContentLength.Valid(0)
        val parsed = raw.trim().toLongOrNull() ?: return ContentLength.Invalid
        if (parsed < 0L) return ContentLength.Invalid
        if (parsed > maxBody.toLong()) return ContentLength.TooLarge
        return ContentLength.Valid(parsed.toInt())
    }

    /**
     * Proteção anti-CSRF para o servidor local: aceita requisições nativas sem Origin/Referer
     * (compatível com o app do celular) ou vindas apenas de origem loopback/rede local privada.
     * Rejeita páginas externas ou origens opacas ("Origin: null").
     */
    fun isAllowedLocalOrigin(origin: String?, referer: String?): Boolean {
        val o = origin?.trim().orEmpty()
        if (o.isNotEmpty()) {
            if (o.equals("null", ignoreCase = true)) return false
            return isLocalHttpUrl(o)
        }
        val r = referer?.trim().orEmpty()
        if (r.isNotEmpty()) {
            return isLocalHttpUrl(r)
        }
        return true
    }

    /** Confere se o Content-Type informado tem tipo de mídia exatamente application/json. */
    fun isJsonContentType(contentType: String?): Boolean {
        val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return false
        return mediaType == "application/json"
    }

    /** Confere se o Content-Type do envio de capa é uma imagem ou ausente/binário compatível. */
    fun isImageContentType(contentType: String?): Boolean {
        val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return true
        return mediaType.startsWith("image/") || mediaType == "application/octet-stream"
    }

    /**
     * Normaliza o endereço digitado em Ajustes para host:porta válido (padrão 8080),
     * rejeitando esquemas não-HTTP, caminhos, credenciais embutidas ou portas inválidas.
     */
    fun normalizePainelAddress(address: String, defaultPort: Int = 8080): String? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("://") && !trimmed.startsWith("http://", ignoreCase = true)) return null
        val noScheme = trimmed.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "").trimEnd('/')
        if (noScheme.isEmpty()) return null
        if (noScheme.any { it == '/' || it == '?' || it == '#' || it == '@' || it.isWhitespace() }) return null

        val colonCount = noScheme.count { it == ':' }
        if (colonCount > 1) return null

        val host: String
        val port: Int
        if (colonCount == 1) {
            val idx = noScheme.indexOf(':')
            host = noScheme.substring(0, idx)
            port = noScheme.substring(idx + 1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
        } else {
            host = noScheme
            port = defaultPort
        }
        if (!HOST_REGEX.matches(host)) return null
        return "$host:$port"
    }

    /** Texto padrão HTTP para os códigos de resposta emitidos pelo servidor local. */
    fun reasonPhrase(status: Int): String = when (status) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        413 -> "Payload Too Large"
        415 -> "Unsupported Media Type"
        else -> "Error"
    }

    private fun isLocalHttpUrl(rawUrl: String): Boolean {
        val uri = try {
            URI(rawUrl)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trim('[', ']') ?: return false
        return isLocalHost(host)
    }

    private fun isLocalHost(host: String): Boolean {
        if (host == "localhost" || host == "::1") return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val a = octets[0]
        val b = octets[1]
        return a == 127 ||
            a == 10 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254)
    }
}
