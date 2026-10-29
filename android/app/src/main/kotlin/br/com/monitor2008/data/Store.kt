package br.com.monitor2008.data

import android.content.Context
import br.com.monitor2008.domain.RadioStation
import br.com.monitor2008.domain.Station
import br.com.monitor2008.domain.Vehicles
import org.json.JSONArray
import org.json.JSONObject

data class Refuel(
    val epochDay: Long,
    val liters: Double,
    val pricePerLiter: Double,
    val full: Boolean,
    val odometerKm: Double,
    val realKmPerL: Double?,
    /** Hora do abastecimento (registros antigos: meio-dia do dia registrado). */
    val timeMs: Long = epochDay * 86_400_000L + 43_200_000L,
    val stationId: String? = null,
    val ethanol: Boolean = false,
    /** Litros reais ÷ previstos do tanque que terminou neste abastecimento (só cheio a cheio). */
    val tankRatio: Double? = null,
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

    // Trajeto: soma de todas as viagens até o usuário zerar.
    fun loadTrip(): DoubleArray? =
        p.getString("trip", null)?.split(';')?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 4 }?.toDoubleArray()

    fun saveTrip(distanceKm: Double, liters: Double, seconds: Double, kmWithFuel: Double) {
        p.edit().putString("trip", "$distanceKm;$liters;$seconds;$kmWithFuel").apply()
    }

    /** Dia em que o trajeto foi zerado pela última vez (null = nunca). */
    var tripSinceDay: Long?
        get() = if (p.contains("tripSinceDay")) p.getLong("tripSinceDay", 0) else null
        set(v) { if (v == null) p.edit().remove("tripSinceDay").apply() else p.edit().putLong("tripSinceDay", v).apply() }

    /**
     * Diferença entre o hodômetro do carro e os km contados pelo app (null = ainda não informado).
     * Km do carro = km do app + diferença. Os km das trocas ficam gravados em km do app.
     */
    var odoOffset: Double?
        get() = p.getString("odoOffset", null)?.toDoubleOrNull()
        set(v) { p.edit().putString("odoOffset", v?.toString()).apply() }

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

    /** Último adaptador BLE que conectou: o app conecta direto nele, sem precisar procurar. */
    var bleAddress: String?
        get() = p.getString("bleAddress", null)
        set(v) { p.edit().putString("bleAddress", v).apply() }
    var bleName: String?
        get() = p.getString("bleName", null)
        set(v) { p.edit().putString("bleName", v).apply() }

    /** Apps abertos ao lado do Monitor (null = o primeiro instalado). */
    var mapPackage: String?
        get() = p.getString("mapPackage", null)
        set(v) { p.edit().putString("mapPackage", v).apply() }
    var musicPackage: String?
        get() = p.getString("musicPackage", null)
        set(v) { p.edit().putString("musicPackage", v).apply() }

    /** Ao sair do app, continuar numa janela flutuante (picture-in-picture). */
    var pipOnLeave: Boolean
        get() = p.getBoolean("pipOnLeave", false)
        set(v) { p.edit().putBoolean("pipOnLeave", v).apply() }

    /**
     * Perfil do carro deste aparelho ([Vehicles]); null = ainda não escolhido. Instalação antiga, que já
     * rodou com o adaptador, era do 2008: vale como 2008 sem perguntar.
     */
    var vehicleId: String?
        get() = p.getString("vehicleId", null) ?: if (odometerKm > 0) Vehicles.PEUGEOT_2008.id else null
        set(v) { p.edit().putString("vehicleId", v).apply() }

    /** Aba Mídias: "MUSICA" ou "RADIO" ao lado dos números do Painel. */
    var mediaMode: String
        get() = p.getString("mediaMode", "MUSICA") ?: "MUSICA"
        set(v) { p.edit().putString("mediaMode", v).apply() }

    /** Apps que já tocaram algo (inclui o Bluetooth e o rádio do multimídia): viram fontes na aba Mídias. */
    val mediaSeen: Set<String> get() = p.getStringSet("mediaSeen", emptySet()) ?: emptySet()

    fun addMediaSeen(pkg: String) {
        val now = mediaSeen
        if (pkg !in now) p.edit().putStringSet("mediaSeen", now + pkg).apply()
    }

    // Rádio pela internet
    private fun radioJson(st: RadioStation) = JSONObject()
        .put("id", st.id).put("name", st.name).put("url", st.url).put("favicon", st.favicon ?: "")
        .put("tags", st.tags).put("state", st.state).put("hls", st.hls)

    private fun radioFrom(o: JSONObject) = RadioStation(
        id = o.getString("id"), name = o.getString("name"), url = o.getString("url"),
        favicon = o.optString("favicon").ifBlank { null }, tags = o.optString("tags"), state = o.optString("state"),
        hls = o.optBoolean("hls", false),
    )

    var lastRadio: RadioStation?
        get() = p.getString("lastRadio", null)?.let { runCatching { radioFrom(JSONObject(it)) }.getOrNull() }
        set(v) { p.edit().putString("lastRadio", v?.let { radioJson(it).toString() }).apply() }

    fun radioFavorites(): List<RadioStation> {
        val arr = JSONArray(p.getString("radioFavorites", "[]"))
        return (0 until arr.length()).mapNotNull { runCatching { radioFrom(arr.getJSONObject(it)) }.getOrNull() }
    }

    fun saveRadioFavorites(list: List<RadioStation>) {
        val arr = JSONArray()
        list.forEach { arr.put(radioJson(it)) }
        p.edit().putString("radioFavorites", arr.toString()).apply()
    }

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
                timeMs = o.optLong("time", o.getLong("day") * 86_400_000L + 43_200_000L),
                stationId = o.optString("station").ifBlank { null },
                ethanol = o.optBoolean("eth", false),
                tankRatio = if (o.has("ratio") && !o.isNull("ratio")) o.getDouble("ratio") else null,
            )
        }
    }

    // Postos (registro de postos e qualidade do combustível)
    fun stations(): List<Station> {
        val arr = JSONArray(p.getString("stations", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Station(o.getString("id"), o.getString("name"), o.optString("address"), o.getDouble("lat"), o.getDouble("lon"))
        }
    }

    fun saveStations(list: List<Station>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("address", it.address).put("lat", it.lat).put("lon", it.lon))
        }
        p.edit().putString("stations", arr.toString()).apply()
    }

    fun addRefuel(r: Refuel) {
        val arr = JSONArray(p.getString("refuels", "[]"))
        arr.put(
            JSONObject()
                .put("day", r.epochDay).put("liters", r.liters).put("price", r.pricePerLiter)
                .put("full", r.full).put("odo", r.odometerKm)
                .put("real", r.realKmPerL ?: JSONObject.NULL)
                .put("time", r.timeMs).put("station", r.stationId ?: "").put("eth", r.ethanol)
                .put("ratio", r.tankRatio ?: JSONObject.NULL),
        )
        p.edit().putString("refuels", arr.toString()).apply()
    }
}
