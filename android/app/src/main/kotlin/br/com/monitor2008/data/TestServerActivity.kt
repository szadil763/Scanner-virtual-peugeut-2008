package br.com.monitor2008.data

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/**
 * TESTE (só APK do multimídia): servidor HTTP mínimo dentro do app. Abre a porta 8080 e serve a
 * página de teste (assets/teste-painel). No celular, basta abrir o endereço mostrado na tela no
 * navegador. Se o relógio da página andar, o caminho celular → multimídia funciona.
 * Depois do teste, esta atividade sai do manifesto do multimídia.
 */
class TestServerActivity : Activity() {

    private var server: ServerSocket? = null
    @Volatile private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = TextView(this).apply {
            textSize = 26f
            setPadding(48, 48, 48, 48)
        }
        setContentView(info)

        val page = try {
            assets.open("teste-painel/index.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<h1>Página de teste não encontrada</h1>"
        }

        try {
            val s = ServerSocket(PORT)
            server = s
            running = true
            thread(isDaemon = true) {
                while (running) {
                    try {
                        val client = s.accept()
                        thread(isDaemon = true) { serve(client, page) }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            val lines = addresses().map { "http://$it:$PORT" }
            info.text = if (lines.isEmpty()) {
                "Servidor ligado na porta $PORT, mas o multimídia não tem endereço de rede.\n" +
                    "Conecte o multimídia no Wi-Fi ou no hotspot do celular e abra esta tela de novo."
            } else {
                "Servidor ligado.\n\nNo celular, abra o navegador em:\n\n" + lines.joinToString("\n\n")
            }
        } catch (e: Exception) {
            info.text = "Não consegui abrir a porta $PORT: ${e.message}"
        }
    }

    override fun onDestroy() {
        running = false
        try { server?.close() } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        const val PORT = 8080

        /** Endereços IPv4 deste aparelho, sem o loopback (ex.: 192.168.43.1). */
        fun addresses(): List<String> = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
        } catch (_: Exception) {
            emptyList()
        }

        private fun serve(client: Socket, page: String) {
            client.use { s ->
                try {
                    val reader = s.getInputStream().bufferedReader()
                    // Lê a linha de pedido e descarta os cabeçalhos até a linha vazia.
                    reader.readLine()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val body = page.toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Cache-Control: no-store\r\n" +
                        "Connection: close\r\n\r\n"
                    s.getOutputStream().apply {
                        write(head.toByteArray(Charsets.UTF_8))
                        write(body)
                        flush()
                    }
                } catch (_: Exception) {
                    // Cliente desconectou no meio: nada a fazer.
                }
            }
        }
    }
}
