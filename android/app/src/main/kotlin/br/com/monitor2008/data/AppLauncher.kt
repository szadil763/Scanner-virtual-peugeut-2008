package br.com.monitor2008.data

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Rational

/** Um app instalado que pode ser aberto ao lado do Monitor. */
data class AppChoice(val pkg: String, val label: String)

/**
 * Abre o app de mapa ou de música junto com o Monitor:
 * - tela já dividida: abre na outra metade (FLAG_ACTIVITY_LAUNCH_ADJACENT); abrir um troca pelo outro;
 * - Monitor em tela cheia: o Android não deixa um app forçar a divisão (e o multimídia ignorou o
 *   pedido no teste), então o app abre em tela cheia e o Monitor vira janela flutuante por cima.
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
    fun openMap(activity: Activity?): String? = open(mapPackage ?: mapApps().firstOrNull()?.pkg, "mapa", activity)

    fun openMusic(activity: Activity?): String? = open(musicPackage ?: musicApps().firstOrNull()?.pkg, "música", activity)

    private fun open(pkg: String?, what: String, activity: Activity?): String? {
        pkg ?: return "Nenhum app de $what instalado."
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return "O app de $what escolhido não está instalado."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (activity?.isInMultiWindowMode == true) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        } else if (activity != null && pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            } catch (e: Exception) {
                // Sem janela flutuante neste multimídia: o app abre por cima, como um app normal.
            }
        }
        return try {
            (activity ?: context).startActivity(intent)
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
