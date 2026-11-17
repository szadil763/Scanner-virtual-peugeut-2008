package br.com.monitor2008.domain

import java.util.Locale

/** Uma leitura da tensão da bateria junto com o que o carro estava fazendo. */
data class BatteryPoint(
    val timeMs: Long,
    val volts: Double,
    val rpm: Double,
    val speedKmh: Double,
    /** Carga do motor (PID 04), se o carro informar. */
    val loadPct: Double?,
    /** Abertura da borboleta (PID 11), se o carro informar. */
    val throttlePct: Double?,
    /** Temperatura do motor (PID 05). Acima de [BatteryLog.FAN_FROM_C] a ventoinha do radiador provavelmente está ligada. */
    val coolantC: Double? = null,
)

/** Situação de rodagem na hora da leitura. O alternador pode se comportar diferente em cada uma. */
enum class BatteryRegime(val label: String) {
    PARADO("Parado, motor ligado"),
    SEM_ACELERAR("Rodando sem acelerar (descida, freio-motor)"),
    CRUZEIRO("Rodando com carga leve (cruzeiro)"),
    ACELERANDO("Acelerando (carga alta)"),
    RODANDO("Rodando (o carro não informa a carga)"),
}

data class BatteryStat(val label: String, val count: Int, val avgV: Double, val minV: Double, val maxV: Double)

/**
 * Registro da tensão da bateria com a rotação, a velocidade e a carga. Serve para ver QUANDO a tensão cai (por exemplo,
 * numa descida sem acelerar) e levar a prova ao mecânico. Só leitura; o app não muda nada no carro.
 */
object BatteryLog {
    const val MAX_POINTS = 3600
    const val MIN_GAP_MS = 2_000L
    const val MIN_RPM = 400.0

    /** Abaixo disso, o pé está fora do acelerador (freio-motor). */
    const val COAST_BELOW_PCT = 18.0
    /** A partir disso, é aceleração forte. */
    const val ACCEL_FROM_PCT = 50.0
    /**
     * A ventoinha do radiador costuma ligar perto de 97 °C (EW10 do C4 e EC5 do 2008; a confirmar). Não há PID da
     * ventoinha, então o motor quente é o sinal indireto. O ar-condicionado também liga a ventoinha e não aparece aqui.
     */
    const val FAN_FROM_C = 97.0
    const val HOT_LABEL = "Motor quente (97 °C ou mais: ventoinha provavelmente ligada)"
    const val COOL_LABEL = "Motor abaixo de 97 °C (ventoinha provavelmente desligada)"

    fun regime(speedKmh: Double, loadPct: Double?, throttlePct: Double?): BatteryRegime {
        if (speedKmh < 3.0) return BatteryRegime.PARADO
        // A carga do motor separa melhor "sem acelerar" de "cruzeiro" que a borboleta, que varia com o câmbio.
        val x = loadPct ?: throttlePct ?: return BatteryRegime.RODANDO
        return when {
            x < COAST_BELOW_PCT -> BatteryRegime.SEM_ACELERAR
            x >= ACCEL_FROM_PCT -> BatteryRegime.ACELERANDO
            else -> BatteryRegime.CRUZEIRO
        }
    }

    fun regime(p: BatteryPoint) = regime(p.speedKmh, p.loadPct, p.throttlePct)

    /** Grava no máximo uma leitura a cada [MIN_GAP_MS] (o registro seria enorme a cada leitura do carro). */
    fun shouldRecord(last: BatteryPoint?, now: BatteryPoint): Boolean = last == null || now.timeMs - last.timeMs >= MIN_GAP_MS

    private fun stat(label: String, ps: List<BatteryPoint>) =
        BatteryStat(label, ps.size, ps.map { it.volts }.average(), ps.minOf { it.volts }, ps.maxOf { it.volts })

    fun stats(points: List<BatteryPoint>): List<BatteryStat> =
        points.groupBy { regime(it) }.entries.sortedBy { it.key.ordinal }.map { (r, ps) -> stat(r.label, ps) }

    /** Separa as leituras com o motor quente (ventoinha) das outras. Só as que têm a temperatura gravada. */
    fun heatStats(points: List<BatteryPoint>): List<BatteryStat> {
        val withTemp = points.filter { it.coolantC != null }
        val (hot, cool) = withTemp.partition { it.coolantC!! >= FAN_FROM_C }
        return listOfNotNull(cool.takeIf { it.isNotEmpty() }?.let { stat(COOL_LABEL, it) },
            hot.takeIf { it.isNotEmpty() }?.let { stat(HOT_LABEL, it) })
    }

    /** O que o Diagnóstico mostra: por situação de rodagem e, depois, motor frio x quente. */
    fun allStats(points: List<BatteryPoint>): List<BatteryStat> = stats(points) + heatStats(points)

    private fun f(v: Double, d: Int) = String.format(Locale.US, "%.${d}f", v)

    const val CSV_HEADER = "hora_ms,volts,rpm,km_h,carga_pct,acelerador_pct,situacao,motor_c"

    fun csvLine(p: BatteryPoint): String =
        "${p.timeMs},${f(p.volts, 2)},${p.rpm.toInt()},${f(p.speedKmh, 0)},${p.loadPct?.let { f(it, 0) } ?: ""},${p.throttlePct?.let { f(it, 0) } ?: ""},${regime(p).name},${p.coolantC?.let { f(it, 0) } ?: ""}"

    fun parseCsvLine(line: String): BatteryPoint? = try {
        val c = line.split(',')
        BatteryPoint(c[0].toLong(), c[1].toDouble(), c[2].toDouble(), c[3].toDouble(), c[4].toDoubleOrNull(), c[5].toDoubleOrNull(),
            c.getOrNull(7)?.toDoubleOrNull()) // registros antigos não têm a temperatura
    } catch (e: Exception) { null }

    /** Texto para compartilhar (WhatsApp): resumo por situação e, no fim, uma amostra das leituras. */
    fun report(carName: String, points: List<BatteryPoint>, maxLines: Int = 200): String {
        if (points.isEmpty()) return "Sem leituras de bateria gravadas ainda. Rode um pouco com o app conectado."
        val sb = StringBuilder()
        sb.appendLine("*Tensão da bateria — $carName*")
        sb.appendLine("${points.size} leituras (uma a cada 2 s, motor ligado).")
        sb.appendLine()
        allStats(points).forEach {
            sb.appendLine("• ${it.label}: média ${f(it.avgV, 2)} V (mín ${f(it.minV, 2)} / máx ${f(it.maxV, 2)}), ${it.count} leituras")
        }
        sb.appendLine()
        sb.appendLine(CSV_HEADER)
        val step = maxOf(1, points.size / maxLines)
        points.filterIndexed { i, _ -> i % step == 0 }.forEach { sb.appendLine(csvLine(it)) }
        return sb.toString()
    }
}
