package br.com.monitor2008.data

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import br.com.monitor2008.domain.RadioCatalog
import br.com.monitor2008.domain.RadioStation
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RadioState(
    val station: RadioStation? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    /** Música ou programa que a rádio informa (nem todas informam). */
    val streamTitle: String? = null,
    val favorites: List<RadioStation> = emptyList(),
)

/**
 * Controle do rádio pela internet para as telas: manda o [RadioService] tocar, pausar e trocar de
 * rádio, e guarda as favoritas. Um por processo ([get]).
 */
class RadioPlayer private constructor(private val app: Application) {
    private val store = Store(app)
    private val _state = MutableStateFlow(RadioState(station = store.lastRadio, favorites = store.radioFavorites()))
    val state: StateFlow<RadioState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying, error = if (isPlaying) null else it.error) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(playing = false, buffering = false, error = "Não foi possível tocar: rádio fora do ar ou sem internet.")
            }
        }

        /** Rádios que informam a música tocando mandam o nome no lugar do título. */
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val t = mediaMetadata.title?.toString()?.trim()
            _state.update { it.copy(streamTitle = t?.takeIf { s -> s.isNotEmpty() && s != it.station?.name }) }
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { block(it); return }
        pending += block
        if (future != null) return
        val f = MediaController.Builder(app, SessionToken(app, ComponentName(app, RadioService::class.java))).buildAsync()
        future = f
        f.addListener({
            val c = try {
                f.get()
            } catch (e: Exception) {
                future = null
                pending.clear()
                _state.update { it.copy(buffering = false, error = "Não foi possível iniciar o rádio.") }
                return@addListener
            }
            controller = c
            c.addListener(listener)
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(app))
    }

    fun play(st: RadioStation) {
        store.lastRadio = st
        _state.update { it.copy(station = st, buffering = true, error = null, streamTitle = null) }
        withController { c ->
            val extras = Bundle().apply { putBoolean(EXTRA_HLS, st.hls) }
            val item = MediaItem.Builder()
                .setMediaId(st.id)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(st.url)).setExtras(extras).build())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(st.name)
                        .setArtist(st.subtitle.ifBlank { "Rádio" })
                        .setArtworkUri(st.favicon?.let { Uri.parse(it) })
                        .setIsPlayable(true)
                        .build(),
                )
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    /** Toca a última rádio ouvida (ou a primeira favorita). Falso se nunca ouviu nenhuma. */
    fun resume(): Boolean {
        val c = controller
        if (c != null && c.mediaItemCount > 0) {
            c.play()
            return true
        }
        val st = _state.value.station ?: _state.value.favorites.firstOrNull() ?: return false
        play(st)
        return true
    }

    fun toggle() {
        if (_state.value.playing) controller?.pause() else resume()
    }

    fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
        _state.update { it.copy(playing = false, buffering = false, streamTitle = null) }
    }

    /** Próxima ou anterior entre as favoritas (botões ⏭ ⏮ com o rádio tocando). */
    fun step(forward: Boolean) {
        RadioCatalog.step(_state.value.favorites, _state.value.station?.id, forward)?.let { play(it) }
    }

    fun isFavorite(st: RadioStation?) = st != null && _state.value.favorites.any { it.id == st.id }

    fun toggleFavorite(st: RadioStation) {
        val list = _state.value.favorites
        val next = if (list.any { it.id == st.id }) list.filter { it.id != st.id } else list + st
        store.saveRadioFavorites(next)
        _state.update { it.copy(favorites = next) }
    }

    companion object {
        const val EXTRA_HLS = "hls"

        @Volatile private var instance: RadioPlayer? = null

        fun get(app: Application): RadioPlayer =
            instance ?: synchronized(this) { instance ?: RadioPlayer(app).also { instance = it } }
    }
}
