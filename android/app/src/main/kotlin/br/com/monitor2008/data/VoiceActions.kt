package br.com.monitor2008.data

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.withResumed
import br.com.monitor2008.MainActivity
import br.com.monitor2008.domain.VoiceCommand
import br.com.monitor2008.domain.VoiceCommands
import br.com.monitor2008.domain.VoiceIntent
import br.com.monitor2008.domain.VoiceNav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Pedidos de voz que não precisam da tela do Monitor: rota, mapa, rádio, música, pausar, próxima.
 * Funcionam também com o Monitor atrás do mapa (🎤 da janela flutuante ou a palavra "Monitor").
 * Os que precisam da tela (abrir uma aba, registrar abastecimento) trazem o Monitor para a frente.
 */
object VoiceActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** A tela do Monitor, enquanto está na frente (MainActivity grava em onResume e limpa em onPause). */
    @Volatile private var resumed: WeakReference<Activity>? = null

    fun onResumed(a: Activity) { resumed = WeakReference(a) }
    fun onPaused(a: Activity) { if (resumed?.get() === a) resumed = null }

    val screenVisible: Boolean get() = resumed?.get() != null

    /** Atende sem a tela do Monitor. */
    fun handles(cmd: VoiceCommand): Boolean = when (cmd.intent) {
        VoiceIntent.IR_PARA, VoiceIntent.TOCAR_RADIO, VoiceIntent.TOCAR_MUSICA,
        VoiceIntent.PAUSAR, VoiceIntent.PROXIMA, VoiceIntent.ANTERIOR -> true
        VoiceIntent.ABRIR -> cmd.nav == VoiceNav.MAPA ||
            // "tocar rádio" com o Monitor atrás: só toca, sem trazer a tela.
            (!screenVisible && cmd.play && (cmd.nav == VoiceNav.RADIO || cmd.nav == VoiceNav.MUSICA))
        else -> false
    }

    /** Traz o Monitor para a frente (a aba pedida é aberta pela tela ao voltar). */
    fun bringToFront(app: Application) {
        if (screenVisible) return
        try {
            app.startActivity(
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (_: Exception) { }
    }

    fun execute(app: Application, cmd: VoiceCommand, voice: VoiceAssistant) {
        when (cmd.intent) {
            // "ir para casa", "ir para a rua augusta 500": lugar salvo ou o endereço falado.
            VoiceIntent.IR_PARA -> {
                val q = cmd.query ?: return
                val address = VoiceCommands.placeFor(q, Store(app).places()) ?: q
                openApp { act -> AppLauncher(app).routeTo(address, act)?.let { voice.say(it) } }
            }
            VoiceIntent.ABRIR -> when (cmd.nav) {
                VoiceNav.MAPA -> openApp { act -> AppLauncher(app).openMap(act)?.let { voice.say(it) } }
                VoiceNav.RADIO -> if (!RadioPlayer.get(app).resume()) voice.say("Nenhuma rádio ouvida ainda. Diga, por exemplo: tocar rádio Jovem Pan.")
                VoiceNav.MUSICA -> MediaWatcher.get(app).let { w -> if (w.now.value?.playing != true) w.playPause() }
                else -> {}
            }
            // "desligar o rádio", "pausar", "próxima", "anterior": o rádio do app se estiver tocando; senão o app de música.
            VoiceIntent.PAUSAR, VoiceIntent.PROXIMA, VoiceIntent.ANTERIOR -> {
                val radio = RadioPlayer.get(app)
                val media = MediaWatcher.get(app)
                // O que tocava antes da escuta (ela pausa o som para ouvir melhor).
                val radioOn = voice.radioWasPlaying || radio.state.value.playing
                val mediaOn = voice.mediaWasPlaying || media.now.value?.playing == true
                when (cmd.intent) {
                    VoiceIntent.PAUSAR -> when {
                        radioOn -> { radio.pause(); voice.say("Rádio pausado.") }
                        mediaOn -> { media.pause(); voice.say("Música pausada.") }
                        else -> voice.say("Nada tocando agora.")
                    }
                    VoiceIntent.PROXIMA -> if (radioOn) radio.step(true) else media.next()
                    else -> if (radioOn) radio.step(false) else media.previous()
                }
            }
            // "tocar rádio jovem pan": favoritas primeiro, depois a lista de rádios pela internet.
            VoiceIntent.TOCAR_RADIO -> {
                val q = cmd.query ?: return
                scope.launch {
                    val player = RadioPlayer.get(app)
                    var offline = false
                    val found = VoiceCommands.bestRadio(q, player.state.value.favorites) ?: try {
                        val list = RadioBrowser.search(name = q, limit = 20).ifEmpty {
                            RadioBrowser.search(name = q.removeSuffix(" fm").trim(), limit = 20)
                        }
                        VoiceCommands.bestRadio(q, list) ?: list.firstOrNull()
                    } catch (e: Exception) { offline = true; null }
                    when {
                        found != null -> { player.play(found); voice.say("Tocando ${found.name}.") }
                        offline -> voice.say("Sem internet para procurar a rádio.")
                        else -> voice.say("Não achei a rádio $q.")
                    }
                }
            }
            // "tocar péricles no spotify": pela sessão do app, sem tirá-lo do fundo; senão abre o app com a busca.
            VoiceIntent.TOCAR_MUSICA -> {
                val q = cmd.query ?: return
                val launcher = AppLauncher(app)
                val pkg = launcher.musicFor(cmd.app)
                when {
                    pkg == null -> voice.say("Nenhum app de música instalado.")
                    MediaWatcher.get(app).playFromSearchQuietly(pkg, q) -> {}
                    else -> launcher.playFromSearch(pkg, q, resumed?.get())?.let { voice.say(it) }
                }
            }
            else -> {}
        }
    }

    /**
     * Abre o mapa: com o Monitor na frente, espera a janela da conversa fechar e a tela voltar ao
     * primeiro plano (vindo da tela de reconhecimento do sistema ela ainda está pausada e não vira
     * janela flutuante); com o Monitor atrás, abre direto.
     */
    private fun openApp(open: (Activity?) -> Unit) {
        val act = resumed?.get()
        if (act == null) { open(null); return }
        scope.launch {
            delay(500)
            val owner = act as? LifecycleOwner
            if (owner != null) owner.lifecycle.withResumed { open(act) } else open(act)
        }
    }
}
