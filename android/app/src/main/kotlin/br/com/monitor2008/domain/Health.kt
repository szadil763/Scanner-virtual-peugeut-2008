package br.com.monitor2008.domain

import br.com.monitor2008.data.Sample
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Resumo de uma viagem para a manutenção preventiva. Gravar segundo a segundo encheria a memória;
 * o resumo guarda só o que mostra a saúde de cada sistema. Campos nulos = sem dado nessa viagem.
 *
 * "Marcha lenta" = parado (velocidade < 1 km/h), motor ligado e quente (≥ 80 °C).
 * "Quente" = motor ligado com arrefecimento ≥ 70 °C (a injeção já está em malha fechada).
 * "Regime estável" = quente, ≥ 20 km/h e carga ≥ 20%: fora dos cortes de injeção ao tirar o pé,
 * quando as duas sondas caem juntas perto de 0 V e fariam o catalisador parecer ruim.
 */
data class TripHealth(
    val startMs: Long,
    val endMs: Long,
    val km: Double,
    val runningMin: Double,
    val coolantStartC: Double? = null,
    val coolantMaxC: Double? = null,
    /** Minutos até 80 °C, só quando a viagem começou com o motor frio (< 50 °C). */
    val warmupMin: Double? = null,
    val idleRpmAvg: Double? = null,
    val idleRpmSd: Double? = null,
    val idleMapAvg: Double? = null,
    val idleThrottleAvg: Double? = null,
    /** Correção total (curta + longa) em marcha lenta. */
    val idleTrimAvg: Double? = null,
    val ltftAvg: Double? = null,
    val stftAvg: Double? = null,
    val o2s1Min: Double? = null,
    val o2s1Max: Double? = null,
    /** Quantas vezes por minuto a sonda cruza 0,45 V. Limitado pela taxa de leitura do adaptador. */
    val o2s1CrossPerMin: Double? = null,
    val o2s2Min: Double? = null,
    val o2s2Max: Double? = null,
    /** Desvio padrão das sondas em regime estável: base da avaliação do catalisador. */
    val o2s1Sd: Double? = null,
    val o2s2Sd: Double? = null,
    val timingAvg: Double? = null,
    /** Tensão com o motor desligado, logo antes da partida. */
    val batteryRestV: Double? = null,
    val batteryRunAvgV: Double? = null,
    val batteryRunMinV: Double? = null,
    val batteryRunMaxV: Double? = null,
    val milOn: Boolean? = null,
    val storedDtcs: List<String> = emptyList(),
    val pendingDtcs: List<String> = emptyList(),
)

/** Média, desvio, mínimo e máximo sem guardar as amostras. */
private class Stat {
    var n = 0
        private set
    private var sum = 0.0
    private var sumSq = 0.0
    private var min = Double.POSITIVE_INFINITY
    private var max = Double.NEGATIVE_INFINITY

    fun add(v: Double?) {
        if (v == null) return
        n++; sum += v; sumSq += v * v
        if (v < min) min = v
        if (v > max) max = v
    }

    fun avg(minN: Int = 1): Double? = if (n >= minN) sum / n else null
    fun sd(minN: Int = 5): Double? = if (n >= minN) sqrt(((sumSq / n) - (sum / n) * (sum / n)).coerceAtLeast(0.0)) else null
    fun minOrNull() = if (n > 0) min else null
    fun maxOrNull() = if (n > 0) max else null
}

/** Acumula as leituras de uma viagem e devolve o [TripHealth] no fim. */
class HealthRecorder {
    private var startMs = 0L
    private var lastMs = 0L
    private var runningS = 0.0
    private var km = 0.0
    private var coolantStart: Double? = null
    private val coolant = Stat()
    private var warmup: Double? = null
    private val idleRpm = Stat()
    private val idleMap = Stat()
    private val idleThrottle = Stat()
    private val idleTrim = Stat()
    private val ltft = Stat()
    private val stft = Stat()
    private val o2s1 = Stat()
    private val o2s2 = Stat()
    private var o2Crossings = 0
    private var o2LastHigh: Boolean? = null
    private var o2Seconds = 0.0
    private val timing = Stat()
    private var restV: Double? = null
    private val runV = Stat()
    private var mil: Boolean? = null
    private var stored: List<String> = emptyList()
    private var pending: List<String> = emptyList()
    private var lastLtft: Double? = null

    /** O motor já funcionou nesta viagem. */
    val started: Boolean get() = runningS > 0

