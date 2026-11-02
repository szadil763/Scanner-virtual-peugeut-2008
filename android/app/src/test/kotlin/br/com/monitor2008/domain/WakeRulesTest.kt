package br.com.monitor2008.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeRulesTest {
    @Test fun detectsTheWord() {
        assertTrue(WakeRules.isWake("{\n  \"text\" : \"monitor\"\n}"))
        assertTrue(WakeRules.isWake("{\"text\": \"[unk] monitor\"}"))
        assertFalse(WakeRules.isWake("{\"text\" : \"\"}"))
        assertFalse(WakeRules.isWake("{\"text\" : \"[unk]\"}"))
        assertFalse(WakeRules.isWake("{\"partial\" : \"monitor\"}"))
        assertFalse(WakeRules.isWake(null))
    }

    @Test fun partialIsFaster() {
        assertTrue(WakeRules.isWakePartial("{\n  \"partial\" : \"monitor\"\n}"))
        assertFalse(WakeRules.isWakePartial("{\"partial\" : \"[unk] monitor [unk]\"}"))
        assertFalse(WakeRules.isWakePartial("{\"partial\" : \"\"}"))
        assertFalse(WakeRules.isWakePartial("{\"text\" : \"monitor\"}"))
    }

    @Test fun listensOnlyWhenFree() {
        assertTrue(WakeRules.shouldListen(enabled = true, modelReady = true, micAllowed = true, assistantBusy = false, inCall = false))
        assertFalse(WakeRules.shouldListen(enabled = true, modelReady = true, micAllowed = true, assistantBusy = true, inCall = false))
        assertFalse(WakeRules.shouldListen(enabled = true, modelReady = true, micAllowed = true, assistantBusy = false, inCall = true))
        assertFalse(WakeRules.shouldListen(enabled = true, modelReady = false, micAllowed = true, assistantBusy = false, inCall = false))
        assertFalse(WakeRules.shouldListen(enabled = false, modelReady = true, micAllowed = true, assistantBusy = false, inCall = false))
    }
}
