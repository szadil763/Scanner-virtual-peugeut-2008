package br.com.monitor2008.data

import android.app.Application
import android.os.Handler
import android.os.Looper
import br.com.monitor2008.domain.FuelQuality
import br.com.monitor2008.domain.ResumeRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ligar o carro e encontrar tudo como ficou: a mesma rádio ou música tocando e o mapa no mesmo destino.
 * Enquanto o app está vivo, grava a cada 30 s que está vivo e o que está tocando; a tela que estava na
 * frente ("PAINEL", "MAPA" ou "MUSICA") e o último destino são gravados quando mudam.
 *
 * Retoma em dois casos: na primeira abertura do processo ([resumeOnce], o multimídia reiniciou) e quando
 * o batimento volta depois de mais de 3 min parado (o multimídia dormiu com o carro desligado).
 */
object SessionResume {
    private const val BEAT_MS = 30_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false
    @Volatile private var resumed = false
    @Volatile private var holdBeat = false

    // O que a sessão anterior deixou, lido antes de qualquer tela mudar "o que está na frente".
    @Volatile private var captured = false
    private var frontBefore: String? = null
    private var frontBeforeAtMs = 0L

    /** Chamar bem no começo (onCreate das telas e do BootReceiver): guarda o estado da sessão anterior. */
    fun capture(app: Application) {
        if (captured) return
        captured = true
        val store = Store(app)
        frontBefore = store.lastFront
        frontBeforeAtMs = store.lastFrontAtMs
    }

    /** Marca o Monitor como a tela da frente (as telas chamam em onResume). */
    fun markPanelFront(app: Application) {
        val store = Store(app)
        store.lastFront = "PAINEL"
        store.lastFrontAtMs = System.currentTimeMillis()
    }

    /** Chamar uma vez por processo (o MonitorEngine e o Painel fazem isso). */
    fun startHeartbeat(app: Application, scope: CoroutineScope = this.scope) {
        if (started) return
        started = true
        val store = Store(app)
        scope.launch {
            // As marcas da sessão anterior só podem ser trocadas depois que o resumeOnce as leu (até 2 min).
            var waited = 0L
            while (!resumed && waited < 120_000L) { delay(1_000); waited += 1_000 }
            while (isActive) {
                val now = System.currentTimeMillis()
                // O batimento voltou depois de muito tempo: o multimídia dormiu com o carro desligado.
                if (resumed && ResumeRules.wokeFromOff(store.aliveAtMs, now)) {
                    holdBeat = true
                    try { resumeSession(app, store.lastFront, store.lastFrontAtMs) } finally { holdBeat = false }
                }
                if (!holdBeat) {
                    playingPkg(app)?.let { store.saveLastPlaying(it, now) }
                    store.aliveAtMs = now
                }
                delay(BEAT_MS)
            }
        }
    }

    private fun playingPkg(app: Application): String? {
        if (RadioPlayer.get(app).state.value.playing) return app.packageName
        return MediaWatcher.get(app).now.value?.takeIf { it.playing }?.pkg
    }

    /** Na primeira tela do processo: continua a música ou rádio e volta ao mapa, como estavam ao desligar. */
    fun resumeOnce(app: Application) {
        if (resumed) return
        capture(app)
        resumed = true
        resumeSession(app, frontBefore, frontBeforeAtMs)
    }

    private fun resumeSession(app: Application, front: String?, frontAtMs: Long) {
        val store = Store(app)
        // Música e rádio: no thread principal (o player da rádio exige).
        if (store.resumeMedia) {
            val pkg = store.lastPlayingPkg
            if (ResumeRules.shouldResume(store.aliveAtMs, store.lastPlayingAtMs, pkg)) {
                Handler(Looper.getMainLooper()).post {
                    MediaWatcher.get(app).start()
                    if (pkg == app.packageName) RadioPlayer.get(app).resume()
                    else if (pkg != null) MediaWatcher.get(app).resumeQuietly(pkg)
                }
            }
        }
        if (store.resumeMap && ResumeRules.shouldResumeMap(front, frontAtMs, System.currentTimeMillis())) retomarMapa(app, store)
    }

    /**
     * Reabre o app de mapa no último destino (ou só o app, se não havia rota). Se o carro desligou a menos de
     * 300 m do destino, a viagem acabou: não abre nada e esquece a rota.
     */
    private fun retomarMapa(app: Application, store: Store) {
        scope.launch {
            delay(5_000) // deixa a música e o sistema subirem primeiro
            val agora = System.currentTimeMillis()
            val destino = store.lastRouteAddress?.takeIf { agora - store.lastRouteAtMs in 0..ResumeRules.MAP_MAX_AGE_MS }
            val lat = store.lastRouteLat
            val lon = store.lastRouteLon
            if (destino != null && lat != null && lon != null) {
                val fix = try { PlaceFinder.current(app, timeoutMs = 8_000) } catch (_: Exception) { null }
                val distancia = fix?.let { FuelQuality.distanceM(it.latitude, it.longitude, lat, lon) }
                if (ResumeRules.arrived(distancia)) {
                    store.clearLastRoute()
                    store.lastFront = "PAINEL"
                    return@launch
                }
            }
            withContext(Dispatchers.Main) {
                val launcher = AppLauncher(app)
                val erro = if (destino != null) launcher.routeTo(destino, null) else launcher.openMap(null)
                // O Monitor fica por cima do mapa em miniatura, com os números do carro.
                if (erro == null && FloatingPanel.available(app)) FloatingPanel.show(app, PainelServerActivity::class.java)
            }
        }
    }
}
