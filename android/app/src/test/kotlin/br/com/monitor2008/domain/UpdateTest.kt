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

    @Test fun signingKeyComesFromTheNotes() {
        assertEquals(1, UpdateRules.signingKey("Versão 0.1.170. versionCode: 170"))
        assertEquals(2, UpdateRules.signingKey("Versão 0.1.171. versionCode: 171. chave: 2"))
        assertEquals(2, UpdateRules.parse("Versão 0.1.171 versionCode: 171 chave: 2", url)?.signingKey)
        assertEquals(1, UpdateRules.parse("versionCode: 170", url)?.signingKey)
    }

    @Test fun otherKeyNeedsReinstall() {
        val nova = UpdateRules.parse("versionCode: 171 chave: 2", url)
        val antiga = UpdateRules.parse("versionCode: 171", url)
        assertTrue(UpdateRules.needsReinstall(nova, installedKey = 1))
        assertFalse(UpdateRules.needsReinstall(nova, installedKey = 2))
        assertFalse(UpdateRules.needsReinstall(antiga, installedKey = 1))
        assertFalse(UpdateRules.needsReinstall(null, installedKey = 1))
    }

    @Test fun releasePageForTheBrowser() {
        assertEquals("https://github.com/o/r/releases/tag/latest", UpdateRules.releasePage("o", "r", UpdateChannel.TESTE))
    }

    @Test fun channelPointsToItsRelease() {
        assertEquals("https://api.github.com/repos/o/r/releases/tags/estavel", UpdateRules.releaseUrl("o", "r", UpdateChannel.ESTAVEL))
        assertEquals("https://api.github.com/repos/o/r/releases/tags/latest", UpdateRules.releaseUrl("o", "r", UpdateChannel.TESTE))
    }
}
