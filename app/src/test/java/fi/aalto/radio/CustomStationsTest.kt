package fi.aalto.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomStationsTest {

    @Test
    fun addressesAreNormalised() {
        assertEquals("https://stream.example.fi/radio", CustomStations.normalizeUrl("  stream.example.fi/radio "))
        assertEquals("http://1.2.3.4:8000/live", CustomStations.normalizeUrl("http://1.2.3.4:8000/live"))
        assertNull(CustomStations.normalizeUrl(""))
        assertNull(CustomStations.normalizeUrl("ftp://example.fi/a.mp3"))
        assertNull(CustomStations.normalizeUrl("not an address"))
    }

    @Test
    fun streamsAreRecognised() {
        assertTrue(CustomStations.looksLikeStream("audio/mpeg", "https://a.fi/live", hasIcyHeaders = false))
        assertTrue(CustomStations.looksLikeStream("application/vnd.apple.mpegurl", "https://a.fi/x", false))
        assertTrue(CustomStations.looksLikeStream("application/octet-stream", "https://a.fi/radio.pls", false))
        assertTrue(CustomStations.looksLikeStream("text/html", "https://a.fi/", hasIcyHeaders = true))
        assertFalse(CustomStations.looksLikeStream("text/html; charset=utf-8", "https://a.fi/radio", false))
    }

    @Test
    fun nameFallsBackToStreamNameThenHost() {
        assertEquals("Radio Testi", CustomStations.defaultName("https://www.example.fi/x", "Radio Testi"))
        assertEquals("example.fi", CustomStations.defaultName("https://www.example.fi/x", null))
    }

    @Test
    fun customStationsAreRecognisedById() {
        val station = CustomStations.station(CustomStations.newId(), "Oma Radio", "https://a.fi/live")
        assertTrue(CustomStations.isCustom(station.id))
        assertEquals("OR", station.initials)
        assertFalse(CustomStations.isCustom("radio-rock"))
    }
}
