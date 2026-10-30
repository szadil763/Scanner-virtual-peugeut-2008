package br.com.monitor2008.data

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import br.com.monitor2008.domain.RemoteVersion
import br.com.monitor2008.domain.UpdateChannel
import br.com.monitor2008.domain.UpdateRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpToDate(val channel: UpdateChannel) : UpdateState()
    data class Available(val version: RemoteVersion) : UpdateState()
    data class Downloading(val version: RemoteVersion, val progress: Float) : UpdateState()
    /** Baixado: falta o dono tocar em "Instalar" na tela do Android. */
    data class Ready(val version: RemoteVersion, val file: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/**
 * Atualização pelo próprio app: consulta a página de Releases do GitHub do canal escolhido, baixa o
 * APK e abre a instalação do Android (a confirmação final é sempre do usuário; sem Play Store, o
 * Android não deixa instalar sozinho). Os dados ficam: o APK é assinado sempre com a mesma chave.
 * Um por processo ([get]).
 */
class Updater private constructor(private val app: Application) {
    private val store = Store(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val installedCode: Int = try {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (e: Exception) {
        0
    }

    var channel: UpdateChannel
        get() = UpdateChannel.values().firstOrNull { it.name == store.updateChannel } ?: UpdateChannel.ESTAVEL
        set(v) {
            store.updateChannel = v.name
            _state.value = UpdateState.Idle
        }

    /** Ao abrir o app: confere no máximo a cada 6 horas, sem mostrar erro (pode estar sem internet). */
    fun checkQuietly() {
        if (System.currentTimeMillis() - store.updateCheckedMs < 6 * 3_600_000L) return
        check(quiet = true)
    }

    fun check(quiet: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch {
            val ch = channel
            _state.value = try {
                val remote = fetch(ch)
                store.updateCheckedMs = System.currentTimeMillis()
                when {
                    remote == null -> if (ch == UpdateChannel.ESTAVEL) {
                        UpdateState.Failed("Ainda não há versão estável publicada.")
                    } else UpdateState.Failed("Não foi possível ler a versão publicada.")
                    UpdateRules.isNewer(remote, installedCode) -> UpdateState.Available(remote)
                    else -> UpdateState.UpToDate(ch)
                }
            } catch (e: Exception) {
                if (quiet) UpdateState.Idle else UpdateState.Failed("Sem internet ou GitHub fora do ar. Tente de novo.")
            }
        }
    }

    private suspend fun fetch(ch: UpdateChannel): RemoteVersion? = withContext(Dispatchers.IO) {
        val c = URL(UpdateRules.releaseUrl(OWNER, REPO, ch)).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Monitor2008")
        try {
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val assets = o.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name") == Features.apkName }?.optString("browser_download_url")
            UpdateRules.parse(o.optString("body") + "\n" + o.optString("name"), apk)
        } finally {
            c.disconnect()
        }
    }

    fun download(v: RemoteVersion) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(v, 0f)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val out = File(dir, "monitor-${v.versionCode}.apk")
                    // O link do GitHub redireciona para outro servidor (https para https: o Java segue sozinho).
                    val c = URL(v.apkUrl).openConnection() as HttpURLConnection
                    c.connectTimeout = 15_000
                    c.readTimeout = 30_000
                    c.instanceFollowRedirects = true
                    c.setRequestProperty("User-Agent", "Monitor2008")
                    try {
                        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
                        val total = c.contentLengthLong
                        var done = 0L
                        var lastShown = 0f
                        c.inputStream.use { input ->
                            out.outputStream().use { output ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    output.write(buf, 0, n)
                                    done += n
                                    val p = if (total > 0) done.toFloat() / total else 0f
                                    if (p - lastShown >= .02f) {
                                        lastShown = p
                                        withContext(Dispatchers.Main) { _state.value = UpdateState.Downloading(v, p) }
                                    }
                                }
                            }
                        }
                        if (total > 0 && done != total) throw IllegalStateException("download incompleto")
                    } finally {
                        c.disconnect()
                    }
                    out
                }
                _state.value = UpdateState.Ready(v, file)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("O download não terminou. Confira a internet e tente de novo.")
            }
        }
    }

    /** O Android só deixa abrir a instalação se o usuário liberou "instalar apps desconhecidos" para o Monitor. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || app.packageManager.canRequestPackageInstalls()

    /** Tela do Android onde o usuário libera "instalar apps desconhecidos" para o Monitor. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Abre a tela "Instalar" do Android com o APK baixado. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    companion object {
        const val OWNER = "szadil763"
        const val REPO = "Scanner-virtual-peugeut-2008"

        @Volatile private var instance: Updater? = null

        fun get(app: Application): Updater =
            instance ?: synchronized(this) { instance ?: Updater(app).also { instance = it } }
    }
}
