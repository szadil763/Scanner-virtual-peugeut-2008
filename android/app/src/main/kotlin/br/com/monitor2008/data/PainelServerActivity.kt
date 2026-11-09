package br.com.monitor2008.data

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import java.util.Locale
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
import kotlinx.coroutines.runBlocking

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
    @Volatile private var capa: ByteArray? = null
    /** Posto achado pelo GPS deste multimídia (tem prioridade sobre o do celular). */
    @Volatile private var postoLocal: JSONObject? = null
    private var web: WebView? = null
    private var tts: TextToSpeech? = null
    /** Estado da voz deste aparelho, mostrado no painel (para saber por que a fala não sai). */
    @Volatile private var ttsStatus = "iniciando"

    /** Ponte para o painel falar a resposta de voz pelo texto-para-voz deste aparelho. */
    inner class VozBridge {
        @JavascriptInterface
        fun falar(texto: String) {
            val engine = tts
            if (engine == null) { ttsStatus = "voz não iniciou"; return }
            aplicarVoz()
            if (engine.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "painel-voz") == TextToSpeech.ERROR) {
                ttsStatus = "erro ao falar"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SessionResume.capture(application) // estado da sessão anterior, antes de esta tela mudar o que está na frente
        super.onCreate(savedInstanceState)
        val info = TextView(this).apply {
            textSize = 18f
            setPadding(24, 12, 24, 12)
        }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("pt", "BR"))
                ttsStatus = if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    "sem voz em português instalada"
                } else {
                    "pronta"
                }
            } else {
                ttsStatus = "sem motor de voz neste aparelho"
            }
        }
        // Confere em silêncio se há versão nova (no máximo a cada 6 h); o painel mostra um ponto em Mais.
        Updater.get(application).checkQuietly()
        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            addJavascriptInterface(VozBridge(), "monitorVoz")
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

    /**
     * Liga o acompanhamento da música ao abrir/voltar para o painel. Antes só a tela principal do app fazia
     * isso, então pelo ícone "Painel" o tocador nunca via o que o Spotify estava tocando.
     */
    override fun onResume() {
        super.onResume()
        if (Features.mediaControl) MediaWatcher.get(application).start()
        FloatingPanel.hide(this) // de volta ao painel: a miniatura some
        // Ligar o carro: continua a música e volta ao mapa. O batimento também detecta o multimídia acordando.
        SessionResume.startHeartbeat(application)
        SessionResume.resumeOnce(application)
        SessionResume.markPanelFront(application)
    }

    override fun onDestroy() {
        running = false
        try { server?.close() } catch (_: Exception) { }
        web?.destroy()
        tts?.shutdown()
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
                        // A miniatura sobre outros apps mostra estes números (o multimídia não lê o carro).
                        try {
                            val j = JSONObject(latestJson!!)
                            fun d(k: String): Double? = j.optDouble(k, Double.NaN).takeIf { it.isFinite() }
                            FloatingPanel.setExterno(
                                br.com.monitor2008.domain.FloatValues(
                                    speedKmh = d("speed"), kmPerL = d("kmL"), avgKmPerL = d("tripAvgKmL"),
                                    rpm = d("rpm"), coolantC = d("coolant"), batteryV = d("battery"),
                                ),
                            )
                        } catch (_: Exception) { }
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
                    method == "POST" && path == "/capa" -> {
                        if (body.isNotEmpty()) capa = body
                        send(out, 204, "text/plain; charset=utf-8", ByteArray(0))
                    }
                    method == "GET" && path == "/capa" -> {
                        val img = capa
                        if (img != null) send(out, 200, "image/jpeg", img)
                        else send(out, 404, "text/plain; charset=utf-8", ByteArray(0))
                    }
                    method == "POST" && path == "/comando" -> {
                        val obj = try { JSONObject(String(body, Charsets.UTF_8)) } catch (_: Exception) { null }
                        val acaoLocal = obj?.optString("acao") ?: ""
                        var respondido = false
                        if (acaoLocal == "mapa.limpar") Store(application).clearLastRoute() // destino limpo: não retoma
                        if (acaoLocal == "posto.localizar") {
                            postoLocal = null
                            thread(isDaemon = true) { localizarNoMultimidia() }
                        } else if (obj != null && acaoLocal == "musica.buscar") {
                            // Busca de música: espera o resultado (até 3 s) e devolve o texto, para o painel
                            // mostrar na hora o que o app fez (ou o erro) em vez de ficar sem resposta.
                            val resultado = arrayOfNulls<String>(1)
                            val trava = java.util.concurrent.CountDownLatch(1)
                            val textoBusca = obj.optString("texto")
                            runOnUiThread {
                                try {
                                    val ok = PainelLink.executarMidia(application, acaoLocal, textoBusca)
                                    resultado[0] = if (ok) PainelLink.avisoMusica.second.ifBlank { "Pedido enviado." }
                                    else "Este aparelho não controla música (falta o acesso às notificações)."
                                } catch (e: Throwable) {
                                    resultado[0] = "Erro ao buscar: ${e.message ?: e.javaClass.simpleName}"
                                } finally {
                                    trava.countDown()
                                }
                            }
                            trava.await(3, java.util.concurrent.TimeUnit.SECONDS)
                            val corpo = JSONObject().put("mensagem", resultado[0] ?: "O app não respondeu em 3 segundos.").toString()
                            send(out, 200, "application/json; charset=utf-8", corpo.toByteArray(Charsets.UTF_8))
                            respondido = true
                        } else if (obj != null && (acaoLocal.startsWith("musica.") || acaoLocal.startsWith("radio."))) {
                            // Música e rádio: tocam aqui, no multimídia (não passam pelo celular).
                            val texto = obj.optString("texto")
                            runOnUiThread { PainelLink.executarMidia(application, acaoLocal, texto) }
                        } else if (obj != null && (acaoLocal == "mapa.abrir" || acaoLocal == "mapa.rota")) {
                            // Mapa: abre o do próprio multimídia; se não conseguir, pede ao celular.
                            runOnUiThread {
                                val erro = try {
                                    if (acaoLocal == "mapa.abrir") AppLauncher(application).openMap(null)
                                    else obj.optString("texto").trim().takeIf { it.isNotEmpty() }?.let { q ->
                                        val endereco = br.com.monitor2008.domain.VoiceCommands.placeFor(q, Store(application).places()) ?: q
                                        AppLauncher(application).routeTo(endereco, null)
                                    } ?: "Digite o destino."
                                } catch (_: Exception) { "Não consegui abrir o mapa." }
                                if (erro != null && comandoValido(obj)) pending.add(System.currentTimeMillis() to obj.toString())
                                // Mapa aberto aqui: o Monitor fica por cima em miniatura (números do carro); o toque volta ao painel.
                                if (erro == null && FloatingPanel.available(application)) {
                                    FloatingPanel.show(application, PainelServerActivity::class.java)
                                }
                            }
                        } else if (obj != null && acaoLocal in AJUSTES_LOCAIS) {
                            // Ajustes deste multimídia: mudam as preferências daqui, não passam pelo celular.
                            runOnUiThread { executarAjuste(obj) }
                        } else if (obj != null && comandoValido(obj)) {
                            pending.add(System.currentTimeMillis() to obj.toString())
                            // Fila curta: se o celular não estiver ligado, não acumula comandos.
                            while (pending.size > 5) pending.poll()
                        }
                        if (!respondido) send(out, 204, "text/plain; charset=utf-8", ByteArray(0))
                    }
                    method == "GET" && path == "/estado" -> {
                        val age = if (latestAtMs == 0L) -1 else System.currentTimeMillis() - latestAtMs
                        val json = "{\"idadeMs\":$age,\"dados\":${latestJson ?: "null"},\"vozMultimidia\":\"$ttsStatus\",\"postoLocal\":${postoLocal?.toString() ?: "null"}}"
                        send(out, 200, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
                    }
                    method == "GET" && path == "/midia" ->
                        send(out, 200, "application/json; charset=utf-8", midiaLocal().toByteArray(Charsets.UTF_8))
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

    /** Vozes em português deste aparelho, na ordem de qualidade (vazia enquanto o leitor não iniciou). */
    private fun vozesPt(): List<br.com.monitor2008.domain.TtsVoiceOption> {
        val engine = tts ?: return emptyList()
        val pt = try { engine.voices.orEmpty().filter { it.locale.language == "pt" } } catch (_: Exception) { emptyList() }
        return br.com.monitor2008.domain.TtsVoices.options(pt.map {
            br.com.monitor2008.domain.TtsVoiceInfo(it.name, it.quality, it.isNetworkConnectionRequired)
        })
    }

    /** Aplica a voz escolhida (ou a guardada), a velocidade e o tom ao leitor deste aparelho. */
    private fun aplicarVoz(nome: String? = null) {
        val engine = tts ?: return
        val s = Store(application)
        engine.setSpeechRate(s.ttsRate)
        engine.setPitch(s.ttsPitch)
        (nome ?: s.ttsVoice)?.let { n -> engine.voices?.firstOrNull { it.name == n }?.let { engine.voice = it } }
    }

    /** Ajustes deste multimídia: o que a tela mostra e o que cada botão muda. */
    private fun executarAjuste(o: JSONObject) {
        val acao = o.optString("acao")
        val valor = o.optString("valor")
        if (valor.length > 200) return
        val st = Store(application)
        val lanc = AppLauncher(application)
        when (acao) {
            "ajuste.mapa" -> if (lanc.mapApps().any { it.pkg == valor }) lanc.mapPackage = valor
            "ajuste.musica" -> if (lanc.musicApps().any { it.pkg == valor }) lanc.musicPackage = valor
            "ajuste.voz" -> if (vozesPt().any { it.name == valor }) {
                st.ttsVoice = valor
                aplicarVoz()
            }
            "ajuste.vozOuvir" -> if (vozesPt().any { it.name == valor }) {
                aplicarVoz(valor)
                tts?.speak("Consumo de doze vírgula três quilômetros por litro.", TextToSpeech.QUEUE_FLUSH, null, "amostra")
            }
            "ajuste.velocidade" -> {
                st.ttsRate = br.com.monitor2008.domain.TtsVoices.step(st.ttsRate, o.optInt("valor", 0) > 0)
                aplicarVoz()
            }
            "ajuste.tom" -> {
                st.ttsPitch = br.com.monitor2008.domain.TtsVoices.step(st.ttsPitch, o.optInt("valor", 0) > 0)
                aplicarVoz()
            }
            "ajuste.continuar" -> st.resumeMedia = o.optBoolean("valor", true)
            "ajuste.continuarMapa" -> st.resumeMap = o.optBoolean("valor", true)
            "ajuste.lugarRemover" -> st.savePlaces(st.places().filter { it.first != valor })
            "sistema.notificacoes" -> SystemSettings.notificationAccess(this)
            "sistema.sobrepor" -> SystemSettings.overlay(this)
            // Teste da miniatura: sem a permissão, abre a tela para liberar; com ela, mostra a miniatura por 8 s.
            "ajuste.flutuanteTestar" -> if (!android.provider.Settings.canDrawOverlays(this)) {
                SystemSettings.overlay(this)
            } else {
                st.floatMode = "MONITOR"
                FloatingPanel.show(this, PainelServerActivity::class.java)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ FloatingPanel.hide(this) }, 8_000L)
            }
            "ajuste.flutuante" -> if (valor == "MONITOR" || valor == "ANDROID") {
                st.floatMode = valor
                if (valor == "ANDROID") FloatingPanel.hide(this)
            }
            "ajuste.flutuanteItem" -> br.com.monitor2008.domain.FloatItem.values().firstOrNull { it.name == valor }?.let { item ->
                st.floatItems = br.com.monitor2008.domain.FloatRules.serialize(
                    br.com.monitor2008.domain.FloatRules.toggle(br.com.monitor2008.domain.FloatRules.parse(st.floatItems), item),
                )
                FloatingPanel.refresh(this)
            }
            "ajuste.flutuanteTamanho" -> valor.toIntOrNull()?.let { st.floatSize = it.coerceIn(0, 2); FloatingPanel.refresh(this) }
            "atualizacao.verificar" -> Updater.get(application).check()
            "atualizacao.instalar" -> {
                val up = Updater.get(application)
                when (val s = up.state.value) {
                    is UpdateState.Available -> {
                        if (up.canInstall()) up.download(s.version) else startActivity(up.permissionIntent())
                    }
                    is UpdateState.Ready -> {
                        if (up.canInstall()) up.install(this, s.file) else startActivity(up.permissionIntent())
                    }
                    else -> {}
                }
            }
        }
    }

    /** Estado da atualização do software, para a tela Ajustes do painel. */
    private fun atualizacaoLocal(): JSONObject {
        val up = Updater.get(application)
        val (texto, podeInstalar, pronta) = when (val s = up.state.value) {
            is UpdateState.Idle -> Triple("Versão instalada: ${up.installedCode}. Toque em Verificar.", false, false)
            is UpdateState.Checking -> Triple("Verificando…", false, false)
            is UpdateState.UpToDate -> Triple("Já está na versão mais recente (${up.installedCode}).", false, false)
            is UpdateState.Available -> Triple("Versão nova: ${s.version.versionName}. Toque em Baixar e instalar.", true, false)
            is UpdateState.Downloading -> Triple("Baixando ${s.version.versionName}: ${(s.progress * 100).toInt()}%", false, false)
            is UpdateState.Ready -> Triple("Versão ${s.version.versionName} baixada. Toque em Instalar agora.", true, true)
            is UpdateState.Failed -> Triple(s.message, false, false)
        }
        return JSONObject().apply {
            put("texto", texto)
            put("podeInstalar", podeInstalar)
            put("pronta", pronta)
        }
    }

    /** Lista de ajustes locais para o painel (mapa, música, voz, lugares e rádio ao ligar). */
    private fun ajustesLocais(): JSONObject {
        val st = Store(application)
        val lanc = AppLauncher(application)
        return JSONObject().apply {
            put("mapas", JSONArray().apply { lanc.mapApps().forEach { put(JSONObject().put("pkg", it.pkg).put("nome", it.label)) } })
            put("mapaApp", lanc.mapPackage ?: "")
            put("musicas", JSONArray().apply { lanc.musicApps().forEach { put(JSONObject().put("pkg", it.pkg).put("nome", it.label)) } })
            put("musicaApp", lanc.musicPackage ?: "")
            put("vozes", JSONArray().apply {
                vozesPt().forEach { put(JSONObject().put("nome", it.name).put("rotulo", it.label).put("detalhe", it.detail)) }
            })
            put("vozAtual", st.ttsVoice ?: "")
            put("velocidade", Math.round(st.ttsRate * 10) / 10.0)
            put("tom", Math.round(st.ttsPitch * 10) / 10.0)
            put("continuar", st.resumeMedia)
            put("continuarMapa", st.resumeMap)
            put("flutuante", JSONObject().apply {
                put("modo", st.floatMode)
                put("permissao", android.provider.Settings.canDrawOverlays(this@PainelServerActivity))
                put("tamanho", st.floatSize)
                put("itens", JSONArray().apply {
                    br.com.monitor2008.domain.FloatRules.parse(st.floatItems).forEach { put(it.name) }
                })
                put("todos", JSONArray().apply {
                    br.com.monitor2008.domain.FloatItem.values().forEach { put(JSONObject().put("id", it.name).put("nome", it.label)) }
                })
            })
            put("lugares", JSONArray().apply {
                st.places().forEach { put(JSONObject().put("nome", it.first).put("endereco", it.second)) }
            })
        }
    }

    private var capaLocalKey: String? = null

    /**
     * Posto pelo GPS deste multimídia. Se ele não achar, pede ao celular (que aparece no
     * próximo envio do painel).
     */
    private fun localizarNoMultimidia() {
        val fix = try { runBlocking { PlaceFinder.current(application, timeoutMs = 8_000) } } catch (_: Exception) { null }
        if (fix == null) {
            pending.add(System.currentTimeMillis() to JSONObject().put("acao", "posto.localizar").toString())
            return
        }
        val achado = try { runBlocking { PlaceFinder.address(application, fix.latitude, fix.longitude) } } catch (_: Exception) { null }
        val endereco = if (achado.isNullOrBlank()) "Endereço não encontrado" else achado
        postoLocal = JSONObject().apply {
            put("ok", true)
            put("lat", fix.latitude)
            put("lon", fix.longitude)
            put("endereco", endereco)
            put("origem", "multimídia")
        }
    }

    /** Música e rádio deste aparelho (o multimídia tem o próprio acesso às notificações). */
    private fun midiaLocal(): String {
        val np = if (Features.mediaControl) MediaWatcher.get(application).now.value else null
        val art = np?.art
        val chave = if (np != null) "${np.pkg}|${np.title}|${np.artist}" else null
        if (np != null && art != null && chave != null && chave != capaLocalKey) {
            capaLocalKey = chave
            val pequena = android.graphics.Bitmap.createScaledBitmap(art, 300, 300, true)
            val img = ByteArrayOutputStream()
            pequena.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, img)
            capa = img.toByteArray()
        }
        val musica: Any = np?.let { n ->
            JSONObject().apply {
                put("app", n.app)
                put("titulo", n.title ?: "")
                put("artista", n.artist ?: "")
                put("tocando", n.playing)
                put("posMs", n.positionNow())
                put("durMs", n.durationMs)
                put("chave", "${n.pkg}|${n.title}|${n.artist}")
                put("capa", n.art != null)
            }
        } ?: JSONObject.NULL
        val rs = RadioPlayer.get(application).state.value
        val st = rs.station
        val radio: Any = if (st != null) {
            JSONObject().apply {
                put("nome", st.name)
                put("titulo", rs.streamTitle ?: "")
                put("tocando", rs.playing)
            }
        } else JSONObject.NULL
        // Lista de rádios: favoritas primeiro, depois as mais ouvidas do catálogo (busca em segundo plano).
        if (!radiosBuscando && System.currentTimeMillis() - radiosTentativaMs > 30_000) buscarRadios()
        val lista = JSONArray()
        PainelLink.radiosMultimidia.forEach { r ->
            lista.put(JSONObject().put("id", r.id).put("nome", r.name).put("assunto", r.subtitle)
                .put("tocando", st?.id == r.id))
        }
        return JSONObject().apply {
            put("musica", musica)
            put("radio", radio)
            put("radios", lista)
            put("ajustes", ajustesLocais())
            put("atualizacao", atualizacaoLocal())
            put("musicaAviso", JSONObject().put("id", PainelLink.avisoMusica.first).put("texto", PainelLink.avisoMusica.second))
            // Sem o acesso às notificações o painel não vê o que toca (o painel mostra como ativar).
            put("acessoNotificacoes", if (Features.mediaControl) MediaWatcher.get(application).allowed.value else true)
            put("musicaDisponivel", Features.mediaControl)
        }.toString()
    }

    @Volatile private var radiosBuscando = false
    @Volatile private var radiosTentativaMs = 0L

    /** Favoritas e as mais ouvidas do catálogo (precisa de internet do hotspot). Tenta a cada 30 s no máximo. */
    private fun buscarRadios() {
        radiosBuscando = true
        radiosTentativaMs = System.currentTimeMillis()
        thread(isDaemon = true) {
            try {
                val favoritas = Store(application).radioFavorites()
                val catalogo = try { runBlocking { br.com.monitor2008.data.RadioBrowser.search(limit = 30) } } catch (_: Exception) { emptyList() }
                PainelLink.radiosMultimidia = (favoritas + catalogo).distinctBy { it.id }
            } finally {
                radiosBuscando = false
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
        private const val MAX_BODY = 300_000

        /** Botões do painel que o celular executa (ver PainelLink.execute). */
        private val COMANDOS = setOf(
            "musica.playpause", "musica.next", "musica.prev",
            "radio.playpause", "radio.next", "radio.prev",
            "abastecer", "musica.buscar", "manutencao.feito", "diag.lerFalhas", "ajuste.sol", "ajuste.direcao", "ajuste.combustivel", "ajuste.calibracao", "ajuste.preco", "ajuste.eficiencia", "ajuste.carro", "voz.ouvir", "pendente.cancelar", "mapa.abrir", "mapa.rota", "mapa.limpar", "posto.localizar", "nuvem.sincronizar", "abastecimento.apagar", "abastecimento.editar", "ajuste.calibrarPeloCarro", "trajeto.zerar",
        )
        /** Ajustes que mudam as preferências deste multimídia (mapa, música, voz, lugares, rádio ao ligar). */
        private val AJUSTES_LOCAIS = setOf(
            "ajuste.mapa", "ajuste.musica", "ajuste.voz", "ajuste.vozOuvir", "ajuste.velocidade", "ajuste.tom",
            "ajuste.continuar", "ajuste.lugarRemover", "atualizacao.verificar", "atualizacao.instalar",
            "sistema.notificacoes", "sistema.sobrepor", "ajuste.flutuante", "ajuste.flutuanteItem", "ajuste.flutuanteTamanho", "ajuste.flutuanteTestar", "ajuste.continuarMapa",
        )
        private const val VALIDADE_MS = 20_000L
        /** Fila de comandos (instante, JSON) esperando o próximo envio do celular. */
        private val pending = ConcurrentLinkedQueue<Pair<Long, String>>()

        /** Confere o pedido antes de guardar: ação conhecida e, no abastecimento, valores plausíveis. */
        private fun comandoValido(o: JSONObject): Boolean {
            val acao = o.optString("acao")
            if (acao !in COMANDOS) return false
            if (acao == "diag.lerFalhas" || acao == "pendente.cancelar" || acao == "mapa.abrir" || acao == "mapa.limpar" || acao == "nuvem.sincronizar") return true
            if (acao == "mapa.rota") return o.optString("texto").isNotBlank() && o.optString("texto").length <= 150
            if (acao == "ajuste.sol" || acao == "voz.ouvir" || acao == "posto.localizar") return true
            if (acao == "ajuste.combustivel") return o.optString("valor") in setOf("E30", "E32", "ETANOL")
            if (acao == "ajuste.calibracao" || acao == "ajuste.preco" || acao == "ajuste.eficiencia") return o.has("valor")
            if (acao == "ajuste.carro") return o.optString("valor").isNotBlank()
            if (acao == "ajuste.direcao") return o.optString("valor") in setOf("AUTO", "ALWAYS_FULL", "ALWAYS_SIMPLE")
            if (acao == "manutencao.feito") return o.optString("id").isNotBlank() && o.optString("id").length <= 40
            if (acao == "musica.buscar") return o.optString("texto").isNotBlank() && o.optString("texto").length <= 100
            if (acao == "ajuste.calibrarPeloCarro") return o.optDouble("valor", Double.NaN).let { it.isFinite() && it in 2.0..40.0 }
            if (acao == "abastecimento.apagar") return o.optLong("id") > 0
            if (acao == "abastecimento.editar") {
                val litros = o.optDouble("litros", Double.NaN)
                val preco = o.optDouble("preco", Double.NaN)
                return o.optLong("id") > 0 &&
                    (o.isNull("litros") || !o.has("litros") || (litros.isFinite() && litros in 0.5..200.0)) &&
                    (o.isNull("preco") || !o.has("preco") || (preco.isFinite() && preco in 1.0..30.0)) &&
                    o.optString("posto").length <= 100
            }
            if (acao != "abastecer") return true
            if (o.optString("posto").length > 100 || o.optString("endereco").length > 200) return false
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
