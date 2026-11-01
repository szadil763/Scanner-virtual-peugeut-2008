package br.com.monitor2008.data

import android.app.Application
import br.com.monitor2008.domain.ResumeRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ligar o carro e encontrar o app como ficou: a mesma aba (guardada pela tela) e a mesma rádio ou
 * música tocando. Enquanto o app está vivo, grava a cada 30 s que está vivo e o que está tocando;
 * na primeira abertura depois de ligar, [resumeOnce] decide pelo [ResumeRules] se continua.
 */
object SessionResume {
    private const val BEAT_MS = 30_000L
    @Volatile private var started = false
    @Volatile private var resumed = false

    /** Chamar uma vez por processo (o MonitorEngine faz isso). */
    fun startHeartbeat(app: Application, scope: CoroutineScope) {
        if (started) return
        started = true
        val store = Store(app)
        scope.launch {
            // As marcas da sessão anterior só podem ser trocadas depois que o resumeOnce as leu (até 2 min).
            var waited = 0L
            while (!resumed && waited < 120_000L) { delay(1_000); waited += 1_000 }
            while (isActive) {
                val now = System.currentTimeMillis()
                playingPkg(app)?.let { store.saveLastPlaying(it, now) }
                store.aliveAtMs = now
                delay(BEAT_MS)
            }
        }
    }

    private fun playingPkg(app: Application): String? {
        if (RadioPlayer.get(app).state.value.playing) return app.packageName
        return MediaWatcher.get(app).now.value?.takeIf { it.playing }?.pkg
    }

    /** Na primeira tela do processo: se a música ou a rádio tocava quando o carro desligou, continua. */
    fun resumeOnce(app: Application) {
        if (resumed) return
        resumed = true
        val store = Store(app)
        if (!store.resumeMedia) return
        val pkg = store.lastPlayingPkg
        if (!ResumeRules.shouldResume(store.aliveAtMs, store.lastPlayingAtMs, pkg)) return
        if (pkg == app.packageName) {
            RadioPlayer.get(app).resume()
        } else if (pkg != null) {
            MediaWatcher.get(app).resumeQuietly(pkg)
        }
    }
}
