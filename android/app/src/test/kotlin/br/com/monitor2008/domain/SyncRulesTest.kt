package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {

    @Test fun validatesInviteEmailsProperly() {
        assertTrue(SyncRules.isValidEmail(" Motorista@Gmail.com "))
        assertTrue(SyncRules.isValidEmail("pai.nome+carro@dominio.com.br"))

        assertFalse(SyncRules.isValidEmail(""))
        assertFalse(SyncRules.isValidEmail("sem-arroba"))
        assertFalse(SyncRules.isValidEmail("a@b"))
        assertFalse(SyncRules.isValidEmail("user@.com"))
        assertFalse(SyncRules.isValidEmail("user..x@gmail.com"))
        assertFalse(SyncRules.isValidEmail("user @gmail.com"))
    }

    @Test fun addsNormalizedUniqueInvitesWithoutDuplicates() {
        val result = SyncRules.addInvite(listOf("Pai@Gmail.com", "invalido", "mae@uol.com.br"), "  pai@gmail.com ")
        assertEquals(listOf("pai@gmail.com", "mae@uol.com.br"), result)

        val added = SyncRules.addInvite(result!!, "Filho@Hotmail.com")
        assertEquals(listOf("pai@gmail.com", "mae@uol.com.br", "filho@hotmail.com"), added)

        assertNull(SyncRules.addInvite(added!!, "invalido@"))
    }

    @Test fun preservesDirtyStateWhenRevisionAdvancesInFlight() {
        assertFalse(SyncRules.hasPendingChanges(currentRevision = 0L, syncedRevision = 0L))
        assertTrue(SyncRules.hasPendingChanges(currentRevision = 1L, syncedRevision = 0L))

        val synced = SyncRules.nextSyncedRevision(sentRevision = 5L, prevSyncedRevision = 3L)
        assertEquals(5L, synced)
        // Sem novas mudanças durante o envio: fica limpo.
        assertFalse(SyncRules.hasPendingChanges(currentRevision = 5L, syncedRevision = synced))
        // Mudança ocorreu enquanto o envio da revisão 5 ainda estava em curso: segue pendente.
        assertTrue(SyncRules.hasPendingChanges(currentRevision = 6L, syncedRevision = synced))
    }
}
