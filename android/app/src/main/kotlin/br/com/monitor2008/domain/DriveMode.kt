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

/**
 * Bateria baixa com o motor ligado só vira alarme se durar: abaixo de [LOW_V] por [LOW_MS] seguidos, ou abaixo de
 * [VERY_LOW_V] por [VERY_LOW_MS]. A ventoinha do radiador (e o ar-condicionado) liga por um ou dois minutos e pode puxar
 * mais que o alternador dá em marcha lenta; a tensão cai e volta sozinha quando ela desliga. Isso não é defeito.
 * Para sair do "baixo" a tensão precisa voltar a [RECOVER_V] (evita zerar a contagem com uma leitura solta).
 */
class LowBatteryWatch {
    private var lowSince: Long? = null
    private var veryLowSince: Long? = null
    var alarm = false
        private set

    fun update(nowMs: Long, volts: Double?, rpm: Double?): Boolean {
        if (volts == null || (rpm ?: 0.0) <= 400) { reset(); return false }
        lowSince = when { volts < LOW_V -> lowSince ?: nowMs; volts >= RECOVER_V -> null; else -> lowSince }
        veryLowSince = when { volts < VERY_LOW_V -> veryLowSince ?: nowMs; volts >= LOW_V -> null; else -> veryLowSince }
        alarm = (lowSince?.let { nowMs - it >= LOW_MS } == true) || (veryLowSince?.let { nowMs - it >= VERY_LOW_MS } == true)
        return alarm
    }

    fun reset() { lowSince = null; veryLowSince = null; alarm = false }

    companion object {
        const val LOW_V = 12.6
        const val RECOVER_V = 12.8
        const val LOW_MS = 120_000L
        const val VERY_LOW_V = 12.0
        const val VERY_LOW_MS = 20_000L
    }
}

/** Alerta grave: faixa vermelha na tela e aviso falado (uma vez a cada [REPEAT_MS] por tipo). */
data class CriticalAlert(val id: String, val text: String, val spoken: String)

object CriticalAlerts {
    const val REPEAT_MS = 10 * 60_000L

    private fun n(v: Double, d: Int) = String.format(Locale("pt", "BR"), "%.${d}f", v)

    /**
     * Só o que pede ação já: motor muito quente e bateria descarregando com o motor ligado. [batteryLowSustained] vem de
     * [LowBatteryWatch]: a tensão tem de ficar baixa por um tempo (a ventoinha do radiador puxa muita corrente e
     * derruba a tensão por um ou dois minutos sem que o alternador esteja com defeito).
     */
    fun check(coolantC: Double?, batteryV: Double?, rpm: Double?, batteryLowSustained: Boolean = true): List<CriticalAlert> {
        val out = mutableListOf<CriticalAlert>()
        if (coolantC != null && coolantC >= 110) {
            out += CriticalAlert("temp", "MOTOR MUITO QUENTE: ${n(coolantC, 0)} °C. Pare em local seguro e desligue o motor.",
                "Atenção: motor a ${n(coolantC, 0)} graus. Pare em local seguro e desligue o motor.")
        }
        if (batteryLowSustained && batteryV != null && (rpm ?: 0.0) > 400 && batteryV < LowBatteryWatch.LOW_V) {
            out += CriticalAlert("bat",
                "BATERIA DESCARREGANDO: ${n(batteryV, 1)} V há mais de 2 min com o motor ligado. O carro gasta mais do que o " +
                    "alternador carrega. Veja em Diagnóstico se acontece com o motor quente (ventoinha) e leve ao eletricista.",
                "Atenção: a bateria está descarregando. ${n(batteryV, 1)} volts com o motor ligado há mais de dois minutos.")
        }
        return out
    }

    /** Fala o alerta se ainda não falou dele nos últimos [REPEAT_MS]. */
    fun shouldSpeak(id: String, nowMs: Long, lastSpoken: Map<String, Long>): Boolean =
        nowMs - (lastSpoken[id] ?: 0L) >= REPEAT_MS
}
