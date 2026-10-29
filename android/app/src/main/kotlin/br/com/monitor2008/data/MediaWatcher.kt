package br.com.monitor2008.data

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** O que está tocando agora em algum app de música. */
data class NowPlaying(
    val pkg: String,
    val app: String,
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val art: Bitmap? = null,
    /** 0 = sem duração (rádio ao vivo, Bluetooth que não informa). */
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    /** Momento (SystemClock.elapsedRealtime) em que [positionMs] valia; a tela soma o tempo passado. */
    val positionAt: Long = 0,
    val speed: Float = 1f,
) {
    fun positionNow(): Long =
        if (!playing || positionAt == 0L) positionMs else positionMs + ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong()
}

/**
 * Mini player: acompanha a sessão de mídia ativa (Spotify, YouTube Music, rádio...) e manda
 * tocar/pausar/próxima. Precisa do "acesso a notificações" ([MediaListenerService]); sem ele,
 * [allowed] fica falso e a tela mostra o botão para liberar. Um por processo ([get]).
 */
class MediaWatcher private constructor(private val app: Application) {
    private val msm: MediaSessionManager? = app.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(app, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val launcher = AppLauncher(app)
    private val store = Store(app)

    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()
    private val _allowed = MutableStateFlow(false)
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private var controller: MediaController? = null
    private var registered = false

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onSessionDestroyed() = refresh(null)
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { refresh(it) }

    /** Chamar ao abrir/voltar para o app: confere a permissão e começa a acompanhar. */
    fun start() {
        _allowed.value = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
        val m = msm ?: return
        if (!_allowed.value) return
        try {
            if (!registered) {
                m.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
                registered = true
            }
            refresh(m.getActiveSessions(component))
        } catch (e: SecurityException) {
            _allowed.value = false
        }
    }

    /** Prefere a sessão que está tocando; depois a do app de música escolhido; depois qualquer uma. */
    private fun refresh(sessions: List<MediaController>?) {
        val list = sessions ?: try { msm?.getActiveSessions(component) } catch (e: SecurityException) { null } ?: emptyList()
        val pick = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.packageName == launcher.musicPackage }
            ?: list.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = pick
            pick?.registerCallback(callback, handler)
        }
        update()
    }

    private fun update() {
        val c = controller
        if (c == null) {
            _now.value = null
            return
        }
        val m = c.metadata
        val st = c.playbackState
        if (c.packageName != app.packageName) store.addMediaSeen(c.packageName)
        _now.value = NowPlaying(
            pkg = c.packageName,
            app = launcher.label(c.packageName),
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            playing = st?.state == PlaybackState.STATE_PLAYING,
            art = m?.let {
                it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            },
            durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
            positionMs = st?.position?.coerceAtLeast(0L) ?: 0L,
            positionAt = st?.lastPositionUpdateTime ?: 0L,
            speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }

    /**
     * Começa a tocar no app [pkg] sem tirar o Monitor da frente: usa a sessão dele se já existir;
     * senão manda a tecla "tocar" para o app. Se em 3 s nada tocar, abre o app (o Spotify, por
     * exemplo, só aceita tocar depois de aberto uma vez).
     */
    fun playOn(pkg: String, activity: Activity?) {
        val sessions = try { msm?.getActiveSessions(component) } catch (e: SecurityException) { null } ?: emptyList()
        val existing = sessions.firstOrNull { it.packageName == pkg }
        if (existing != null) {
            existing.transportControls.play()
            return
        }
        val sent = sendPlayKey(pkg)
        handler.postDelayed({
            val n = _now.value
            if (n == null || n.pkg != pkg || !n.playing) launcher.openApp(pkg, activity)
        }, if (sent) 3_000L else 0L)
    }

    private fun sendPlayKey(pkg: String): Boolean {
        val receiver = app.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg), 0)
            .firstOrNull()?.activityInfo ?: return false
        return try {
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                app.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON)
                        .setComponent(ComponentName(receiver.packageName, receiver.name))
                        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)),
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    companion object {
        @Volatile private var instance: MediaWatcher? = null

        fun get(app: Application): MediaWatcher =
            instance ?: synchronized(this) { instance ?: MediaWatcher(app).also { instance = it } }
    }
}
