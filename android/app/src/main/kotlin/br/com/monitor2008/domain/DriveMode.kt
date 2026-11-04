package br.com.monitor2008.domain

import java.util.Locale

/** Escolha do dono em Ajustes. */
enum class DriveModeSetting(val label: String) {
    AUTO("Automático"), ALWAYS_FULL("Sempre completo"), ALWAYS_SIMPLE("Sempre simples")
}

/**
 * Modo direção: com o carro andando, a tela fica simples (menos abas, 4 números grandes, sem textos
 * explicativos). Para não ficar trocando a cada semáforo: entra depois de [ENTER_MS] acima de
 * [ENTER_KMH]; sai só depois de [EXIT_MS] parado.
 */
class DriveModeTracker {
    private var fastSince: Long? = null
    private var stoppedSince: Long? = null
    var active = false
        private set

    fun update(nowMs: Long, speedKmh: Double?, setting: DriveModeSetting): Boolean {
        val v = speedKmh ?: 0.0
        when (setting) {
            DriveModeSetting.ALWAYS_FULL -> { active = false; return false }
            DriveModeSetting.ALWAYS_SIMPLE -> { active = true; return true }
            DriveModeSetting.AUTO -> {}
        }
        fastSince = if (v > ENTER_KMH) fastSince ?: nowMs else null
        stoppedSince = if (v < STOP_KMH) stoppedSince ?: nowMs else null
        if (!active && fastSince?.let { nowMs - it >= ENTER_MS } == true) active = true
        if (active && stoppedSince?.let { nowMs - it >= EXIT_MS } == true) active = false
        return active
    }

    fun reset() { fastSince = null; stoppedSince = null; active = false }

    companion object {
        const val ENTER_KMH = 20.0
        const val STOP_KMH = 1.0
        const val ENTER_MS = 10_000L
        const val EXIT_MS = 60_000L
    }
}

/** Alerta grave: faixa vermelha na tela e aviso falado (uma vez a cada [REPEAT_MS] por tipo). */
data class CriticalAlert(val id: String, val text: String, val spoken: String)

object CriticalAlerts {
    const val REPEAT_MS = 10 * 60_000L

    private fun n(v: Double, d: Int) = String.format(Locale("pt", "BR"), "%.${d}f", v)

    /** Só o que pede ação já: motor muito quente e bateria sem carga com o motor ligado. */
    fun check(coolantC: Double?, batteryV: Double?, rpm: Double?): List<CriticalAlert> {
        val out = mutableListOf<CriticalAlert>()
        if (coolantC != null && coolantC >= 110) {
            out += CriticalAlert("temp", "MOTOR MUITO QUENTE: ${n(coolantC, 0)} °C. Pare em local seguro e desligue o motor.",
                "Atenção: motor a ${n(coolantC, 0)} graus. Pare em local seguro e desligue o motor.")
        }
        if (batteryV != null && (rpm ?: 0.0) > 400 && batteryV < 12.6) {
            out += CriticalAlert("bat", "BATERIA NÃO ESTÁ CARREGANDO: ${n(batteryV, 1)} V com o motor ligado. Procure uma oficina.",
                "Atenção: a bateria não está carregando. ${n(batteryV, 1)} volts com o motor ligado.")
        }
        return out
    }

    /** Fala o alerta se ainda não falou dele nos últimos [REPEAT_MS]. */
    fun shouldSpeak(id: String, nowMs: Long, lastSpoken: Map<String, Long>): Boolean =
        nowMs - (lastSpoken[id] ?: 0L) >= REPEAT_MS
}
