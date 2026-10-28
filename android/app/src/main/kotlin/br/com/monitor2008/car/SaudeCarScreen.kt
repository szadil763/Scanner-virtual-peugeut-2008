package br.com.monitor2008.car

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import br.com.monitor2008.domain.HealthStatus
import br.com.monitor2008.ui.MonitorEngine

/**
 * Saúde no Android Auto: lista dos sistemas, alertas primeiro. Dirigindo, o Android Auto limita o
 * tamanho da lista; os gráficos e as explicações completas ficam na tela do app.
 */
class SaudeCarScreen(carContext: CarContext, private val engine: MonitorEngine) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val ui = engine.ui.value
        val limit = try {
            carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (e: Exception) {
            6
        }
        val order = listOf(HealthStatus.ALERTA, HealthStatus.ATENCAO, HealthStatus.OK, HealthStatus.APRENDENDO)
        val list = ItemList.Builder()
        ui.health.sortedBy { order.indexOf(it.status) }.take(limit).forEach { item ->
            val (label, color) = when (item.status) {
                HealthStatus.OK -> "OK" to CarColor.GREEN
                HealthStatus.ATENCAO -> "ATENÇÃO" to CarColor.YELLOW
                HealthStatus.ALERTA -> "ALERTA" to CarColor.RED
                HealthStatus.APRENDENDO -> "APRENDENDO" to CarColor.DEFAULT
            }
            val text = SpannableString("$label · ${item.value}")
            text.setSpan(ForegroundCarColorSpan.create(color), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            list.addItem(Row.Builder().setTitle(item.title).addText(text).build())
        }
        if (ui.health.isEmpty()) list.setNoItemsMessage("Sem dados ainda.")
        return ListTemplate.Builder()
            .setTitle("Saúde do carro")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
