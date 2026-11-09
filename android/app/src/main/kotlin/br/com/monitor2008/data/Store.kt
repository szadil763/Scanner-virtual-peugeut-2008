package br.com.monitor2008.data

import android.content.Context
import android.content.SharedPreferences
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
    private val ctx = context.applicationContext
    /** Preferências do aparelho (tema, voz, painel, última aba...): não mudam ao trocar de carro. */
    private val app: SharedPreferences = ctx.getSharedPreferences("monitor2008_app", Context.MODE_PRIVATE)
    /** Dados do carro escolhido (viagens, abastecimentos, odômetro, manutenção, adaptador). */
    private val p: SharedPreferences get() = ctx.getSharedPreferences(carFileName(carKey), Context.MODE_PRIVATE)

    init { migrarPreferenciasDoAparelho() }

    /** Id do carro ativo (ver Vehicles). Sem escolha ainda, vale o 2008 (instalação antiga). */
    val carKey: String get() = app.getString("vehicleId", null) ?: Vehicles.PEUGEOT_2008.id

    /** Copia, uma vez, as preferências do aparelho do arquivo antigo (que era único). */
    private fun migrarPreferenciasDoAparelho() {
        if (app.getBoolean("migrado_v1", false)) return
        val velho = ctx.getSharedPreferences("monitor2008", Context.MODE_PRIVATE)
        val ed = app.edit()
        for ((k, v) in velho.all) {
            if (k !in CHAVES_DO_APARELHO) continue
            when (v) {
                is String -> ed.putString(k, v)
                is Boolean -> ed.putBoolean(k, v)
                is Float -> ed.putFloat(k, v)
                is Int -> ed.putInt(k, v)
                is Long -> ed.putLong(k, v)
            }
        }
        ed.putBoolean("migrado_v1", true).apply()
    }

    /**
     * Adaptador (endereço, em maiúsculas) → id do carro. Já vem com os dois adaptadores conhecidos;
     * os outros são aprendidos ao escolher o carro em Ajustes com o adaptador conectado.
     */
    var adapterCars: Map<String, String>
        get() {
            val raw = app.getString("adapterCars", null)
                ?: "81:23:45:67:89:BA>${Vehicles.PEUGEOT_2008.id};01:23:45:67:89:BA>${Vehicles.C4_LOUNGE_20.id}"
            return raw.split(';').mapNotNull { e ->
                val parts = e.split('>')
                if (parts.size == 2 && parts[0].isNotBlank()) parts[0].uppercase() to parts[1] else null
            }.toMap()
        }
        set(v) { app.edit().putString("adapterCars", v.entries.joinToString(";") { "${it.key}>${it.value}" }).apply() }

    fun learnAdapter(address: String, carId: String) {
        adapterCars = adapterCars + (address.uppercase() to carId)
    }

    private fun d(key: String, def: Double) = p.getFloat(key, def.toFloat()).toDouble()

    // Odômetro e acumuladores: salvos em lote por saveProgress() para não gravar a cada leitura.
    val odometerKm: Double get() = p.getString("odometerKm", null)?.toDoubleOrNull() ?: 0.0
    val uncalLiters: Double get() = p.getString("uncalLiters", null)?.toDoubleOrNull() ?: 0.0
    val kmSinceFull: Double get() = p.getString("kmSinceFull", null)?.toDoubleOrNull() ?: 0.0

    /** Tudo que é do carro (preferências do arquivo do carro), com o tipo de cada valor. */
    fun exportarCarro(): org.json.JSONObject {
        val out = org.json.JSONObject()
        for ((k, v) in p.all) {
            val o = org.json.JSONObject()
            when (v) {
                is String -> out.put(k, o.put("t", "s").put("v", v))
                is Boolean -> out.put(k, o.put("t", "b").put("v", v))
                is Float -> out.put(k, o.put("t", "f").put("v", v.toDouble()))
                is Int -> out.put(k, o.put("t", "i").put("v", v))
                is Long -> out.put(k, o.put("t", "l").put("v", v))
            }
        }
        return out
    }

    /** Grava o que veio da nuvem no arquivo do carro. */
    fun importarCarro(j: org.json.JSONObject) {
        val ed = p.edit()
        for (k in j.keys()) {
            val o = j.getJSONObject(k)
            when (o.getString("t")) {
                "s" -> ed.putString(k, o.getString("v"))
                "b" -> ed.putBoolean(k, o.getBoolean("v"))
                "f" -> ed.putFloat(k, o.getDouble("v").toFloat())
                "i" -> ed.putInt(k, o.getInt("v"))
                "l" -> ed.putLong(k, o.getLong("v"))
            }
        }
        ed.apply()
    }

    /** O carro já tem dados neste aparelho? (senão, pode trazer da nuvem sem perder nada) */
    fun carroTemDados(): Boolean = p.contains("odometerKm") || p.contains("trip") || p.contains("refuels")

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
        // Instalação nova já usa o adaptador (o cliente instala para usar no carro, não para ver a demonstração).
        get() = app.getBoolean("useSimulated", false)
        set(v) { app.edit().putBoolean("useSimulated", v).apply() }
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
    /** Adaptador associado para a conexão automática (data/AutoConnect); null = desligada. */
    var autoConnectAddress: String?
        get() = p.getString("autoConnectAddress", null)
        set(v) { p.edit().putString("autoConnectAddress", v).apply() }

    /** O adaptador já conectou pelo Bluetooth clássico neste aparelho: tenta o clássico antes do BLE. */
    var classicWorks: Boolean
        get() = p.getBoolean("classicWorks", false)
        set(v) { p.edit().putBoolean("classicWorks", v).apply() }

    var bleAddress: String?
        get() = p.getString("bleAddress", null)
        set(v) { p.edit().putString("bleAddress", v).apply() }
    var bleName: String?
        get() = p.getString("bleName", null)
        set(v) { p.edit().putString("bleName", v).apply() }

    /** Apps abertos ao lado do Monitor (null = o primeiro instalado). */
    var mapPackage: String?
        get() = app.getString("mapPackage", null)
        set(v) { app.edit().putString("mapPackage", v).apply() }
    var musicPackage: String?
        get() = app.getString("musicPackage", null)
        set(v) { app.edit().putString("musicPackage", v).apply() }

    /** Ao sair do app, continuar numa janela flutuante (picture-in-picture). */
    var pipOnLeave: Boolean
        get() = app.getBoolean("pipOnLeave", false)
        set(v) { app.edit().putBoolean("pipOnLeave", v).apply() }

    /**
     * Perfil do carro deste aparelho ([Vehicles]); null = ainda não escolhido. Instalação antiga, que já
     * rodou com o adaptador, era do 2008: vale como 2008 sem perguntar.
     */
    var vehicleId: String?
        get() = app.getString("vehicleId", null) ?: if (odometerKm > 0) Vehicles.PEUGEOT_2008.id else null
        set(v) { app.edit().putString("vehicleId", v).apply() }

    /**
     * Canal de atualização (UpdateChannel): "ESTAVEL" só recebe versões aprovadas pelo dono; "TESTE"
     * recebe todo APK novo. O aparelho do dono (já rodou com o adaptador) começa no Teste.
     */
    var updateChannel: String
        get() = app.getString("updateChannel", null) ?: if (odometerKm > 0) "TESTE" else "ESTAVEL"
        set(v) { app.edit().putString("updateChannel", v).apply() }
    var updateCheckedMs: Long
        get() = app.getLong("updateCheckedMs", 0L)
        set(v) { app.edit().putLong("updateCheckedMs", v).apply() }

    // Km rodados sem o app (domain/MissedDistance): última leitura do contador da central (PID 31)
    // e o hodômetro do app naquele momento.
    val distCounterKm: Double? get() = p.getString("distCounterKm", null)?.toDoubleOrNull()
    val distCounterAppKm: Double? get() = p.getString("distCounterAppKm", null)?.toDoubleOrNull()

    fun saveDistCounter(counterKm: Double, appKm: Double) {
        p.edit().putString("distCounterKm", counterKm.toString()).putString("distCounterAppKm", appKm.toString()).apply()
    }

    /** O tanque atual teve km rodados sem o app: o consumo dele não entra na calibração nem na nota do posto. */
    var tankIncomplete: Boolean
        get() = p.getBoolean("tankIncomplete", false)
        set(v) { p.edit().putBoolean("tankIncomplete", v).apply() }

    /** Aviso de km rodados sem o app, até o usuário tocar em OK. */
    var missedNotice: String?
        get() = p.getString("missedNotice", null)
        set(v) { p.edit().putString("missedNotice", v).apply() }

    // Voltar como estava ao ligar o carro (data/SessionResume).
    var lastTab: String?
        get() = app.getString("lastTab", null)
        set(v) { app.edit().putString("lastTab", v).apply() }
    var aliveAtMs: Long
        get() = app.getLong("aliveAtMs", 0L)
        set(v) { app.edit().putLong("aliveAtMs", v).apply() }
    val lastPlayingPkg: String? get() = app.getString("lastPlayingPkg", null)
    val lastPlayingAtMs: Long get() = app.getLong("lastPlayingAtMs", 0L)
    fun saveLastPlaying(pkg: String, atMs: Long) {
        app.edit().putString("lastPlayingPkg", pkg).putLong("lastPlayingAtMs", atMs).apply()
    }
    /** Continuar a rádio ou a música que tocava quando o carro desligou. */
    var resumeMedia: Boolean
        get() = app.getBoolean("resumeMedia", true)
        set(v) { app.edit().putBoolean("resumeMedia", v).apply() }
    /** Abastecimento: o dono informa o valor pago (true) ou os litros (false). Lembra a última escolha. */
    var refuelByMoney: Boolean
        get() = app.getBoolean("refuelByMoney", false)
        set(v) { app.edit().putBoolean("refuelByMoney", v).apply() }
    /**
     * Ler o carro sempre, mesmo sem abrir o app: o multimídia liga a leitura em segundo plano ao
     * ligar, e ela reconecta sozinha quando o motor liga. Padrão: ligado.
     */
    var backgroundReading: Boolean
        get() = app.getBoolean("backgroundReading", true)
        set(v) { app.edit().putBoolean("backgroundReading", v).apply() }

    /** Endereço do painel no multimídia (ex.: 10.12.38.252:8080). Vazio = não envia. */
    var painelAddress: String
        get() = app.getString("painelAddress", "") ?: ""
        set(v) { app.edit().putString("painelAddress", v.trim()).apply() }

    /** Tela ao dirigir: AUTO, ALWAYS_FULL ou ALWAYS_SIMPLE (domain/DriveMode). */
    var driveMode: String
        get() = app.getString("driveMode", "AUTO") ?: "AUTO"
        set(v) { app.edit().putString("driveMode", v).apply() }

    /** Tema "sol forte": textos brancos e fundo preto, para ler com o sol batendo na tela. */
    var highContrast: Boolean
        get() = app.getBoolean("highContrast", false)
        set(v) { app.edit().putBoolean("highContrast", v).apply() }

    /** Última vez que o dono conferiu ou informou o km do painel (lembrete mensal). 0 = nunca. */
    var odoCheckedMs: Long
        get() = p.getLong("odoCheckedMs", 0L)
        set(v) { p.edit().putLong("odoCheckedMs", v).apply() }

    /** Chamar o comando de voz dizendo "Monitor" (reconhecimento offline; o modelo é baixado uma vez). */
    var wakeWord: Boolean
        get() = app.getBoolean("wakeWord", false)
        set(v) { app.edit().putBoolean("wakeWord", v).apply() }

    /** Janela flutuante: "MONITOR" (própria, tamanho escolhido) ou "ANDROID" (a do sistema). */
    var floatMode: String
        get() = app.getString("floatMode", "MONITOR") ?: "MONITOR"
        set(v) { app.edit().putString("floatMode", v).apply() }
    var floatSize: Int
        get() = app.getInt("floatSize", 1)
        set(v) { app.edit().putInt("floatSize", v).apply() }
    var floatItems: String?
        get() = app.getString("floatItems", null)
        set(v) { app.edit().putString("floatItems", v).apply() }
    var floatX: Int
        get() = app.getInt("floatX", 24)
        set(v) { app.edit().putInt("floatX", v).apply() }
    var floatY: Int
        get() = app.getInt("floatY", 120)
        set(v) { app.edit().putInt("floatY", v).apply() }

    /** Voz do comando de voz (nome técnico da voz do leitor); null = a melhor que o app achar. */
    var ttsVoice: String?
        get() = app.getString("ttsVoice", null)
        set(v) { app.edit().putString("ttsVoice", v).apply() }
    /** Velocidade e tom da fala (1,0 = normal). */
    var ttsRate: Float
        get() = app.getFloat("ttsRate", 1.0f)
        set(v) { app.edit().putFloat("ttsRate", v).apply() }
    var ttsPitch: Float
        get() = app.getFloat("ttsPitch", 1.0f)
        set(v) { app.edit().putFloat("ttsPitch", v).apply() }
    /** Abrir o app sozinho quando o multimídia liga (precisa da permissão "sobrepor a outros apps"). */
    var autoOpen: Boolean
        get() = app.getBoolean("autoOpen", false)
        set(v) { app.edit().putBoolean("autoOpen", v).apply() }

    /** Aba Mídias: "MUSICA" ou "RADIO" ao lado dos números do Painel. */
    var mediaMode: String
        get() = app.getString("mediaMode", "MUSICA") ?: "MUSICA"
        set(v) { app.edit().putString("mediaMode", v).apply() }

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
        get() = app.getString("lastRadio", null)?.let { runCatching { radioFrom(JSONObject(it)) }.getOrNull() }
        set(v) { app.edit().putString("lastRadio", v?.let { radioJson(it).toString() }).apply() }

    /** Lugares salvos para o comando de voz ("ir para casa"): nome e endereço. */
    fun places(): List<Pair<String, String>> = try {
        val arr = JSONArray(app.getString("places", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { it.optString("name") to it.optString("address") }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
    } catch (e: Exception) { emptyList() }

    fun savePlaces(list: List<Pair<String, String>>) {
        val arr = JSONArray()
        list.forEach { (n, a) -> arr.put(org.json.JSONObject().put("name", n).put("address", a)) }
        app.edit().putString("places", arr.toString()).apply()
    }

    fun radioFavorites(): List<RadioStation> {
        val arr = JSONArray(app.getString("radioFavorites", "[]"))
        return (0 until arr.length()).mapNotNull { runCatching { radioFrom(arr.getJSONObject(it)) }.getOrNull() }
    }

    fun saveRadioFavorites(list: List<RadioStation>) {
        val arr = JSONArray()
        list.forEach { arr.put(radioJson(it)) }
        app.edit().putString("radioFavorites", arr.toString()).apply()
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


/** Chaves de preferências que são do aparelho (não mudam ao trocar de carro). */
private val CHAVES_DO_APARELHO = setOf(
    "highContrast", "driveMode", "ttsVoice", "ttsRate", "ttsPitch", "wakeWord", "backgroundReading", "floatMode", "floatSize", "floatItems", "floatX", "floatY", "lastTab", "mapPackage", "musicPackage", "mediaMode", "radioFavorites", "lastRadio", "places", "refuelByMoney", "updateChannel", "updateCheckedMs", "autoOpen", "resumeMedia", "pipOnLeave", "painelAddress", "useSimulated", "vehicleId", "lastPlayingAtMs", "lastPlayingPkg", "aliveAtMs"
)

/** Arquivo de preferências de cada carro: o 2008 segue no arquivo de sempre (não perde o histórico). */
internal fun carFileName(carId: String): String =
    if (carId == Vehicles.PEUGEOT_2008.id) "monitor2008" else "monitor2008_$carId"
