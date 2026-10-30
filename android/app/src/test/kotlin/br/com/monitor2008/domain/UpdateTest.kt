package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    private val url = "https://github.com/x/y/releases/download/latest/monitor-2008.apk"

    @Test fun readsVersionCodeLine() {
        val v = UpdateRules.parse("Versão 0.1.31. APK de teste... versionCode: 31", url)
        assertEquals(31, v?.versionCode)
        assertEquals("0.1.31", v?.versionName)
    }

    @Test fun oldNotesUseTheVersionName() {
        assertEquals(24, UpdateRules.versionCode("Versão 0.1.24. APK de teste gerado em 29/10/2026"))
    }

    @Test fun missingDataGivesNull() {
        assertNull(UpdateRules.parse("sem versão", url))
        assertNull(UpdateRules.parse("versionCode: 3", null))
        assertNull(UpdateRules.parse("versionCode: 3", "http://inseguro/apk"))
    }

    @Test fun onlyNewerCounts() {
        val v = UpdateRules.parse("versionCode: 30", url)
        assertTrue(UpdateRules.isNewer(v, 29))
        assertFalse(UpdateRules.isNewer(v, 30))
        assertFalse(UpdateRules.isNewer(null, 1))
    }

    @Test fun eachDeviceGetsItsApk() {
        assertEquals("monitor-2008.apk", UpdateRules.apkName(phone = false))
        assertEquals("monitor-2008-celular.apk", UpdateRules.apkName(phone = true))
    }

    @Test fun channelPointsToItsRelease() {
        assertEquals("https://api.github.com/repos/o/r/releases/tags/estavel", UpdateRules.releaseUrl("o", "r", UpdateChannel.ESTAVEL))
        assertEquals("https://api.github.com/repos/o/r/releases/tags/latest", UpdateRules.releaseUrl("o", "r", UpdateChannel.TESTE))
    }
}
