package br.com.monitor2008.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.monitor2008.data.UpdateState
import br.com.monitor2008.data.Updater
import br.com.monitor2008.domain.UpdateChannel

@Composable
private fun rememberUpdater(): Updater {
    val ctx = LocalContext.current
    return remember { Updater.get(ctx.applicationContext as Application) }
}

/**
 * O passo seguinte da atualização, num botão só: baixar, liberar a instalação ou instalar.
 * Parado apenas (regra do app: nada de tarefas com o carro andando).
 */
@Composable
private fun UpdateAction(state: UpdateState, moving: Boolean, compact: Boolean = false) {
    val ctx = LocalContext.current
    val updater = rememberUpdater()
    // A permissão "instalar apps desconhecidos" pode ter sido liberada fora do app: confere ao voltar.
    var canInstall by remember { mutableStateOf(updater.canInstall()) }
    LifecycleResumeEffect(Unit) {
        canInstall = updater.canInstall()
        onPauseOrDispose { }
    }
    val h = Modifier.height(if (compact) 56.dp else 60.dp)
    when (state) {
        is UpdateState.Available -> Button({ updater.download(state.version) }, h, enabled = !moving) {
            Text("Baixar a versão ${state.version.versionName}", fontSize = 17.sp)
        }
        is UpdateState.Ready -> if (!canInstall) {
            Button({ ctx.startActivity(updater.permissionIntent()) }, h, enabled = !moving) {
                Text("Permitir instalar atualizações", fontSize = 17.sp)
            }
        } else {
            Button({
                try {
                    updater.install(ctx, state.file)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Não foi possível abrir a instalação.", Toast.LENGTH_LONG).show()
                }
            }, h, enabled = !moving) { Text("Instalar a versão ${state.version.versionName}", fontSize = 17.sp) }
        }
        else -> {}
    }
}

/** Aviso no alto de todas as abas quando há versão nova (ou já baixada). */
@Composable
internal fun UpdateBanner(moving: Boolean) {
    val updater = rememberUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    val failed = state as? UpdateState.Failed
    if (failed?.browserUrl != null) {
        val ctx = LocalContext.current
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(failed.message, fontSize = 15.sp, color = Warn, modifier = Modifier.weight(1f))
                OutlinedButton({
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(failed.browserUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Nenhum navegador encontrado.", Toast.LENGTH_LONG).show()
                    }
                }, Modifier.height(56.dp), enabled = !moving) { Text("Baixar pelo navegador", fontSize = 16.sp) }
            }
        }
        return
    }
    val v = when (val s = state) {
        is UpdateState.Available -> s.version
        is UpdateState.Ready -> s.version
        is UpdateState.Downloading -> s.version
        else -> return
    }
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Versão nova do app: ${v.versionName}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                val s = state
                if (s is UpdateState.Downloading) {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                } else if (moving) {
                    Text("Pare o carro para atualizar.", fontSize = 14.sp, color = Warn)
                }
            }
            UpdateAction(state, moving, compact = true)
        }
    }
}

/** Seção de Ajustes: versão instalada, canal e o botão de atualizar. */
@Composable
internal fun UpdateSection(moving: Boolean) {
    val ctx = LocalContext.current
    val updater = rememberUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    var channel by remember { mutableStateOf(updater.channel) }
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { null } ?: "?"
    }

    Text("Atualização do app", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Versão instalada: $version", fontSize = 17.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UpdateChannel.values().forEach { c ->
            FilterChip(selected = channel == c, onClick = { channel = c; updater.channel = c },
                label = { Text(c.label, fontSize = 18.sp) }, modifier = Modifier.height(56.dp))
        }
    }
    Text(
        if (channel == UpdateChannel.ESTAVEL) "Estável: só versões já testadas e aprovadas. Indicado para quem usa o app no dia a dia."
        else "Teste: recebe toda versão nova assim que sai, antes de ser aprovada.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp,
    )
    OutlinedButton({ updater.check() }, Modifier.height(60.dp), enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading) {
        Text("Procurar atualização", fontSize = 18.sp)
    }
    when (val s = state) {
        UpdateState.Idle -> {}
        UpdateState.Checking -> Text("Procurando...", fontSize = 16.sp)
        is UpdateState.UpToDate -> Text("O app está atualizado (canal ${s.channel.label}).", color = OkGreen, fontSize = 16.sp)
        is UpdateState.Available -> Text("Versão ${s.version.versionName} disponível.", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        is UpdateState.Downloading -> {
            Text("Baixando a versão ${s.version.versionName}...", fontSize = 16.sp)
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(10.dp))
        }
        is UpdateState.Ready -> Text(
            "Versão ${s.version.versionName} baixada. Toque em Instalar e confirme na tela do Android; os dados do app continuam.",
            fontSize = 16.sp,
        )
        is UpdateState.Failed -> {
            Text(s.message, color = Warn, fontSize = 16.sp)
            s.browserUrl?.let { url ->
                OutlinedButton({
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Nenhum navegador encontrado.", Toast.LENGTH_LONG).show()
                    }
                }, Modifier.height(60.dp), enabled = !moving) { Text("Baixar pelo navegador", fontSize = 18.sp) }
            }
        }
    }
    UpdateAction(state, moving)
    if (moving && (state is UpdateState.Available || state is UpdateState.Ready)) Text("Pare o carro para atualizar.", color = Warn)
    Text("Na primeira atualização o Android pede para permitir que o Monitor instale apps. Depois, é só tocar em Instalar.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
}