    fun add(s: Sample, dtSeconds: Double) {
        if (startMs == 0L) startMs = s.timeMs
        lastMs = s.timeMs
        val rpm = s.rpm ?: return
        val running = rpm > 400
        if (!running) {
            // Motor desligado (ou dando partida): a tensão de repouso só vale antes de ligar.
            if (!started) s.batteryV?.let { restV = it }
            return
        }

        runningS += dtSeconds
        val speed = s.speedKmh ?: 0.0
        km += speed * dtSeconds / 3600.0
        s.ltftPct?.let { lastLtft = it }

        s.coolantC?.let { c ->
            if (coolantStart == null) coolantStart = c
            coolant.add(c)
            val start = coolantStart!!
            if (warmup == null && start < 50 && c >= 80) warmup = runningS / 60.0
        }
        val c = s.coolantC
        val warm = c != null && c >= 70
        val idle = speed < 1 && c != null && c >= 80

        if (idle) {
            idleRpm.add(rpm)
            idleMap.add(s.mapKpa)
            idleThrottle.add(s.throttlePct)
            val trim = s.stftPct?.let { it + (lastLtft ?: 0.0) }
            idleTrim.add(trim)
        }
        if (warm) {
            ltft.add(s.ltftPct)
            stft.add(s.stftPct)
            timing.add(s.timingDeg)
        }
        // Sondas só em regime estável: no corte de injeção (tirando o pé) as duas vão a ~0 V juntas.
        val steady = warm && speed >= 20 && (s.loadPct ?: 0.0) >= 20
        if (steady) {
            s.o2s2V?.let { o2s2.add(it) }
            s.o2s1V?.let { v ->
                o2s1.add(v)
                o2Seconds += dtSeconds
                val high = v > 0.45
                if (o2LastHigh != null && o2LastHigh != high) o2Crossings++
                o2LastHigh = high
            }
        }
        runV.add(s.batteryV)
        s.milOn?.let { mil = (mil ?: false) || it }
    }

    fun setDtcs(storedCodes: List<String>, pendingCodes: List<String>) {
        stored = storedCodes
        pending = pendingCodes
    }

    /** Fecha a viagem. Null se o motor funcionou menos de [minRunningS] segundos (não vale guardar). */
    fun finish(minRunningS: Double = 120.0): TripHealth? {
        if (runningS < minRunningS) return null
        return TripHealth(
            startMs = startMs,
            endMs = lastMs,
            km = km,
            runningMin = runningS / 60.0,
            coolantStartC = coolantStart,
            coolantMaxC = coolant.maxOrNull(),
            warmupMin = warmup,
            idleRpmAvg = idleRpm.avg(MIN_IDLE),
            idleRpmSd = idleRpm.sd(MIN_IDLE),
            idleMapAvg = idleMap.avg(MIN_IDLE),
            idleThrottleAvg = idleThrottle.avg(3),
            idleTrimAvg = idleTrim.avg(MIN_IDLE),
            ltftAvg = ltft.avg(3),
            stftAvg = stft.avg(MIN_IDLE),
            o2s1Min = o2s1.minOrNull()?.takeIf { o2s1.n >= MIN_O2 },
            o2s1Max = o2s1.maxOrNull()?.takeIf { o2s1.n >= MIN_O2 },
            o2s1CrossPerMin = if (o2s1.n >= MIN_O2 && o2Seconds > 0) o2Crossings / (o2Seconds / 60.0) else null,
            o2s2Min = o2s2.minOrNull()?.takeIf { o2s2.n >= 3 },
            o2s2Max = o2s2.maxOrNull()?.takeIf { o2s2.n >= 3 },
            o2s1Sd = o2s1.sd(MIN_O2),
            o2s2Sd = o2s2.sd(3),
            timingAvg = timing.avg(3),
            batteryRestV = restV,
            batteryRunAvgV = runV.avg(2),
            batteryRunMinV = runV.minOrNull(),
            batteryRunMaxV = runV.maxOrNull(),
            milOn = mil,
            storedDtcs = stored,
            pendingDtcs = pending,
        )
    }

    private companion object {
        const val MIN_IDLE = 10
        const val MIN_O2 = 30
    }
}

enum class HealthStatus { OK, ATENCAO, ALERTA, APRENDENDO }

/** Linha de referência no gráfico (limite de atenção ou de alerta). */
data class RefLine(val value: Double, val alert: Boolean)

