package br.com.monitor2008.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.monitor2008.data.ConnState
import br.com.monitor2008.domain.FlowMethod
import br.com.monitor2008.domain.MaintState
import br.com.monitor2008.obd.Pid
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

private val OkGreen = Color(0xFF4ADE80)
private val Warn = Color(0xFFFBBF24)
private val Bad = Color(0xFFF87171)

private val BR = Locale("pt", "BR")
private fun Double.f(d: Int = 1): String = String.format(BR, "%.${d}f", this)
private fun Double?.f(d: Int = 1): String = this?.f(d) ?: "--"
private fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

enum class Tab(val label: String) {
    PAINEL("Painel"), CONSUMO("Consumo"), MANUTENCAO("Manutenção"), DIAGNOSTICO("Diagnóstico"), AJUSTES("Ajustes")
}

@Composable
fun MonitorApp(vm: MonitorViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.PAINEL) }

    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            Tab.values().forEach { t ->
                NavigationRailItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = {},
                    label = { Text(t.label, fontSize = 16.sp) },
                    alwaysShowLabel = true,
                    modifier = Modifier.height(72.dp),
                )
            }
        }
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            when (tab) {
                Tab.PAINEL -> PainelScreen(ui)
                Tab.CONSUMO -> ConsumoScreen(ui, vm)
                Tab.MANUTENCAO -> ManutencaoScreen(ui, vm)
                Tab.DIAGNOSTICO -> DiagnosticoScreen(ui, vm)
                Tab.AJUSTES -> AjustesScreen(ui, vm)
            }
        }
    }
}

// ---------- componentes ----------

@Composable
private fun Metric(label: String, value: String, unit: String, modifier: Modifier = Modifier, big: Boolean = false, color: Color = Color.Unspecified) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = if (big) 96.sp else 40.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.width(6.dp))
                Text(unit, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = if (big) 14.dp else 6.dp))
            }
        }
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onMinus, enabled = enabled, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Text("−", fontSize = 26.sp) }
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
        OutlinedButton(onClick = onPlus, enabled = enabled, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 26.sp) }
    }
}

@Composable
private fun Spark(values: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val max = 30.0
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / 119f
            val y = size.height - (v.coerceIn(0.0, max) / max * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        listOf(0f, 10f, 20f, 30f).forEach { g ->
            val y = size.height - g / 30f * size.height
            drawLine(Color(0x33FFFFFF), Offset(0f, y), Offset(size.width, y), 1f)
        }
        drawPath(path, OkGreen, style = Stroke(width = 6f, cap = StrokeCap.Round))
    }
}

// ---------- telas ----------

@Composable
private fun PainelScreen(ui: UiState) {
    val s = ui.sample
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (ui.simulated) {
        Text("DADOS SIMULADOS · ligue o adaptador em Ajustes", color = Warn, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    } else if (ui.conn.state != ConnState.CONNECTED) {
        Text(ui.conn.message.ifBlank { "Aguardando o adaptador..." }, color = Warn, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric("Velocidade", s?.speedKmh?.let { Math.round(it).toString() } ?: "--", "km/h", Modifier.fillMaxWidth().weight(1f), big = true)
            Metric("Rotação", s?.rpm?.let { Math.round(it).toString() } ?: "--", "rpm", Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp).fillMaxSize()) {
                    Text("CONSUMO AGORA", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(ui.kmPerL.f(), fontSize = 96.sp, fontWeight = FontWeight.Bold, color = if ((ui.kmPerL ?: 99.0) < 7) Warn else OkGreen)
                        Text(" km/l", fontSize = 22.sp, modifier = Modifier.padding(bottom = 14.dp))
                    }
                    Text("${ui.litersPerHour.f(2)} L/h · ${methodLabel(ui.method)}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Spark(ui.history, Modifier.fillMaxWidth().height(110.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("Média da viagem", ui.tripAvgKmPerL.f(), "km/l", Modifier.weight(1f))
                Metric("Distância", ui.tripKm.f(1), "km", Modifier.weight(1f))
            }
        }
        Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric("Temp. do motor", s?.coolantC?.let { Math.round(it).toString() } ?: "--", "°C", Modifier.fillMaxWidth(),
                color = if ((s?.coolantC ?: 0.0) > 105) Bad else Color.Unspecified)
            Metric("Bateria", s?.batteryV.f(1), "V", Modifier.fillMaxWidth())
            Metric("Combustível", s?.fuelLevelPct?.let { Math.round(it).toString() } ?: "--", "%", Modifier.fillMaxWidth(),
                color = if ((s?.fuelLevelPct ?: 100.0) < 12) Bad else Color.Unspecified)
            Metric("Ar de admissão", s?.intakeC?.let { Math.round(it).toString() } ?: "--", "°C", Modifier.fillMaxWidth())
        }
    }
    }
}

