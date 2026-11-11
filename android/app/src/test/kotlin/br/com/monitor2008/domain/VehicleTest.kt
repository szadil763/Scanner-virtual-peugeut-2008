package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTest {

    private fun trip() = TripHealth(startMs = 0, endMs = 0, km = 20.0, runningMin = 30.0, coolantMaxC = 90.0)

    private fun input(v: Vehicle, kmL: Double = 10.0, health: List<HealthItem> = emptyList()) =
        AdvisorInput(List(6) { trip() }, health, emptyList(), kmL, 200.0, 0.30, true, vehicle = v)

    @Test fun profilesAreFoundById() {
        assertEquals(Vehicles.C4_LOUNGE_20, Vehicles.byId("citroen-c4-lounge-20"))
        assertNull(Vehicles.byId("nada"))
        assertEquals(Vehicles.all.size, Vehicles.all.map { it.id }.toSet().size)
    }

    @Test fun everyProfileHasNotesForTheModelSpecificItems() {
        Vehicles.all.forEach { v ->
            listOf("mix", "idle", "cool", "o2", "cat", "tbi", "map", "bat").forEach { id ->
                assertTrue("${v.id} sem nota para $id", v.notes[id]?.isNotBlank() == true)
            }
        }
    }

    @Test fun c4GetsItsOwnNotesAndGearboxTip() {
        val item = HealthItem("idle", "Marcha lenta", HealthStatus.ALERTA, "150 rpm", "Instável.")
        val tips = Advisor.tips(input(Vehicles.C4_LOUNGE_20, health = listOf(item)))
        assertTrue(tips.first { it.title.startsWith("Marcha lenta") }.text.contains("EW10"))
        assertFalse(tips.any { it.text.contains("EC5") })
        assertTrue(tips.first { it.title == "Câmbio" }.text.contains("C4 Lounge"))
    }

    @Test fun consumptionLimitDependsOnTheCar() {
        // 7,5 km/l na gasolina: alto para o 2008 1.6 (limite 8), normal para o C4 2.0 (limite 7).
        assertTrue(Advisor.tips(input(Vehicles.PEUGEOT_2008, kmL = 7.5)).any { it.title.startsWith("Consumo alto") })
        assertFalse(Advisor.tips(input(Vehicles.C4_LOUNGE_20, kmL = 7.5)).any { it.title.startsWith("Consumo alto") })
    }

    @Test fun chainEngineHasNoTimingBeltItem() {
        assertFalse(MaintCatalog.itemsFor(Vehicles.NISSAN_TIIDA_18).any { it.id == "correia" })
        assertTrue(MaintCatalog.itemsFor(Vehicles.PEUGEOT_2008).any { it.id == "correia" })
        assertTrue(Vehicles.NISSAN_TIIDA_18.periodicTip.contains("corrente"))
    }

    @Test fun reportNamesTheCar() {
        val r = Report.build(
            ReportInput("29/10/2026", "0.1.24", null, 0, emptyList(), emptyList(), emptyList(), emptyList(), 0.0, null,
                "Gasolina E30", emptyList(), Vehicles.C4_LOUNGE_20),
        )
        assertTrue(r.contains("Citroën C4 Lounge 2.0 16V flex (EW10)"))
    }

    @Test fun wmiGivesOnlyTheRegisteredProfileOfThatFactory() {
        assertEquals(Vehicles.PEUGEOT_2008, Vehicles.candidateByWmi("936CMNFP0GB000001"))
        assertEquals(Vehicles.C4_LOUNGE_20, Vehicles.candidateByWmi("8BCCMRFJ0GB000001"))
        assertEquals(Vehicles.NISSAN_TIIDA_18, Vehicles.candidateByWmi("3N1BC1AS0BK000001"))
        assertNull(Vehicles.candidateByWmi("9BWZZZ377VT004251")) // Volkswagen: sem perfil cadastrado
        assertNull(Vehicles.candidateByWmi(null))
        assertNull(Vehicles.candidateByWmi("936CMNFP0GB00000")) // 16 caracteres
    }

    @Test fun vinMustHave17CharactersWithoutIOQ() {
        assertTrue(Vehicles.validVin("936CMNFP0GB000001"))
        assertFalse(Vehicles.validVin(null))
        assertFalse(Vehicles.validVin("936CMNFP0GB00001")) // 16
        assertFalse(Vehicles.validVin("936CMNFP0GBO00001")) // tem a letra O
        assertFalse(Vehicles.validVin("936cmnfp0gb000001")) // minúsculas não valem (o app converte antes)
    }

    @Test fun carsWithoutProfileGetTheirOwnKeyAndAGenericProfile() {
        val key = Vehicles.customKey("9BWZZZ377VT004251")
        assertEquals("vin-9BWZZZ377VT004251", key)
        assertTrue(Vehicles.isCustomKey(key))
        assertNull(Vehicles.byId(key)) // o app usa o perfil genérico nesse caso
        assertEquals("Carro 004251", Vehicles.nameOf(key))
        assertEquals("Peugeot 2008", Vehicles.nameOf(Vehicles.PEUGEOT_2008.id))
        assertFalse(Vehicles.isCustomKey(Vehicles.PEUGEOT_2008.id))
        assertFalse(Vehicles.all.contains(Vehicles.AUTO))
    }
}
