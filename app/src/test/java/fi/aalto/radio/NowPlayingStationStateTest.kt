package fi.aalto.radio

import fi.aalto.radio.catalog.CatalogSource
import fi.aalto.radio.catalog.CatalogStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingStationStateTest {
    @Test
    fun selectedStationOutsideFirstFiveRemainsInTheSingleRadioList() {
        val selected = requireNotNull(StationCatalog.stationById("yle-vega"))

        val radioStations = stationsForRadioList(StationCatalog.allStations, selected)
        val selectedInList = radioStations.single { it.id == selected.id }

        assertEquals(selected.name, selectedInList.name)
        assertEquals(selected.logoUrl, selectedInList.logoUrl)
    }

    @Test
    fun radioHomeIsShortAndStillIncludesSelectedStation() {
        val selected = requireNotNull(StationCatalog.stationById("radio-rock"))

        val homeStations = stationsForRadioHome(StationCatalog.allStations, selected, maxCount = 10)

        assertTrue(homeStations.size <= 10)
        assertEquals(1, homeStations.count { it.stableId == selected.stableId })
        assertEquals(selected.name, homeStations.single { it.stableId == selected.stableId }.name)
    }

    @Test
    fun selectedStationOutsideHomeWindowRemainsTheDialAndNowPlayingStation() {
        val selected = requireNotNull(StationCatalog.stationById("radio-rock"))
        val homeWindow = StationCatalog.allStations.take(5)

        val dialStations = stationsForNowPlaying(homeWindow, selected)

        assertTrue(dialStations.any { it.id == selected.id })
        val dialSelected = dialStations.first { it.id == selected.id }
        assertEquals("Radio Rock", dialSelected.name)
        assertEquals(selected.preferredStreamUrl, dialSelected.preferredStreamUrl)
        assertNotEquals(StationCatalog.DEFAULT_STATION_ID, dialSelected.id)
    }

    @Test
    fun existingSelectedStationKeepsStableHomeOrdering() {
        val selected = requireNotNull(StationCatalog.stationById("ylex"))

        assertEquals(
            StationCatalog.allStations.take(5).map { it.id },
            stationsForNowPlaying(StationCatalog.allStations.take(5), selected).map { it.id }
        )
    }

    @Test
    fun logicalDuplicateUsesSelectedIdentityAndBestAvailableLogo() {
        val builtIn = requireNotNull(StationCatalog.stationById("yle-radio-1"))
        val catalog = builtIn.copy(
            id = "radio-browser:abc",
            radioBrowserStationUuid = "abc",
            faviconUrl = "https://catalog.example/yle-radio-1.png"
        )

        val dialStations = stationsForNowPlaying(listOf(builtIn, catalog), catalog)

        assertEquals(1, dialStations.size)
        assertEquals(catalog.id, dialStations.single().id)
        assertEquals(catalog.faviconUrl, dialStations.single().faviconUrl)
    }

    @Test
    fun catalogPlayableAdapterPreservesIdentityStreamAndFavicon() {
        val catalog = CatalogStation(
            source = CatalogSource.RADIO_BROWSER,
            sourceStationId = "rock123",
            canonicalName = "Radio Rock",
            streamUrl = "https://rock.example/stream",
            resolvedStreamUrl = "https://rock.example/resolved",
            homepageUrl = "https://rock.example",
            logoUrl = "https://rock.example/logo.png",
            countryCode = "FI",
            countryName = "Finland",
            region = null,
            languages = listOf("fi"),
            rawTags = listOf("rock"),
            codec = "AAC",
            bitrateKbps = 128,
            votes = 1,
            clickCount = 1,
            clickTrend = 0,
            lastCheckOk = true,
            lastCheckAt = null,
            latitude = null,
            longitude = null
        )

        val playable = requireNotNull(catalog.toPlayableRadioStationOrNull())

        assertEquals("radio-browser:rock123", playable.id)
        assertEquals("Radio Rock", playable.name)
        assertEquals("https://rock.example/resolved", playable.preferredStreamUrl)
        assertEquals(catalog.logoUrl, playable.logoUrl)
        assertEquals("Finland", playable.location)
    }

    @Test
    fun dialWindowKeepsDistinctStationsAndDoesNotInventDuplicates() {
        val first = requireNotNull(StationCatalog.stationById("radio-helsinki"))
            .copy(id = "radio-city-helsinki", name = "Radio City Helsinki")
        val second = first.copy(
            id = "radio-city-oulu",
            name = "Radio City Oulu",
            streamUrl = "https://stream.radio-city-oulu.example",
            preferredStreamUrl = "https://stream.radio-city-oulu.example"
        )

        assertEquals(1, stationsForNowPlaying(listOf(first), first).size)
        assertEquals(2, stationsForNowPlaying(listOf(first, second), first).size)
        assertEquals(4, stationsForNowPlaying(listOf(first, second) + StationCatalog.allStations.take(2), first).size)
    }
}
