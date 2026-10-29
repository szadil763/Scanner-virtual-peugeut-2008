package br.com.monitor2008.ui

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.monitor2008.data.AppLauncher
import br.com.monitor2008.data.ConnState
import br.com.monitor2008.data.MediaWatcher
import br.com.monitor2008.data.NowPlaying
import br.com.monitor2008.data.PlaceFinder
import br.com.monitor2008.data.RadioBrowser
import br.com.monitor2008.data.RadioPlayer
import br.com.monitor2008.data.Store
import br.com.monitor2008.domain.RadioStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// ---------- Mídias: player grande, fontes de música e rádio pela internet ----------
//
// Nenhum app consegue mostrar o Spotify ou o Amazon Music dentro dele: o player controla a música
// que está tocando (sessão de mídia) e as fontes mandam o app tocar em segundo plano. O rádio pela
// internet toca no próprio app (RadioService).

/** Fonte de música: app instalado ou que já tocou algo (o Bluetooth e o rádio do multimídia entram assim). */
private data class Source(val pkg: String, val label: String)

@Composable
private fun rememberSources(): List<Source> {
    val ctx = LocalContext.current
    return remember {
        val launcher = AppLauncher(ctx.applicationContext)
        val seen = Store(ctx.applicationContext).mediaSeen.filter { it != ctx.packageName }
        (launcher.musicApps().map { Source(it.pkg, it.label) } + seen.map { Source(it, launcher.label(it)) })
            .distinctBy { it.pkg }
            .map { if (it.label.contains("bluetooth", ignoreCase = true)) it.copy(label = "Bluetooth (celular)") else it }
    }
}

/** Título e linha de baixo do que está tocando. O Bluetooth do multimídia costuma não mandar o nome da faixa. */
private fun texts(n: NowPlaying): Pair<String, String> {
    val bt = n.app.contains("bluetooth", ignoreCase = true)
    val title = n.title?.takeIf { it.isNotBlank() && it != n.app } ?: if (bt) "Música do celular (Bluetooth)" else n.app
    val sub = listOfNotNull(n.artist, n.app.takeIf { !bt || n.title != null }).distinct().filter { it != title && it.isNotBlank() }
    return title to sub.joinToString(" · ")
}

