package br.com.monitor2008.data

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Tela do registro de erros (ver [CrashLog]). Feita só com views simples do Android, sem o motor do app nem Compose,
 * para abrir mesmo quando o erro está no resto do app.
 */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texto = CrashLog.pending(this) ?: run { abrirApp(); return }
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
        fun botao(rotulo: String, acao: () -> Unit) = Button(this).apply {
            text = rotulo
            textSize = 18f
            minHeight = dp(64)
            setOnClickListener { acao() }
        }
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 15, 20))
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        raiz.addView(TextView(this).apply {
            text = "O Monitor 2008 fechou sozinho da última vez"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        raiz.addView(TextView(this).apply {
            text = "Mande este registro para quem cuida do app (pelo WhatsApp). Ele mostra o motivo do erro e ajuda a corrigir. " +
                "Não tem dados pessoais: só a versão, o modelo do aparelho e onde o app parou."
            textSize = 16f
            setTextColor(Color.rgb(148, 163, 184))
            setPadding(0, dp(8), 0, dp(12))
        })
        raiz.addView(botao("Enviar pelo WhatsApp") {
            val envio = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Erro do Monitor 2008")
                .putExtra(Intent.EXTRA_TEXT, texto)
            startActivity(Intent.createChooser(envio, "Enviar o registro do erro"))
        })
        raiz.addView(botao("Abrir o app de novo") { CrashLog.clear(this); abrirApp() })
        val rolagem = ScrollView(this).apply {
            setPadding(0, dp(12), 0, 0)
            addView(TextView(this@CrashReportActivity).apply {
                text = texto
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.rgb(203, 213, 225))
                setTextIsSelectable(true)
            })
        }
        raiz.addView(rolagem, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(raiz)
    }

    /** Volta para a tela de entrada do app (painel no multimídia, tela principal no celular). */
    private fun abrirApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }
}
