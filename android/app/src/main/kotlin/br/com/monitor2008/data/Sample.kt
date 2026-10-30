package br.com.monitor2008.data

/** Uma leitura do carro. Campos nulos = o carro não respondeu esse PID. */
data class Sample(
    val timeMs: Long,
    val rpm: Double? = null,
    val speedKmh: Double? = null,
    val coolantC: Double? = null,
    val loadPct: Double? = null,
    val mapKpa: Double? = null,
    val intakeC: Double? = null,
    val mafGs: Double? = null,
    val throttlePct: Double? = null,
    val fuelLevelPct: Double? = null,
    val batteryV: Double? = null,
    val fuelRateLph: Double? = null,
    val stftPct: Double? = null,
    val ltftPct: Double? = null,
    val timingDeg: Double? = null,
    val o2s1V: Double? = null,
    val o2s2V: Double? = null,
    /** Luz de injeção acesa (PID 01). */
    val milOn: Boolean? = null,
    /** Quantidade de falhas armazenadas que a central informa (PID 01). */
    val dtcCount: Int? = null,
    /** Contador da central: km desde que as falhas foram apagadas (PID 31). Lido de tempos em tempos. */
    val distSinceClearKm: Double? = null,
)

enum class ConnState { IDLE, CONNECTING, CONNECTED, ERROR }

/** Estado da conexão mostrado na tela Diagnóstico. */
data class ConnectionInfo(
    val state: ConnState = ConnState.IDLE,
    val message: String = "",
    val protocol: String = "",
    val supportedPids: Set<Int> = emptySet(),
)
