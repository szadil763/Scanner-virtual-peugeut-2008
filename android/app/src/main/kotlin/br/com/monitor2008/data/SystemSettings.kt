package br.com.monitor2008.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech

/**
 * Atalhos para a tela exata do Android onde o dono libera algo (pedido do dono: "leve direto para
 * as configurações"). Cada um tenta a tela mais específica e, se o multimídia não tiver, uma mais
 * geral. Devolve false se nenhuma abriu.
 */
object SystemSettings {

    private fun open(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    private fun appUri(ctx: Context) = Uri.parse("package:${ctx.packageName}")

    /** Tela do app (Permissões: microfone, localização, Bluetooth, notificações). */
    fun app(ctx: Context) = open(ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(ctx)),
        Intent(Settings.ACTION_SETTINGS))

    /** Acesso a notificações, já na chave do Monitor quando o Android deixa (11+). */
    fun notificationAccess(ctx: Context): Boolean {
        val component = ComponentName(ctx, MediaListenerService::class.java)
        val detail = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else null
        return open(ctx, *listOfNotNull(detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), Intent(Settings.ACTION_SETTINGS)).toTypedArray())
    }

    /** "Sobrepor a outros apps" do Monitor. */
    fun overlay(ctx: Context) = open(ctx,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, appUri(ctx)),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(ctx)))

    /** Chave geral de localização do aparelho. */
    fun location(ctx: Context) = open(ctx, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun bluetooth(ctx: Context) = open(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    /** Saída de texto para fala (escolher o leitor e baixar vozes). */
    fun textToSpeech(ctx: Context) = open(ctx,
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
        Intent(Settings.ACTION_SETTINGS))

    /** Baixar vozes no leitor de voz instalado. */
    fun installVoices(ctx: Context) = open(ctx,
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
        Intent("com.android.settings.TTS_SETTINGS"))

    /** Página de um app na Play Store (ex.: reconhecimento e vozes do Google). */
    fun playStore(ctx: Context, pkg: String) = open(ctx,
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))

    const val GOOGLE_TTS = "com.google.android.tts"
    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
}
