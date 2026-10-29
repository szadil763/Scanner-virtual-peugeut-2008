package br.com.monitor2008.data

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Toca o rádio pela internet em segundo plano (com o mapa ou outro app na frente). A sessão de mídia
 * faz o rádio aparecer no mini player, na notificação e nos botões do volante do multimídia.
 * Quem manda tocar é o [RadioPlayer].
 */
class RadioService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // true = respeita o foco de áudio: ao tocar o Spotify (ou o GPS falar), o rádio pausa ou abaixa.
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        session = MediaSession.Builder(this, player).setCallback(callback).build()
    }

    /**
     * O endereço do rádio chega em requestMetadata: por segurança, o Android não repassa o link da
     * mídia de quem controla (a tela) para quem toca (este serviço).
     */
    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val items = mediaItems.map { item ->
                val hls = item.requestMetadata.extras?.getBoolean(RadioPlayer.EXTRA_HLS) == true
                item.buildUpon()
                    .setUri(item.requestMetadata.mediaUri)
                    .setMimeType(if (hls) MimeTypes.APPLICATION_M3U8 else null)
                    .build()
            }.toMutableList()
            return Futures.immediateFuture(items)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** App fechado na lista de recentes: se não estiver tocando, encerra. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
