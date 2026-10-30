package br.com.monitor2008.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class DtcInfoTest {

    @Test fun knownCodeIsExplained() = assertTrue(DtcInfo.describe("p0171").startsWith("Mistura pobre"))

    @Test fun manufacturerCode() = assertTrue(DtcInfo.describe("P1336").contains("fabricante"))

    @Test fun networkCode() = assertTrue(DtcInfo.describe("U0100").contains("comunicação"))
}
