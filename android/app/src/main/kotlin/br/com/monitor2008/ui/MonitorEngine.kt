package br.com.monitor2008.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import br.com.monitor2008.data.BluetoothObdSource
import br.com.monitor2008.data.BtPermissions
import br.com.monitor2008.data.ConnState
import br.com.monitor2008.data.ConnectionInfo
import br.com.monitor2008.data.HealthStore
import br.com.monitor2008.data.ObdSource
import br.com.monitor2008.data.Refuel
import br.com.monitor2008.data.Sample
import br.com.monitor2008.data.SimulatedSource
import br.com.monitor2008.data.Store
import br.com.monitor2008.domain.EngineConfig
import br.com.monitor2008.domain.FlowMethod
import br.com.monitor2008.domain.FuelBlend
import br.com.monitor2008.domain.FuelCalc
import br.com.monitor2008.domain.HealthItem
import br.com.monitor2008.domain.HealthRecorder
import br.com.monitor2008.domain.HealthRules
import br.com.monitor2008.domain.ItemStatus
import br.com.monitor2008.domain.MaintCatalog
import br.com.monitor2008.domain.MaintItem
import br.com.monitor2008.domain.Maintenance
import br.com.monitor2008.domain.OilPlan
import br.com.monitor2008.domain.OilStatus
import br.com.monitor2008.domain.RefuelState
import br.com.monitor2008.domain.TripAccumulator
import br.com.monitor2008.domain.TripHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiState(
    val sample: Sample? = null,
    val kmPerL: Double? = null,
    val litersPerHour: Double? = null,
    val method: FlowMethod? = null,
    val tripAvgKmPerL: Double? = null,
    val tripAvgL100: Double? = null,
    val tripKm: Double = 0.0,
    val tripLiters: Double = 0.0,
    val tripAvgSpeed: Double = 0.0,
    val tripMinutes: Int = 0,
    val odometerKm: Double = 0.0,
    val ethanolFraction: Double = 0.30,
    val calibration: Double = 1.0,
    val volumetricEfficiency: Double = 0.80,
    val fuelPrice: Double = 6.21,
    val oil: OilStatus? = null,
    val oilConfigured: Boolean = false,
    val oilIntervalKm: Int = 10_000,
    val refuels: List<Refuel> = emptyList(),
    val simulated: Boolean = true,
    val adapterName: String = "",
    val paired: PairedList = PairedList(),
    val conn: ConnectionInfo = ConnectionInfo(),
    val dtcs: List<String>? = null,
    val pendingDtcs: List<String>? = null,
    val health: List<HealthItem> = emptyList(),
    val tripsRecorded: Int = 0,
    val maint: List<MaintRow> = emptyList(),
    val pipOnLeave: Boolean = false,
    val history: List<Double> = emptyList(),
) {
    /** Com o carro andando, o app não aceita digitação nem registros. */
    val moving: Boolean get() = (sample?.speedKmh ?: 0.0) > 0.5
}

/** Item de manutenção por km/data e sua situação. [status] null = ainda não informado. */
data class MaintRow(val item: MaintItem, val status: ItemStatus?)

/** Aparelhos pareados no Bluetooth, para escolher o adaptador em Ajustes. */
data class PairedList(
    val names: List<String> = emptyList(),
    /** Motivo de a lista estar vazia (Bluetooth desligado, sem permissão...), ou vazio. */
    val problem: String = "",
    val needsPermission: Boolean = false,
)

/**
 * O "motor" do app: conexão com o adaptador, cálculos, viagens e estado das telas. É um só por
 * processo ([get]), compartilhado entre a tela do app e o Android Auto: o adaptador aceita uma
 * conexão por vez, então as duas telas precisam usar a mesma.
 */
