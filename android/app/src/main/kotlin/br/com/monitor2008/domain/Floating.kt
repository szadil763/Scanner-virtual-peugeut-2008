package br.com.monitor2008.domain

import java.util.Locale

/** Números que a janela flutuante do Monitor pode mostrar. */
enum class FloatItem(val label: String, val unit: String) {
    VELOCIDADE("Velocidade", "km/h"),
    CONSUMO("Consumo agora", "km/l"),
    MEDIA("Média", "km/l"),
    ROTACAO("Rotação", "rpm"),
    TEMPERATURA("Motor", "°C"),
    BATERIA("Bateria", "V"),
}

/** Tamanho da janela: largura em dp e letras do número e do nome. */
data class FloatSize(val label: String, val widthDp: Int, val valueSp: Float, val labelSp: Float)

/** Valores do momento (null = sem dado). */
data class FloatValues(
    val speedKmh: Double? = null,
    val kmPerL: Double? = null,
    val avgKmPerL: Double? = null,
    val rpm: Double? = null,
    val coolantC: Double? = null,
    val batteryV: Double? = null,
)

/**
 * Regras da janela flutuante própria (pedido do dono: a do Android era pequena e não dava para
 * aumentar). O dono escolhe o tamanho e os números; a janela guarda a posição.
 */
object FloatRules {
    val SIZES = listOf(
        FloatSize("Pequena", 260, 30f, 12f),
        FloatSize("Média", 360, 42f, 14f),
        FloatSize("Grande", 480, 58f, 17f),
    )

    val DEFAULT = listOf(FloatItem.VELOCIDADE, FloatItem.CONSUMO, FloatItem.ROTACAO, FloatItem.TEMPERATURA)

    fun size(index: Int): FloatSize = SIZES[index.coerceIn(0, SIZES.size - 1)]

    fun stepSize(index: Int, up: Boolean): Int = (index + if (up) 1 else -1).coerceIn(0, SIZES.size - 1)

    /** Lista gravada ("VELOCIDADE,CONSUMO"); vazia ou inválida = [DEFAULT]. Mantém a ordem do enum. */
    fun parse(saved: String?): List<FloatItem> {
        val names = saved?.split(',')?.map { it.trim() }?.toSet().orEmpty()
        val items = FloatItem.values().filter { it.name in names }
        return items.ifEmpty { DEFAULT }
    }

    fun serialize(items: List<FloatItem>): String = items.joinToString(",") { it.name }

    /** Liga ou desliga um número; sempre fica pelo menos um. */
    fun toggle(items: List<FloatItem>, item: FloatItem): List<FloatItem> {
        val next = if (item in items) items - item else items + item
        return FloatItem.values().filter { it in next }.ifEmpty { items }
    }

    /** Texto do número na janela ("—" sem dado; consumo parado também é "—"). */
    fun text(item: FloatItem, v: FloatValues): String {
        fun f(x: Double?, d: Int) = x?.let { String.format(Locale("pt", "BR"), "%.${d}f", it) } ?: "—"
        return when (item) {
            FloatItem.VELOCIDADE -> f(v.speedKmh, 0)
            FloatItem.CONSUMO -> if ((v.speedKmh ?: 0.0) < 3) "—" else f(v.kmPerL, 1)
            FloatItem.MEDIA -> f(v.avgKmPerL, 1)
            FloatItem.ROTACAO -> f(v.rpm, 0)
            FloatItem.TEMPERATURA -> f(v.coolantC, 0)
            FloatItem.BATERIA -> f(v.batteryV, 1)
        }
    }

    /** Temperatura alta ou bateria fraca: o número fica em destaque na janela. */
    fun warn(item: FloatItem, v: FloatValues): Boolean = when (item) {
        FloatItem.TEMPERATURA -> (v.coolantC ?: 0.0) >= 105
        FloatItem.BATERIA -> v.batteryV != null && (if ((v.rpm ?: 0.0) > 400) v.batteryV < 13.2 else v.batteryV < 12.0)
        else -> false
    }
}
