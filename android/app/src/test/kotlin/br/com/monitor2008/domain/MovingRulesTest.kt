package br.com.monitor2008.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovingRulesTest {
    @Test fun freshReadingAboveThresholdIsMoving() =
        assertTrue(MovingRules.isMoving(speedKmh = 14.0, sampleAgeMs = 1_000))

    @Test fun staleReadingDoesNotLockTheApp() {
        // O adaptador parou de responder com o carro "a 14 km/h": depois de 10 s isso não vale mais.
        assertFalse(MovingRules.isMoving(speedKmh = 14.0, sampleAgeMs = 11_000))
        assertFalse(MovingRules.isMoving(speedKmh = 14.0, sampleAgeMs = 10 * 60_000))
    }

    @Test fun stoppedOrNoSpeedIsNotMoving() {
        assertFalse(MovingRules.isMoving(speedKmh = 0.0, sampleAgeMs = 500))
        assertFalse(MovingRules.isMoving(speedKmh = 0.4, sampleAgeMs = 500)) // ruído
        assertFalse(MovingRules.isMoving(speedKmh = null, sampleAgeMs = 500))
    }

    @Test fun clockGoingBackwardsIsNotMoving() =
        assertFalse(MovingRules.isMoving(speedKmh = 30.0, sampleAgeMs = -5_000))
}
