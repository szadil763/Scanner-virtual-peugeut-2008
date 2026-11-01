package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefuelMathTest {
    @Test fun litersFromMoney() {
        assertEquals(16.367, RefuelMath.litersFromAmount(100.0, 6.11)!!, 1e-9)
        assertEquals(40.0, RefuelMath.litersFromAmount(250.0, 6.25)!!, 1e-9)
    }

    @Test fun missingOrInvalidValues() {
        assertNull(RefuelMath.litersFromAmount(null, 6.0))
        assertNull(RefuelMath.litersFromAmount(100.0, 0.0))
        assertNull(RefuelMath.litersFromAmount(-5.0, 6.0))
    }
}
