package fi.aalto.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStationSelectionTest {
    @Test
    fun eachSearchResultResolvesToItsOwnStableStationId() {
        val baseIds = StationCatalog.allStations.map { it.id }
        val searches = listOf("Radio Rock", "YleX")

        searches.forEach { query ->
            val result = StationCatalog.search(query).single()
            val resolved = StationCatalog.stationById(result.id)

            assertTrue(result.id in baseIds)
            assertEquals(result.id, resolved?.id)
            assertEquals(result.name, resolved?.name)
            assertEquals(result.preferredStreamUrl, resolved?.preferredStreamUrl)
        }

        val radioRock = StationCatalog.search("Radio Rock").single()
        assertNotEquals(StationCatalog.DEFAULT_STATION_ID, radioRock.id)
        assertEquals("radio-rock", radioRock.id)
        assertEquals(
            "https://aud-stream-radiorock.nm-elemental.nelonenmedia.fi/playlist.m3u8",
            radioRock.preferredStreamUrl
        )
    }

    @Test
    fun unknownCatalogIdentityDoesNotFallbackToTheDefaultStation() {
        val catalogId = "radio-browser:radio-rock-uuid"

        assertEquals(null, StationCatalog.stationById(catalogId))
        assertNotEquals(
            StationCatalog.DEFAULT_STATION_ID,
            StationCatalog.stationById(catalogId)?.id
        )
    }

    @Test
    fun filteringAndReorderingDoNotChangeResultIdentity() {
        val results = StationCatalog.search("Radio")
        val reordered = results.reversed()

        assertEquals(
            reordered.map { it.id },
            reordered.map { StationCatalog.stationById(it.id)?.id }
        )
    }
}
