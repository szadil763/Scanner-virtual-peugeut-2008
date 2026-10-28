package br.com.monitor2008.ui

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.monitor2008.data.AppChoice
import br.com.monitor2008.data.AppLauncher
import br.com.monitor2008.data.BtPermissions
import br.com.monitor2008.data.MediaWatcher
import br.com.monitor2008.data.ConnState
import br.com.monitor2008.domain.FlowMethod
import br.com.monitor2008.domain.HealthItem
import br.com.monitor2008.domain.HealthStatus
import br.com.monitor2008.domain.RefLine
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
    PAINEL("Painel"), CONSUMO("Consumo"), SAUDE("Saúde"), MANUTENCAO("Manutenção"), DIAGNOSTICO("Diagnóstico"), AJUSTES("Ajustes")
}

/**
 * Três tamanhos: tela inteira; tela dividida com mapas ou música (layout compacto, abaixo de
 * [COMPACT_WIDTH]); e janela flutuante (só os números principais).
 */
private val COMPACT_WIDTH = 800.dp

@Composable
fun MonitorApp(vm: MonitorEngine) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.PAINEL) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < COMPACT_WIDTH
        if (maxWidth < 420.dp || maxHeight < 320.dp) MiniPanel(ui) else Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Tab.values().forEach { t ->
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {},
                        label = { Text(t.label, fontSize = if (compact) 13.sp else 16.sp) },
                        alwaysShowLabel = true,
                        modifier = Modifier.height(if (compact) 60.dp else 68.dp),
                    )
                }
            }
            Column(Modifier.fillMaxSize().padding(if (compact) 10.dp else 16.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (tab) {
                        Tab.PAINEL -> if (compact) PainelCompact(ui) else PainelScreen(ui)
                        Tab.CONSUMO -> ConsumoScreen(ui, vm)
                        Tab.SAUDE -> SaudeScreen(ui, compact)
                        Tab.MANUTENCAO -> ManutencaoScreen(ui, vm)
                        Tab.DIAGNOSTICO -> DiagnosticoScreen(ui, vm)
                        Tab.AJUSTES -> AjustesScreen(ui, vm)
                    }
                }
                // Mapa, música e mini player em todas as abas.
                Spacer(Modifier.height(8.dp))
                MediaBar(compact)
            }
        }
    }
}

