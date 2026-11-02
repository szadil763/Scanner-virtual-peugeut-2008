@file:OptIn(ExperimentalLayoutApi::class)

package br.com.monitor2008.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import br.com.monitor2008.data.Store
import br.com.monitor2008.data.SystemSettings
import br.com.monitor2008.domain.TtsVoices
import br.com.monitor2008.domain.VoiceCommands
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.monitor2008.data.ConnState
import br.com.monitor2008.data.VoiceAssistant
import br.com.monitor2008.data.VoiceCheck
import br.com.monitor2008.data.VoiceState
import br.com.monitor2008.domain.HealthStatus
import br.com.monitor2008.domain.VoiceAlert
import br.com.monitor2008.domain.VoiceSnapshot
import kotlinx.coroutines.delay

/** O que o comando de voz precisa saber da tela, na hora da pergunta. */
internal fun UiState.voiceSnapshot() = VoiceSnapshot(
    connected = !simulated && conn.state == ConnState.CONNECTED,
    kmPerLNow = kmPerL,
    tripAvgKmPerL = tripAvgKmPerL,
    tripKm = tripKm,
    tripMinutes = tripMinutes,
    coolantC = sample?.coolantC,
    batteryV = sample?.batteryV,
    speedKmh = sample?.speedKmh,
    rpm = sample?.rpm,
    oilKmLeft = oil?.kmLeft,
    oilDaysLeft = oil?.daysLeft,
    alerts = health.filter { it.status == HealthStatus.ALERTA || it.status == HealthStatus.ATENCAO }
        .map { VoiceAlert(it.title, it.status == HealthStatus.ALERTA) },
    dtcs = dtcs,
    moving = moving,
)

@Composable
private fun rememberVoice(): VoiceAssistant {
    val ctx = LocalContext.current
    return remember { VoiceAssistant.get(ctx.applicationContext as Application) }
}

/**
 * Começa uma pergunta: pede o microfone na primeira vez; sem reconhecimento direto no aparelho,
 * tenta a tela de reconhecimento do sistema.
 */
@Composable
internal fun rememberVoiceStart(): () -> Unit {
    val voice = rememberVoice()
    val system = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            voice.handle(r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty())
        }
    }
    fun start() {
        if (!voice.canListenDirectly() && voice.check().recognizerActivity) {
            try { system.launch(voice.recognizeIntent()); return } catch (_: Exception) { }
        }
        voice.listen()
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else voice.listen() // mostra "falta a permissão do microfone" com o botão para liberar
    }
    return {
        if (voice.micAllowed()) start() else mic.launch(Manifest.permission.RECORD_AUDIO)
    }
}

/** Botão Voz (barra do rodapé no multimídia). */
@Composable
internal fun VoiceButton(modifier: Modifier = Modifier.height(56.dp)) {
    val start = rememberVoiceStart()
    Button(start, modifier) { Text("🎤 Voz", fontSize = 17.sp) }
}

