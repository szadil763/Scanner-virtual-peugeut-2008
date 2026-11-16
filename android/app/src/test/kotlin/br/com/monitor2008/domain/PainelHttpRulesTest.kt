package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PainelHttpRulesTest {

    @Test fun parsesValidRequestLineAndCaseInsensitiveHeaders() {
        val raw = "POST /comando?t=123 HTTP/1.1\r\n" +
            "Host: 127.0.0.1:8080\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: 42\r\n\r\n"
        val head = PainelHttpRules.parseRequestHead(raw)!!
        assertEquals("POST", head.method)
        assertEquals("/comando", head.path)
        assertEquals("t=123", head.query)
        assertEquals("application/json; charset=utf-8", head.headers["content-type"])
        assertEquals("42", head.headers["content-length"])
    }

    @Test fun rejectsMalformedOrTraversalRequestTargets() {
        assertNull(PainelHttpRules.parseRequestHead(""))
        assertNull(PainelHttpRules.parseRequestHead("BROKEN"))
        assertNull(PainelHttpRules.parseRequestHead("GET ../etc/passwd HTTP/1.1\r\n"))
        assertNull(PainelHttpRules.parseRequestHead("GET /painel/../segredo HTTP/1.1\r\n"))
        assertNull(PainelHttpRules.parseRequestHead("123 /estado HTTP/1.1\r\n"))
    }

    @Test fun validatesContentLengthBounds() {
        assertEquals(ContentLength.Valid(0), PainelHttpRules.parseContentLength(emptyMap(), 300_000))
        assertEquals(ContentLength.Valid(512), PainelHttpRules.parseContentLength(mapOf("content-length" to "512"), 300_000))
        assertEquals(ContentLength.TooLarge, PainelHttpRules.parseContentLength(mapOf("content-length" to "300001"), 300_000))
        assertEquals(ContentLength.Invalid, PainelHttpRules.parseContentLength(mapOf("content-length" to "-5"), 300_000))
        assertEquals(ContentLength.Invalid, PainelHttpRules.parseContentLength(mapOf("content-length" to "abc"), 300_000))
    }

    @Test fun allowsNativeAndLocalOriginsWhileBlockingExternalSites() {
        assertTrue(PainelHttpRules.isAllowedLocalOrigin(null, null))
        assertTrue(PainelHttpRules.isAllowedLocalOrigin("http://127.0.0.1:8080", null))
        assertTrue(PainelHttpRules.isAllowedLocalOrigin("http://localhost:8080", null))
        assertTrue(PainelHttpRules.isAllowedLocalOrigin("http://192.168.43.1:8080", null))
        assertTrue(PainelHttpRules.isAllowedLocalOrigin("http://10.12.38.252:8080", null))
        assertTrue(PainelHttpRules.isAllowedLocalOrigin("http://172.20.10.2:8080", null))
        assertTrue(PainelHttpRules.isAllowedLocalOrigin(null, "http://127.0.0.1:8080/painel"))

        assertFalse(PainelHttpRules.isAllowedLocalOrigin("null", null))
        assertFalse(PainelHttpRules.isAllowedLocalOrigin("https://evil.example", null))
        assertFalse(PainelHttpRules.isAllowedLocalOrigin("http://172.32.0.1:8080", null))
        assertFalse(PainelHttpRules.isAllowedLocalOrigin(null, "https://phishing.site/form.html"))
        assertFalse(PainelHttpRules.isAllowedLocalOrigin("javascript:alert(1)", null))
    }

    @Test fun checksJsonContentTypeStrictly() {
        assertTrue(PainelHttpRules.isJsonContentType("application/json"))
        assertTrue(PainelHttpRules.isJsonContentType("Application/JSON; charset=utf-8"))
        assertFalse(PainelHttpRules.isJsonContentType(null))
        assertFalse(PainelHttpRules.isJsonContentType("text/plain"))
        assertFalse(PainelHttpRules.isJsonContentType("application/x-www-form-urlencoded"))
        assertFalse(PainelHttpRules.isJsonContentType("multipart/form-data; boundary=abc"))
    }

    @Test fun normalizesValidPanelAddressesAndRejectsInvalidOnes() {
        assertEquals("10.12.38.252:8080", PainelHttpRules.normalizePainelAddress("10.12.38.252"))
        assertEquals("192.168.43.1:9090", PainelHttpRules.normalizePainelAddress("http://192.168.43.1:9090/"))
        assertNull(PainelHttpRules.normalizePainelAddress("   "))
        assertNull(PainelHttpRules.normalizePainelAddress("https://192.168.43.1:8080"))
        assertNull(PainelHttpRules.normalizePainelAddress("192.168.43.1/painel"))
        assertNull(PainelHttpRules.normalizePainelAddress("user@192.168.43.1"))
        assertNull(PainelHttpRules.normalizePainelAddress("192.168.43.1:99999"))
        assertNull(PainelHttpRules.normalizePainelAddress("192.168.43.1:0"))
    }
}
