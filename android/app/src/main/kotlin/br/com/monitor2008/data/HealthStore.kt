package br.com.monitor2008.data

import android.content.Context
import br.com.monitor2008.domain.TripHealth
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Viagens gravadas para a tela Saúde, num arquivo JSON no armazenamento do app.
 * Guarda as últimas [MAX] viagens (um resumo por viagem, poucos KB no total).
 */
class HealthStore(context: Context) {
    private val file = File(context.filesDir, "viagens.json")

    fun load(): List<TripHealth> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList() // arquivo corrompido não pode derrubar o app
    }

    fun save(trips: List<TripHealth>) {
        val arr = JSONArray()
        trips.takeLast(MAX).forEach { arr.put(toJson(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun JSONObject.d(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.codes(k: String): List<String> =
        optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

    private fun toJson(t: TripHealth) = JSONObject().apply {
        put("start", t.startMs); put("end", t.endMs); put("km", t.km); put("min", t.runningMin)
        fun opt(k: String, v: Double?) { if (v != null) put(k, v) }
        opt("coolStart", t.coolantStartC); opt("coolMax", t.coolantMaxC); opt("warmup", t.warmupMin)
        opt("idleRpm", t.idleRpmAvg); opt("idleRpmSd", t.idleRpmSd); opt("idleMap", t.idleMapAvg)
        opt("idleThr", t.idleThrottleAvg); opt("idleTrim", t.idleTrimAvg); opt("ltft", t.ltftAvg); opt("stft", t.stftAvg)
        opt("o2Min", t.o2s1Min); opt("o2Max", t.o2s1Max); opt("o2Cross", t.o2s1CrossPerMin)
        opt("o2bMin", t.o2s2Min); opt("o2bMax", t.o2s2Max); opt("timing", t.timingAvg)
        opt("vRest", t.batteryRestV); opt("vRun", t.batteryRunAvgV); opt("vMin", t.batteryRunMinV); opt("vMax", t.batteryRunMaxV)
        t.milOn?.let { put("mil", it) }
        put("dtc", JSONArray(t.storedDtcs)); put("pend", JSONArray(t.pendingDtcs))
    }

    private fun fromJson(o: JSONObject) = TripHealth(
        startMs = o.getLong("start"), endMs = o.getLong("end"), km = o.getDouble("km"), runningMin = o.getDouble("min"),
        coolantStartC = o.d("coolStart"), coolantMaxC = o.d("coolMax"), warmupMin = o.d("warmup"),
        idleRpmAvg = o.d("idleRpm"), idleRpmSd = o.d("idleRpmSd"), idleMapAvg = o.d("idleMap"),
        idleThrottleAvg = o.d("idleThr"), idleTrimAvg = o.d("idleTrim"), ltftAvg = o.d("ltft"), stftAvg = o.d("stft"),
        o2s1Min = o.d("o2Min"), o2s1Max = o.d("o2Max"), o2s1CrossPerMin = o.d("o2Cross"),
        o2s2Min = o.d("o2bMin"), o2s2Max = o.d("o2bMax"), timingAvg = o.d("timing"),
        batteryRestV = o.d("vRest"), batteryRunAvgV = o.d("vRun"), batteryRunMinV = o.d("vMin"), batteryRunMaxV = o.d("vMax"),
        milOn = if (o.has("mil")) o.getBoolean("mil") else null,
        storedDtcs = o.codes("dtc"), pendingDtcs = o.codes("pend"),
    )

    private companion object {
        const val MAX = 400
    }
}
