package br.com.monitor2008.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

sealed class WakeModelState {
    data object Missing : WakeModelState()
    data class Downloading(val progress: Float) : WakeModelState()
    data object Ready : WakeModelState()
    data class Failed(val message: String) : WakeModelState()
}

/**
 * Modelo de voz em português do reconhecimento offline (Vosk, ~40 MB), baixado uma vez quando o
 * dono liga a palavra "Monitor" em Ajustes; assim o APK não fica pesado para quem não usa.
 */
object WakeModel {
    private const val URL_MODEL = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<WakeModelState>(WakeModelState.Missing)
    val state: StateFlow<WakeModelState> = _state.asStateFlow()

    private fun dir(ctx: Context) = File(ctx.filesDir, "vosk-pt")
    private fun marker(ctx: Context) = File(dir(ctx), ".pronto")

    fun path(ctx: Context): String = dir(ctx).absolutePath

    fun ready(ctx: Context): Boolean = marker(ctx).exists()

    /** Atualiza o estado (ao abrir Ajustes). */
    fun refresh(ctx: Context) {
        if (_state.value is WakeModelState.Downloading) return
        _state.value = if (ready(ctx)) WakeModelState.Ready else WakeModelState.Missing
    }

    fun download(ctx: Context) {
        val app = ctx.applicationContext
        if (_state.value is WakeModelState.Downloading || ready(app)) { refresh(app); return }
        _state.value = WakeModelState.Downloading(0f)
        scope.launch {
            _state.value = try {
                withContext(Dispatchers.IO) { fetch(app) }
                WakeModelState.Ready
            } catch (e: Exception) {
                dir(app).deleteRecursively()
                WakeModelState.Failed("O download não terminou (${e.message ?: e.javaClass.simpleName}). Confira a internet e tente de novo.")
            }
        }
    }

    private suspend fun fetch(app: Context) {
        val target = dir(app)
        target.deleteRecursively()
        target.mkdirs()
        var url = URL_MODEL
        var c: HttpURLConnection? = null
        for (attempt in 0 until 5) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Monitor2008")
            when (conn.responseCode) {
                200 -> { c = conn; break }
                301, 302, 303, 307, 308 -> {
                    url = URL(URL(url), conn.getHeaderField("Location") ?: throw IllegalStateException("redirecionamento")).toString()
                    conn.disconnect()
                }
                else -> { conn.disconnect(); throw IllegalStateException("HTTP ${conn.responseCode}") }
            }
        }
        val conn = c ?: throw IllegalStateException("redirecionamentos demais")
        try {
            val total = conn.contentLengthLong
            var read = 0L
            var last = 0f
            // Lê o zip direto da rede, sem guardar o arquivo inteiro; tira a pasta de cima ("vosk-model-small-pt-0.3/").
            val counting = object : java.io.FilterInputStream(conn.inputStream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0) read += n
                    return n
                }
            }
            ZipInputStream(counting.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name.substringAfter('/', "")
                    if (name.isEmpty() || name.contains("..")) continue
                    val out = File(target, name)
                    if (e.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                    val p = if (total > 0) read.toFloat() / total else 0f
                    if (p - last >= 0.02f) {
                        last = p
                        withContext(Dispatchers.Main) { _state.value = WakeModelState.Downloading(p.coerceAtMost(1f)) }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (target.list().isNullOrEmpty()) throw IllegalStateException("arquivo vazio")
        marker(app).writeText("ok")
    }

    fun delete(ctx: Context) {
        dir(ctx).deleteRecursively()
        _state.value = WakeModelState.Missing
    }
}
