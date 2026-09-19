package fi.aalto.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationDialMathTest {
    private val detentPx = 112f

    @Test
    fun dragMovesDirectlyToAdjacentLogicalStationsAndWraps() {
        assertEquals(3, circularStationIndex(advanceDialDrag(2, 0f, -detentPx, detentPx).virtualPosition, 5))
        assertEquals(1, circularStationIndex(advanceDialDrag(2, 0f, detentPx, detentPx).virtualPosition, 5))
        assertEquals(0, circularStationIndex(advanceDialDrag(4, 0f, -detentPx, detentPx).virtualPosition, 5))
        assertEquals(4, circularStationIndex(advanceDialDrag(0, 0f, detentPx, detentPx).virtualPosition, 5))
    }

    @Test
    fun flingTravelIsCappedAtFourDetents() {
        assertEquals(6, dialSnapTargetPosition(2, 0f, -20_000f, detentPx))
        assertEquals(-2, dialSnapTargetPosition(2, 0f, 20_000f, detentPx))
        assertEquals(3, dialSnapTargetPosition(2, 0f, -20_000f, detentPx, maxTravelDetents = 1))
    }

    @Test
    fun smallMovementHasNoDetentAndCrossingTwoHasTwo() {
        val small = advanceDialDrag(2, 0f, -detentPx * 0.2f, detentPx)
        assertEquals(0, small.crossedDetents)
        assertEquals(2, small.virtualPosition)

        val two = advanceDialDrag(2, 0f, -detentPx * 2f, detentPx)
        assertEquals(2, two.crossedDetents)
        assertEquals(4, two.virtualPosition)
    }

    @Test
    fun oneAndTwoStationLayoutsDoNotInventThreeStationNeighbors() {
        assertEquals(intArrayOf(0).toList(), dialVisibleSlotOffsets(1, settling = false).toList())
        assertEquals(intArrayOf(0, 1).toList(), dialVisibleSlotOffsets(2, settling = false).toList())
        assertTrue(dialVisibleSlotOffsets(3, settling = false).contentEquals(intArrayOf(-1, 0, 1)))
    }

    @Test
    fun snapDurationStaysShortAndControlled() {
        assertTrue(dialSnapDurationMillis(0f, -detentPx, detentPx) in 120..220)
        assertEquals(220, dialSnapDurationMillis(0f, -detentPx * 4f, detentPx))
    }

    @Test
    fun browsingThroughIntermediateStationsCommitsOnlyTheFinalStationOnce() {
        var virtualPosition = 0 // A
        var offsetPx = 0f
        repeat(3) {
            val update = advanceDialDrag(virtualPosition, offsetPx, -detentPx, detentPx)
            virtualPosition = update.virtualPosition
            offsetPx = update.offsetPx
        }

        assertEquals(3, virtualPosition) // D; B and C were browse-only positions.
        assertEquals(0f, offsetPx)
        assertEquals(1, dialPlaybackCommitCount(0, virtualPosition))
    }

    @Test
    fun duplicateLogicalRecordsBecomeOneDialPosition() {
        val first = requireNotNull(StationCatalog.stationById("yle-radio-1")).copy(
            id = "record-1",
            radioBrowserStationUuid = "same-logical-station"
        )
        val duplicate = first.copy(id = "record-2")
        val other = requireNotNull(StationCatalog.stationById("ylex"))

        assertEquals(
            listOf("record-1", other.id),
            stationsForNowPlaying(listOf(first, duplicate, other), first).map { it.id }
        )
    }

    @Test
    fun dialUsesAllFavoritesInFavoriteOrderAndWrapsAcrossTheFullCollection() {
        val favoriteIds = listOf("ylex", "yle-radio-1", "yle-puhe", "radio-rock", "radio-suomipop")
        val favorites = favoriteIds.map { requireNotNull(StationCatalog.stationById(it)) }
        val dialStations = stationsForDial(favorites, favorites[1])

        assertEquals(favoriteIds, dialStations.map { it.id })
        var virtualPosition = 1 // Yle Radio 1
        repeat(4) {
            virtualPosition = advanceDialDrag(virtualPosition, 0f, -detentPx, detentPx).virtualPosition
        }
        assertEquals("ylex", dialStations[circularStationIndex(virtualPosition, dialStations.size)].id)
    }

    @Test
    fun dialKeepsNonFavoriteCurrentStationAndSupportsEightFavorites() {
        val favoriteStations = StationCatalog.allStations.take(3)
        val current = requireNotNull(StationCatalog.stationById("radio-rock"))
        assertEquals(
            favoriteStations.map { it.id } + current.id,
            stationsForDial(favoriteStations, current).map { it.id }
        )

        val eightFavorites = (0 until 8).map { index ->
            favoriteStations[index % favoriteStations.size].copy(
                id = "favorite-$index",
                preferredStreamUrl = "https://example.test/station-$index"
            )
        }
        assertEquals(8, stationsForDial(eightFavorites, eightFavorites[4]).size)
    }

    @Test
    fun dialTitleUsesTheSelectedStationsOwnMetadata() {
        val vega = requireNotNull(StationCatalog.stationById("yle-vega"))
        val rock = requireNotNull(StationCatalog.stationById("radio-rock"))

        // One genre in Aalto's words, not the raw tags after it (visual pass 2026-09-19).
        assertEquals("Yle Vega - Puhe & Viihde - Suomi", stationDialTitle(vega))
        assertEquals("Radio Rock - Rock - Suomi", stationDialTitle(rock))
    }

    @Test
    fun dialTitleChangesAllMetadataTogetherAndOmitsMissingFields() {
        val nova = RadioStation(
            id = "radio-browser:nova",
            radioBrowserStationUuid = "nova",
            name = "Nova",
            description = "",
            initials = "NOVA",
            logoColorArgb = 0L,
            streamUrl = "https://example.test/nova",
            countryCode = "TR",
            tags = listOf("pop"),
            category = "Pop"
        )
        val incomplete = nova.copy(category = "", tags = emptyList(), countryCode = "")

        assertEquals("Nova - Pop - Turkki", stationDialTitle(nova))
        assertEquals("Nova", stationDialTitle(incomplete))
    }
}
