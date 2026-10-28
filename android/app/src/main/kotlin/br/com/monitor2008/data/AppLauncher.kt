package br.com.monitor2008.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** Um app instalado que pode ser aberto ao lado do Monitor. */
data class AppChoice(val pkg: String, val label: String)

/**
 * Abre o app de mapa ou de música **ao lado** do Monitor, em tela dividida
 * (FLAG_ACTIVITY_LAUNCH_ADJACENT). Abrir um substitui o outro na metade da tela, então dá para
 * alternar entre mapa e música sem sair do Monitor. Se o multimídia não respeitar a tela
 * dividida, o app abre em tela cheia.
 */
class AppLauncher(private val context: Context) {
    private val pm = context.packageManager
    private val store = Store(context)

    /** Apps de mapa instalados: os que abrem endereços (geo:) e os navegadores conhecidos. */
    fun mapApps(): List<AppChoice> {
        val byIntent = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")), 0)
            .map { it.activityInfo.packageName }
        return choices(KNOWN_MAPS + byIntent)
    }

    /** Apps de música instalados: os que oferecem player de mídia e os conhecidos. */
    fun musicApps(): List<AppChoice> {
        val services = pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0)
            .map { it.serviceInfo.packageName }
        val activities = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), 0,
        ).map { it.activityInfo.packageName }
        return choices(KNOWN_MUSIC + services + activities)
    }

    /** Só os instalados que abrem pelo ícone, sem repetir, na ordem da lista. */
    private fun choices(pkgs: List<String>): List<AppChoice> = pkgs.distinct()
        .filter { it != context.packageName && pm.getLaunchIntentForPackage(it) != null }
        .map { AppChoice(it, label(it)) }

    fun label(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    var mapPackage: String?
        get() = store.mapPackage
        set(v) { store.mapPackage = v }
    var musicPackage: String?
        get() = store.musicPackage
        set(v) { store.musicPackage = v }

    /** Devolve null se abriu, ou a mensagem de erro. */
    fun openMap(): String? = openAdjacent(mapPackage ?: mapApps().firstOrNull()?.pkg, "mapa")

    fun openMusic(): String? = openAdjacent(musicPackage ?: musicApps().firstOrNull()?.pkg, "música")

    private fun openAdjacent(pkg: String?, what: String): String? {
        pkg ?: return "Nenhum app de $what instalado."
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return "O app de $what escolhido não está instalado."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        return try {
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            "Não foi possível abrir o app de $what."
        }
    }

    private companion object {
        val KNOWN_MAPS = listOf(
            "com.google.android.apps.maps", "com.waze", "net.osmand", "net.osmand.plus",
            "com.sygic.aura", "com.here.app.maps", "ru.yandex.yandexnavi",
        )
        val KNOWN_MUSIC = listOf(
            "com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app",
            "com.amazon.mp3", "com.apple.android.music", "com.soundcloud.android", "com.audible.application",
            "com.google.android.apps.podcasts",
        )
    }
}
