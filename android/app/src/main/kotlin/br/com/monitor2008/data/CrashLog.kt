package br.com.monitor2008.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de erros: quando o app fecha sozinho, grava o motivo (versão, aparelho, Android e o rastro do erro) num
 * arquivo do próprio app. Na próxima abertura, a tela [CrashReportActivity] mostra o registro e manda pelo WhatsApp.
 * Nada sai do aparelho sem o dono tocar em "Enviar". Criado porque um celular parou de abrir na 0.1.173 e não havia
 * como saber o motivo.
 */
object CrashLog {
    private const val FILE = "ultimo-erro.txt"
    private const val MAX_CHARS = 8_000

    fun install(context: Context) {
        val app = context.applicationContext
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            try { save(app, thread, erro) } catch (_: Throwable) { }
            anterior?.uncaughtException(thread, erro)
        }
    }

    private fun save(context: Context, thread: Thread, erro: Throwable) {
        val sw = StringWriter()
        erro.printStackTrace(PrintWriter(sw))
        val versao = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pi.versionName} (${if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()})"
        } catch (_: Exception) { "?" }
        val anteriores = file(context).takeIf { it.exists() }?.readText()?.let { 1 + (Regex("Vezes: (\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 1) } ?: 1
        val texto = buildString {
            appendLine("Monitor 2008 fechou sozinho")
            appendLine("Quando: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())}")
            appendLine("Vezes: $anteriores")
            appendLine("Versão do app: $versao")
            appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Parte do app: ${thread.name}")
            appendLine()
            append(sw.toString())
        }
        file(context).writeText(texto.take(MAX_CHARS))
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** O último erro gravado, ou null se o app não fechou sozinho desde a última vez que o dono viu. */
    fun pending(context: Context): String? = try {
        file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    fun clear(context: Context) { try { file(context).delete() } catch (_: Exception) { } }

    /**
     * Chamar no começo do onCreate das telas de entrada: se há erro gravado, abre a tela do registro e devolve true
     * (a tela que chamou deve terminar ali, para não fechar de novo antes de o dono ver o motivo).
     */
    fun showIfPending(activity: Activity): Boolean {
        if (pending(activity) == null) return false
        activity.startActivity(Intent(activity, CrashReportActivity::class.java))
        activity.finish()
        return true
    }
}
