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

    @Test fun sleepsFifteenMinutesAfterTheCar() {
        val t0 = 1_000_000L
        assertTrue(WakeRules.awake(t0 + 60 * 60_000L, engineRunning = true, lastActivityMs = t0))
        assertTrue(WakeRules.awake(t0 + 14 * 60_000L, engineRunning = false, lastActivityMs = t0))
        assertFalse(WakeRules.awake(t0 + 15 * 60_000L, engineRunning = false, lastActivityMs = t0))
    }

    @Test fun adapterConnectedIsNotEngineRunning() {
        // O adaptador responde com o carro desligado (rotação 0): não conta como carro ligado.
        assertFalse(WakeRules.engineRunning(connected = true, rpm = 0.0))
        assertFalse(WakeRules.engineRunning(connected = true, rpm = null))
        assertFalse(WakeRules.engineRunning(connected = false, rpm = 800.0))
        assertTrue(WakeRules.engineRunning(connected = true, rpm = 780.0))
    }

    @Test fun phoneListensOnlyInTheCar() {
        assertTrue(WakeRules.inCar(phone = false, engineRunning = false, bluetoothAudio = false))
        assertFalse(WakeRules.inCar(phone = true, engineRunning = false, bluetoothAudio = false))
        assertTrue(WakeRules.inCar(phone = true, engineRunning = false, bluetoothAudio = true))
        assertTrue(WakeRules.inCar(phone = true, engineRunning = true, bluetoothAudio = false))
    }

    @Test fun listensOnlyWhenFree() {
        assertTrue(WakeRules.shouldListen(enabled = true, modelReady = true, micAllowed = true, assistantBusy = false, inCall = false))
        assertFalse(WakeRules.shouldListen(enabled = true, modelReady = true, micAllowed = true, assistantBusy = true, inCall = false))
        assertFalse(WakeRules.shouldListen(enabled = true, modelReady = true, micAllowed = true, assistantBusy = false, inCall = true))
        assertFalse(WakeRules.shouldListen(enabled = true, modelReady = false, micAllowed = true, assistantBusy = false, inCall = false))
        assertFalse(WakeRules.shouldListen(enabled = false, modelReady = true, micAllowed = true, assistantBusy = false, inCall = false))
    }
}
