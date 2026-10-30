package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorTest {

    private fun trip(min: Double = 30.0, km: Double = 20.0, coolMax: Double = 90.0) =
        TripHealth(startMs = 0, endMs = 0, km = km, runningMin = min, coolantMaxC = coolMax)

    private fun input(
        trips: List<TripHealth> = List(6) { trip() },
        health: List<HealthItem> = emptyList(),
        maint: List<Pair<MaintItem, ItemStatus?>> = emptyList(),
        kmL: Double? = 10.0,
        km: Double = 200.0,
        ethanol: Double = 0.30,
        calibrated: Boolean = true,
        stored: List<String> = emptyList(),
        pending: List<String> = emptyList(),
    ) = AdvisorInput(trips, health, maint, kmL, km, ethanol, calibrated, stored, pending)

    private fun titles(i: AdvisorInput) = Advisor.tips(i).map { it.title }

    @Test fun alwaysHasModelChecks() {
        val t = titles(input())
        assertTrue(t.contains("Câmbio"))
        assertTrue(t.contains("Verificações periódicas do modelo"))
    }

    @Test fun storedCodeIsHighPriorityAndFirst() {
        val tips = Advisor.tips(input(stored = listOf("P0301")))
        assertEquals("Falha armazenada P0301", tips.first().title)
        assertEquals(TipLevel.ALTA, tips.first().level)
        assertTrue(tips.first().text.contains("bobina"))
    }

    @Test fun healthAlertGetsModelNote() {
        val item = HealthItem("cool", "Arrefecimento", HealthStatus.ALERTA, "107 °C", "Temperatura alta.")
        val tip = Advisor.tips(input(health = listOf(item))).first()
        assertEquals(TipLevel.ALTA, tip.level)
        assertTrue(tip.text.contains("válvula termostática"))
    }

    @Test fun fuelPatternGetsFuelNote() {
        val item = HealthItem("mix", "Mistura", HealthStatus.ALERTA, "+31%", "Mistura pobre, parecida parado e andando: ...")
        assertTrue(Advisor.tips(input(health = listOf(item))).first().text.contains("aba Postos"))
    }

    @Test fun okHealthItemGivesNoTip() {
        val item = HealthItem("mix", "Mistura", HealthStatus.OK, "+2%", "ok")
        assertTrue(titles(input(health = listOf(item))).none { it.startsWith("Mistura") })
    }

    @Test fun shortTripsMeanSevereUse() =
        assertTrue(titles(input(trips = List(8) { trip(min = 6.0, km = 3.0, coolMax = 70.0) })).contains("Uso severo: muitas viagens curtas"))

    @Test fun highConsumptionForGasoline() =
        assertTrue(titles(input(kmL = 6.5)).any { it.startsWith("Consumo alto") })

    @Test fun ethanolHasLowerExpectation() =
        assertTrue(titles(input(kmL = 6.5, ethanol = 0.95)).none { it.startsWith("Consumo alto") })

    @Test fun dueMaintenanceAndUnknownBelt() {
        val belt = MaintCatalog.items.first { it.id == "correia" }
        val air = MaintCatalog.items.first { it.id == "filtro_ar" }
        val t = Advisor.tips(input(maint = listOf(air to ItemStatus(-100.0, 10, MaintState.DUE), belt to null)))
        assertTrue(t.any { it.title == "Filtro de ar do motor: vencido" && it.level == TipLevel.ALTA })
        assertTrue(t.any { it.title.startsWith("Correia dentada: confirme") })
    }

    @Test fun fewTripsSaysSo() = assertTrue(titles(input(trips = listOf(trip()))).contains("Poucos dados ainda"))
}
