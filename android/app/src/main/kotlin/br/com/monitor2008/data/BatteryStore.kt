package br.com.monitor2008.data

import android.content.Context
import br.com.monitor2008.domain.BatteryLog
import br.com.monitor2008.domain.BatteryPoint
import java.io.File

/** Arquivo do registro da tensão da bateria de um carro (uma leitura por linha, no formato CSV). */
class BatteryStore(context: Context, private val carKey: String) {
    private val file = File(context.applicationContext.filesDir, "bateria-$carKey.csv")

    /** As últimas [max] leituras. */
    fun load(max: Int = BatteryLog.MAX_POINTS): List<BatteryPoint> = try {
        if (!file.exists()) emptyList()
        else file.readLines().takeLast(max).mapNotNull { BatteryLog.parseCsvLine(it) }
    } catch (e: Exception) { emptyList() }

    fun append(points: List<BatteryPoint>) {
        if (points.isEmpty()) return
        try {
            file.appendText(points.joinToString("\n", postfix = "\n") { BatteryLog.csvLine(it) })
            // Não deixa o arquivo crescer sem fim: passando do dobro do que se guarda, fica só com o final.
            if (file.length() > 500_000L) file.writeText(load(BatteryLog.MAX_POINTS).joinToString("\n", postfix = "\n") { BatteryLog.csvLine(it) })
        } catch (_: Exception) { }
    }

    fun clear() { try { file.delete() } catch (_: Exception) { } }
}
