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
        if (ok) start() else voice.listen() // mostra "falta a permissão do microfone"
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
                    is VoiceState.Error -> Text(s.message, fontSize = 20.sp, color = Warn)
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
internal fun VoiceSection() {
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
        "Ou peça: \"abrir a saúde\", \"tocar rádio\", \"ouvir música\", \"abrir o mapa\", " +
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
        line(c.ttsPortuguese, when (c.ttsPortuguese) {
            true -> "Voz em português instalada"
            null -> "Iniciando a voz do app..."
            false -> "Sem voz em português: instale \"Serviços de fala do Google\" ou outra voz em português"
        })
    }
}

@Composable
private fun line(ok: Boolean?, text: String) =
    Text((if (ok == true) "✓ " else if (ok == null) "… " else "✗ ") + text, fontSize = 16.sp,
        color = if (ok == true) OkGreen else if (ok == null) MaterialTheme.colorScheme.onSurfaceVariant else Warn)
