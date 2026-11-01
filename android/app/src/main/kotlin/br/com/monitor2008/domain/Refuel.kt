package br.com.monitor2008.domain

import kotlin.math.round

/** Contas do abastecimento digitado pelo dono. */
object RefuelMath {
    /**
     * Litros a partir do valor pago e do preço por litro da bomba, com 3 casas (como a bomba mostra).
     * Null se algum valor faltar ou não for positivo.
     */
    fun litersFromAmount(amount: Double?, pricePerLiter: Double?): Double? {
        if (amount == null || pricePerLiter == null || amount <= 0 || pricePerLiter <= 0) return null
        return round(amount / pricePerLiter * 1000) / 1000
    }
}
