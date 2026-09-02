package fi.aalto.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationCatalogTest {
    @Test
    fun stationIdentityDoesNotDependOnStreamUrl() {
        val station = StationCatalog.stationById("yle-klassinen")

        requireNotNull(station)
        assertEquals("yle-klassinen", station.id)
        assertNotEquals(station.streamUrl, station.id)
    }

    @Test
    fun searchMatchesStationNameAndTags() {
        val rockResults = StationCatalog.search("rock")
        val cultureResults = StationCatalog.search("kulttuuri")

        assertTrue(rockResults.any { it.id == "radio-rock" })
        assertTrue(cultureResults.any { it.id == "yle-radio-1" })
    }

    @Test
    fun favoriteToggleUsesStableStationIds() {
        val added = FavoriteIds.toggle(emptySet(), "radio-helsinki")
        val removed = FavoriteIds.toggle(added, "radio-helsinki")

        assertEquals(setOf("radio-helsinki"), added)
        assertEquals(emptySet<String>(), removed)
    }

    @Test
    fun radioBrowserStationUuidCanMapToStableStationId() {
        val stationUuid = "123e4567-e89b-12d3-a456-426614174000"
        val station = RadioStation(
            id = StationIdentity.fromRadioBrowserStationUuid(stationUuid),
            radioBrowserStationUuid = stationUuid,
            name = "Example Radio",
            description = "Example",
            initials = "EX",
            logoColorArgb = 0xFF000000,
            streamUrl = "https://example.com/live",
            countryCode = "FI",
            tags = listOf("example"),
            category = "Example"
        )
        val entity = station.toEntity(updatedAt = 100L)

        assertEquals(stationUuid, station.id)
        assertEquals(stationUuid, entity.radioBrowserStationUuid)
        assertEquals(stationUuid, entity.toDomain().id)
    }

    @Test
    fun streamUrlChangeDoesNotChangeLogicalStationIdentity() {
        val station = StationCatalog.stationById("ylex")

        requireNotNull(station)
        val changedStream = station.copy(
            streamUrl = "https://example.com/new-ylex-stream",
            preferredStreamUrl = "https://example.com/new-ylex-stream"
        )

        assertEquals(station.id, changedStream.id)
        assertNotEquals(station.streamUrl, changedStream.streamUrl)
    }
}
