package br.com.monitor2008.car

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.com.monitor2008.data.ConnState
import br.com.monitor2008.domain.HealthStatus
import br.com.monitor2008.ui.MonitorEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Painel no Android Auto: grade de 6 blocos. Os modelos do Android Auto não têm texto grande, então
 * cada número é desenhado como imagem. Título, quantidade e ordem dos blocos ficam sempre iguais:
 * assim cada atualização conta como "refresh" e o Android Auto não bloqueia o app.
 */
class PainelCarScreen(carContext: CarContext) : Screen(carContext) {

    private val engine = MonitorEngine.get(carContext.applicationContext as Application)

    init {
        // Atualiza uma vez por segundo enquanto a tela está visível.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(1_000)
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val ui = engine.ui.value
        val s = ui.sample
        fun n(v: Double?, d: Int = 0) = v?.let { String.format(BR, "%.${d}f", it) } ?: "--"

        val status = when {
            ui.simulated -> "Dados simulados: desligue em Ajustes no celular"
            ui.conn.state != ConnState.CONNECTED -> ui.conn.message.ifBlank { "Aguardando o adaptador..." }
            else -> "Conectado"
        }
        val kmPerL = ui.kmPerL
        val hot = (s?.coolantC ?: 0.0) > 105
        val alerts = ui.health.count { it.status == HealthStatus.ALERTA }
        val warns = ui.health.count { it.status == HealthStatus.ATENCAO }
        val saudeText = when {
            alerts > 0 -> "Saúde: $alerts alerta(s)"
            warns > 0 -> "Saúde: $warns atenção"
            else -> "Saúde OK"
        }
        val items = ItemList.Builder()
            .addItem(tile("Velocidade", n(s?.speedKmh), "km/h", WHITE, status))
            .addItem(tile("Consumo", n(kmPerL, 1), "km/l", if ((kmPerL ?: 99.0) < 7) YELLOW else GREEN, "${n(ui.litersPerHour, 2)} L/h"))
            .addItem(tile("Rotação", n(s?.rpm), "rpm", WHITE, saudeText))
            .addItem(tile("Temp. motor", n(s?.coolantC), "°C", if (hot) RED else WHITE, null))
            .addItem(tile("Média do trajeto", n(ui.tripAvgKmPerL, 1), "km/l", WHITE, "${n(ui.tripKm, 1)} km"))
            .addItem(tile("Bateria", n(s?.batteryV, 1), "V", WHITE, null))
            .build()

        val saude = Action.Builder()
            .setTitle("Saúde")
            .setOnClickListener { screenManager.push(SaudeCarScreen(carContext, engine)) }
            .build()

        return GridTemplate.Builder()
            .setTitle("Monitor 2008")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items)
            .setActionStrip(ActionStrip.Builder().addAction(saude).build())
            .build()
    }

    private fun tile(label: String, value: String, unit: String, color: Int, text: String?): GridItem {
        val b = GridItem.Builder().setTitle(label).setImage(valueIcon(value, unit, color), GridItem.IMAGE_TYPE_LARGE)
        if (text != null) b.setText(text)
        return b.build()
    }

    /** Desenha o número grande e a unidade numa imagem quadrada. */
    private fun valueIcon(value: String, unit: String, color: Int): CarIcon {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = 150f
        }
        while (p.measureText(value) > size * 0.95f && p.textSize > 30f) p.textSize -= 6f
        c.drawText(value, size / 2f, size * 0.62f, p)
        val u = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = GREY
            textAlign = Paint.Align.CENTER
            textSize = 46f
        }
        c.drawText(unit, size / 2f, size * 0.92f, u)
        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }

    private companion object {
        val BR = Locale("pt", "BR")
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GREEN = 0xFF4ADE80.toInt()
        const val YELLOW = 0xFFFBBF24.toInt()
        const val RED = 0xFFF87171.toInt()
        const val GREY = 0xFFB0BEC5.toInt()
    }
}
