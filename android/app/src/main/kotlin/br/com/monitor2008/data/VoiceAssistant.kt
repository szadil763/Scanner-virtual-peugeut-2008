package br.com.monitor2008.data

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import br.com.monitor2008.domain.TtsVoiceInfo
import br.com.monitor2008.domain.TtsVoiceOption
import br.com.monitor2008.domain.TtsVoices
import br.com.monitor2008.domain.VoiceCommand
import br.com.monitor2008.domain.VoiceCommands
import br.com.monitor2008.domain.VoiceIntent
import br.com.monitor2008.domain.VoiceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class VoiceState {
    data object Idle : VoiceState()
    data class Listening(val partial: String = "") : VoiceState()
    data class Answer(val heard: String, val intent: VoiceIntent, val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

/** O que o aparelho tem para o comando de voz (tela de teste em Ajustes). */
data class VoiceCheck(
    val micAllowed: Boolean,
    val recognizer: Boolean,
    val recognizerApps: List<String>,
    val recognizerActivity: Boolean,
    /** Null = o leitor de voz ainda está iniciando. */
    val ttsPortuguese: Boolean?,
)

/**
 * Comando de voz: ouve uma pergunta (reconhecimento de fala do próprio aparelho), entende pelas
 * regras de [VoiceCommands] e responde em voz alta. Só enquanto o dono pede (botão Voz): o
 * microfone não fica ligado esperando. A música abaixa enquanto ouve e fala. Um por processo; usar
 * na thread principal.
 */
class VoiceAssistant private constructor(private val app: Application) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val store = Store(app)
    private var ttsReady: Boolean? = null
    private var pendingSpeech: String? = null
    private val audio = app.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null

    /**
     * Pedido para a tela (abrir uma aba, abrir o registro de abastecimento preenchido). Quem atende
     * limpa com [consumeAction].
     */
    private val _action = MutableStateFlow<VoiceCommand?>(null)
    val action: StateFlow<VoiceCommand?> = _action.asStateFlow()
    fun consumeAction() { _action.value = null }

    /** Lê o que o app sabe agora, na hora da pergunta. */
    var snapshot: () -> VoiceSnapshot = { VoiceSnapshot(connected = false) }

    fun micAllowed() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun check(): VoiceCheck {
        val services = try {
            app.packageManager.queryIntentServices(Intent("android.speech.RecognitionService"), 0)
                .map { it.loadLabel(app.packageManager).toString() }
        } catch (e: Exception) { emptyList() }
        val activity = try {
            app.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
        } catch (e: Exception) { false }
        initTts()
        return VoiceCheck(micAllowed(), SpeechRecognizer.isRecognitionAvailable(app), services, activity, ttsReady)
    }

    /** Intent da tela de reconhecimento do sistema (alternativa quando não há serviço para o app usar direto). */
    fun recognizeIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Pergunte: consumo, temperatura, bateria, óleo...")

    fun canListenDirectly() = SpeechRecognizer.isRecognitionAvailable(app)

    /** Começa a ouvir. Sem permissão do microfone ou sem reconhecimento, mostra o motivo. */
    /**
     * [beepWhenReady]: chamado pela palavra "Monitor" — bipa só quando o reconhecimento está pronto
     * (falar antes perdia o começo do pedido). [retry]: segunda chance quando não ouviu nada.
     */
    fun listen(beepWhenReady: Boolean = false, retry: Int = 0) {
        handsFree = beepWhenReady || !VoiceActions.screenVisible
        lastUseMs = System.currentTimeMillis()
        retries = retry
        initTts()
        if (!micAllowed()) { fail("Falta a permissão do microfone. Abra o Monitor e libere o microfone."); return }
        if (!canListenDirectly()) {
            fail("Este aparelho não tem reconhecimento de voz instalado (veja o teste em Ajustes > Comando de voz).")
            return
        }
        tts?.stop()
        stopListening()
        beforeListen?.invoke()
        // O que tocava antes de pausar para ouvir: "desligar o rádio" age nele mesmo já pausado pela escuta.
        if (retry == 0) {
            radioWasPlaying = RadioPlayer.get(app).state.value.playing
            mediaWasPlaying = MediaWatcher.get(app).now.value?.playing == true
        }
        // Rádio do app baixa para 3%; os outros apps de música abaixam (sem pausar), para ouvir o pedido.
        RadioService.abaixar(true)
        requestFocus(pauseOthers = false)
        val r = try { SpeechRecognizer.createSpeechRecognizer(app) } catch (e: Exception) { null }
        if (r == null) { fail("Não foi possível abrir o reconhecimento de voz."); return }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState.Listening()
                if (beepWhenReady) beep()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (_state.value is VoiceState.Listening) _state.value = VoiceState.Listening(p)
            }
            override fun onResults(results: Bundle?) {
                handle(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                destroyRecognizer()
            }
            override fun onError(error: Int) {
                destroyRecognizer()
                // Sem tela (palavra "Monitor" ou janela flutuante): uma segunda chance antes de desistir.
                val nothingHeard = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (nothingHeard && handsFree && retries < 1) {
                    _state.value = VoiceState.Idle
                    speak("Não entendi. Pode repetir?")
                    scope.launch {
                        delay(400)
                        while (speaking) delay(100)
                        listen(beepWhenReady = true, retry = retries + 1)
                    }
                    return
                }
                fail(errorText(error))
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        _state.value = VoiceState.Listening()
        try {
            r.startListening(recognizeIntent().putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
                // Pausas curtas no meio do pedido ("abasteci cem reais... a seis e onze") não encerram a escuta.
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L))
        } catch (e: Exception) {
            destroyRecognizer()
            fail("Não foi possível abrir o reconhecimento de voz.")
        }
    }

    /** Resultado vindo da tela de reconhecimento do sistema (alternativa). */
    fun handle(candidates: List<String>) {
        if (candidates.isEmpty()) { fail("Não ouvi nada. Toque em Voz e fale perto do microfone."); return }
        val (cmd, heard) = VoiceCommands.parse(candidates)
        val snap = snapshot()
        val text = VoiceCommands.answer(cmd, snap)
        // Fala primeiro: as ações abaixo podem falar depois (resultado da busca da rádio, por exemplo).
        if (text.isNotBlank()) speak(text)
        val needsScreen = cmd.intent == VoiceIntent.ABRIR || (cmd.intent == VoiceIntent.ABASTECER && !snap.moving)
        when {
            // Rota, mapa, rádio, música, pausar: atende mesmo com o Monitor atrás do mapa.
            VoiceActions.handles(cmd) -> {
                _state.value = VoiceState.Idle
                VoiceActions.execute(app, cmd, this)
            }
            // Abrir aba ou abastecimento: a tela atende (e o Monitor vem para a frente, se estava atrás).
            needsScreen -> {
                _state.value = VoiceState.Idle
                _action.value = cmd
                VoiceActions.bringToFront(app)
            }
            // Pergunta com o Monitor atrás (🎤 da janela flutuante ou "Monitor"): só a resposta falada.
            !VoiceActions.screenVisible -> _state.value = VoiceState.Idle
            else -> _state.value = VoiceState.Answer(heard, cmd.intent, text)
        }
    }

    fun close() {
        stopListening()
        tts?.stop()
        abandonFocus()
        _state.value = VoiceState.Idle
    }

    /** Fala depois do que já está falando (resultado de uma busca, por exemplo). */
    fun say(text: String) {
        if (PainelLink.vozNoPainel()) { PainelLink.enviarFala(text); return }
        if (ttsReady != true) { speak(text); return }
        requestFocus()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "monitor-voz-2")
    }

    /** Fala um texto (também usado pelo teste em Ajustes). */
    fun speak(text: String) {
        if (PainelLink.vozNoPainel()) { PainelLink.enviarFala(text); return }
        initTts()
        when (ttsReady) {
            true -> {
                requestFocus()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "monitor-voz")
            }
            null -> pendingSpeech = text
            false -> abandonFocus()
        }
    }

    private fun fail(message: String) {
        abandonFocus()
        if (!VoiceActions.screenVisible) {
            // Sem a tela do Monitor (janela flutuante ou "Monitor"): o motivo vai falado.
            _state.value = VoiceState.Idle
            speak(message)
        } else {
            _state.value = VoiceState.Error(message)
        }
    }

    @Volatile private var handsFree = false
    /** Último uso do comando de voz: renova os 15 minutos da escuta da palavra "Monitor". */
    @Volatile var lastUseMs = 0L
        private set
    /** Tocava quando o comando de voz começou a ouvir (antes de pausar para ouvir). */
    @Volatile var radioWasPlaying = false
        private set
    @Volatile var mediaWasPlaying = false
        private set
    @Volatile private var retries = 0

    private fun beep() {
        try { android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150) } catch (_: Exception) { }
    }

    /** Ouvindo ou falando: a escuta da palavra "Monitor" espera (o microfone é um só). */
    val busy: Boolean get() = _state.value is VoiceState.Listening || recognizer != null || speaking

    @Volatile private var speaking = false

    private fun stopListening() {
        try { recognizer?.cancel() } catch (_: Exception) { }
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        try { recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
    }

    private fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(app) { status ->
            val t = tts
            ttsReady = if (status == TextToSpeech.SUCCESS && t != null) {
                val r = t.setLanguage(Locale("pt", "BR"))
                r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            } else false
            if (ttsReady == true) {
                t?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                t?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { speaking = true }
                    override fun onDone(utteranceId: String?) { speaking = false; abandonFocus() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaking = false; abandonFocus() }
                })
                applyVoice()
                pendingSpeech?.let { pendingSpeech = null; speak(it) }
            }
        }
    }

    // ---------- Escolha da voz (Ajustes) ----------

    /**
     * Vozes em português, lidas UMA vez e fora da tela: no celular o leitor do Google tem centenas
     * de vozes e pedir a lista na thread da tela travava a aba Ajustes (menu "Mais", 0.1.50).
     */
    @Volatile private var cachedPt: List<android.speech.tts.Voice>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun ptVoices(): List<android.speech.tts.Voice> = cachedPt.orEmpty()

    private suspend fun loadPt(): List<android.speech.tts.Voice> {
        cachedPt?.let { if (it.isNotEmpty()) return it }
        val t = tts ?: return emptyList()
        if (ttsReady != true) return emptyList()
        val list = withContext(Dispatchers.IO) {
            try {
                t.voices.orEmpty().filter { it.locale.language == "pt" && (it.locale.country.isEmpty() || it.locale.country == "BR") }
            } catch (e: Exception) { emptyList() }
        }
        cachedPt = list
        return list
    }

    private fun info(v: android.speech.tts.Voice) = TtsVoiceInfo(
        v.name, v.quality, v.isNetworkConnectionRequired,
        v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true,
    )

    /** Vozes em português para a tela de Ajustes (vazia enquanto o leitor inicia). Lê fora da tela. */
    suspend fun voiceOptions(): List<TtsVoiceOption> {
        initTts()
        return TtsVoices.options(loadPt().map(::info))
    }

    /** Nome da voz em uso (a escolhida ou a melhor achada). */
    fun currentVoice(): String? = store.ttsVoice ?: TtsVoices.best(ptVoices().map(::info))?.name

    var rate: Float
        get() = store.ttsRate
        set(v) { store.ttsRate = v; applyVoice() }
    var pitch: Float
        get() = store.ttsPitch
        set(v) { store.ttsPitch = v; applyVoice() }

    fun chooseVoice(name: String) {
        store.ttsVoice = name
        applyVoice()
    }

    /** Fala uma frase de exemplo com a voz [name], sem trocar a escolhida. */
    fun preview(name: String) {
        val t = tts ?: return
        if (ttsReady != true) return
        ptVoices().firstOrNull { it.name == name }?.let { try { t.voice = it } catch (_: Exception) { } }
        speak("Motor a noventa graus. Consumo de doze vírgula três quilômetros por litro.")
        // O leitor guarda a voz junto com cada fala pedida: já dá para voltar para a escolhida.
        applySettings()
    }

    /** Aplica a voz escolhida (ou a melhor), a velocidade e o tom. A lista de vozes é lida fora da tela. */
    private fun applyVoice() {
        applyRateAndPitch()
        scope.launch {
            loadPt()
            applySettings()
        }
    }

    private fun applyRateAndPitch() {
        val t = tts ?: return
        if (ttsReady != true) return
        t.setSpeechRate(store.ttsRate)
        t.setPitch(store.ttsPitch)
    }

    /** Com a lista já lida: só troca a voz (rápido). */
    private fun applySettings() {
        val t = tts ?: return
        if (ttsReady != true) return
        val voices = ptVoices()
        val chosen = voices.firstOrNull { it.name == store.ttsVoice }
            ?: TtsVoices.best(voices.map(::info))?.let { b -> voices.firstOrNull { it.name == b.name } }
        try { chosen?.let { t.voice = it } } catch (_: Exception) { }
        applyRateAndPitch()
    }

    /**
     * Áudio enquanto ouve e fala. Ouvindo ([pauseOthers]): o rádio e a música PAUSAM, porque com o
     * som só abaixado o microfone pegava o rádio junto e não entendia o pedido (relato da 0.1.55).
     * Falando: só abaixam. Ao terminar, voltam a tocar sozinhos.
     */
    private var focusPauses = false

    private fun requestFocus(pauseOthers: Boolean = false) {
        if (audio == null) return
        if (focus != null) {
            if (!pauseOthers || focusPauses) return
            abandonFocus()
        }
        focusPauses = pauseOthers
        val kind = if (pauseOthers) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        val req = AudioFocusRequest.Builder(kind)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            ).build()
        try { audio.requestAudioFocus(req); focus = req } catch (_: Exception) { }
    }

    private fun abandonFocus() {
        RadioService.abaixar(false)
        val f = focus ?: return
        focus = null
        try { audio?.abandonAudioFocusRequest(f) } catch (_: Exception) { }
    }

    private fun errorText(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não entendi. Toque em Voz e fale de novo, perto do microfone."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            "O reconhecimento de voz precisa de internet neste aparelho. Confira o Wi-Fi ou o roteador do celular."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta a permissão do microfone."
        SpeechRecognizer.ERROR_AUDIO -> "O microfone não respondeu. Confira se ele está ligado no multimídia."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O reconhecimento de voz está ocupado. Tente de novo em alguns segundos."
        else -> "O reconhecimento de voz falhou (código $code). Tente de novo."
    }

    companion object {
        /** A escuta da palavra "Monitor" (MicService) solta o microfone aqui, antes de o comando de voz ouvir. */
        @Volatile var beforeListen: (() -> Unit)? = null

        @Volatile private var instance: VoiceAssistant? = null

        fun get(app: Application): VoiceAssistant =
            instance ?: synchronized(this) { instance ?: VoiceAssistant(app).also { instance = it } }
    }
}