data class HealthItem(
    val id: String,
    val title: String,
    val status: HealthStatus,
    /** Valor atual em texto (ao vivo, se houver; senão o das últimas viagens). */
    val value: String,
    /** O que o valor significa e o que fazer. */
    val advice: String,
    /** Um ponto por viagem, da mais antiga para a mais recente (null = sem dado na viagem). */
    val series: List<Double?> = emptyList(),
    val unit: String = "",
    val lines: List<RefLine> = emptyList(),
)

/**
 * Regras da tela Saúde. Limites fixos valem desde a primeira viagem; os itens que dependem do
 * "normal" do carro (borboleta, vácuo) comparam com a mediana das primeiras [BASELINE] viagens.
 *
 * Não é diagnóstico: são indicadores que costumam mudar antes de uma falha.
 */
object HealthRules {
    const val BASELINE = 5
    private const val RECENT = 3
    private const val CHART = 30

    fun evaluate(trips: List<TripHealth>, live: Sample?): List<HealthItem> = listOf(
        dtcs(trips, live),
        mixture(trips, live),
        o2Sensor(trips, live),
        catalyst(trips),
        idle(trips),
        throttleBody(trips),
        vacuum(trips),
        cooling(trips, live),
        battery(trips, live),
        ignition(trips, live),
    )

    // ---------- auxiliares ----------

    private fun series(trips: List<TripHealth>, f: (TripHealth) -> Double?) = trips.takeLast(CHART).map(f)

    /** Média das últimas [RECENT] viagens que têm o dado. */
    private fun recent(trips: List<TripHealth>, f: (TripHealth) -> Double?): Double? {
        val v = trips.mapNotNull(f).takeLast(RECENT)
        return if (v.isEmpty()) null else v.average()
    }

    private fun baseline(trips: List<TripHealth>, f: (TripHealth) -> Double?): Double? {
        val v = trips.mapNotNull(f)
        if (v.size < BASELINE + 1) return null // precisa das primeiras viagens e de pelo menos uma depois
        return v.take(BASELINE).sorted()[BASELINE / 2]
    }

    /** O pior estado entre vários ("aprendendo" perde para qualquer leitura real). */
    private fun worst(vararg s: HealthStatus) =
        s.maxByOrNull { if (it == HealthStatus.APRENDENDO) -1 else it.ordinal } ?: HealthStatus.OK

    private fun level(v: Double, warn: Double, alert: Double) = when {
        v >= alert -> HealthStatus.ALERTA
        v >= warn -> HealthStatus.ATENCAO
        else -> HealthStatus.OK
    }

    fun fmt(v: Double?, d: Int = 1): String = v?.let { String.format(java.util.Locale("pt", "BR"), "%.${d}f", it) } ?: "--"

    private fun noData(id: String, title: String, what: String) =
        HealthItem(id, title, HealthStatus.APRENDENDO, "--", "Ainda sem dados: $what")

    // ---------- itens ----------

    private fun dtcs(trips: List<TripHealth>, live: Sample?): HealthItem {
        val last = trips.lastOrNull()
        val mil = live?.milOn ?: last?.milOn
        val stored = last?.storedDtcs.orEmpty()
        val pending = last?.pendingDtcs.orEmpty()
        val count = live?.dtcCount ?: stored.size
        val status = when {
            mil == true || count > 0 || stored.isNotEmpty() -> HealthStatus.ALERTA
            pending.isNotEmpty() -> HealthStatus.ATENCAO
            mil == null && last == null -> HealthStatus.APRENDENDO
            else -> HealthStatus.OK
        }
        val value = when {
            mil == true -> "Luz acesa"
            stored.isNotEmpty() -> stored.joinToString(" ")
            pending.isNotEmpty() -> "Pendente: " + pending.joinToString(" ")
            count > 0 -> "$count falha(s)"
            mil == null && last == null -> "--"
            else -> "Nenhuma"
        }
        val advice = when (status) {
            HealthStatus.ALERTA -> "A central registrou falha. Veja os códigos em Diagnóstico e leve a um mecânico com eles anotados."
            HealthStatus.ATENCAO -> "Falha pendente: a central viu um problema, mas ainda não acendeu a luz. Se repetir em mais viagens, vai virar falha."
            HealthStatus.APRENDENDO -> "Lido a cada viagem com o adaptador conectado."
            HealthStatus.OK -> "Sem falhas armazenadas nem pendentes."
        }
        return HealthItem("dtc", "Luz de injeção e falhas", status, value, advice,
            series(trips) { (it.storedDtcs.size + it.pendingDtcs.size).toDouble() }, "códigos")
    }

