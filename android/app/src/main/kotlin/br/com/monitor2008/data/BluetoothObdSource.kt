package br.com.monitor2008.data

import android.annotation.SuppressLint
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
 * Adaptador ELM327 por Bluetooth clássico (RFCOMM/SPP). O adaptador precisa estar pareado
 * antes, nas configurações de Bluetooth do aparelho (senha comum: 1234, 8888 ou 0000).
 *
 * Reconecta sozinho se a conexão cair e pausa a leitura com o carro desligado
 * (consultar com a ignição desligada mantém os módulos acordados e descarrega a bateria).
 *
 * NUNCA TESTADO NO CARRO. Primeira tarefa da fase 1: validar com o adaptador real.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT é pedida na MainActivity
class BluetoothObdSource(
    private val context: Context,
    private val preferredName: String?,
) : ObdSource {

    override val name = "Adaptador Bluetooth"

    private val _info = MutableStateFlow(ConnectionInfo())
    override val info: StateFlow<ConnectionInfo> = _info.asStateFlow()

    private var socket: BluetoothSocket? = null
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
                    var cycle = 0
                    var stoppedSince = 0L
                    while (currentCoroutineContext().isActive) {
                        for (pid in plan(cycle, supported)) {
                            val raw = synchronized(lock) { c.send(pid.command) }
                            val v = ObdParser.parse(pid, raw)
                            note(pid, raw, v)
                            if (v != null) values[pid] = v
                        }
                        emit(toSample(values))
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

    /** Guarda no registro a primeira resposta de cada dado, para ver o que o carro realmente devolve. */
    private fun note(pid: Pid, raw: String, value: Double?) {
        if (!seen.add(pid)) return
        val shown = raw.replace('\r', ' ').replace('\n', ' ').trim()
        log("${pid.command} (${pid.label}) -> $shown => ${value ?: "sem valor"}")
    }

    /** PIDs rápidos a cada ciclo; os lentos (temperaturas, bateria, nível) a cada 10 ciclos. */
    private fun plan(cycle: Int, supported: Set<Int>): List<Pid> {
        val fast = listOf(Pid.RPM, Pid.SPEED, Pid.FUEL_RATE, Pid.MAF, Pid.MAP, Pid.LOAD)
        val slow = listOf(Pid.COOLANT_TEMP, Pid.INTAKE_TEMP, Pid.MODULE_VOLTAGE, Pid.FUEL_LEVEL, Pid.THROTTLE)
        val wanted = if (cycle % 10 == 0) fast + slow else fast
        return if (supported.isEmpty()) wanted else wanted.filter { it.code in supported }
    }

    private fun toSample(v: Map<Pid, Double>) = Sample(
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
        batteryV = v[Pid.MODULE_VOLTAGE],
        fuelRateLph = v[Pid.FUEL_RATE],
    )

    private fun fail(msg: String): Elm327Client? {
        log("ERRO: $msg")
        _info.value = ConnectionInfo(ConnState.ERROR, msg)
        return null
    }

    private fun connect(): Elm327Client? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return fail("Este aparelho não tem Bluetooth")
        if (!adapter.isEnabled) return fail("Bluetooth desligado")

        val bonded = adapter.bondedDevices.toList()
        val device = bonded.firstOrNull { matches(it) }
        if (device == null) {
            val names = bonded.joinToString(", ") { it.name ?: it.address }.ifBlank { "nenhum" }
            return fail("Adaptador não encontrado entre os pareados ($names). Pareie o ELM327 nas configurações de Bluetooth ou digite o nome dele em Ajustes.")
        }
        log("Dispositivo: ${device.name} (${device.address})")

        adapter.cancelDiscovery()
        val s = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: IOException) {
            log("Conexão padrão falhou (${e.message}); tentando o canal 1")
            // Alguns clones só aceitam o canal 1 direto (truque conhecido).
            try {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                (m.invoke(device, 1) as BluetoothSocket).also { it.connect() }
            } catch (e2: Exception) {
                return fail("Não foi possível conectar: ${e2.message ?: e.message}")
            }
        }
        socket = s
        return Elm327Client(s.inputStream, s.outputStream) { cmd, resp ->
            if (logging) log("$cmd -> ${resp.replace('\r', ' ').replace('\n', ' ').trim()}")
        }.also { client = it }
    }

    private fun matches(d: BluetoothDevice): Boolean {
        val n = d.name ?: return false
        if (!preferredName.isNullOrBlank()) return n.equals(preferredName, ignoreCase = true)
        return KNOWN_NAMES.any { n.contains(it, ignoreCase = true) }
    }

    private fun close() {
        try { socket?.close() } catch (_: IOException) { }
        socket = null
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
