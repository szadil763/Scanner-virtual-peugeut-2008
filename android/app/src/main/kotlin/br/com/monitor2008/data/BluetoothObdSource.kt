package br.com.monitor2008.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import br.com.monitor2008.obd.Elm327Client
import br.com.monitor2008.obd.ObdParser
import br.com.monitor2008.obd.Pid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Adaptador ELM327 por Bluetooth. Tenta primeiro o clássico (RFCOMM/SPP), se o adaptador estiver
 * pareado nas configurações do aparelho; senão procura por BLE ([BleLink]), que não usa PIN.
 *
 * Reconecta sozinho se a conexão cair e pausa a leitura com o carro desligado
 * (consultar com a ignição desligada mantém os módulos acordados e descarrega a bateria).
 *
 * NUNCA TESTADO NO CARRO. Primeira tarefa da fase 1: validar com o adaptador real.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT e BLUETOOTH_SCAN são pedidas na MainActivity
class BluetoothObdSource(
    private val context: Context,
    private val preferredName: String?,
) : ObdSource {

    override val name = "Adaptador Bluetooth"

    private val _info = MutableStateFlow(ConnectionInfo())
    override val info: StateFlow<ConnectionInfo> = _info.asStateFlow()

    private val store = Store(context)
    private var socket: BluetoothSocket? = null
    private var bleLink: BleLink? = null
    private var client: Elm327Client? = null
    private val lock = Any() // o ELM327 atende um comando por vez

    // Registro para diagnóstico: conversa de inicialização e a primeira resposta de cada dado.
    private val logLines = ArrayList<String>()
    private var logging = false
    private val seen = HashSet<Pid>()

    override fun diagnosticLog(): List<String> = synchronized(logLines) { logLines.toList() }

    private fun log(line: String) {
        synchronized(logLines) {
            logLines.add(line)
            while (logLines.size > 300) logLines.removeAt(0)
        }
    }

    override fun samples(): Flow<Sample> = flow {
        while (currentCoroutineContext().isActive) {
            _info.value = ConnectionInfo(ConnState.CONNECTING, "Procurando o adaptador...")
            val c = connect()
            if (c == null) { // connect() já deixou a mensagem de erro em _info
                delay(RETRY_MS)
                continue
            }

            var engineOff = false
            try {
                logging = true
                seen.clear()
                val protocol = synchronized(lock) { c.initialize() }
                // Sem resposta de rotação = ignição desligada: não vale insistir agora.
                val rpm0 = synchronized(lock) { c.query(Pid.RPM) }
                if (rpm0 == null) {
                    logging = false
                    engineOff = true
                } else {
                    val supported = synchronized(lock) { c.supportedPids() }
                    logging = false
                    _info.value = ConnectionInfo(ConnState.CONNECTED, "Conectado", protocol, supported)

                    val values = mutableMapOf<Pid, Double>()
                    var adapterVolts: Double? = null
                    var cycle = 0
                    var stoppedSince = 0L
                    while (currentCoroutineContext().isActive) {
                        for (pid in plan(cycle, supported)) {
                            val raw = synchronized(lock) { c.send(pid.command) }
                            val v = if (pid == Pid.MONITOR_STATUS) ObdParser.monitorStatus(raw) else ObdParser.parse(pid, raw)
                            note(pid, raw, v)
                            if (v != null) values[pid] = v
                        }
                        // O 2008 não informa o PID 42: a bateria vem da medição do próprio adaptador.
                        if (cycle % 10 == 0 && Pid.MODULE_VOLTAGE.code !in supported) {
                            val raw = synchronized(lock) { c.send("ATRV") }
                            ObdParser.voltage(raw)?.let { adapterVolts = it }
                            if (cycle == 0) log("ATRV (Bateria pelo adaptador) -> ${raw.shownRaw()} => ${adapterVolts ?: "sem valor"}")
                        }
                        emit(toSample(values, adapterVolts))
                        cycle++

                        val rpm = values[Pid.RPM]
                        if (rpm != null && rpm > 0) {
                            stoppedSince = 0L
                        } else {
                            val now = System.currentTimeMillis()
                            if (stoppedSince == 0L) stoppedSince = now
                            if (now - stoppedSince > ENGINE_OFF_MS) { engineOff = true; break }
                        }
                    }
                }
            } catch (e: IOException) {
                log("ERRO: ${e.message}")
                _info.value = ConnectionInfo(ConnState.ERROR, "Conexão perdida: ${e.message ?: "sem resposta"}. Tentando de novo...")
            } finally {
                logging = false
                close()
            }

            if (engineOff) {
                _info.value = ConnectionInfo(ConnState.IDLE, "Carro desligado: leitura pausada. Volta sozinha quando ligar.")
                delay(PAUSE_MS)
            } else {
                delay(RETRY_MS)
            }
        }
    }
        .flowOn(Dispatchers.IO)
        .catch { e ->
            _info.value = ConnectionInfo(ConnState.ERROR, e.message ?: "Falha na conexão")
        }
        .onCompletion { close() }

    override suspend fun readDtcs(): List<String> = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext emptyList()
        synchronized(lock) { c.readDtcs() }
    }

    override suspend fun readPendingDtcs(): List<String> = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext emptyList()
        synchronized(lock) { c.readPendingDtcs() }
    }

    /** Guarda no registro a primeira resposta de cada dado, para ver o que o carro realmente devolve. */
    private fun note(pid: Pid, raw: String, value: Double?) {
        if (!seen.add(pid)) return
        val shown = raw.shownRaw()
        log("${pid.command} (${pid.label}) -> $shown => ${value ?: "sem valor"}")
    }

    /**
     * PIDs rápidos a cada ciclo; os lentos (temperaturas, bateria, nível) a cada 10 ciclos.
     * A sonda antes do catalisador e a correção curta ficam nos rápidos: a saúde da sonda é medida
     * pelo quanto ela oscila.
     */
    private fun plan(cycle: Int, supported: Set<Int>): List<Pid> {
        val fast = listOf(Pid.RPM, Pid.SPEED, Pid.FUEL_RATE, Pid.MAF, Pid.MAP, Pid.LOAD, Pid.O2_S1, Pid.STFT)
        val slow = listOf(
            Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.MODULE_VOLTAGE, Pid.FUEL_LEVEL, Pid.THROTTLE,
            Pid.LTFT, Pid.TIMING, Pid.O2_S2, Pid.MONITOR_STATUS, Pid.DIST_SINCE_CLEAR,
        )
        val wanted = if (cycle % 10 == 0) fast + slow else fast
        // Se o filtro esvaziar a lista (máscara mal lida), consulta tudo: o que não existir volta NO DATA.
        return wanted.filter { it.code in supported }.ifEmpty { wanted }
    }

    private fun toSample(v: Map<Pid, Double>, adapterVolts: Double?) = Sample(
        timeMs = System.currentTimeMillis(),
        rpm = v[Pid.RPM],
        speedKmh = v[Pid.SPEED],
        coolantC = v[Pid.COOLANT_TEMP],
        loadPct = v[Pid.LOAD],
        mapKpa = v[Pid.MAP],
        intakeC = v[Pid.INTAKE_TEMP],
        mafGs = v[Pid.MAF],
        throttlePct = v[Pid.THROTTLE],
        fuelLevelPct = v[Pid.FUEL_LEVEL],
        batteryV = v[Pid.MODULE_VOLTAGE] ?: adapterVolts,
        fuelRateLph = v[Pid.FUEL_RATE],
        stftPct = v[Pid.STFT],
        ltftPct = v[Pid.LTFT],
        timingDeg = v[Pid.TIMING],
        o2s1V = v[Pid.O2_S1],
        o2s2V = v[Pid.O2_S2],
        milOn = v[Pid.MONITOR_STATUS]?.let { (it.toInt() and 0x80) != 0 },
        dtcCount = v[Pid.MONITOR_STATUS]?.let { it.toInt() and 0x7F },
        distSinceClearKm = v[Pid.DIST_SINCE_CLEAR],
    )

    private fun fail(msg: String): Elm327Client? {
        log("ERRO: $msg")
        _info.value = ConnectionInfo(ConnState.ERROR, msg)
        return null
    }

    /**
     * Ordem: BLE primeiro, depois o clássico pareado. O adaptador do dono é duplo (OBDII clássico +
     * OBDBLE): no C4 o multimídia conseguiu parear o OBDII, e o app ficava preso tentando o clássico
     * (cada tentativa espera até o Android desistir) antes de chegar ao BLE, que é o caminho que
     * funciona no 2008. Agora o BLE vem antes, e o clássico tem limite de tempo.
     */
    private fun connect(): Elm327Client? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return fail("Este aparelho não tem Bluetooth")
        if (!adapter.isEnabled) return fail("Bluetooth desligado")

        val bonded = adapter.bondedDevices.toList().firstOrNull { matches(it.name) }

        // 1) BLE (por exemplo "OBDBLE"): não precisa parear nem de PIN. Busca mais curta se ainda há o clássico para tentar.
        val ble = try {
            connectBle(adapter, scanMs = if (bonded != null) 8_000 else 15_000)
        } catch (e: SecurityException) {
            return fail("Sem permissão para procurar o adaptador. Toque em Permitir Bluetooth em Ajustes.")
        }
        if (ble != null) {
            log("Dispositivo BLE: ${ble.deviceName}")
            bleLink = ble
            return Elm327Client(ble.input, ble.output, ::exchanged).also { client = it }
        }

        // 2) Bluetooth clássico, se o adaptador estiver pareado.
        if (bonded != null) {
            _info.value = ConnectionInfo(ConnState.CONNECTING, "Tentando o ${bonded.name} pelo Bluetooth clássico...")
            log("Dispositivo: ${bonded.name} (${bonded.address})")
            adapter.cancelDiscovery()
            val s = try {
                bonded.createRfcommSocketToServiceRecord(SPP_UUID).also { connectWithTimeout(it) }
            } catch (e: IOException) {
                log("Conexão padrão falhou (${e.message}); tentando o canal 1")
                // Alguns clones só aceitam o canal 1 direto (truque conhecido).
                try {
                    val m = bonded.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    (m.invoke(bonded, 1) as BluetoothSocket).also { connectWithTimeout(it) }
                } catch (e2: Exception) {
                    log("Clássico falhou: ${e2.message ?: e.message}")
                    null
                }
            }
            if (s != null) {
                socket = s
                return Elm327Client(s.inputStream, s.outputStream, ::exchanged).also { client = it }
            }
            return fail(
                "Adaptador não conectou. Com a ignição ligada, se continuar assim: nas configurações de Bluetooth do " +
                    "aparelho, esqueça (desparear) o ${bonded.name}. O app usa o OBDBLE sem parear, como no 2008. Tentando de novo...",
            )
        }
        return fail(
            "Adaptador não encontrado. Ligue a ignição. Se o Monitor estiver aberto em outro aparelho (celular), " +
                "feche lá: o adaptador aceita uma conexão por vez. Tentando de novo...",
        )
    }

    /** O connect() do Bluetooth clássico pode travar meio minuto; fecha o socket se passar de 12 s. */
    private fun connectWithTimeout(s: BluetoothSocket) {
        val watchdog = Thread {
            try {
                Thread.sleep(12_000)
                s.close()
            } catch (_: InterruptedException) {
            } catch (_: IOException) {
            }
        }.apply { isDaemon = true; start() }
        try {
            s.connect()
        } finally {
            watchdog.interrupt()
        }
    }

    /**
     * Conecta por BLE. Primeiro direto no endereço que já conectou antes (sem busca: com o celular
     * tocando música pelo multimídia, a busca fica lenta e às vezes não acha o adaptador); se falhar,
     * procura pelo nome. Cada conexão tenta 3 vezes, porque o chip do multimídia costuma falhar a
     * primeira (status 133) quando já tem áudio Bluetooth ativo.
     */
    private fun connectBle(adapter: BluetoothAdapter, scanMs: Long): BleLink? {
        val addr = store.bleAddress
        if (addr != null && BluetoothAdapter.checkBluetoothAddress(addr) && matches(store.bleName)) {
            _info.value = ConnectionInfo(ConnState.CONNECTING, "Conectando no adaptador ${store.bleName}...")
            log("BLE: conectando direto em ${store.bleName} ($addr)")
            openWithRetry(adapter.getRemoteDevice(addr))?.let { return it }
        }

        _info.value = ConnectionInfo(ConnState.CONNECTING, "Procurando o adaptador BLE...")
        val seen = mutableSetOf<String>()
        val device = BleLink.find(adapter, ::matches, scanMs = scanMs, seen = seen)
        if (device == null) {
            log("BLE: a busca não achou o adaptador. Aparelhos BLE vistos: ${synchronized(seen) { seen.joinToString(", ") }.ifBlank { "nenhum" }}")
            return null
        }
        return openWithRetry(device)?.also {
            store.bleAddress = device.address
            store.bleName = it.deviceName
        }
    }

    private fun openWithRetry(device: BluetoothDevice): BleLink? {
        repeat(3) { attempt ->
            BleLink.open(context, device, ::log)?.let { return it }
            if (attempt < 2) Thread.sleep(1_500)
        }
        return null
    }

    private fun exchanged(cmd: String, resp: String) {
        if (logging) log("$cmd -> ${resp.shownRaw()}")
    }

    /** Resposta para o registro: "|" marca cada quebra de linha, para ver como as centrais chegam. */
    private fun String.shownRaw() = trim().replace("\r\n", "|").replace('\r', '|').replace('\n', '|')
        .replace(Regex("[\\x00-\\x1F]"), "·")

    private fun matches(n: String?): Boolean {
        if (n == null) return false
        if (!preferredName.isNullOrBlank()) return n.equals(preferredName, ignoreCase = true)
        return KNOWN_NAMES.any { n.contains(it, ignoreCase = true) }
    }

    private fun close() {
        try { socket?.close() } catch (_: IOException) { }
        socket = null
        bleLink?.close()
        bleLink = null
        client = null
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val KNOWN_NAMES = listOf("OBD", "ELM", "V-LINK", "VLINK", "VGATE", "IOS-")
        const val RETRY_MS = 5_000L
        const val PAUSE_MS = 60_000L
        const val ENGINE_OFF_MS = 5 * 60_000L
    }
}