    private fun mixture(trips: List<TripHealth>, live: Sample?): HealthItem {
        val title = "Mistura (correção de combustível)"
        val ltft = recent(trips) { it.ltftAvg }
        val idleTrim = recent(trips) { it.idleTrimAvg }
        val now = live?.ltftPct
        val v = ltft ?: now ?: return noData("mix", title, "precisa do motor quente.")
        val status = level(abs(v), 10.0, 20.0)
        val sign = if (v >= 0) "pobre (a central está adicionando combustível)" else "rica (a central está tirando combustível)"
        // O padrão separa as causas: entrada falsa de ar pesa muito parado (pouco ar) e quase some
        // andando; combustível, entrega de combustível ou sensor de ar erram na mesma proporção sempre.
        val leak = idleTrim != null && ltft != null && idleTrim - ltft > 8
        val proportional = idleTrim != null && ltft != null && abs(idleTrim - ltft) <= 5
        val advice = buildString {
            when {
                status == HealthStatus.OK -> {
                    append("Dentro do normal (entre −10% e +10%).")
                    if (leak) append(" Em marcha lenta a correção é bem maior que andando: típico de entrada falsa de ar começando.")
                }
                v < 0 -> append("Mistura $sign. Verifique bicos pingando, sonda lambda e pressão de combustível.")
                leak -> append(
                    "Mistura $sign, bem maior parado que andando: típico de entrada falsa de ar " +
                        "(mangueiras do respiro do cárter, junta do coletor, mangueiras de vácuo).",
                )
                proportional -> append(
                    "Mistura $sign, parecida parado e andando: aponta para o combustível (fora do padrão ou com água), " +
                        "filtro de combustível entupido, bomba fraca, bicos sujos ou sensor de pressão do coletor. " +
                        "Teste: abasteça tanque cheio em outro posto de confiança e veja este gráfico depois de uns 50 km. " +
                        "Se cair, era o combustível; se continuar, é o carro (peça medição da pressão de combustível).",
                )
                else -> append("Mistura $sign. Verifique entrada falsa de ar (mangueiras, junta do coletor), bicos e pressão da bomba.")
            }
            append(" Carro flex: logo depois de trocar de combustível a correção muda por uns 50 km; avalie depois disso.")
        }
        val value = "${if (v > 0) "+" else ""}${fmt(v)}%" + (idleTrim?.let { " · lenta ${if (it > 0) "+" else ""}${fmt(it)}%" } ?: "")
        return HealthItem("mix", title, status, value, advice, series(trips) { it.ltftAvg }, "%",
            listOf(RefLine(10.0, false), RefLine(-10.0, false), RefLine(20.0, true), RefLine(-20.0, true)))
    }

    private fun o2Sensor(trips: List<TripHealth>, live: Sample?): HealthItem {
        val title = "Sonda lambda (antes do catalisador)"
        val range = recent(trips) { t -> if (t.o2s1Min != null && t.o2s1Max != null) t.o2s1Max - t.o2s1Min else null }
        val cross = recent(trips) { it.o2s1CrossPerMin }
        if (range == null) {
            return HealthItem("o2", title, HealthStatus.APRENDENDO, live?.o2s1V?.let { "${fmt(it, 2)} V" } ?: "--",
                "Ainda sem dados: precisa de alguns minutos com o motor quente.")
        }
        val status = when {
            range < 0.3 -> HealthStatus.ALERTA
            range < 0.5 -> HealthStatus.ATENCAO
            else -> HealthStatus.OK
        }
        val advice = when (status) {
            HealthStatus.OK -> "Oscilando bem (uma sonda boa vai de ~0,1 a ~0,9 V)."
            else -> "A sonda está oscilando pouco: sinal de envelhecimento. Uma sonda fraca aumenta o consumo; considere a troca preventiva."
        } + (cross?.let { " Cruzamentos: ${fmt(it, 0)}/min (medida aproximada pelo adaptador)." } ?: "")
        return HealthItem("o2", title, status, "faixa ${fmt(range, 2)} V", advice,
            series(trips) { t -> if (t.o2s1Min != null && t.o2s1Max != null) t.o2s1Max - t.o2s1Min else null }, "V",
            listOf(RefLine(0.5, false), RefLine(0.3, true)))
    }

