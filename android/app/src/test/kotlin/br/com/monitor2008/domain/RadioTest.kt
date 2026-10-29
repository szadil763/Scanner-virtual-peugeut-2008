package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioTest {

    private fun st(id: String, name: String, url: String = "https://x/$id", pop: Int = 0) =
        RadioStation(id, name, url, popularity = pop)

    @Test fun searchUrlEncodesNameAndState() {
        val u = RadioCatalog.searchUrl("de1", name = "jovem pan", state = "São Paulo", limit = 20)
        assertTrue(u.startsWith("https://de1.api.radio-browser.info/json/stations/search?countrycode=BR"))
        assertTrue(u.contains("name=jovem+pan"))
        assertTrue(u.contains("state=S%C3%A3o+Paulo"))
        assertTrue(u.contains("limit=20"))
        assertFalse(RadioCatalog.searchUrl("de1", name = "  ").contains("name="))
    }

    @Test fun cleanRemovesDuplicatesAndBrokenLinks() {
        val list = listOf(
            st("1", "Rádio Jovem Pan FM", pop = 10),
            st("2", "radio  jovem pan fm ", pop = 50),
            st("3", "CBN", url = "ftp://nada"),
            st("4", "   "),
            st("5", "Band FM"),
        )
        val c = RadioCatalog.clean(list)
        assertEquals(listOf("2", "5"), c.map { it.id })
        assertEquals("radio jovem pan fm", c.first().name)
    }

    @Test fun mergeKeepsFirstListOrder() {
        val m = RadioCatalog.merge(listOf(st("a", "Alpha FM")), listOf(st("b", "Band FM"), st("c", "Alpha FM")))
        assertEquals(listOf("Alpha FM", "Band FM"), m.map { it.name })
    }

    @Test fun stepWrapsAround() {
        val l = listOf(st("a", "A"), st("b", "B"), st("c", "C"))
        assertEquals("a", RadioCatalog.step(l, "c", forward = true)?.id)
        assertEquals("c", RadioCatalog.step(l, "a", forward = false)?.id)
        assertEquals("a", RadioCatalog.step(l, "zz", forward = true)?.id)
        assertNull(RadioCatalog.step(emptyList(), "a", forward = true))
    }

    @Test fun subtitleShowsStateAndTwoTags() {
        val s = RadioStation("1", "X", "https://x", tags = "pop, rock,news", state = "São Paulo")
        assertEquals("São Paulo · pop, rock", s.subtitle)
    }
}
