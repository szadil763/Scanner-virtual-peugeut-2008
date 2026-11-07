package br.com.monitor2008.data

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Painel no multimídia (só o APK do multimídia). Abre uma porta na rede e atende:
 * - GET /          o painel (assets/painel), que se atualiza sozinho;
 * - POST /estado   recebe os números do carro enviados pelo celular (PainelLink);
 * - GET /estado    os últimos números e a idade deles (o painel lê daqui);
 * - GET /teste     página de teste de conexão.
 * A tela mostra o endereço a digitar no celular (Ajustes > Painel no multimídia).
 */
class PainelServerActivity : Activity() {

    private var server: ServerSocket? = null
    @Volatile private var running = false
    @Volatile private var latestJson: String? = null
    @Volatile private var latestAtMs = 0L
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = TextView(this).apply {
            textSize = 18f
            setPadding(24, 12, 24, 12)
        }
        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
        }
        web = view
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        try {
            val s = ServerSocket(PORT)
            server = s
            running = true
            thread(isDaemon = true, name = "painel-servidor") {
                while (running) {
                    val client = try { s.accept() } catch (_: Exception) { break }
                    thread(isDaemon = true) { handle(client) }
                }
            }
            val lines = addresses().map { "$it:$PORT" }
            info.text = if (lines.isEmpty()) {
                "Sem rede: ligue o hotspot do celular e conecte o multimídia nele."
            } else {
                "Endereço para o celular (Ajustes > Painel no multimídia): " + lines.joinToString(" ou ")
            }
            view.loadUrl("http://127.0.0.1:$PORT/")
        } catch (e: Exception) {
            info.text = "Não consegui abrir a porta $PORT: ${e.message}"
        }
    }

    override fun onDestroy() {
        running = false
        try { server?.close() } catch (_: Exception) { }
        web?.destroy()
        super.onDestroy()
    }

    private fun handle(client: Socket) {
        client.use { sock ->
            try {
                val input = sock.getInputStream()
                val out = sock.getOutputStream()
                val head = readHead(input)
                val lines = head.split("\r\n")
                val parts = lines.firstOrNull()?.split(" ") ?: return
                val method = parts.getOrElse(0) { "" }
                val path = parts.getOrElse(1) { "/" }.substringBefore('?')
                val len = lines.drop(1)
                    .firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                val body = if (len in 1..MAX_BODY) readBody(input, len) else ByteArray(0)
                when {
                    method == "POST" && path == "/estado" -> {
                        latestJson = String(body, Charsets.UTF_8)
                        latestAtMs = System.currentTimeMillis()
                        // Resposta: os comandos que os botões pediram desde o último envio.
                        val cmds = JSONArray()
                        val agora = System.currentTimeMillis()
                        while (true) {
                            val item = pending.poll() ?: break
                            // Comando velho (celular estava desligado) não vale: abastecimento não entra depois.
                            if (agora - item.first <= VALIDADE_MS) cmds.put(JSONObject(item.second))
                        }
                        val json = JSONObject().put("comandos", cmds).toString()
                        send(out, 200, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
                    }
                    method == "POST" && path == "/comando" -> {
                        val obj = try { JSONObject(String(body, Charsets.UTF_8)) } catch (_: Exception) { null }
                        if (obj != null && comandoValido(obj)) {
                            pending.add(System.currentTimeMillis() to obj.toString())
                            // Fila curta: se o celular não estiver ligado, não acumula comandos.
                            while (pending.size > 5) pending.poll()
                        }
                        send(out, 204, "text/plain; charset=utf-8", ByteArray(0))
                    }
                    method == "GET" && path == "/estado" -> {
                        val age = if (latestAtMs == 0L) -1 else System.currentTimeMillis() - latestAtMs
                        val json = "{\"idadeMs\":$age,\"dados\":${latestJson ?: "null"}}"
                        send(out, 200, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
                    }
                    method == "GET" && path == "/ping" ->
                        send(out, 200, "text/plain; charset=utf-8", "monitor2008".toByteArray(Charsets.UTF_8))
                    method == "GET" && (path == "/" || path == "/painel") ->
                        send(out, 200, "text/html; charset=utf-8", asset("painel/index.html"))
                    method == "GET" && path == "/teste" ->
                        send(out, 200, "text/html; charset=utf-8", asset("teste-painel/index.html"))
                    else -> send(out, 404, "text/plain; charset=utf-8", "Não encontrado".toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // Cliente cortou a conexão: nada a fazer.
            }
        }
    }

    private fun asset(name: String): ByteArray = try {
        assets.open(name).use { it.readBytes() }
    } catch (_: Exception) {
        "<h1>Arquivo $name não encontrado</h1>".toByteArray(Charsets.UTF_8)
    }

    /** Lê a linha de pedido e os cabeçalhos (até a linha vazia), byte a byte. */
    private fun readHead(input: InputStream): String {
        val buf = ByteArrayOutputStream()
        var a = 0
        var b = 0
        var c = 0
        var d = 0
        while (buf.size() < MAX_HEAD) {
            val x = input.read()
            if (x < 0) break
            buf.write(x)
            a = b; b = c; c = d; d = x
            if (a == 13 && b == 10 && c == 13 && d == 10) break
        }
        return String(buf.toByteArray(), Charsets.ISO_8859_1)
    }

    private fun readBody(input: InputStream, len: Int): ByteArray {
        val body = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = input.read(body, off, len - off)
            if (n < 0) break
            off += n
        }
        return body.copyOf(off)
    }

    private fun send(out: OutputStream, status: Int, type: String, body: ByteArray) {
        val reason = when (status) {
            200 -> "OK"
            204 -> "No Content"
            else -> "Not Found"
        }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    companion object {
        const val PORT = 8080
        private const val MAX_HEAD = 16_384
        private const val MAX_BODY = 65_536

        /** Botões do painel que o celular executa (ver PainelLink.execute). */
        private val COMANDOS = setOf(
            "musica.playpause", "musica.next", "musica.prev",
            "radio.playpause", "radio.next", "radio.prev",
            "abastecer",
        )
        private const val VALIDADE_MS = 20_000L
        /** Fila de comandos (instante, JSON) esperando o próximo envio do celular. */
        private val pending = ConcurrentLinkedQueue<Pair<Long, String>>()

        /** Confere o pedido antes de guardar: ação conhecida e, no abastecimento, valores plausíveis. */
        private fun comandoValido(o: JSONObject): Boolean {
            val acao = o.optString("acao")
            if (acao !in COMANDOS) return false
            if (acao != "abastecer") return true
            val preco = o.optDouble("preco", Double.NaN)
            val litros = o.optDouble("litros", Double.NaN)
            val valor = o.optDouble("valor", Double.NaN)
            val precoOk = preco.isFinite() && preco in 1.0..30.0
            val quantidadeOk = (litros.isFinite() && litros in 0.5..200.0) || (valor.isFinite() && valor in 1.0..2000.0)
            return precoOk && quantidadeOk
        }

        /** Endereços IPv4 deste aparelho, sem o loopback (ex.: 10.12.38.252). */
        fun addresses(): List<String> = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