    private fun catalyst(trips: List<TripHealth>): HealthItem {
        val title = "Catalisador"
        // Catalisador bom "acalma" o sinal: a sonda de trás varia bem menos que a da frente.
        // Desvio padrão em regime estável (viagens antigas, sem esse dado, ficam de fora).
        fun ratioOf(t: TripHealth) = if (t.o2s1Sd != null && t.o2s2Sd != null) t.o2s2Sd / t.o2s1Sd.coerceAtLeast(0.05) else null
        val ratio = recent(trips, ::ratioOf)
            ?: return noData("cat", title, "precisa de alguns minutos andando acima de 20 km/h com o motor quente.")
        val status = when {
            ratio > 0.75 -> HealthStatus.ALERTA
            ratio > 0.5 -> HealthStatus.ATENCAO
            else -> HealthStatus.OK
        }
        val advice = when (status) {
            HealthStatus.OK -> "A sonda de trás oscila bem menos que a da frente: catalisador trabalhando."
            else -> "A sonda de trás está oscilando quase como a da frente: o catalisador pode estar perdendo eficiência. Confirme com um mecânico antes de trocar."
        }
        return HealthItem("cat", title, status, "${fmt(ratio * 100, 0)}% da oscilação da frente", advice,
            series(trips) { t -> ratioOf(t)?.let { it * 100 } }, "%", listOf(RefLine(50.0, false), RefLine(75.0, true)))
    }

    private fun idle(trips: List<TripHealth>): HealthItem {
        val title = "Marcha lenta"
        val avg = recent(trips) { it.idleRpmAvg }
        val sd = recent(trips) { it.idleRpmSd }
        if (avg == null || sd == null) return noData("idle", title, "precisa de alguns segundos parado com o motor quente.")
        val status = worst(
            level(sd, 60.0, 120.0),
            if (avg < 600 || avg > 1000) HealthStatus.ATENCAO else HealthStatus.OK,
        )
        val advice = when (status) {
            HealthStatus.OK -> "Rotação estável."
            else -> "Marcha lenta oscilando ou fora da faixa: verifique velas, bobinas e limpeza do corpo de borboleta."
        }
        return HealthItem("idle", title, status, "${fmt(avg, 0)} rpm ± ${fmt(sd, 0)}", advice,
            series(trips) { it.idleRpmSd }, "rpm de oscilação", listOf(RefLine(60.0, false), RefLine(120.0, true)))
    }

    private fun throttleBody(trips: List<TripHealth>): HealthItem {
        val title = "Corpo de borboleta"
        val base = baseline(trips) { it.idleThrottleAvg }
        val now = recent(trips) { it.idleThrottleAvg }
        val n = trips.count { it.idleThrottleAvg != null }
        if (base == null || now == null) {
            return HealthItem("tbi", title, HealthStatus.APRENDENDO, now?.let { "${fmt(it)}%" } ?: "--",
                "Aprendendo o normal do seu carro ($n de ${BASELINE + 1} viagens com marcha lenta).", series(trips) { it.idleThrottleAvg }, "%")
        }
        val diff = now - base
        val status = level(diff, 2.0, 4.0)
        val advice = when (status) {
            HealthStatus.OK -> "Abertura em marcha lenta igual à das primeiras viagens."
            else -> "A borboleta está abrindo mais para manter a marcha lenta: sujeira acumulada. Uma limpeza do corpo de borboleta costuma resolver."
        }
        return HealthItem("tbi", title, status, "${fmt(now)}% (normal ${fmt(base)}%)", advice,
            series(trips) { it.idleThrottleAvg }, "%", listOf(RefLine(base + 2, false), RefLine(base + 4, true)))
    }

    private fun vacuum(trips: List<TripHealth>): HealthItem {
        val title = "Vácuo do motor"
        val base = baseline(trips) { it.idleMapAvg }
        val now = recent(trips) { it.idleMapAvg }
        val n = trips.count { it.idleMapAvg != null }
        if (base == null || now == null) {
            return HealthItem("map", title, HealthStatus.APRENDENDO, now?.let { "${fmt(it, 0)} kPa" } ?: "--",
                "Aprendendo o normal do seu carro ($n de ${BASELINE + 1} viagens com marcha lenta).", series(trips) { it.idleMapAvg }, "kPa")
        }
        val diff = now - base
        val status = level(diff, 5.0, 10.0)
        val advice = when (status) {
            HealthStatus.OK -> "Pressão do coletor em marcha lenta igual à das primeiras viagens."
            else -> "A pressão em marcha lenta subiu (menos vácuo): pode ser entrada falsa de ar, válvula do cânister ou desgaste. Vale um teste de vazamento."
        }
        return HealthItem("map", title, status, "${fmt(now, 0)} kPa (normal ${fmt(base, 0)})", advice,
            series(trips) { it.idleMapAvg }, "kPa", listOf(RefLine(base + 5, false), RefLine(base + 10, true)))
    }