private fun methodLabel(m: FlowMethod?) = when (m) {
    FlowMethod.FUEL_RATE -> "taxa de combustível"
    FlowMethod.MAF -> "fluxo de ar (MAF)"
    FlowMethod.MAP -> "pressão do coletor (MAP)"
    null -> "sem dados"
}

@Composable
private fun ConsumoScreen(ui: UiState, vm: MonitorViewModel) {
    var dialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Consumo", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric("Média da viagem", ui.tripAvgKmPerL.f(), "km/l", Modifier.weight(1f))
            Metric("Combustível usado", ui.tripLiters.f(2), "L", Modifier.weight(1f))
            Metric("Velocidade média", ui.tripAvgSpeed.f(0), "km/h", Modifier.weight(1f))
        }
        Text("Custo da viagem: R$ ${(ui.tripLiters * ui.fuelPrice).f(2)} · ${ui.tripAvgL100.f(1)} L/100 km · ${ui.tripMinutes} min")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("km/l nos últimos minutos", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spark(ui.history, Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { dialog = true }, enabled = !ui.moving, modifier = Modifier.height(60.dp)) { Text("Registrar abastecimento", fontSize = 18.sp) }
            OutlinedButton(onClick = vm::resetTrip, modifier = Modifier.height(60.dp)) { Text("Zerar viagem", fontSize = 18.sp) }
            if (ui.moving) Text("Pare o carro para registrar.")
        }
        Text("Abastecimentos", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (ui.refuels.isEmpty()) Text("Nenhum registro ainda. Dois tanques cheios seguidos calibram o consumo.")
        ui.refuels.take(8).forEach { r ->
            val day = LocalDate.ofEpochDay(r.epochDay)
            Text("%02d/%02d · %s L · R$ %s/L%s%s".format(day.dayOfMonth, day.monthValue, r.liters.f(1), r.pricePerLiter.f(2),
                if (r.full) " · cheio" else " · parcial", r.realKmPerL?.let { " · ${it.f(1)} km/l real" } ?: ""))
        }
    }
    if (dialog) RefuelDialog(ui.fuelPrice, onDismiss = { dialog = false }) { l, p, full -> vm.registerRefuel(l, p, full); dialog = false }
}