/** Janela flutuante: só o que importa dirigindo, em letra grande. */
@Composable
private fun MiniPanel(ui: UiState) {
    val s = ui.sample
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(s?.speedKmh?.let { Math.round(it).toString() } ?: "--", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(" km/h", fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Text(ui.kmPerL.f(), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if ((ui.kmPerL ?: 99.0) < 7) Warn else OkGreen)
            Text(" km/l", fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row {
            Text("${s?.rpm?.let { Math.round(it).toString() } ?: "--"} rpm", fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            val hot = (s?.coolantC ?: 0.0) > 105
            Text("${s?.coolantC?.let { Math.round(it).toString() } ?: "--"} °C", fontSize = 16.sp, color = if (hot) Bad else Color.Unspecified)
        }
        val alerts = ui.health.count { it.status == HealthStatus.ALERTA }
        if (alerts > 0) Text("$alerts alerta(s) em Saúde", color = Bad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- componentes ----------

@Composable
private fun Metric(
    label: String, value: String, unit: String, modifier: Modifier = Modifier, big: Boolean = false,
    color: Color = Color.Unspecified, valueSize: TextUnit = if (big) 96.sp else 40.sp,
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = valueSize, fontWeight = FontWeight.Bold, color = color)
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
                Metric("Média do trajeto", ui.tripAvgKmPerL.f(), "km/l", Modifier.weight(1f))
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

/** Painel para meia tela (ao lado de mapas ou música): os mesmos dados, em duas colunas. */
@Composable
private fun PainelCompact(ui: UiState) {
    val s = ui.sample
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ui.simulated) {
            Text("DADOS SIMULADOS", color = Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else if (ui.conn.state != ConnState.CONNECTED) {
            Text(ui.conn.message.ifBlank { "Aguardando o adaptador..." }, color = Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        val gap = Arrangement.spacedBy(10.dp)
        Row(horizontalArrangement = gap) {
            Metric("Velocidade", s?.speedKmh?.let { Math.round(it).toString() } ?: "--", "km/h", Modifier.weight(1f), valueSize = 56.sp)
            Metric("Consumo", ui.kmPerL.f(), "km/l", Modifier.weight(1f), valueSize = 56.sp, color = if ((ui.kmPerL ?: 99.0) < 7) Warn else OkGreen)
        }
        Row(horizontalArrangement = gap) {
            Metric("Rotação", s?.rpm?.let { Math.round(it).toString() } ?: "--", "rpm", Modifier.weight(1f), valueSize = 32.sp)
            Metric("Temp. motor", s?.coolantC?.let { Math.round(it).toString() } ?: "--", "°C", Modifier.weight(1f), valueSize = 32.sp,
                color = if ((s?.coolantC ?: 0.0) > 105) Bad else Color.Unspecified)
        }
        Row(horizontalArrangement = gap) {
            Metric("Média trajeto", ui.tripAvgKmPerL.f(), "km/l", Modifier.weight(1f), valueSize = 32.sp)
            Metric("Bateria", s?.batteryV.f(1), "V", Modifier.weight(1f), valueSize = 32.sp)
        }
        val alerts = ui.health.count { it.status == HealthStatus.ALERTA }
        val warns = ui.health.count { it.status == HealthStatus.ATENCAO }
        if (alerts + warns > 0) {
            Text("Saúde: $alerts alerta(s), $warns atenção", color = if (alerts > 0) Bad else Warn, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- mapa e música ao lado ----------

/**
 * Atalhos para abrir o mapa ou a música na outra metade da tela, e o mini player do que está
 * tocando. No layout compacto (tela dividida) os botões ficam em cima e o player embaixo.
 */
@Composable
private fun MediaBar(compact: Boolean) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val media = remember { MediaWatcher.get(app) }
    val launcher = remember { AppLauncher(app) }
    val activity = remember(ctx) { ctx.findActivity() }
    val now by media.now.collectAsStateWithLifecycle()
    val allowed by media.allowed.collectAsStateWithLifecycle()
    fun open(result: String?) { if (result != null) Toast.makeText(ctx, result, Toast.LENGTH_LONG).show() }

    val buttons: @Composable () -> Unit = {
        Button({ open(launcher.openMap(activity)) }, Modifier.height(56.dp)) { Text("Mapa", fontSize = 17.sp) }
        OutlinedButton({ open(launcher.openMusic(activity)) }, Modifier.height(56.dp)) { Text("Música", fontSize = 17.sp) }
    }
    val player: @Composable (Modifier) -> Unit = { mod ->
        Row(mod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val n = now
            Column(Modifier.weight(1f)) {
                when {
                    !allowed -> Text("Para ver a música tocando, libere em Ajustes.", fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    n == null -> Text("Nada tocando", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        Text(n.title ?: n.app, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(n.artist, n.app).joinToString(" · "), fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (allowed && n != null) {
                MediaKey("⏮") { media.previous() }
                MediaKey(if (n.playing) "⏸" else "▶") { media.playPause() }
                MediaKey("⏭") { media.next() }
            }
        }
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        if (compact) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
                player(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                buttons()
                Spacer(Modifier.width(6.dp))
                player(Modifier.weight(1f))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun MediaKey(symbol: String, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Text(symbol, fontSize = 22.sp) }
}

/** Escolha dos apps de mapa e música e liberação do mini player. */
@Composable
private fun MapMusicSettings() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val launcher = remember { AppLauncher(app) }
    val media = remember { MediaWatcher.get(app) }
    val allowed by media.allowed.collectAsStateWithLifecycle()
    val maps = remember { launcher.mapApps() }
    val musics = remember { launcher.musicApps() }
    var mapPkg by remember { mutableStateOf(launcher.mapPackage ?: maps.firstOrNull()?.pkg) }
    var musicPkg by remember { mutableStateOf(launcher.musicPackage ?: musics.firstOrNull()?.pkg) }

    Text("Mapa e música", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Com a tela dividida (pelo botão de apps recentes), os botões Mapa e Música abrem o app na outra metade; " +
        "abrir um troca pelo outro. Com o Monitor em tela cheia, o app abre em tela cheia e o Monitor vira janela flutuante.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    Text("App de mapa", fontSize = 18.sp)
    AppChips(maps, mapPkg, "Nenhum app de mapa instalado.") { mapPkg = it; launcher.mapPackage = it }
    Text("App de música", fontSize = 18.sp)
    AppChips(musics, musicPkg, "Nenhum app de música instalado.") { musicPkg = it; launcher.musicPackage = it }
    if (allowed) {
        Text("Mini player ativado: o Painel mostra a música tocando.", color = OkGreen, fontSize = 15.sp)
    } else {
        Button({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, Modifier.height(56.dp)) {
            Text("Mostrar a música tocando no Painel", fontSize = 17.sp)
        }
        Text("Abre \"acesso a notificações\": ative o Monitor 2008. É o que permite ver e controlar a música; " +
            "o app não lê nem guarda suas notificações.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun AppChips(apps: List<AppChoice>, selected: String?, empty: String, onSelect: (String) -> Unit) {
    if (apps.isEmpty()) {
        Text(empty, color = Warn)
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.forEach { a ->
            FilterChip(selected = a.pkg == selected, onClick = { onSelect(a.pkg) },
                label = { Text(a.label, fontSize = 18.sp) }, modifier = Modifier.height(56.dp))
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
private fun ConsumoScreen(ui: UiState, vm: MonitorEngine) {
    var dialog by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Consumo", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val since = ui.tripSinceDay?.let { LocalDate.ofEpochDay(it) }
        Text(
            "Trajeto: soma todas as viagens " +
                (since?.let { "desde %02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: "até você zerar") +
                " · ${Math.round(ui.tripKm)} km em ${ui.tripMinutes / 60} h ${ui.tripMinutes % 60} min",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric("Média do trajeto", ui.tripAvgKmPerL.f(), "km/l", Modifier.weight(1f))
            Metric("Combustível usado", ui.tripLiters.f(2), "L", Modifier.weight(1f))
            Metric("Velocidade média", ui.tripAvgSpeed.f(0), "km/h", Modifier.weight(1f))
        }
        Text("Custo do trajeto: R$ ${(ui.tripLiters * ui.fuelPrice).f(2)} · ${ui.tripAvgL100.f(1)} L/100 km")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("km/l nos últimos minutos", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spark(ui.history, Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { dialog = true }, enabled = !ui.moving, modifier = Modifier.height(60.dp)) { Text("Registrar abastecimento", fontSize = 18.sp) }
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.height(60.dp)) { Text("Zerar trajeto", fontSize = 18.sp) }
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
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Zerar trajeto") },
        text = { Text("Apaga a soma de ${Math.round(ui.tripKm)} km: média de consumo, velocidade média, combustível e custo. Os abastecimentos e a Saúde não são apagados.") },
        confirmButton = { TextButton(onClick = { vm.resetTrip(); confirmReset = false }) { Text("Zerar") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
    )
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

// ---------- Saúde (manutenção preventiva) ----------

private fun statusColor(st: HealthStatus) = when (st) {
    HealthStatus.OK -> OkGreen
    HealthStatus.ATENCAO -> Warn
    HealthStatus.ALERTA -> Bad
    HealthStatus.APRENDENDO -> Color(0xFF94A3B8)
}

private fun statusLabel(st: HealthStatus) = when (st) {
    HealthStatus.OK -> "OK"
    HealthStatus.ATENCAO -> "ATENÇÃO"
    HealthStatus.ALERTA -> "ALERTA"
    HealthStatus.APRENDENDO -> "APRENDENDO"
}

@Composable
private fun SaudeScreen(ui: UiState, compact: Boolean) {
    // Alertas primeiro, depois atenção; "aprendendo" por último.
    val order = listOf(HealthStatus.ALERTA, HealthStatus.ATENCAO, HealthStatus.OK, HealthStatus.APRENDENDO)
    val items = ui.health.sortedBy { order.indexOf(it.status) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Saúde do carro", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val alerts = items.count { it.status == HealthStatus.ALERTA }
        val warns = items.count { it.status == HealthStatus.ATENCAO }
        Text(
            when {
                alerts > 0 -> "$alerts alerta(s) e $warns ponto(s) de atenção"
                warns > 0 -> "$warns ponto(s) de atenção"
                else -> "Nenhum alerta"
            },
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = if (alerts > 0) Bad else if (warns > 0) Warn else OkGreen,
        )
        Text(
            "Baseado em ${ui.tripsRecorded} viagem(ns) gravada(s). Indicadores que costumam mudar antes de uma falha; " +
                "não substituem o diagnóstico de um mecânico.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp,
        )
        if (ui.simulated) {
            Text("Dados simulados: as viagens só são gravadas com o adaptador conectado.", color = Warn, fontSize = 15.sp)
        }
        val perRow = if (compact) 1 else 2
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { HealthCard(it, Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HealthCard(item: HealthItem, modifier: Modifier = Modifier) {
    val color = statusColor(item.status)
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(statusLabel(item.status), color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(item.value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (item.status == HealthStatus.OK) Color.Unspecified else color)
            if (item.series.count { it != null } >= 2) {
                TrendChart(item.series, item.lines, color, Modifier.fillMaxWidth().height(80.dp))
                Text("Últimas ${item.series.size} viagens · ${item.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.advice, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Um ponto por viagem; linhas tracejadas = limites de atenção (amarelo) e alerta (vermelho). */
@Composable
private fun TrendChart(values: List<Double?>, lines: List<RefLine>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val all = values.filterNotNull() + lines.map { it.value }
        var lo = all.minOrNull() ?: return@Canvas
        var hi = all.maxOrNull() ?: return@Canvas
        val pad = ((hi - lo) * 0.1).coerceAtLeast(0.5)
        lo -= pad; hi += pad
        fun y(v: Double) = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
        val step = if (values.size > 1) size.width / (values.size - 1) else 0f
        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        lines.forEach { l ->
            drawLine(if (l.alert) Bad else Warn, Offset(0f, y(l.value)), Offset(size.width, y(l.value)), 2f, pathEffect = dash)
        }
        val path = Path()
        var open = false
        values.forEachIndexed { i, v ->
            if (v == null) { open = false; return@forEachIndexed }
            val p = Offset(step * i, y(v))
            if (open) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
            open = true
            drawCircle(color, 4f, p)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

@Composable
private fun ManutencaoScreen(ui: UiState, vm: MonitorEngine) {
    var confirm by remember { mutableStateOf(false) }
    var itemToConfirm by remember { mutableStateOf<MaintRow?>(null) }
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

        Text("Outros itens", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Intervalos comuns para o 2008 1.6 flex automático: confirme no manual e no plano de revisões da Peugeot.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ui.maint.forEach { row -> MaintCard(row, ui.moving) { itemToConfirm = row } }
    }
    itemToConfirm?.let { row ->
        AlertDialog(
            onDismissRequest = { itemToConfirm = null },
            title = { Text(row.item.name) },
            text = { Text("Registrar que foi feito hoje, com a quilometragem atual do app. O prazo volta a contar a partir de agora.") },
            confirmButton = { TextButton(onClick = { vm.registerMaintDone(row.item.id); itemToConfirm = null }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { itemToConfirm = null }) { Text("Cancelar") } },
        )
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
private fun MaintCard(row: MaintRow, moving: Boolean, onDone: () -> Unit) {
    val st = row.status
    val color = when (st?.state) { MaintState.OK -> OkGreen; MaintState.SOON -> Warn; MaintState.DUE -> Bad; null -> Color(0xFF94A3B8) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.item.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val interval = listOfNotNull(
                    row.item.intervalKm?.let { "${String.format(BR, "%,d", it)} km" },
                    row.item.intervalMonths?.let { "$it meses" },
                ).joinToString(" ou ")
                val left = if (st == null) {
                    "Informe a última troca (toque em \"Fiz agora\" se foi hoje) · a cada $interval"
                } else {
                    listOfNotNull(
                        st.kmLeft?.let { if (it <= 0) "vencido por km" else "${String.format(BR, "%,d", Math.round(it))} km" },
                        st.daysLeft?.let { if (it <= 0) "vencido por data" else "$it dias" },
                    ).joinToString(" · ") + " · a cada $interval"
                }
                Text(left, color = color, fontSize = 15.sp)
                Text(row.item.why, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onDone, enabled = !moving, modifier = Modifier.height(56.dp)) { Text("Fiz agora", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun DiagnosticoScreen(ui: UiState, vm: MonitorEngine) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnóstico", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val context = LocalContext.current
        val version = remember {
            try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { null }
        }
        Text("Versão do app: ${version ?: "?"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        val c = ui.conn
        val color = when (c.state) { ConnState.CONNECTED -> OkGreen; ConnState.ERROR -> Bad; else -> Warn }
        Text("Conexão: ${c.message.ifBlank { "aguardando" }}${if (c.protocol.isNotBlank()) " · protocolo ${c.protocol}" else ""}", color = color, fontSize = 18.sp)
        OutlinedButton(onClick = vm::start, modifier = Modifier.height(60.dp)) { Text("Reconectar", fontSize = 18.sp) }
        Text("Falhas armazenadas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        when (val d = ui.dtcs) {
            null -> Text("Toque em \"Ler falhas\" para consultar a central.")
            else -> Text(if (d.isEmpty()) "Nenhuma falha armazenada." else d.joinToString(", "))
        }
        ui.pendingDtcs?.let { p ->
            Text(if (p.isEmpty()) "Nenhuma falha pendente." else "Pendentes (ainda sem luz): " + p.joinToString(", "),
                color = if (p.isEmpty()) Color.Unspecified else Warn)
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
private fun AjustesScreen(ui: UiState, vm: MonitorEngine) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(ui.pipOnLeave, { vm.setPipOnLeave(it) })
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Janela flutuante ao sair do app", fontSize = 18.sp)
                Text("Ao tocar em Início, o monitor continua numa janelinha sobre o mapa ou a música. " +
                    "Para tela dividida, use o botão de apps recentes do multimídia.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }
        if (!ui.simulated && ui.conn.message.isNotBlank()) {
            Text(ui.conn.message, color = if (ui.conn.state == ConnState.ERROR) Warn else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        }
        AdapterPicker(ui, vm)
        MapMusicSettings()
    }
}

/** Lista os aparelhos pareados para tocar no adaptador, em vez de digitar o nome. */
@Composable
private fun AdapterPicker(ui: UiState, vm: MonitorEngine) {
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshPaired() }
    // Atualiza ao abrir a tela e ao voltar das configurações de Bluetooth.
    LifecycleResumeEffect(Unit) {
        vm.refreshPaired()
        onPauseOrDispose { }
    }

    Text("Adaptador OBD2", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text("Adaptador BLE (como o OBDBLE) não precisa parear nem de PIN: deixe em Detectar sozinho. " +
        "Adaptador clássico precisa estar pareado antes no Bluetooth do aparelho; aí ele aparece aqui para tocar.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ vm.refreshPaired() }, Modifier.height(56.dp)) { Text("Atualizar lista", fontSize = 18.sp) }
        OutlinedButton({ context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, Modifier.height(56.dp), enabled = !ui.moving) {
            Text("Abrir Bluetooth do aparelho", fontSize = 18.sp)
        }
    }
    if (ui.paired.problem.isNotBlank()) Text(ui.paired.problem, color = Warn, fontSize = 16.sp)
    if (ui.paired.needsPermission) {
        Button({ askPermission.launch(BtPermissions.needed) }, Modifier.height(56.dp)) {
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
