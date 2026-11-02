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
    fun listen() {
        initTts()
        if (!micAllowed()) { _state.value = VoiceState.Error("Falta a permissão do microfone."); return }
        if (!canListenDirectly()) {
            _state.value = VoiceState.Error("Este aparelho não tem reconhecimento de voz instalado (veja o teste em Ajustes > Comando de voz).")
            return
        }
        tts?.stop()
        stopListening()
        requestFocus()
        val r = try { SpeechRecognizer.createSpeechRecognizer(app) } catch (e: Exception) { null }
        if (r == null) { fail("Não foi possível abrir o reconhecimento de voz."); return }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _state.value = VoiceState.Listening() }
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
                fail(errorText(error))
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        _state.value = VoiceState.Listening()
        try {
            r.startListening(recognizeIntent().putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName))
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
        val acts = cmd.intent in setOf(VoiceIntent.ABRIR, VoiceIntent.IR_PARA, VoiceIntent.TOCAR_RADIO, VoiceIntent.TOCAR_MUSICA,
            VoiceIntent.PAUSAR, VoiceIntent.PROXIMA, VoiceIntent.ANTERIOR) ||
            (cmd.intent == VoiceIntent.ABASTECER && !snap.moving)
        // Abrir tela ou abastecimento: fecha a conversa (a tela pedida aparece) e só fala a confirmação.
        _state.value = if (acts) VoiceState.Idle else VoiceState.Answer(heard, cmd.intent, text)
        if (acts) _action.value = cmd
        if (text.isNotBlank()) speak(text)
    }

    fun close() {
        stopListening()
        tts?.stop()
        abandonFocus()
        _state.value = VoiceState.Idle
    }

    /** Fala depois do que já está falando (resultado de uma busca, por exemplo). */
    fun say(text: String) {
        if (ttsReady != true) { speak(text); return }
        requestFocus()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "monitor-voz-2")
    }

    /** Fala um texto (também usado pelo teste em Ajustes). */
    fun speak(text: String) {
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
        _state.value = VoiceState.Error(message)
    }

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
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { abandonFocus() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { abandonFocus() }
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

    /** Abaixa a música enquanto ouve e fala (o rádio do app e os outros apps de música respeitam). */
    private fun requestFocus() {
        if (focus != null || audio == null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            ).build()
        try { audio.requestAudioFocus(req); focus = req } catch (_: Exception) { }
    }

    private fun abandonFocus() {
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
        @Volatile private var instance: VoiceAssistant? = null

        fun get(app: Application): VoiceAssistant =
            instance ?: synchronized(this) { instance ?: VoiceAssistant(app).also { instance = it } }
    }
}
