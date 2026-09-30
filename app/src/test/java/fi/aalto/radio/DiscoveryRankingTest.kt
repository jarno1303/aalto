package fi.aalto.radio

import fi.aalto.radio.catalog.CatalogSource
import fi.aalto.radio.catalog.CatalogStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryRankingTest {

    @Test
    fun exactNameBeatsMuchMorePopularTagOnlyMatch() {
        val exact = station("exact", "Rock", tags = listOf("music"))
        val popular = station("popular", "Mega FM", tags = listOf("rock"))
        val ranked = DiscoveryRanking.rank(
            stations = listOf(popular, exact),
            query = "rock",
            favoriteIds = emptySet(),
            recentStations = emptyList(),
            catalogStations = listOf(
                catalog(popular, clicks = 1_000_000, votes = 10_000),
                catalog(exact, clicks = 1, votes = 0)
            )
        )

        assertEquals("exact", ranked.first().id)
    }

    @Test
    fun healthyPopularStationWinsOtherwiseEqualBrowseRanking() {
        val healthy = station("healthy", "A", tags = listOf("pop"))
        val unknown = station("unknown", "B", tags = listOf("pop"))
        val ranked = DiscoveryRanking.rank(
            stations = listOf(unknown, healthy),
            query = "",
            favoriteIds = emptySet(),
            recentStations = emptyList(),
            catalogStations = listOf(
                catalog(healthy, health = true, clicks = 1000),
                catalog(unknown, health = null, clicks = 1)
            )
        )

        assertEquals("healthy", ranked.first().id)
    }

    @Test
    fun recentListeningCreatesLightGenreAffinity() {
        val rock = station("rock", "Rock Two", tags = listOf("rock"))
        val news = station("news", "News One", tags = listOf("news"))
        val recentRock = station("recent", "Recent Rock", tags = listOf("rock"))

        val ranked = DiscoveryRanking.rank(
            stations = listOf(news, rock),
            query = "",
            favoriteIds = emptySet(),
            recentStations = listOf(recentRock),
            catalogStations = emptyList()
        )

        assertEquals("rock", ranked.first().id)
    }

    @Test
    fun similarStationsNeedSemanticOverlapAndExcludeCurrentStation() {
        val current = station("current", "Current", tags = listOf("rock", "classic"), language = "fi")
        val close = station("close", "Close", tags = listOf("rock", "classic"), language = "fi")
        val weak = station("weak", "Weak", tags = listOf("rock"), language = "en")
        val unrelated = station("other", "Other", tags = listOf("news"), language = "de")

        val related = DiscoveryRanking.similarStations(
            candidates = listOf(current, unrelated, weak, close),
            selectedStation = current,
            favoriteIds = emptySet(),
            recentStations = listOf(current),
            catalogStations = emptyList()
        )

        assertFalse(related.any { it.id == current.id })
        assertFalse(related.any { it.id == unrelated.id })
        assertEquals("close", related.first().id)
        assertTrue(related.any { it.id == weak.id })
    }

    private fun station(
        id: String,
        name: String,
        tags: List<String>,
        language: String = "fi"
    ) = RadioStation(
        id = id,
        radioBrowserStationUuid = id,
        name = name,
        description = "",
        initials = name.take(2).uppercase(),
        logoColorArgb = 0L,
        streamUrl = "https://example.test/$id",
        countryCode = "FI",
        tags = tags,
        category = tags.firstOrNull().orEmpty(),
        languages = listOf(language),
        declaredBitrateKbps = 128
    )

    private fun catalog(
        station: RadioStation,
        health: Boolean? = true,
        clicks: Int = 10,
        votes: Int = 2
    ) = CatalogStation(
        source = CatalogSource.RADIO_BROWSER,
        sourceStationId = station.id,
        canonicalName = station.name,
        streamUrl = station.streamUrl,
        resolvedStreamUrl = null,
        homepageUrl = null,
        logoUrl = null,
        countryCode = station.countryCode,
        countryName = "Finland",
        region = null,
        languages = station.languages,
        rawTags = station.tags,
        codec = "MP3",
        bitrateKbps = station.declaredBitrateKbps,
        votes = votes,
        clickCount = clicks,
        clickTrend = 0,
        lastCheckOk = health,
        lastCheckAt = 1L,
        latitude = null,
        longitude = null
    )
}
