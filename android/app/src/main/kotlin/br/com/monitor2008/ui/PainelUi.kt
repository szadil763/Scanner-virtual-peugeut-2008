package br.com.monitor2008.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.monitor2008.data.Store

/** Ajustes: endereço do painel no multimídia (o celular envia os números para ele). */
@Composable
internal fun PainelSection() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var addr by remember { mutableStateOf(store.painelAddress) }
    var saved by remember { mutableStateOf(false) }
    Text("Painel no multimídia", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Com o multimídia ligado no hotspot deste celular, o app envia o painel para ele. " +
            "Digite o endereço que aparece na tela \"Painel\" do multimídia (ex.: 10.12.38.252:8080). " +
            "Deixe vazio para não enviar.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 15.sp,
    )
    OutlinedTextField(
        value = addr,
        onValueChange = { addr = it; saved = false },
        label = { Text("Endereço do multimídia") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button({ store.painelAddress = addr; saved = true }, Modifier.height(56.dp)) {
        Text("Salvar endereço", fontSize = 17.sp)
    }
    if (saved) {
        Text(
            if (store.painelAddress.isBlank()) "Envio desligado." else "Salvo: o painel será enviado para ${store.painelAddress}.",
            fontSize = 15.sp,
        )
    }
}
