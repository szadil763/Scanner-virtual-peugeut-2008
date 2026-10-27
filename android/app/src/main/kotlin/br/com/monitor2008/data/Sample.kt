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
)

enum class ConnState { IDLE, CONNECTING, CONNECTED, ERROR }

/** Estado da conexão mostrado na tela Diagnóstico. */
data class ConnectionInfo(
    val state: ConnState = ConnState.IDLE,
    val message: String = "",
    val protocol: String = "",
    val supportedPids: Set<Int> = emptySet(),
)
