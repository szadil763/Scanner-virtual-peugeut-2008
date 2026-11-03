package br.com.monitor2008.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.monitor2008.data.PanelReader
import br.com.monitor2008.domain.OdometerRead
import kotlinx.coroutines.launch

/**
 * "Ler da foto": o dono fotografa o painel e o app acha o km na foto (domain/OdometerRead). Mostra
 * o número para conferir antes de salvar. Sem câmera (multimídia), escolhe uma foto da galeria.
 */
@Composable
internal fun OdometerPhotoButton(ui: UiState, vm: MonitorEngine) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<Uri?>(null) }

    fun read(uri: Uri) {
        reading = true
        scope.launch {
            val lines = PanelReader.lines(ctx, uri)
            reading = false
            val km = OdometerRead.pick(lines, ui.carKm)
            if (km == null) {
                failed = "Não achei o km na foto. Tire outra mais de perto, com o hodômetro no centro e sem reflexo."
            } else {
                found = km.toString()
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { read(it) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photo
        if (ok && uri != null) read(uri)
    }

    OutlinedButton({
        try {
            val uri = PanelReader.photoUri(ctx)
            photo = uri
            camera.launch(uri)
        } catch (e: Exception) {
            // Sem câmera (multimídia): uma foto já tirada, da galeria.
            try { gallery.launch("image/*") } catch (_: Exception) { failed = "Este aparelho não tem câmera nem galeria." }
        }
    }, enabled = !ui.moving && !reading, modifier = Modifier.height(56.dp)) {
        Text(if (reading) "Lendo a foto..." else "📷 Ler da foto", fontSize = 16.sp)
    }

    found?.let { initial ->
        var text by remember(initial) { mutableStateOf(initial) }
        val value = text.filter { it.isDigit() }.toDoubleOrNull()
        AlertDialog(
            onDismissRequest = { found = null },
            title = { Text("Km do painel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Li na foto: ${String.format(BR, "%,d", initial.toInt())} km. Confira com o painel antes de salvar.", fontSize = 16.sp)
                    OutlinedTextField(text, { text = it }, label = { Text("Km") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !ui.moving)
                }
            },
            confirmButton = {
                TextButton({ value?.let { vm.setCarOdometer(it) }; found = null }, enabled = value != null && !ui.moving) { Text("Salvar") }
            },
            dismissButton = { TextButton({ found = null }) { Text("Cancelar") } },
        )
    }
    failed?.let { msg ->
        AlertDialog(
            onDismissRequest = { failed = null },
            title = { Text("Foto do painel") },
            text = { Text(msg, fontSize = 16.sp) },
            confirmButton = { TextButton({ failed = null }) { Text("OK") } },
        )
    }
}

/** Lembrete mensal: o km do app bate com o do painel? */
@Composable
internal fun OdoCheckBanner(ui: UiState, vm: MonitorEngine, onFix: () -> Unit) {
    if (!ui.odoCheckDue || ui.carKm == null) return
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Confira o hodômetro", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("O app marca ${String.format(BR, "%,d", Math.round(ui.carKm))} km. Bate com o painel?", fontSize = 15.sp)
            }
            OutlinedButton({ vm.confirmOdometer() }, Modifier.height(56.dp)) { Text("Bate", fontSize = 16.sp) }
            OutlinedButton(onFix, Modifier.height(56.dp)) { Text("Corrigir", fontSize = 16.sp) }
        }
    }
}