class MonitorEngine private constructor(private val app: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val store = Store(app)
    private val trip = TripAccumulator()
    private var source: ObdSource? = null
    private var sourceJob: Job? = null
    private var infoJob: Job? = null

    private var odometerKm = store.odometerKm
    private var uncalLiters = store.uncalLiters
    private var kmSinceFull = store.kmSinceFull
    private var lastTimeMs = 0L
    private var lastSaveMs = 0L

    private var lastSample: Sample? = null
    private var lastKmPerL: Double? = null
    private var lastLph: Double? = null
    private var lastMethod: FlowMethod? = null
    private var conn = ConnectionInfo()
    private var dtcs: List<String>? = null
    private val history = ArrayDeque<Double>()
    private var paired = PairedList()
    private var pendingDtcs: List<String>? = null

    // Saúde: viagens gravadas (só com o adaptador real) e a viagem em andamento.
    private val healthStore = HealthStore(app)
    private var trips: List<TripHealth> = healthStore.load()
    private var recorder = HealthRecorder()
    private var tripDtcsRead = false
    private var engineOffSince = 0L
    private var refuelList: List<Refuel> = store.refuels().asReversed() // mais recente primeiro

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init { start() }

    /** (Re)inicia a leitura com a fonte escolhida nos Ajustes. */
    fun start() {
        closeTrip()
        sourceJob?.cancel(); infoJob?.cancel()
        trip.reset(); history.clear(); lastTimeMs = 0L
        lastSample = null; lastKmPerL = null; lastLph = null; lastMethod = null; dtcs = null; pendingDtcs = null

        val src: ObdSource = if (store.useSimulated) {
            SimulatedSource()
        } else {
            BluetoothObdSource(app, store.adapterName.ifBlank { null })
        }
        source = src
        infoJob = scope.launch {
            src.info.collect {
                // Conexão perdida ou carro desligado: a viagem termina aqui.
                if (it.state != ConnState.CONNECTED) closeTrip()
                conn = it
                publish()
            }
        }
        sourceJob = scope.launch {
            src.samples().collect { onSample(it) }
        }
    }

    private fun onSample(s: Sample) {
        val dt = if (lastTimeMs == 0L) 0.0 else ((s.timeMs - lastTimeMs) / 1000.0).coerceIn(0.0, 5.0)
        lastTimeMs = s.timeMs
        val speed = s.speedKmh ?: 0.0

        val engine = EngineConfig(volumetricEfficiency = store.volumetricEfficiency)
        val raw = FuelCalc.estimate(s, FuelBlend(store.ethanolFraction), engine, calibration = 1.0)
        val lph = raw?.let { it.litersPerHour * store.calibration }

        trip.add(dt, speed, lph)
        val km = speed * dt / 3600.0
        odometerKm += km
        kmSinceFull += km
        if (raw != null) uncalLiters += raw.litersPerHour * dt / 3600.0

        lastSample = s
        lastLph = lph
        lastMethod = raw?.method
        lastKmPerL = lph?.let { FuelCalc.kmPerL(speed, it) }
        lastKmPerL?.let {
            history.addLast(it)
            if (history.size > 120) history.removeFirst()
        }

        if (!store.useSimulated) recordHealth(s, dt)

        if (s.timeMs - lastSaveMs > 20_000) {
            store.saveProgress(odometerKm, uncalLiters, kmSinceFull)
            lastSaveMs = s.timeMs
        }
        publish()
    }

    /** Alimenta a viagem da tela Saúde. Simulação não grava (não pode sujar o histórico do carro). */
    private fun recordHealth(s: Sample, dt: Double) {
        recorder.add(s, dt)
        val running = (s.rpm ?: 0.0) > 400
        if (running) {
            engineOffSince = 0L
            // Falhas armazenadas e pendentes: uma leitura por viagem, logo que o motor liga.
            if (!tripDtcsRead) {
                tripDtcsRead = true
                val src = source ?: return
                scope.launch {
                    val stored = src.readDtcs()
                    val pending = src.readPendingDtcs()
                    recorder.setDtcs(stored, pending)
                    dtcs = stored; pendingDtcs = pending
                    publish()
                }
            }
        } else if (recorder.started) {
            if (engineOffSince == 0L) engineOffSince = s.timeMs
            if (s.timeMs - engineOffSince > ENGINE_OFF_TRIP_END_MS) closeTrip()
        }
    }

    /** Fecha a viagem em andamento e grava o resumo, se ela durou o suficiente. */
    private fun closeTrip() {
        recorder.finish()?.let { t ->
            trips = (trips + t).takeLast(400)
            healthStore.save(trips)
        }
        recorder = HealthRecorder()
        tripDtcsRead = false
        engineOffSince = 0L
    }

    // ---------- ações das telas ----------

    fun readDtcs() {
        val src = source ?: return
        scope.launch {
            dtcs = src.readDtcs()
            pendingDtcs = src.readPendingDtcs()
            publish()
        }
    }

    fun registerMaintDone(id: String) {
        if (_ui.value.moving) return
        store.setMaintDone(id, odometerKm, LocalDate.now().toEpochDay())
        publish()
    }

    fun setPipOnLeave(on: Boolean) { store.pipOnLeave = on; publish() }

    fun diagnosticLog(): List<String> = source?.diagnosticLog().orEmpty()

    fun resetTrip() { trip.reset(); history.clear(); publish() }

    fun registerRefuel(liters: Double, price: Double, full: Boolean) {
        if (_ui.value.moving) return
        val before = RefuelState(
            baselineValid = store.baselineValid,
            uncalLiters = uncalLiters,
            pendingRealLiters = store.pendingRealLiters,
            kmSinceFull = kmSinceFull,
            factor = store.calibration,
        )
        val result = FuelCalc.applyRefuel(before, liters, full)
        store.baselineValid = result.state.baselineValid
        store.pendingRealLiters = result.state.pendingRealLiters
        store.calibration = result.state.factor
        uncalLiters = result.state.uncalLiters
        kmSinceFull = result.state.kmSinceFull
        store.fuelPrice = price
        store.addRefuel(Refuel(LocalDate.now().toEpochDay(), liters, price, full, odometerKm, result.realKmPerL))
        store.saveProgress(odometerKm, uncalLiters, kmSinceFull)
        refuelList = store.refuels().asReversed()
        publish()
    }

    fun registerOilChange() {
        if (_ui.value.moving) return
        store.oilLastKm = odometerKm
        store.oilLastDay = LocalDate.now().toEpochDay()
        publish()
    }

    fun setEthanolFraction(f: Double) { store.ethanolFraction = f; publish() }
    fun setCalibration(v: Double) { store.calibration = v.coerceIn(0.5, 1.5); publish() }
    fun setVolumetricEfficiency(v: Double) { store.volumetricEfficiency = v.coerceIn(0.5, 1.0); publish() }
    fun setAdapterName(name: String) {
        if (store.adapterName == name) return
        store.adapterName = name
        if (store.useSimulated) publish() else start() // reconecta já com o adaptador escolhido
    }

    @SuppressLint("MissingPermission") // a permissão é conferida logo abaixo
    /** Lê os aparelhos pareados. O pareamento em si é feito nas configurações de Bluetooth do aparelho. */
    fun refreshPaired() {
        val ctx = app
        paired = if (BtPermissions.missing(ctx).isNotEmpty()) {
            PairedList(problem = "O app ainda não tem permissão para usar o Bluetooth.", needsPermission = true)
        } else {
            val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
            when {
                adapter == null -> PairedList(problem = "Este aparelho não tem Bluetooth.")
                !adapter.isEnabled -> PairedList(problem = "Bluetooth desligado. Ligue o Bluetooth e toque em Atualizar.")
                else -> try {
                    val names = adapter.bondedDevices.mapNotNull { it.name ?: it.address }.distinct().sorted()
                    PairedList(names, if (names.isEmpty()) "Nenhum aparelho pareado ainda." else "")
                } catch (e: SecurityException) {
                    PairedList(problem = "O app ainda não tem permissão para usar o Bluetooth.", needsPermission = true)
                }
            }
        }
        publish()
    }

    fun setSimulated(simulated: Boolean) {
        if (store.useSimulated == simulated) return
        store.useSimulated = simulated
        start()
    }

    /** Grava o progresso (odômetro e acumuladores) quando o app sai da tela. A viagem continua. */
    fun saveNow() {
        store.saveProgress(odometerKm, uncalLiters, kmSinceFull)
    }

    // ---------- estado exposto ----------

    private fun publish() { _ui.value = snapshot() }

    private fun snapshot(): UiState {
        val today = LocalDate.now().toEpochDay()
        val plan = OilPlan(
            intervalKm = store.oilIntervalKm,
            intervalMonths = store.oilIntervalMonths,
            lastChangeKm = store.oilLastKm,
            lastChangeEpochDay = store.oilLastDay,
        )
        return UiState(
            sample = lastSample,
            kmPerL = lastKmPerL,
            litersPerHour = lastLph,
            method = lastMethod,
            tripAvgKmPerL = trip.avgKmPerL,
            tripAvgL100 = trip.avgLPer100Km,
            tripKm = trip.distanceKm,
            tripLiters = trip.liters,
            tripAvgSpeed = trip.avgSpeedKmh,
            tripMinutes = (trip.seconds / 60).toInt(),
            odometerKm = odometerKm,
            ethanolFraction = store.ethanolFraction,
            calibration = store.calibration,
            volumetricEfficiency = store.volumetricEfficiency,
            fuelPrice = store.fuelPrice,
            oil = Maintenance.oilStatus(plan, odometerKm, today),
            oilConfigured = store.oilConfigured,
            oilIntervalKm = store.oilIntervalKm,
            refuels = refuelList,
            simulated = store.useSimulated,
            adapterName = store.adapterName,
            paired = paired,
            conn = conn,
            dtcs = dtcs,
            pendingDtcs = pendingDtcs,
            health = HealthRules.evaluate(trips, lastSample),
            tripsRecorded = trips.size,
            maint = MaintCatalog.items.map { item ->
                MaintRow(
                    item,
                    if (store.maintConfigured(item.id)) {
                        Maintenance.itemStatus(item, store.maintLastKm(item.id), store.maintLastDay(item.id), odometerKm, today)
                    } else null,
                )
            },
            pipOnLeave = store.pipOnLeave,
            history = history.toList(),
        )
    }

    companion object {
        /** Motor parado por mais que isso = fim da viagem. */
        private const val ENGINE_OFF_TRIP_END_MS = 30_000L

        @Volatile private var instance: MonitorEngine? = null

        fun get(app: Application): MonitorEngine =
            instance ?: synchronized(this) { instance ?: MonitorEngine(app).also { instance = it } }
    }
}
