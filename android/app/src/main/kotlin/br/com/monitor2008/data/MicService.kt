package br.com.monitor2008.data

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.monitor2008.MainActivity
import br.com.monitor2008.domain.WakeRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Microfone com o Monitor atrás do mapa. O Android só deixa um app usar o microfone em segundo
 * plano com um serviço em primeiro plano do tipo "microfone", iniciado com o app na frente.
 *
 * Fica ligado enquanto a janela flutuante própria está na tela (🎤 dela) ou enquanto a palavra
 * "Monitor" está ligada em Ajustes. Com a palavra ligada, escuta só "monitor" (Vosk, offline, sem
 * mandar áudio para lugar nenhum); ao ouvir, faz um bipe e abre o comando de voz normal. Para de
 * escutar enquanto o comando de voz ouve ou fala e durante ligações.
 */
class MicService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var model: Model? = null
    private var speech: SpeechService? = null
    private var looping = false

    override fun onCreate() {
        super.onCreate()
        // O microfone é um só: antes do comando de voz ouvir, a escuta da palavra solta o microfone.
        VoiceAssistant.beforeListen = { stopWake() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val store = Store(this)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Monitor 2008")
            .setContentText(if (store.wakeWord) "Diga \"Monitor\" para falar com o app" else "Comando de voz pronto")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            // Sem permissão do microfone ou iniciado com o app atrás: o Android recusa.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!looping) { looping = true; scope.launch { loop() } }
        return START_STICKY
    }

    /** A cada segundo: escuta a palavra quando pode; solta o microfone quando o comando de voz precisa. */
    private suspend fun loop() {
        val voice = VoiceAssistant.get(application)
        val audio = getSystemService(AudioManager::class.java)
        while (scope.isActive) {
            val store = Store(this)
            val inCall = audio?.mode == AudioManager.MODE_IN_CALL || audio?.mode == AudioManager.MODE_IN_COMMUNICATION
            val listen = WakeRules.shouldListen(
                enabled = store.wakeWord, modelReady = WakeModel.ready(this), micAllowed = voice.micAllowed(),
                assistantBusy = voice.busy, inCall = inCall,
            )
            if (listen && speech == null) startWake() else if (!listen && speech != null) stopWake()
            delay(1_000)
        }
    }

    private fun startWake() {
        try {
            val m = model ?: Model(WakeModel.path(this)).also { model = it }
            val rec = Recognizer(m, 16_000f, WakeRules.GRAMMAR)
            val s = SpeechService(rec, 16_000f)
            s.startListening(object : RecognitionListener {
                // Parcial: aciona assim que reconhece "monitor", sem esperar a pausa depois da palavra.
                override fun onPartialResult(hypothesis: String?) { if (WakeRules.isWakePartial(hypothesis)) heard() }
                override fun onResult(hypothesis: String?) { if (WakeRules.isWake(hypothesis)) heard() }
                override fun onFinalResult(hypothesis: String?) { if (WakeRules.isWake(hypothesis)) heard() }
                override fun onError(exception: Exception?) { stopWake() }
                override fun onTimeout() { stopWake() }
            })
            speech = s
        } catch (e: Exception) {
            // Modelo corrompido ou microfone ocupado: tenta de novo no próximo ciclo.
            stopWake()
        }
    }

    private fun stopWake() {
        val s = speech ?: return
        speech = null
        try { s.stop(); s.shutdown() } catch (_: Exception) { }
    }

    /** Ouviu "Monitor": solta o microfone, bipa e abre o comando de voz. */
    private fun heard() {
        if (speech == null) return // já acionou (parcial e final chegam os dois)
        stopWake()
        // O bipe toca quando o reconhecimento já está pronto para ouvir (antes ele tocava cedo e o começo do pedido se perdia).
        VoiceAssistant.get(application).listen(beepWhenReady = true)
    }

    override fun onDestroy() {
        VoiceAssistant.beforeListen = null
        stopWake()
        try { model?.close() } catch (_: Exception) { }
        model = null
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Comando de voz", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        private const val CHANNEL = "voz"
        private const val NOTIFICATION_ID = 2

        /**
         * Liga ou desliga conforme a necessidade (palavra ligada ou janela flutuante na tela). Chamar
         * com o app na frente: com ele atrás o Android não deixa iniciar o serviço de microfone.
         */
        fun update(context: Context) {
            val app = context.applicationContext as Application
            val needed = (Store(app).wakeWord && WakeModel.ready(app) || FloatingPanel.showing) &&
                VoiceAssistant.get(app).micAllowed()
            val intent = Intent(app, MicService::class.java)
            try {
                if (needed) ContextCompat.startForegroundService(app, intent) else app.stopService(intent)
            } catch (_: Exception) { }
        }
    }
}
