package br.com.monitor2008.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Refuel(
    val epochDay: Long,
    val liters: Double,
    val pricePerLiter: Double,
    val full: Boolean,
    val odometerKm: Double,
    val realKmPerL: Double?,
)

/** Persistência simples em SharedPreferences. Trocar por Room ou Firebase só se virar necessidade. */
class Store(context: Context) {
    private val p = context.getSharedPreferences("monitor2008", Context.MODE_PRIVATE)

    private fun d(key: String, def: Double) = p.getFloat(key, def.toFloat()).toDouble()

    // Odômetro e acumuladores: salvos em lote por saveProgress() para não gravar a cada leitura.
    val odometerKm: Double get() = p.getString("odometerKm", null)?.toDoubleOrNull() ?: 0.0
    val uncalLiters: Double get() = p.getString("uncalLiters", null)?.toDoubleOrNull() ?: 0.0
    val kmSinceFull: Double get() = p.getString("kmSinceFull", null)?.toDoubleOrNull() ?: 0.0

    fun saveProgress(odometerKm: Double, uncalLiters: Double, kmSinceFull: Double) {
        p.edit()
            .putString("odometerKm", odometerKm.toString())
            .putString("uncalLiters", uncalLiters.toString())
            .putString("kmSinceFull", kmSinceFull.toString())
            .apply()
    }

    var ethanolFraction: Double
        get() = d("ethanolFraction", 0.30)
        set(v) { p.edit().putFloat("ethanolFraction", v.toFloat()).apply() }
    var calibration: Double
        get() = d("calibration", 1.0)
        set(v) { p.edit().putFloat("calibration", v.toFloat()).apply() }
    var volumetricEfficiency: Double
        get() = d("ve", 0.80)
        set(v) { p.edit().putFloat("ve", v.toFloat()).apply() }
    var fuelPrice: Double
        get() = d("fuelPrice", 6.21)
        set(v) { p.edit().putFloat("fuelPrice", v.toFloat()).apply() }

    var baselineValid: Boolean
        get() = p.getBoolean("baselineValid", false)
        set(v) { p.edit().putBoolean("baselineValid", v).apply() }
    var pendingRealLiters: Double
        get() = d("pendingRealLiters", 0.0)
        set(v) { p.edit().putFloat("pendingRealLiters", v.toFloat()).apply() }

    var useSimulated: Boolean
        get() = p.getBoolean("useSimulated", true)
        set(v) { p.edit().putBoolean("useSimulated", v).apply() }
    var adapterName: String
        get() = p.getString("adapterName", "") ?: ""
        set(v) { p.edit().putString("adapterName", v).apply() }

    // Troca de óleo
    val oilConfigured: Boolean get() = p.contains("oilLastDay")
    var oilLastKm: Double
        get() = p.getString("oilLastKm", null)?.toDoubleOrNull() ?: odometerKm
        set(v) { p.edit().putString("oilLastKm", v.toString()).apply() }
    var oilLastDay: Long
        get() = p.getLong("oilLastDay", java.time.LocalDate.now().toEpochDay())
        set(v) { p.edit().putLong("oilLastDay", v).apply() }
    var oilIntervalKm: Int
        get() = p.getInt("oilIntervalKm", 10_000)
        set(v) { p.edit().putInt("oilIntervalKm", v).apply() }
    var oilIntervalMonths: Int
        get() = p.getInt("oilIntervalMonths", 12)
        set(v) { p.edit().putInt("oilIntervalMonths", v).apply() }

    // Outros itens de manutenção (filtros, velas, correia...): última troca por km e data.
    fun maintConfigured(id: String): Boolean = p.contains("maint_${id}_day")
    fun maintLastKm(id: String): Double = p.getString("maint_${id}_km", null)?.toDoubleOrNull() ?: odometerKm
    fun maintLastDay(id: String): Long = p.getLong("maint_${id}_day", java.time.LocalDate.now().toEpochDay())
    fun setMaintDone(id: String, km: Double, epochDay: Long) {
        p.edit().putString("maint_${id}_km", km.toString()).putLong("maint_${id}_day", epochDay).apply()
    }

    /** Ao sair do app, continuar numa janela flutuante (picture-in-picture). */
    var pipOnLeave: Boolean
        get() = p.getBoolean("pipOnLeave", false)
        set(v) { p.edit().putBoolean("pipOnLeave", v).apply() }

    // Abastecimentos
    fun refuels(): List<Refuel> {
        val arr = JSONArray(p.getString("refuels", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Refuel(
                epochDay = o.getLong("day"),
                liters = o.getDouble("liters"),
                pricePerLiter = o.getDouble("price"),
                full = o.getBoolean("full"),
                odometerKm = o.getDouble("odo"),
                realKmPerL = if (o.isNull("real")) null else o.getDouble("real"),
            )
        }
    }

    fun addRefuel(r: Refuel) {
        val arr = JSONArray(p.getString("refuels", "[]"))
        arr.put(
            JSONObject()
                .put("day", r.epochDay).put("liters", r.liters).put("price", r.pricePerLiter)
                .put("full", r.full).put("odo", r.odometerKm)
                .put("real", r.realKmPerL ?: JSONObject.NULL),
        )
        p.edit().putString("refuels", arr.toString()).apply()
    }
}
