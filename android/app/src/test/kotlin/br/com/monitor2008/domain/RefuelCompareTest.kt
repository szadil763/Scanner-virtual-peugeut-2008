package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefuelCompareTest {
    private fun trip(start: Long) = TripHealth(startMs = start, endMs = start + 1_000, km = 10.0, runningMin = 20.0)
    private val trips = (1..6).map { trip(it * 100L) }
    private val item = HealthItem("cat", "Catalisador", HealthStatus.ALERTA, "95%", "",
        series = listOf(96.0, 94.0, null, 95.0, 80.0, 70.0), unit = "%")

    @Test fun marksTheFirstTripAfterTheRefuelAndCompares() {
        // Abasteceu entre a 4ª e a 5ª viagem.
        val r = RefuelCompare.apply(listOf(item), trips, refuelMs = 450L).single()
        assertEquals(4, r.refuelIndex)
        assertEquals(95.0, r.beforeRefuel!!, 1e-9)
        assertEquals(75.0, r.afterRefuel!!, 1e-9)
        assertEquals(2, r.tripsAfterRefuel)
    }

    @Test fun nothingToCompare() {
        // Sem abastecimento, abastecimento depois da última viagem ou antes de todo o gráfico.
        assertNull(RefuelCompare.apply(listOf(item), trips, null).single().refuelIndex)
        assertNull(RefuelCompare.apply(listOf(item), trips, 10_000L).single().refuelIndex)
        assertNull(RefuelCompare.apply(listOf(item), trips, 50L).single().refuelIndex)
    }
}
