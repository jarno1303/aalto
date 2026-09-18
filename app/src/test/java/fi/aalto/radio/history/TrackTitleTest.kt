package fi.aalto.radio.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackTitleTest {

    @Test
    fun `combines artist and title`() {
        assertEquals(
            "Nightwish – Nemo",
            TrackTitle.format(title = "Nemo", artist = "Nightwish", stationTitle = "Radio Rock", stationDetails = null)
        )
    }

    @Test
    fun `leaves out an artist already in the title`() {
        assertEquals(
            "Nightwish - Nemo",
            TrackTitle.format(title = "Nightwish - Nemo", artist = "Nightwish", stationTitle = null, stationDetails = null)
        )
    }

    @Test
    fun `ignores metadata that only repeats the station`() {
        assertNull(
            TrackTitle.format(title = "Radio Rock", artist = null, stationTitle = "Radio Rock", stationDetails = null)
        )
    }

    @Test
    fun `ignores a url as the title`() {
        assertNull(
            TrackTitle.format(title = "https://example.fi/stream", artist = null, stationTitle = null, stationDetails = null)
        )
    }

    @Test
    fun `strips a now playing prefix`() {
        assertEquals("Abba – Waterloo", TrackTitle.forHistory("Now Playing: Abba – Waterloo", "Radio Nova"))
        assertEquals("Abba – Waterloo", TrackTitle.forHistory("NYT SOI - Abba – Waterloo", "Radio Nova"))
    }

    @Test
    fun `collapses whitespace and quotes`() {
        assertEquals("Abba – Waterloo", TrackTitle.forHistory("  \"Abba –   Waterloo\"  ", null))
    }

    @Test
    fun `refuses adverts, separators and the station name`() {
        assertNull(TrackTitle.forHistory("Advertisement", null))
        assertNull(TrackTitle.forHistory("mainoskatko", null))
        assertNull(TrackTitle.forHistory("- - -", null))
        assertNull(TrackTitle.forHistory("Radio Nova", "Radio Nova"))
        assertNull(TrackTitle.forHistory("ab", null))
        assertNull(TrackTitle.forHistory(null, null))
    }

    @Test
    fun `refuses a promo line with an address`() {
        assertNull(TrackTitle.forHistory("Kuuntele www.radionova.fi", "Radio Nova"))
    }

    @Test
    fun `spots the same song repeating on the same station`() {
        assertEquals(
            true,
            TrackTitle.isRepeat("fi-nova", "Abba – Waterloo", "fi-nova", "abba – waterloo")
        )
        assertEquals(
            false,
            TrackTitle.isRepeat("fi-nova", "Abba – Waterloo", "fi-rock", "Abba – Waterloo")
        )
        assertEquals(
            false,
            TrackTitle.isRepeat(null, null, "fi-nova", "Abba – Waterloo")
        )
    }
}
