package br.com.monitor2008.data

import br.com.monitor2008.domain.MaintCatalog
import br.com.monitor2008.domain.Station
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dados de cada CARRO na nuvem (Firebase Realtime Database), em /carros/<carro>.
 * Quem está na lista de membros do carro lê e grava. Quem é convidado (pelo e-mail, em
 * "convites") entra sozinho ao sincronizar. Os dados são do carro, não da pessoa.
 * Regras: ver docs/FIREBASE.md.
 */
object Nuvem {
    private fun carro(id: String) = FirebaseDatabase.getInstance().reference.child("carros").child(id)

    fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /** Sincroniza o carro: entra na lista do carro, traz o que falta no aparelho e envia o que ele tem. */
    fun sincronizar(store: Store, depois: (Boolean, String) -> Unit) {
        val uid = uid() ?: return depois(false, "Entre com a conta Google primeiro.")
        val id = store.carKey
        carro(id).child("membros").child(uid).setValue(true)
            .addOnSuccessListener { receber(store, id, depois) }
            .addOnFailureListener { depois(false, "Não consegui entrar no carro na nuvem: ${it.message ?: "sem permissão"}") }
    }

    /** Traz da nuvem o que o aparelho ainda não tem (abastecimentos, postos, manutenção, odômetro). */
    private fun receber(store: Store, id: String, depois: (Boolean, String) -> Unit) {
        carro(id).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val abast = JSONArray(snap.child("abastecimentos").getValue(String::class.java) ?: "[]")
                    val postos = JSONArray(snap.child("postos").getValue(String::class.java) ?: "[]")
                    val manut = JSONObject(snap.child("manutencao").getValue(String::class.java) ?: "{}")
                    val odo = snap.child("odometro").getValue(Double::class.java) ?: 0.0
                    if (store.refuels().isEmpty()) {
                        for (i in 0 until abast.length()) {
                            val o = abast.getJSONObject(i)
                            store.addRefuel(
                                Refuel(
                                    epochDay = o.getLong("dia"),
                                    liters = o.getDouble("litros"),
                                    pricePerLiter = o.getDouble("preco"),
                                    full = o.getBoolean("cheio"),
                                    odometerKm = o.optDouble("odometro", 0.0),
                                    realKmPerL = if (o.isNull("kmL")) null else o.getDouble("kmL"),
                                    timeMs = o.optLong("hora", 0L).takeIf { it > 0 } ?: (o.getLong("dia") * 86_400_000L + 43_200_000L),
                                    stationId = if (o.isNull("posto")) null else o.getString("posto"),
                                    ethanol = o.optBoolean("etanol", false),
                                    tankRatio = if (o.isNull("tanque")) null else o.getDouble("tanque"),
                                ),
                            )
                        }
                    }
                    if (store.stations().isEmpty() && postos.length() > 0) {
                        store.saveStations(
                            (0 until postos.length()).map { i ->
                                val p = postos.getJSONObject(i)
                                Station(p.getString("id"), p.getString("name"), p.optString("address", ""), p.getDouble("lat"), p.getDouble("lon"))
                            },
                        )
                    }
                    if (store.odometerKm == 0.0 && odo > 0) store.saveProgress(odo, store.uncalLiters, store.kmSinceFull)
                    for (item in MaintCatalog.items) {
                        val m = manut.optJSONObject(item.id) ?: continue
                        if (!store.maintConfigured(item.id)) store.setMaintDone(item.id, m.getDouble("km"), m.getLong("dia"))
                    }
                }
                enviar(store, id, depois)
            }
            .addOnFailureListener { depois(false, "Não consegui ler o carro na nuvem: ${it.message ?: "sem permissão"}") }
    }

    /** Envia o que o aparelho tem deste carro. */
    private fun enviar(store: Store, id: String, depois: (Boolean, String) -> Unit) {
        val uid = uid() ?: return depois(false, "Entre com a conta Google primeiro.")
        val abast = JSONArray().apply {
            store.refuels().forEach { r ->
                put(JSONObject().apply {
                    put("dia", r.epochDay)
                    put("litros", r.liters)
                    put("preco", r.pricePerLiter)
                    put("cheio", r.full)
                    put("odometro", r.odometerKm)
                    put("kmL", r.realKmPerL ?: JSONObject.NULL)
                    put("hora", r.timeMs)
                    put("posto", r.stationId ?: JSONObject.NULL)
                    put("etanol", r.ethanol)
                    put("tanque", r.tankRatio ?: JSONObject.NULL)
                })
            }
        }
        val postos = JSONArray().apply {
            store.stations().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("address", s.address)
                    put("lat", s.lat)
                    put("lon", s.lon)
                })
            }
        }
        val manut = JSONObject().apply {
            for (item in MaintCatalog.items) {
                if (store.maintConfigured(item.id)) {
                    put(item.id, JSONObject().apply {
                        put("km", store.maintLastKm(item.id))
                        put("dia", store.maintLastDay(item.id))
                    })
                }
            }
        }
        val dados = hashMapOf<String, Any>(
            "membros/$uid" to true,
            "abastecimentos" to abast.toString(),
            "postos" to postos.toString(),
            "manutencao" to manut.toString(),
            "odometro" to store.odometerKm,
            "atualizadoEm" to ServerValue.TIMESTAMP,
        )
        carro(id).updateChildren(dados)
            .addOnSuccessListener { depois(true, "Carro sincronizado com a nuvem.") }
            .addOnFailureListener { depois(false, "Não enviei para a nuvem: ${it.message ?: "sem permissão"}") }
    }

    /** Convida uma pessoa (pelo e-mail Google dela). Ela entra sozinha ao sincronizar. */
    fun convidar(store: Store, email: String, depois: (Boolean, String) -> Unit) {
        val e = email.trim().lowercase()
        if (!e.contains("@")) return depois(false, "Digite um e-mail válido.")
        val ref = carro(store.carKey).child("convites")
        ref.get()
            .addOnSuccessListener { snap ->
                val atual = snap.getValue(String::class.java)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                ref.setValue((atual + e).joinToString(","))
                    .addOnSuccessListener { depois(true, "$e foi convidado. A pessoa entra com a conta Google dela e sincroniza.") }
                    .addOnFailureListener { depois(false, "Não convidei: ${it.message ?: "sem permissão"}") }
            }
            .addOnFailureListener { depois(false, "Não consegui ler os convites: ${it.message ?: "sem permissão"}") }
    }
}
