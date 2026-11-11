package br.com.monitor2008.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import br.com.monitor2008.data.Conta
import br.com.monitor2008.data.Nuvem
import br.com.monitor2008.data.Store
import br.com.monitor2008.domain.Vehicles
import kotlinx.coroutines.delay

/** Ajustes: conta Google, sincronização do carro na nuvem e convite de pessoas para o carro. */
@Composable
internal fun ContaSection() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var email by remember { mutableStateOf(Conta.email()) }
    var aviso by remember { mutableStateOf("") }
    var convite by remember { mutableStateOf("") }
    val entrar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        Conta.aoResultado(r.data) { _, texto ->
            aviso = texto
            email = Conta.email()
        }
    }
    val nomeCarro = Vehicles.byId(store.carKey)?.name ?: store.carKey
    var estado by remember { mutableStateOf(store.nuvemStatus) }
    var abastecimentos by remember { mutableStateOf(store.refuels().size) }
    LaunchedEffect(Unit) {
        while (true) { estado = store.nuvemStatus; abastecimentos = store.refuels().size; delay(2_000) }
    }
    Text("Conta Google e nuvem", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Os dados são de cada carro. Quem está na lista do carro vê o histórico dele, em qualquer aparelho.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 15.sp,
    )
    val atual = email
    if (atual != null) {
        Text("Conectado como: $atual", fontSize = 16.sp)
        Text("Carro deste aparelho: $nomeCarro", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Abastecimentos neste aparelho: $abastecimentos", fontSize = 15.sp)
        Text("Sincronização: ${estado.ifBlank { "ainda não rodou" }}", fontSize = 15.sp)
        Button({ Nuvem.sincronizar(ctx, store) { _, texto -> aviso = texto } }, Modifier.height(56.dp)) {
            Text("Sincronizar este carro", fontSize = 17.sp)
        }
        OutlinedTextField(
            value = convite,
            onValueChange = { convite = it },
            label = { Text("E-mail de quem vai usar este carro") },
            singleLine = true,
        )
        Button({ Nuvem.convidar(store, convite) { ok, texto -> aviso = texto; if (ok) convite = "" } }, Modifier.height(56.dp)) {
            Text("Convidar pessoa", fontSize = 17.sp)
        }
        Button({ Conta.sair(); email = null; aviso = "Conta desconectada." }, Modifier.height(56.dp)) {
            Text("Sair da conta", fontSize = 17.sp)
        }
    } else {
        Button({ entrar.launch(Conta.intencaoEntrar(ctx)) }, Modifier.height(56.dp)) {
            Text("Entrar com Google", fontSize = 17.sp)
        }
    }
    if (aviso.isNotBlank()) Text(aviso, fontSize = 15.sp)
}