    private fun cooling(trips: List<TripHealth>, live: Sample?): HealthItem {
        val title = "Arrefecimento"
        val maxRecent = trips.takeLast(RECENT).mapNotNull { it.coolantMaxC }.maxOrNull()
        val liveC = live?.coolantC
        val peak = listOfNotNull(maxRecent, liveC).maxOrNull() ?: return noData("cool", title, "precisa de uma viagem.")
        val warmup = recent(trips) { it.warmupMin }
        val coldLong = trips.takeLast(RECENT).any { it.runningMin >= 20 && (it.coolantMaxC ?: 99.0) < 75 }
        val status = worst(
            level(peak, 100.0, 105.0),
            if (warmup != null && warmup > 20) HealthStatus.ATENCAO else HealthStatus.OK,
            if (coldLong) HealthStatus.ATENCAO else HealthStatus.OK,
        )
        val advice = when {
            peak >= 105 -> "Temperatura alta: verifique nível do líquido, ventoinha e radiador. Se passar de 110 °C, pare o carro."
            peak >= 100 -> "Temperatura acima do comum: confira o nível do líquido de arrefecimento e se a ventoinha liga."
            coldLong || (warmup != null && warmup > 20) -> "Motor demorando para esquentar: a válvula termostática pode estar travada aberta (aumenta consumo)."
            else -> "Aquece e se mantém na faixa normal."
        } + (warmup?.let { " Aquecimento até 80 °C: ${fmt(it, 0)} min." } ?: "")
        return HealthItem("cool", title, status, "${fmt(liveC ?: peak, 0)} °C" + (if (liveC != null && maxRecent != null) " · máx ${fmt(maxRecent, 0)}" else ""),
            advice, series(trips) { it.coolantMaxC }, "°C máx.", listOf(RefLine(100.0, false), RefLine(105.0, true)))
    }

    private fun battery(trips: List<TripHealth>, live: Sample?): HealthItem {
        val title = "Bateria e alternador"
        val rest = recent(trips) { it.batteryRestV }
        val run = recent(trips) { it.batteryRunAvgV }
        val liveV = live?.batteryV
        if (rest == null && run == null && liveV == null) return noData("bat", title, "precisa da leitura de tensão do adaptador.")
        val restStatus = rest?.let {
            when { it < 11.8 -> HealthStatus.ALERTA; it < 12.2 -> HealthStatus.ATENCAO; else -> HealthStatus.OK }
        } ?: HealthStatus.OK
        val runStatus = run?.let {
            when { it < 13.0 || it > 15.0 -> HealthStatus.ALERTA; it < 13.4 || it > 14.8 -> HealthStatus.ATENCAO; else -> HealthStatus.OK }
        } ?: HealthStatus.OK
        val status = worst(restStatus, runStatus)
        val advice = buildString {
            append(
                when {
                    restStatus != HealthStatus.OK -> "Tensão baixa com o motor desligado: bateria fraca ou consumo parado (o adaptador ligado também consome). "
                    runStatus != HealthStatus.OK -> "Carga fora da faixa com o motor ligado (normal 13,5–14,8 V): verifique alternador e regulador. "
                    else -> "Bateria e carga normais. "
                },
            )
            append("A tensão é medida na tomada OBD e fica uns 0,1–0,2 V abaixo da bateria.")
        }
        val value = listOfNotNull(
            liveV?.let { "${fmt(it)} V agora" },
            rest?.let { "repouso ${fmt(it)}" },
            run?.let { "ligado ${fmt(it)}" },
        ).joinToString(" · ")
        return HealthItem("bat", title, status, value, advice, series(trips) { it.batteryRestV }, "V em repouso",
            listOf(RefLine(12.2, false), RefLine(11.8, true)))
    }

    private fun ignition(trips: List<TripHealth>, live: Sample?): HealthItem {
        val title = "Avanço de ignição"
        val avg = recent(trips) { it.timingAvg }
        val v = avg ?: live?.timingDeg ?: return noData("ign", title, "precisa do motor quente.")
        return HealthItem("ign", title, HealthStatus.OK, "${fmt(v)}°",
            "Acompanhamento: se o avanço médio cair bastante de uma viagem para outra, a central está atrasando a ignição por batida de pino (combustível ruim ou carbonização).",
            series(trips) { it.timingAvg }, "°")
    }
}
