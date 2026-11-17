package br.com.monitor2008.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionTest {
    @Test fun findsTheAndroidAutoReceiversOfTheHeadUnit() {
        assertTrue(Projection.isProjectionApp("com.zjinnova.zlink", "ZLink"))
        assertTrue(Projection.isProjectionApp("com.example.box", "Android Auto"))
        assertTrue(Projection.isProjectionApp("cn.manstep.phonemirrorBox", "AutoKit"))
        assertTrue(Projection.isProjectionApp("com.xyz.link", "CarPlay"))
    }

    @Test fun ignoresMapsAndThePhoneAndroidAutoApp() {
        assertFalse(Projection.isProjectionApp("com.google.android.apps.maps", "Maps"))
        assertFalse(Projection.isProjectionApp("com.waze", "Waze"))
        assertFalse(Projection.isProjectionApp(Projection.PHONE_ANDROID_AUTO, "Android Auto"))
    }
}
