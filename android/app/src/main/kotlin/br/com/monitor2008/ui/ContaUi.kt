package br.com.monitor2008.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import br.com.monitor2008.data.Conta

/** Ajustes: entrar com a conta Google (backup na nuvem, dados separados por conta). */
@Composable
internal fun ContaSection() {
    val ctx = LocalContext.current
    var email by remember { mutableStateOf(Conta.email()) }
    var aviso by remember { mutableStateOf("") }
    val entrar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        Conta.aoResultado(r.data) { _, texto ->
            aviso = texto
            email = Conta.email()
        }
    }
    Text("Conta Google (backup)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Entre com uma conta Google para guardar o histórico na nuvem. Cada conta tem os próprios dados.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 15.sp,
    )
    val atual = email
    if (atual != null) {
        Text("Conectado como: $atual", fontSize = 16.sp)
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
