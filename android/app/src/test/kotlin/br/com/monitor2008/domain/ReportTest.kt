package br.com.monitor2008.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {

    private fun input(stored: List<String>? = listOf("P0171")) = ReportInput(
        dateText = "29/10/2026",
        appVersion = "0.1.16",
        carKm = 85432.0,
        tripsRecorded = 3,
        storedDtcs = stored,
        pendingDtcs = emptyList(),
        health = listOf(HealthItem("mix", "Mistura", HealthStatus.ALERTA, "+31%", "pobre")),
        maint = listOf(MaintCatalog.items.first { it.id == "correia" } to null),
        tripKm = 120.0,
        tripKmPerL = 8.4,
        fuelName = "Gasolina E32",
        tips = listOf(Tip(TipLevel.ALTA, "Falha armazenada P0171", "verificar"), Tip(TipLevel.BAIXA, "Dica geral", "x")),
    )

    @Test fun hasTheEssentials() {
        val r = Report.build(input())
        assertTrue(r.contains("85.432 km"))
        assertTrue(r.contains("P0171: Mistura pobre"))
        assertTrue(r.contains("Mistura: +31% (ALERTA)"))
        assertTrue(r.contains("8,4 km/l em 120 km"))
        assertTrue(r.contains("Correia dentada e tensor: última troca não informada"))
        assertTrue(r.contains("Falha armazenada P0171: verificar"))
        assertFalse(r.contains("Dica geral")) // dicas gerais ficam de fora
    }

    @Test fun codesNotRead() = assertTrue(Report.build(input(stored = null)).contains("Não lidas"))
}
