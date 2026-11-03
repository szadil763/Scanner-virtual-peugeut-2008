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
import br.com.monitor2008.ui.MonitorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.Recognizer
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor

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
    private var wake: WakeListener? = null
    private var looping = false

    override fun onCreate() {
        super.onCreate()
        // O microfone é um só: antes do comando de voz ouvir, a escuta da palavra solta o microfone.
        VoiceAssistant.beforeListen = { stopWake() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Botão "Desligar escuta" da notificação.
        if (intent?.action == ACTION_SNOOZE) {
            snoozed = true
            stopWake()
            if (!FloatingPanel.showing) { stopSelf(); return START_NOT_STICKY }
        }
        createChannel()
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val store = Store(this)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Monitor 2008")
            .setContentText(if (store.wakeWord) "Diga \"Monitor\". Desliga sozinho 15 min depois do carro." else "Comando de voz pronto")
            .setContentIntent(open)
            .apply {
                if (store.wakeWord) addAction(
                    android.R.drawable.ic_lock_silent_mode, "Desligar escuta",
                    PendingIntent.getService(this@MicService, 2, Intent(this@MicService, MicService::class.java).setAction(ACTION_SNOOZE),
                        PendingIntent.FLAG_IMMUTABLE),
                )
            }
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
        // App aberto ou motor que acabou de ligar renovam os 15 minutos (reconectar ao adaptador não renova).
        if (intent?.getBooleanExtra(EXTRA_RENEW, false) == true) lastActivity = System.currentTimeMillis()
        if (!looping) { looping = true; scope.launch { loop() } }
        return START_STICKY
    }

    /** A cada segundo: escuta a palavra quando pode; solta o microfone quando o comando de voz precisa. */
    private suspend fun loop() {
        val voice = VoiceAssistant.get(application)
        val engine = MonitorEngine.get(application)
        val audio = getSystemService(AudioManager::class.java)
        while (scope.isActive) {
            val store = Store(this)
            val now = System.currentTimeMillis()
            val ui = engine.ui.value
            // Motor girando, e não só "conectado ao adaptador": ele fica ligado na tomada com o carro desligado.
            val engineRunning = WakeRules.engineRunning(!ui.simulated && ui.conn.state == ConnState.CONNECTED, ui.sample?.rpm)
            if (engineRunning) lastActivity = now
            if (voice.lastUseMs > lastActivity) lastActivity = voice.lastUseMs
            val awake = WakeRules.awake(now, engineRunning, lastActivity)
            // Carro desligado há 15 min (ou "desativar"), sem uso da voz e sem a janela flutuante: desliga o microfone e some.
            if ((!awake || snoozed) && !FloatingPanel.showing) {
                stopWake()
                stopSelf()
                return
            }
            val inCall = audio?.mode == AudioManager.MODE_IN_CALL || audio?.mode == AudioManager.MODE_IN_COMMUNICATION
            // No celular, só dentro do carro: motor girando ou Bluetooth de áudio do multimídia conectado.
            val inCar = WakeRules.inCar(phone = !Features.mediaControl, engineRunning = engineRunning, bluetoothAudio = bluetoothAudio())
            val listen = awake && inCar && !snoozed && WakeRules.shouldListen(
                enabled = Features.wakeWord && store.wakeWord, modelReady = WakeModel.ready(this), micAllowed = voice.micAllowed(),
                assistantBusy = voice.busy, inCall = inCall,
            )
            if (listen && wake == null) startWake() else if (!listen && wake != null) stopWake()
            delay(1_000)
        }
    }

    /** Celular ligado a um aparelho de áudio Bluetooth (o multimídia do carro, para chamadas e música). */
    @android.annotation.SuppressLint("MissingPermission") // sem a permissão, cai no catch e vale "não"
    private fun bluetoothAudio(): Boolean = try {
        val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        adapter != null && adapter.isEnabled && (
            adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) == android.bluetooth.BluetoothProfile.STATE_CONNECTED ||
                adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET) == android.bluetooth.BluetoothProfile.STATE_CONNECTED
            )
    } catch (e: Exception) { false }

    /** Última atividade (motor girando, uso da voz, app aberto): conta os 15 minutos. */
    private var lastActivity = System.currentTimeMillis()

    /**
     * Escuta a palavra com o microfone do próprio app (não o do Vosk), para ligar o redutor de ruído
     * e o cancelamento de eco do aparelho: com o rádio tocando, o microfone pegava o rádio junto e não
     * reconhecia "Monitor" (relato da 0.1.55). O cancelamento de eco tira do microfone o som que o
     * próprio aparelho está tocando, quando o multimídia oferece esse recurso.
     */
    @android.annotation.SuppressLint("MissingPermission") // conferida em shouldListen (micAllowed)
    private fun startWake() {
        try {
            val m = model ?: Model(WakeModel.path(this)).also { model = it }
            val rec = Recognizer(m, RATE.toFloat(), WakeRules.GRAMMAR)
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE / 5 * 2) * 2)
            if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); rec.close(); return }
            val session = record.audioSessionId
            val effects = listOfNotNull(
                if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.apply { enabled = true } else null,
                if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.apply { enabled = true } else null,
            )
            record.startRecording()
            val running = java.util.concurrent.atomic.AtomicBoolean(true)
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            val thread = Thread {
                val buf = ShortArray(RATE / 10) // 100 ms
                try {
                    while (running.get()) {
                        val n = record.read(buf, 0, buf.size)
                        if (n <= 0) continue
                        val wake = if (rec.acceptWaveForm(buf, n)) WakeRules.isWake(rec.result) else WakeRules.isWakePartial(rec.partialResult)
                        if (wake) {
                            running.set(false)
                            main.post { heard() }
                        }
                    }
                } catch (_: Exception) {
                    main.post { stopWake() }
                }
            }
            wake = WakeListener(record, rec, effects, running, thread)
            thread.start()
        } catch (e: Exception) {
            // Modelo corrompido ou microfone ocupado: tenta de novo no próximo ciclo.
            stopWake()
        }
    }

    private class WakeListener(
        val record: AudioRecord,
        val rec: Recognizer,
        val effects: List<android.media.audiofx.AudioEffect>,
        val running: java.util.concurrent.atomic.AtomicBoolean,
        val thread: Thread,
    )

    private fun stopWake() {
        val w = wake ?: return
        wake = null
        w.running.set(false)
        try { w.record.stop() } catch (_: Exception) { }
        try { w.thread.join(500) } catch (_: Exception) { }
        w.effects.forEach { try { it.release() } catch (_: Exception) { } }
        try { w.record.release() } catch (_: Exception) { }
        try { w.rec.close() } catch (_: Exception) { }
    }

    /** Ouviu "Monitor": solta o microfone, bipa e abre o comando de voz. */
    private fun heard() {
        if (wake == null) return // já acionou
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
        private const val RATE = 16_000
        private const val ACTION_SNOOZE = "br.com.monitor2008.DESLIGAR_ESCUTA"
        private const val EXTRA_RENEW = "renovar"

        /** "Desativar" ou botão da notificação: não escuta "Monitor" até o motor ligar ou o app abrir. */
        @Volatile var snoozed = false
            private set

        fun snooze(context: Context) {
            snoozed = true
            update(context)
        }

        /**
         * Liga ou desliga conforme a necessidade (palavra ligada ou janela flutuante na tela). Chamar
         * com o app na frente: com ele atrás o Android não deixa iniciar o serviço de microfone.
         */
        fun update(context: Context, renew: Boolean = false) {
            val app = context.applicationContext as Application
            // App aberto ou motor ligado: desfaz o "desativar".
            if (renew) snoozed = false
            val needed = (Features.wakeWord && Store(app).wakeWord && WakeModel.ready(app) && !snoozed || FloatingPanel.showing) &&
                VoiceAssistant.get(app).micAllowed()
            val intent = Intent(app, MicService::class.java).putExtra(EXTRA_RENEW, renew)
            try {
                if (needed) ContextCompat.startForegroundService(app, intent) else app.stopService(intent)
            } catch (_: Exception) { }
        }
    }
}
