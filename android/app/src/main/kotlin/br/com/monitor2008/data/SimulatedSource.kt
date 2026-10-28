package br.com.monitor2008.data

import br.com.monitor2008.obd.Pid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Viagem simulada (ciclo de 90 s), igual à do protótipo HTML.
 * Serve para desenvolver e testar a interface sem o carro.
 */
class SimulatedSource(private val tickMs: Long = 500) : ObdSource {
    override val name = "Simulada"

    private val _info = MutableStateFlow(
        ConnectionInfo(
            state = ConnState.CONNECTED,
            message = "Dados simulados",
            protocol = "simulado",
            supportedPids = Pid.values().map { it.code }.toSet() - Pid.FUEL_RATE.code,
        ),
    )
    override val info: StateFlow<ConnectionInfo> = _info.asStateFlow()

    // segundos de cada trecho e velocidade alvo (km/h)
    private val cycle = listOf(8 to 0, 14 to 45, 10 to 60, 14 to 62, 10 to 38, 8 to 0, 14 to 28, 12 to 50)

    private fun targetSpeed(t: Double): Double {
        val total = cycle.sumOf { it.first }
        val x = t % total
        var acc = 0
        for ((d, v) in cycle) {
            if (x < acc + d) return v.toDouble()
            acc += d
        }
        return 0.0
    }

    override fun samples(): Flow<Sample> = flow {
        val dt = tickMs / 1000.0
        var t = 0.0
        var speed = 0.0
        var coolant = 66.0
        var fuel = 74.0
        while (true) {
            t += dt
            val prev = speed
            speed += (targetSpeed(t) - speed).coerceIn(-9.0, 7.0) * 0.45 * dt * 2
            if (speed < 0.6) speed = 0.0
            val accel = (speed - prev) / dt
            val rpm = if (speed < 1) 780.0 else min(5600.0, 1100 + speed * 24 + max(0.0, accel) * 90)
            val maf = 2.4 + speed * 0.19 + max(0.0, accel) * 1.5
            coolant = min(91.0, coolant + 0.2 * dt * (91 - coolant) / 25 * 10)
            fuel = max(5.0, fuel - 0.002 * dt)
            emit(
                Sample(
                    timeMs = System.currentTimeMillis(),
                    rpm = rpm,
                    speedKmh = speed,
                    coolantC = coolant,
                    loadPct = min(100.0, 18 + speed * 0.6 + max(0.0, accel) * 4),
                    mapKpa = (28 + speed * 0.55 + max(0.0, accel) * 3).coerceIn(25.0, 95.0),
                    intakeC = 31 + sin(t / 30) * 1.5,
                    mafGs = maf,
                    fuelLevelPct = fuel,
                    batteryV = 14.1 + sin(t / 7) * 0.1,
                    stftPct = sin(t * 1.3) * 4,
                    ltftPct = 3.1,
                    timingDeg = 14 - min(8.0, max(0.0, accel) * 2),
                    o2s1V = if (sin(t * 2.1) > 0) 0.78 else 0.14,
                    o2s2V = 0.62 + sin(t / 9) * 0.04,
                    milOn = false,
                    dtcCount = 0,
                ),
            )
            delay(tickMs)
        }
    }

    override suspend fun readDtcs(): List<String> = emptyList()
}
