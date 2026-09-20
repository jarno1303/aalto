package fi.aalto.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenresTest {

    private fun station(vararg tags: String) = RadioStation(
        id = "test",
        name = "Test",
        description = "",
        initials = "T",
        logoColorArgb = 0xFF000000,
        streamUrl = "https://example.com/stream",
        countryCode = "FI",
        tags = tags.toList(),
        category = ""
    )

    private fun genre(label: String) = AllGenres.first { it.label == label }

    @Test
    fun everyLabelIsUnique() {
        assertEquals(AllGenres.size, AllGenres.map { it.label }.toSet().size)
    }

    @Test
    fun mainGenresAreTheSearchChipsPlusDecades() {
        val mains = GenreGroups.map { it.main.label }
        assertTrue(DiscoveryCategories.all { it.label in mains })
        assertTrue("Vuosikymmenet" in mains)
    }

    @Test
    fun classicRockNeedsBothWords() {
        assertTrue(genre("Klassinen rock").matches(station("classic rock")))
        assertFalse(genre("Klassinen rock").matches(station("classical")))
        assertFalse(genre("Klassinen rock").matches(station("rock")))
    }

    @Test
    fun finnishLettersAreMatched() {
        assertTrue(genre("Iskelmä & schlager").matches(station("Iskelmä")))
        assertTrue(genre("Iskelmä & schlager").matches(station("schlager")))
    }

    @Test
    fun decades() {
        assertTrue(genre("80-luku").matches(station("80s", "pop")))
        assertTrue(genre("Vuosikymmenet").matches(station("90s")))
        assertFalse(genre("80-luku").matches(station("90s")))
    }

    @Test
    fun hipHopWrittenEitherWay() {
        assertTrue(genre("Hip-hop").matches(station("hip-hop")))
        assertTrue(genre("Hip-hop").matches(station("hiphop")))
        assertTrue(genre("Drum & bass").matches(station("drum and bass")))
    }
}