@Composable
private fun RefuelDialog(price: Double, onDismiss: () -> Unit, onSave: (Double, Double, Boolean) -> Unit) {
    var liters by remember { mutableStateOf("") }
    var p by remember { mutableStateOf(price.f(2)) }
    var full by remember { mutableStateOf(true) }
    val l = parse(liters)
    val pr = parse(p)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar abastecimento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(liters, { liters = it }, label = { Text("Litros") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(p, { p = it }, label = { Text("Preço por litro (R$)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(full, { full = it }); Spacer(Modifier.width(10.dp)); Text("Tanque cheio") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(l!!, pr!!, full) }, enabled = l != null && l > 0 && pr != null && pr > 0) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ManutencaoScreen(ui: UiState, vm: MonitorViewModel) {
    var confirm by remember { mutableStateOf(false) }
    val oil = ui.oil
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Manutenção", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (oil != null) {
            val color = when (oil.state) { MaintState.OK -> OkGreen; MaintState.SOON -> Warn; MaintState.DUE -> Bad }
            Metric("Troca de óleo", Math.round(oil.kmLeft).toString(), "km restantes", big = true, color = color)
            LinearProgressIndicator(progress = { (oil.kmLeft / ui.oilIntervalKm).toFloat().coerceIn(0f, 1f) }, color = color, modifier = Modifier.fillMaxWidth().height(14.dp))
            Text("Por data: ${oil.daysLeft} dias restantes · intervalo ${ui.oilIntervalKm} km ou 12 meses (confirme no manual).")
            if (!ui.oilConfigured) Text("Informe quando foi a última troca: toque em \"Registrei a troca\" se foi hoje.", color = Warn)
        }
        Button(onClick = { confirm = true }, enabled = !ui.moving, modifier = Modifier.height(60.dp)) { Text("Registrei a troca", fontSize = 18.sp) }
        Text("O app não lê o nível de óleo pelo OBD2; o controle é por km rodados desde a última troca e por data.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Registrar troca de óleo") },
        text = { Text("O contador reinicia na quilometragem de hoje e o prazo volta a contar.") },
        confirmButton = { TextButton(onClick = { vm.registerOilChange(); confirm = false }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun DiagnosticoScreen(ui: UiState, vm: MonitorViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnóstico", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val c = ui.conn
        val color = when (c.state) { ConnState.CONNECTED -> OkGreen; ConnState.ERROR -> Bad; else -> Warn }
        Text("Conexão: ${c.message.ifBlank { "aguardando" }}${if (c.protocol.isNotBlank()) " · protocolo ${c.protocol}" else ""}", color = color, fontSize = 18.sp)
        OutlinedButton(onClick = vm::start, modifier = Modifier.height(60.dp)) { Text("Reconectar", fontSize = 18.sp) }
        Text("Falhas armazenadas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        when (val d = ui.dtcs) {
            null -> Text("Toque em \"Ler falhas\" para consultar a central.")
            else -> Text(if (d.isEmpty()) "Nenhuma falha armazenada." else d.joinToString(", "))
        }
        Button(onClick = vm::readDtcs, modifier = Modifier.height(60.dp)) { Text("Ler falhas", fontSize = 18.sp) }
        Text("Somente leitura. O app não apaga falhas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Dados que o carro declarou suportar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (c.supportedPids.isEmpty()) Text("Ainda sem informação (conecte o adaptador).")
        Pid.values().forEach { pid ->
            val ok = pid.code in c.supportedPids
            Text("${if (ok) "✔" else "—"}  ${pid.label}", color = if (ok) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (c.supportedPids.isNotEmpty()) {
            Text("Códigos declarados pelo carro (hex): " + c.supportedPids.sorted().joinToString(" ") { Pid.hex2(it) },
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("Registro do adaptador", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        val clipboard = LocalClipboardManager.current
        var logLines by remember { mutableStateOf(emptyList<String>()) }
        LaunchedEffect(Unit) {
            while (true) {
                logLines = vm.diagnosticLog()
                delay(1000)
            }
        }
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(logLines.joinToString("\n"))) },
            modifier = Modifier.height(60.dp),
        ) { Text("Copiar registro", fontSize = 18.sp) }
        Text(
            if (logLines.isEmpty()) "Sem registro ainda." else logLines.joinToString("\n"),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun AjustesScreen(ui: UiState, vm: MonitorViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ajustes", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Combustível no tanque", fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Gasolina E30" to 0.30, "Gasolina E32" to 0.32, "Etanol" to 0.95).forEach { (name, f) ->
                FilterChip(selected = Math.abs(ui.ethanolFraction - f) < 0.001, onClick = { vm.setEthanolFraction(f) },
                    label = { Text(name, fontSize = 18.sp) }, modifier = Modifier.height(56.dp))
            }
        }
        Text("Fator de calibração (ajustado pelos abastecimentos)", fontSize = 18.sp)
        Stepper(ui.calibration.f(2), { vm.setCalibration(ui.calibration - 0.01) }, { vm.setCalibration(ui.calibration + 0.01) })
        Text("Eficiência volumétrica (usada só no método MAP)", fontSize = 18.sp)
        Stepper(ui.volumetricEfficiency.f(2), { vm.setVolumetricEfficiency(ui.volumetricEfficiency - 0.01) }, { vm.setVolumetricEfficiency(ui.volumetricEfficiency + 0.01) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(!ui.simulated, { vm.setSimulated(!it) }, enabled = !ui.moving)
            Spacer(Modifier.width(12.dp))
            Text(if (ui.simulated) "Dados simulados (ligue para usar o adaptador)" else "Usando o adaptador Bluetooth", fontSize = 18.sp)
        }
        if (!ui.simulated && ui.conn.message.isNotBlank()) {
            Text(ui.conn.message, color = if (ui.conn.state == ConnState.ERROR) Warn else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        }
        AdapterPicker(ui, vm)
    }
}

/** Lista os aparelhos pareados para tocar no adaptador, em vez de digitar o nome. */
@Composable
private fun AdapterPicker(ui: UiState, vm: MonitorViewModel) {
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPaired() }
    // Atualiza ao abrir a tela e ao voltar das configurações de Bluetooth.
    LifecycleResumeEffect(Unit) {
        vm.refreshPaired()
        onPauseOrDispose { }
    }

    Text("Adaptador OBD2", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text("Toque no adaptador na lista. Ele precisa estar pareado antes, no Bluetooth do aparelho (senha comum: 1234 ou 0000).",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ vm.refreshPaired() }, Modifier.height(56.dp)) { Text("Atualizar lista", fontSize = 18.sp) }
        OutlinedButton({ context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, Modifier.height(56.dp), enabled = !ui.moving) {
            Text("Abrir Bluetooth do aparelho", fontSize = 18.sp)
        }
    }
    if (ui.paired.problem.isNotBlank()) Text(ui.paired.problem, color = Warn, fontSize = 16.sp)
    if (ui.paired.needsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Button({ askPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }, Modifier.height(56.dp)) {
            Text("Permitir Bluetooth", fontSize = 18.sp)
        }
    }
    FilterChip(selected = ui.adapterName.isBlank(), onClick = { vm.setAdapterName("") }, enabled = !ui.moving,
        label = { Text("Detectar sozinho (nome com OBD, ELM ou V-LINK)", fontSize = 18.sp) }, modifier = Modifier.height(56.dp).fillMaxWidth())
    ui.paired.names.forEach { n ->
        FilterChip(selected = n.equals(ui.adapterName, ignoreCase = true), onClick = { vm.setAdapterName(n) }, enabled = !ui.moving,
            label = { Text(n, fontSize = 18.sp) }, modifier = Modifier.height(56.dp).fillMaxWidth())
    }
}