/** Janela da conversa: o que ouviu e a resposta, em letra grande. */
@Composable
internal fun VoiceOverlay(ui: UiState) {
    val voice = rememberVoice()
    // A resposta usa o que a tela mostra na hora da pergunta.
    voice.snapshot = { ui.voiceSnapshot() }
    val state by voice.state.collectAsStateWithLifecycle()
    val start = rememberVoiceStart()
    if (state == VoiceState.Idle) return
    AlertDialog(
        onDismissRequest = { voice.close() },
        title = {
            Text(
                when (state) {
                    is VoiceState.Listening -> "Ouvindo..."
                    is VoiceState.Answer -> "Monitor"
                    else -> "Comando de voz"
                },
                fontSize = 24.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val s = state) {
                    is VoiceState.Listening -> {
                        Text(s.partial.ifBlank { "Fale agora. Ex.: qual o consumo?" }, fontSize = 22.sp)
                    }
                    is VoiceState.Answer -> {
                        Text("Você: \"${s.heard}\"", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                    is VoiceState.Error -> {
                        Text(s.message, fontSize = 20.sp, color = Warn)
                        val ctx = LocalContext.current
                        // Leva direto para onde se resolve.
                        when {
                            s.message.contains("microfone", true) && s.message.contains("permiss", true) ->
                                Button({ SystemSettings.app(ctx) }, Modifier.height(56.dp)) { Text("Liberar o microfone", fontSize = 17.sp) }
                            s.message.contains("reconhecimento de voz instalado", true) ->
                                Button({ SystemSettings.playStore(ctx, SystemSettings.GOOGLE_APP) }, Modifier.height(56.dp)) {
                                    Text("Instalar o app Google", fontSize = 17.sp)
                                }
                            s.message.contains("internet", true) ->
                                Button({ ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.height(56.dp)) {
                                    Text("Abrir o Wi-Fi", fontSize = 17.sp)
                                }
                        }
                    }
                    VoiceState.Idle -> {}
                }
            }
        },
        confirmButton = {
            if (state !is VoiceState.Listening) {
                Button(start, Modifier.height(56.dp)) { Text("🎤 Perguntar de novo", fontSize = 17.sp) }
            }
        },
        dismissButton = {
            TextButton({ voice.close() }, Modifier.height(56.dp)) { Text("Fechar", fontSize = 17.sp) }
        },
    )
}

/** Ajustes: teste do comando de voz neste aparelho (microfone, reconhecimento e voz em português). */
@Composable
internal fun VoiceSection(moving: Boolean) {
    val ctx = LocalContext.current
    val voice = rememberVoice()
    var check by remember { mutableStateOf<VoiceCheck?>(null) }
    var tick by remember { mutableStateOf(0) }
    val start = rememberVoiceStart()
    // O leitor de voz demora um pouco para iniciar: confere de novo em seguida.
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        check = voice.check()
        delay(2_500)
        check = voice.check()
    }
    Text("Comando de voz", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text("Toque em 🎤 Voz e pergunte: consumo, viagem, temperatura, bateria, troca de óleo, falhas ou \"como está o carro\". " +
        "Ou peça: \"abrir a saúde\", \"ir para casa\", \"ir para a rua Augusta 500\", \"tocar rádio Jovem Pan\", " +
        "\"tocar Péricles no Spotify\", \"abrir o mapa\", " +
        "\"abasteci cem reais a seis e onze\" (abre o registro preenchido para conferir e salvar). " +
        "O app responde em voz alta. O microfone só liga quando você toca no botão.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button({ tick++ }, Modifier.height(56.dp)) { Text("Testar este aparelho", fontSize = 17.sp) }
        Button(start, Modifier.height(56.dp)) { Text("🎤 Fazer uma pergunta", fontSize = 17.sp) }
        Button({ voice.speak("Olá. O comando de voz está funcionando.") }, Modifier.height(56.dp)) {
            Text("Ouvir a voz do app", fontSize = 17.sp)
        }
    }
    check?.let { c ->
        line(c.micAllowed, if (c.micAllowed) "Microfone permitido" else "Microfone ainda não permitido (o app pede na primeira pergunta)")
        line(c.recognizer || c.recognizerActivity, when {
            c.recognizer -> "Reconhecimento de voz: " + c.recognizerApps.ifEmpty { listOf("disponível") }.joinToString(", ")
            c.recognizerActivity -> "Reconhecimento de voz pela tela do sistema"
            else -> "Sem reconhecimento de voz neste aparelho: precisa do app Google (ou de uma versão com reconhecimento offline)"
        })
        if (!c.micAllowed) OutlinedButton({ SystemSettings.app(ctx) }, Modifier.height(56.dp)) { Text("Liberar o microfone", fontSize = 16.sp) }
        if (!c.recognizer && !c.recognizerActivity) OutlinedButton({ SystemSettings.playStore(ctx, SystemSettings.GOOGLE_APP) }, Modifier.height(56.dp)) {
            Text("Instalar o app Google", fontSize = 16.sp)
        }
        line(c.ttsPortuguese, when (c.ttsPortuguese) {
            true -> "Voz em português instalada"
            null -> "Iniciando a voz do app..."
            false -> "Sem voz em português: instale \"Serviços de fala do Google\" ou outra voz em português"
        })
    }
}

/** Ajustes: qual voz o app usa para responder, a velocidade e o tom. */
@Composable
internal fun VoiceChoiceSection() {
    val voice = rememberVoice()
    var options by remember { mutableStateOf(emptyList<br.com.monitor2008.domain.TtsVoiceOption>()) }
    var current by remember { mutableStateOf(voice.currentVoice()) }
    var loading by remember { mutableStateOf(true) }
    var rate by remember { mutableStateOf(voice.rate) }
    var pitch by remember { mutableStateOf(voice.pitch) }
    var showAll by remember { mutableStateOf(false) }
    // O leitor de voz demora a iniciar: tenta ler a lista por alguns segundos.
    // A lista é lida fora da tela (no celular são centenas de vozes e travava o menu "Mais").
    LaunchedEffect(Unit) {
        repeat(8) {
            options = voice.voiceOptions()
            current = voice.currentVoice()
            if (options.isNotEmpty()) { loading = false; return@LaunchedEffect }
            delay(1_000)
        }
        loading = false
    }
    Text("Voz do app", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    if (options.isEmpty() && loading) {
        Text("Carregando as vozes...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    } else if (options.isEmpty()) {
        Text("Nenhuma voz em português encontrada. Instale \"Serviços de fala do Google\" e baixe a voz Português (Brasil).",
            color = Warn, fontSize = 15.sp)
        VoiceSettingsButtons()
    } else {
        Text("Toque em Ouvir para comparar e em Usar para escolher. As de alta qualidade soam mais naturais. " +
            "Para ter mais vozes, use os botões abaixo da lista.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        (if (showAll) options else options.take(6)).forEach { o ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text((if (o.name == current) "✓ " else "") + o.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        color = if (o.name == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(o.detail, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton({ voice.preview(o.name) }, Modifier.height(56.dp)) { Text("Ouvir", fontSize = 16.sp) }
                Button({ voice.chooseVoice(o.name); current = o.name; voice.speak("Esta é a voz do Monitor.") },
                    Modifier.height(56.dp), enabled = o.name != current) { Text("Usar", fontSize = 16.sp) }
            }
        }
        if (options.size > 6 && !showAll) {
            OutlinedButton({ showAll = true }, Modifier.height(56.dp)) { Text("Ver todas as ${options.size} vozes", fontSize = 16.sp) }
        }
        VoiceSettingsButtons()
    }
    VoiceStepper("Velocidade da fala", rate, onChange = { rate = it; voice.rate = it; voice.speak("Velocidade ${VoiceCommands.n(it.toDouble(), 1)}.") })
    VoiceStepper("Tom da voz", pitch, onChange = { pitch = it; voice.pitch = it; voice.speak("Tom ${VoiceCommands.n(it.toDouble(), 1)}.") })
}

/** Atalhos para conseguir mais vozes: baixar no leitor, configurar a saída de fala, instalar o leitor do Google. */
@Composable
private fun VoiceSettingsButtons() {
    val ctx = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton({ SystemSettings.installVoices(ctx) }, Modifier.height(56.dp)) { Text("Baixar vozes", fontSize = 16.sp) }
        OutlinedButton({ SystemSettings.textToSpeech(ctx) }, Modifier.height(56.dp)) { Text("Saída de texto para fala", fontSize = 16.sp) }
        OutlinedButton({ SystemSettings.playStore(ctx, SystemSettings.GOOGLE_TTS) }, Modifier.height(56.dp)) {
            Text("Serviços de fala do Google", fontSize = 16.sp)
        }
    }
}

@Composable
private fun VoiceStepper(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        OutlinedButton({ onChange(TtsVoices.step(value, false)) }, Modifier.height(56.dp), enabled = value > 0.6f) { Text("−", fontSize = 22.sp) }
        Text(VoiceCommands.n(value.toDouble(), 1) + if (value == 1.0f) " (normal)" else "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        OutlinedButton({ onChange(TtsVoices.step(value, true)) }, Modifier.height(56.dp), enabled = value < 1.6f) { Text("+", fontSize = 22.sp) }
    }
}

/** Lugares para "ir para casa", "me leva pro trabalho": nome e endereço, cadastrados com o carro parado. */
@Composable
internal fun PlacesSection(moving: Boolean) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx.applicationContext) }
    var places by remember { mutableStateOf(store.places()) }
    var adding by remember { mutableStateOf(false) }
    Text("Lugares salvos", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text("Para dizer \"ir para casa\" ou \"me leva pro trabalho\". Sem lugar salvo, o app manda para o mapa o endereço falado.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    places.forEach { (name, address) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(address, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton({
                places = places.filter { it.first != name }
                store.savePlaces(places)
            }, Modifier.height(56.dp), enabled = !moving) { Text("Apagar", fontSize = 16.sp) }
        }
    }
    Button({ adding = true }, Modifier.height(56.dp), enabled = !moving) { Text("Salvar um lugar", fontSize = 17.sp) }
    if (moving) Text("Pare o carro para cadastrar.", color = Warn)
    if (adding) {
        var name by remember { mutableStateOf(if (places.none { it.first.equals("Casa", true) }) "Casa" else "") }
        var address by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Salvar um lugar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nome (ex.: Casa, Trabalho, Mãe)") }, singleLine = true)
                    OutlinedTextField(address, { address = it }, label = { Text("Endereço (rua, número, cidade)") })
                }
            },
            confirmButton = {
                TextButton({
                    places = places.filter { !it.first.equals(name.trim(), true) } + (name.trim() to address.trim())
                    store.savePlaces(places)
                    adding = false
                }, enabled = name.isNotBlank() && address.isNotBlank() && !moving) { Text("Salvar") }
            },
            dismissButton = { TextButton({ adding = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun line(ok: Boolean?, text: String) =
    Text((if (ok == true) "✓ " else if (ok == null) "… " else "✗ ") + text, fontSize = 16.sp,
        color = if (ok == true) OkGreen else if (ok == null) MaterialTheme.colorScheme.onSurfaceVariant else Warn)
