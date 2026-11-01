package br.com.monitor2008.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Onde o carro está, pelo GPS do próprio multimídia (os apps de mapa não repassam a localização),
 * e o endereço pela internet do celular (Geocoder do Android). A localização fica só no aparelho.
 */
object PlaceFinder {

    val permission = Manifest.permission.ACCESS_FINE_LOCATION

    fun allowed(context: Context) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Localização do aparelho ligada (chave "Localização" do Android). Desligada, nenhum app recebe o GPS. */
    fun locationOn(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return try { LocationManagerCompat.isLocationEnabled(lm) } catch (e: Exception) { true }
    }

    /**
     * Localização atual. Primeiro a última posição recente do aparelho (o mapa costuma estar aberto no
     * caminho até o posto, e o GPS demora a achar satélites debaixo da cobertura do posto); depois pede
     * uma posição nova a cada fonte (GPS, combinada, rede), até [timeoutMs] cada; por fim aceita a última
     * conhecida de até 30 min. Null sem permissão, com a localização desligada ou sem nenhuma posição.
     */
    @SuppressLint("MissingPermission") // conferida em allowed()
    suspend fun current(context: Context, timeoutMs: Long = 15_000): Location? {
        if (!allowed(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = try { lm.getProviders(true) } catch (e: Exception) { emptyList() }
        fun lastKnown(maxAgeMs: Long): Location? {
            val now = System.currentTimeMillis()
            return enabled.mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }
                .filter { now - it.time in -60_000L..maxAgeMs }
                .maxByOrNull { it.time }
        }
        lastKnown(2 * 60_000L)?.let { return it }
        val order = listOf(LocationManager.GPS_PROVIDER, "fused", LocationManager.NETWORK_PROVIDER).filter { it in enabled }
        for (provider in order) {
            val fix = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    try {
                        LocationManagerCompat.getCurrentLocation(lm, provider, cancel, ContextCompat.getMainExecutor(context)) {
                            if (cont.isActive) cont.resume(it)
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (fix != null) return fix
        }
        return lastKnown(30 * 60_000L)
    }

    /** Endereço curto ("Av. Paulista, 1000 - Bela Vista"), ou null sem internet. */
    @Suppress("DEPRECATION") // a versão nova (com callback) só existe a partir do Android 13
    suspend fun address(context: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            val a = Geocoder(context, Locale("pt", "BR")).getFromLocation(lat, lon, 1)?.firstOrNull() ?: return@withContext null
            listOfNotNull(
                listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(", ").ifBlank { null },
                a.subLocality ?: a.locality,
            ).joinToString(" - ").ifBlank { a.getAddressLine(0) }
        } catch (e: Exception) {
            null
        }
    }

    /** Estado ("São Paulo"), para as rádios da região. Null sem internet. */
    @Suppress("DEPRECATION")
    suspend fun state(context: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            Geocoder(context, Locale("pt", "BR")).getFromLocation(lat, lon, 1)?.firstOrNull()?.adminArea
                ?.removePrefix("Estado de ")?.removePrefix("Estado do ")?.removePrefix("State of ")
        } catch (e: Exception) {
            null
        }
    }
}