private fun time(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun BigKey(symbol: String, size: Int, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    if (filled) {
        Button(onClick, Modifier.size(size.dp), enabled = enabled) { Text(symbol, fontSize = (size / 3).sp) }
    } else {
        OutlinedButton(onClick, Modifier.size(size.dp), enabled = enabled, shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
            Text(symbol, fontSize = (size / 3).sp)
        }
    }
}

/**
 * Player grande: capa, nome, tempo e botões de 68 a 84 dp. Mostra o rádio do app quando ele está
 * tocando (ou é o último usado); senão, a música de qualquer outro app.
 */
@Composable
internal fun BigPlayer(modifier: Modifier, large: Boolean) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val media = remember { MediaWatcher.get(app) }
    val radio = remember { RadioPlayer.get(app) }
    val now by media.now.collectAsStateWithLifecycle()
    val allowed by media.allowed.collectAsStateWithLifecycle()
    val rs by radio.state.collectAsStateWithLifecycle()
    val n = now
    val showRadio = rs.station != null && (rs.playing || rs.buffering || rs.error != null || n == null || n.pkg == ctx.packageName)

    // Relógio da barra de tempo: só anda com música tocando.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(n?.playing, n?.positionAt) {
        while (n?.playing == true) {
            tick = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val keySize = if (large) 84 else 68

    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val artSize = if (large) 150.dp else 110.dp
                Box(
                    Modifier.size(artSize).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = if (showRadio) null else n?.art
                    if (art != null) {
                        Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(if (showRadio) "RÁDIO" else "♪", fontSize = if (showRadio) 22.sp else 56.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (label, title, sub) = when {
                        showRadio -> Triple("RÁDIO PELA INTERNET", rs.station!!.name, rs.streamTitle ?: rs.station!!.subtitle)
                        n != null -> texts(n).let { (t, s) -> Triple(n.app.uppercase(), t, s) }
                        else -> Triple("MÍDIAS", "Nada tocando", "Escolha uma fonte abaixo.")
                    }
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(title, fontSize = if (large) 28.sp else 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (sub.isNotBlank()) Text(sub, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            when {
                showRadio && rs.error != null -> Text(rs.error!!, color = Bad, fontSize = 16.sp)
                showRadio && rs.buffering -> {
                    Text("Conectando à rádio...", color = Warn, fontSize = 16.sp)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                showRadio && rs.playing -> Text("AO VIVO", color = Bad, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                !showRadio && n != null && n.durationMs > 0 -> {
                    val pos = if (tick >= 0) n.positionNow().coerceIn(0, n.durationMs) else 0L
                    LinearProgressIndicator(progress = { pos.toFloat() / n.durationMs }, modifier = Modifier.fillMaxWidth().height(8.dp))
                    Row {
                        Text(time(pos), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(time(n.durationMs), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                if (showRadio) {
                    val several = rs.favorites.size > 1
                    BigKey("⏮", keySize, enabled = several) { radio.step(forward = false) }
                    BigKey(if (rs.playing) "⏸" else "▶", keySize + 12, filled = true) { radio.toggle() }
                    BigKey("⏭", keySize, enabled = several) { radio.step(forward = true) }
                } else if (n != null) {
                    BigKey("⏮", keySize) { media.previous() }
                    BigKey(if (n.playing) "⏸" else "▶", keySize + 12, filled = true) { media.playPause() }
                    BigKey("⏭", keySize) { media.next() }
                }
            }
            if (showRadio) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    val fav = rs.favorites.any { it.id == rs.station?.id }
                    OutlinedButton({ rs.station?.let { radio.toggleFavorite(it) } }, Modifier.height(56.dp)) {
                        Text(if (fav) "★ Favorita" else "☆ Favoritar", fontSize = 17.sp, color = if (fav) Warn else Color.Unspecified)
                    }
                    OutlinedButton({ radio.stop() }, Modifier.height(56.dp)) { Text("Parar", fontSize = 17.sp) }
                }
            }
            if (showRadio && rs.favorites.size <= 1) {
                Text("Com 2 ou mais favoritas, ⏮ e ⏭ trocam de rádio.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!allowed && !showRadio) {
                Text("Para ver e controlar a música dos outros apps, libere o \"acesso a notificações\" do Monitor 2008 " +
                    "(o app não lê nem guarda suas notificações).", fontSize = 15.sp, color = Warn)
                Button({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, Modifier.height(56.dp)) {
                    Text("Liberar", fontSize = 17.sp)
                }
            }
        }
    }
}

/** Botões das fontes: um toque começa a tocar no app, que fica em segundo plano. */
@Composable
private fun SourceButtons(onRadio: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val media = remember { MediaWatcher.get(app) }
    val radio = remember { RadioPlayer.get(app) }
    val activity = remember(ctx) { ctx.findActivity() }
    val now by media.now.collectAsStateWithLifecycle()
    val rs by radio.state.collectAsStateWithLifecycle()
    val sources = rememberSources()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        sources.forEach { s ->
            val active = now?.let { it.pkg == s.pkg && it.playing } == true
            FilterChip(selected = active, onClick = { media.playOn(s.pkg, activity) },
                label = { Text(s.label, fontSize = 18.sp) }, modifier = Modifier.height(60.dp))
        }
        FilterChip(selected = rs.playing, onClick = onRadio, label = { Text("Rádio", fontSize = 18.sp) }, modifier = Modifier.height(60.dp))
    }
}

private enum class RadioList(val label: String) { FAVORITAS("Favoritas"), POPULARES("Mais ouvidas"), REGIAO("Da região"), BUSCAR("Buscar") }

/** Lista de rádios do Brasil: favoritas, mais ouvidas, do estado (pelo GPS) e busca por nome (só parado). */
@Composable
private fun RadioPanel(moving: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val radio = remember { RadioPlayer.get(ctx.applicationContext as Application) }
    val rs by radio.state.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(if (radio.state.value.favorites.isEmpty()) RadioList.POPULARES else RadioList.FAVORITAS) }
    var list by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var searches by remember { mutableIntStateOf(0) }

    LaunchedEffect(mode, searches) {
        message = null
        list = emptyList()
        if (mode == RadioList.FAVORITAS) return@LaunchedEffect
        if (mode == RadioList.BUSCAR && query.isBlank()) {
            message = "Digite o nome da rádio (com o carro parado)."
            return@LaunchedEffect
        }
        loading = true
        try {
            list = when (mode) {
                RadioList.REGIAO -> {
                    val loc = PlaceFinder.current(ctx, timeoutMs = 8_000)
                    val state = loc?.let { PlaceFinder.state(ctx, it.latitude, it.longitude) }
                    if (state == null) {
                        message = "Sem localização: mostrando as mais ouvidas do Brasil."
                        RadioBrowser.search()
                    } else {
                        RadioBrowser.region(state).also { if (it.isEmpty()) message = "Nenhuma rádio de $state no catálogo." }
                    }
                }
                RadioList.BUSCAR -> RadioBrowser.search(name = query).also { if (it.isEmpty()) message = "Nenhuma rádio com \"$query\"." }
                else -> RadioBrowser.search()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "Sem internet: não foi possível buscar as rádios."
        }
        loading = false
    }

    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rádio pela internet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioList.values().forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label, fontSize = 17.sp) },
                        modifier = Modifier.height(56.dp))
                }
            }
            if (mode == RadioList.BUSCAR) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        query, { query = it }, Modifier.weight(1f), enabled = !moving, singleLine = true,
                        label = { Text("Nome da rádio") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searches++ }),
                    )
                    Button({ searches++ }, Modifier.height(56.dp), enabled = !moving && query.isNotBlank()) { Text("Buscar", fontSize = 17.sp) }
                }
                if (moving) Text("Pare o carro para digitar.", color = Warn, fontSize = 15.sp)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val shown = if (mode == RadioList.FAVORITAS) rs.favorites else list
            if (mode == RadioList.FAVORITAS && shown.isEmpty()) {
                Text("Nenhuma favorita ainda. Toque na ☆ de uma rádio em \"Mais ouvidas\" ou \"Da região\".", fontSize = 16.sp)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(shown, key = { it.id }) { st ->
                    val current = rs.station?.id == st.id
                    val fav = rs.favorites.any { it.id == st.id }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (current) StrongBlue.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable { radio.play(st) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(st.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (st.subtitle.isNotBlank()) Text(st.subtitle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton({ radio.toggleFavorite(st) }, Modifier.size(56.dp), contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (fav) Warn else MaterialTheme.colorScheme.onSurfaceVariant)) {
                            Text(if (fav) "★" else "☆", fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Aba Mídias: player grande e fontes à esquerda, rádios à direita. */
@Composable
internal fun MidiasScreen(ui: UiState, compact: Boolean, painelMusica: Boolean, onPainelMusica: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val radio = remember { RadioPlayer.get(ctx.applicationContext as Application) }
    val onRadio: () -> Unit = { if (!radio.resume()) Toast.makeText(ctx, "Escolha uma rádio na lista.", Toast.LENGTH_LONG).show() }
    val toggle: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(painelMusica, onPainelMusica)
            Spacer(Modifier.width(12.dp))
            Text("Painel + Música: o Painel mostra este player ao lado dos números", fontSize = 16.sp)
        }
    }
    if (compact) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigPlayer(Modifier.fillMaxWidth().height(270.dp), large = false)
            SourceButtons(onRadio)
            RadioPanel(ui.moving, Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigPlayer(Modifier.fillMaxWidth().weight(1f), large = true)
                SourceButtons(onRadio)
                toggle()
            }
            RadioPanel(ui.moving, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * Painel + Música: os números principais à esquerda e o player grande à direita, na mesma tela.
 * Dispensa a tela dividida do Android, que no multimídia deixava a música espremida.
 */
@Composable
internal fun PainelMusicaScreen(ui: UiState) {
    val ctx = LocalContext.current
    val radio = remember { RadioPlayer.get(ctx.applicationContext as Application) }
    val s = ui.sample
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val big = if (maxWidth > 1000.dp) 72.sp else 52.sp
        val mid = if (maxWidth > 1000.dp) 48.sp else 36.sp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ui.simulated) {
                    Text("DADOS SIMULADOS · ligue o adaptador em Ajustes", color = Warn, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else if (ui.conn.state != ConnState.CONNECTED) {
                    Text(ui.conn.message.ifBlank { "Aguardando o adaptador..." }, color = Warn, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Velocidade", s?.speedKmh?.let { Math.round(it).toString() } ?: "—", "km/h",
                        Modifier.weight(1f).fillMaxHeight(), valueSize = big)
                    val cv = consumoView(ui)
                    Metric("Consumo", cv.value, cv.unit, Modifier.weight(1f).fillMaxHeight(), valueSize = big, color = cv.color)
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Rotação", s?.rpm?.let { Math.round(it).toString() } ?: "—", "rpm", Modifier.weight(1f).fillMaxHeight(), valueSize = mid)
                    Metric("Temp. motor", s?.coolantC?.let { Math.round(it).toString() } ?: "--", "°C", Modifier.weight(1f).fillMaxHeight(),
                        valueSize = mid, color = if ((s?.coolantC ?: 0.0) > 105) Bad else Color.Unspecified)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Média trajeto", ui.tripAvgKmPerL.f(), "km/l", Modifier.weight(1f), valueSize = 32.sp)
                    Metric("Distância", ui.tripKm.f(1), "km", Modifier.weight(1f), valueSize = 32.sp)
                }
                saudeResumo(ui)?.let { (t, c) ->
                    Text(t, color = c, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigPlayer(Modifier.fillMaxWidth().weight(1f), large = false)
                SourceButtons {
                    if (!radio.resume()) Toast.makeText(ctx, "Escolha uma rádio na aba Mídias.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
