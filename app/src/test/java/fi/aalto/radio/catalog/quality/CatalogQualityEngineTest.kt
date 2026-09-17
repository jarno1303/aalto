package fi.aalto.radio.catalog.quality

import fi.aalto.radio.catalog.CatalogSource
import fi.aalto.radio.catalog.CatalogStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogQualityEngineTest {
    @Test
    fun taxonomyNormalizesControlledTagsAndChoosesDeterministicPrimary() {
        assertEquals(setOf(AaltoCategory.POP), CatalogTaxonomy.classify(listOf("top 40", "hits")).categories)
        assertEquals(setOf(AaltoCategory.ROCK), CatalogTaxonomy.classify(listOf("classic rock")).categories)
        assertEquals(setOf(AaltoCategory.DANCE_ELECTRONIC), CatalogTaxonomy.classify(listOf("house", "edm")).categories)
        assertEquals(setOf(AaltoCategory.HIPHOP_RNB), CatalogTaxonomy.classify(listOf("hip-hop", "r&b")).categories)
        assertEquals(setOf(AaltoCategory.NEWS_TALK), CatalogTaxonomy.classify(listOf("news", "talk")).categories)
        assertEquals(setOf(AaltoCategory.CLASSICAL), CatalogTaxonomy.classify(listOf("classical", "opera")).categories)
        assertEquals(setOf(AaltoCategory.JAZZ_SOUL), CatalogTaxonomy.classify(listOf("jazz", "soul")).categories)
        assertEquals(setOf(AaltoCategory.COUNTRY_FOLK), CatalogTaxonomy.classify(listOf("country", "folk")).categories)
        assertEquals(setOf(AaltoCategory.FAMILY), CatalogTaxonomy.classify(listOf("children", "kids")).categories)
        assertEquals(setOf(AaltoCategory.OTHER), CatalogTaxonomy.classify(listOf("weird unknown")).categories)

        val mixed = CatalogTaxonomy.classify(listOf("news", "talk", "jazz"))
        assertEquals(setOf(AaltoCategory.NEWS_TALK, AaltoCategory.JAZZ_SOUL), mixed.categories)
        assertEquals(AaltoCategory.NEWS_TALK, mixed.primaryCategory)
    }

    @Test
    fun metadataCleanerPreservesBrandAndRemovesOnlyConfidentSuffixes() {
        val cleaned = CatalogMetadataCleaner.clean("  Radio XYZ 128kbps  ")
        assertEquals("Radio XYZ", cleaned.displayName)
        assertEquals("radio xyz", cleaned.dedupeName)
        assertEquals("Radio XYZ – Live Stream", CatalogMetadataCleaner.clean("Radio XYZ – Live Stream").displayName)
    }

    @Test
    fun qualityIsExplainableBoundedAndHealthDominates() {
        val healthy = station(
            id = "healthy",
            country = "FI",
            name = "Popular Radio",
            tags = listOf("pop"),
            clicks = 1_000_000,
            votes = 100,
            trend = 10,
            health = true,
            favicon = "https://logo"
        )
        val poor = station("poor", "FI", "Small Radio", tags = emptyList(), health = null, bitrate = 32)
        val curated = CatalogQualityEngine().curate(listOf(healthy, poor)).stations
        val healthyResult = curated.first { it.station.sourceStationId == "healthy" }
        val poorResult = curated.first { it.station.sourceStationId == "poor" }
        assertTrue(healthyResult.qualityScore > poorResult.qualityScore)
        assertTrue(healthyResult.qualityScore in 0.0..100.0)
        assertTrue(healthyResult.qualityBreakdown.health >= healthyResult.qualityBreakdown.metadata)

        val huge = CatalogQualityEngine().curate(listOf(healthy.copy(clickCount = Int.MAX_VALUE))).stations.single().qualityScore
        assertTrue(huge <= 100.0)
    }

    @Test
    fun faviconAndBitrateRemainWeakSignals() {
        val withoutBranding = station("without", "FI", "Same Radio A", favicon = null, bitrate = 64)
        val withBranding = withoutBranding.copy(
            sourceStationId = "with",
            canonicalName = "Same Radio B",
            streamUrl = "https://stream/with",
            homepageUrl = "https://home.example/with",
            logoUrl = "https://logo"
        )
        val highBitrate = withoutBranding.copy(
            sourceStationId = "high",
            canonicalName = "Same Radio C",
            streamUrl = "https://stream/high",
            homepageUrl = "https://home.example/high",
            bitrateKbps = 320
        )
        val results = CatalogQualityEngine().curate(listOf(withoutBranding, withBranding, highBitrate)).stations
        val base = results.first { it.station.sourceStationId == "without" }.qualityScore
        val branded = results.first { it.station.sourceStationId == "with" }.qualityScore
        val high = results.first { it.station.sourceStationId == "high" }.qualityScore
        assertTrue(branded - base <= 5.0)
        assertTrue(high - base < 3.0)
    }

    @Test
    fun hardFiltersAreConservativeAndOverridesCanForceInclude() {
        val broken = station("broken", "FI", "Broken", health = false)
        val forced = station("forced", "FI", "test", health = false)
        val overrides = mapOf(
            forced.sourceQualifiedId to AaltoStationOverride(
                sourceQualifiedId = forced.sourceQualifiedId,
                canonicalNameOverride = "Aalto Test Radio",
                categoryOverrides = setOf(AaltoCategory.ROCK),
                qualityBoost = 4.0,
                forceInclude = true
            )
        )
        val result = CatalogQualityEngine(overrides).curate(listOf(broken, forced))
        assertTrue(result.stations.none { it.station.sourceStationId == "broken" })
        val included = result.stations.single()
        assertEquals("Aalto Test Radio", included.displayName)
        assertEquals(setOf(AaltoCategory.ROCK), included.categories)
    }

    @Test
    fun duplicateGroupsAreConservativeAndKeepAlternates() {
        val first = station("a", "FI", "Radio XYZ", stream = "https://same", region = "Helsinki")
        val second = station("b", "FI", "Radio XYZ 128k", stream = "https://other", region = "Helsinki")
        val differentCountry = station("c", "DE", "Radio XYZ", stream = "https://same-de", region = "Berlin")
        val differentRegion = station("d", "FI", "Radio XYZ", stream = "https://other-fi", region = "Oulu")
        val result = CatalogQualityEngine().curate(listOf(first, second, differentCountry, differentRegion))

        assertEquals(3, result.duplicateGroups.size)
        val group = result.duplicateGroups.first { it.members.any { member -> member.station.sourceStationId == "a" } }
        assertEquals(2, group.members.size)
        assertEquals(1, result.stations.count { it.duplicateGroupId == group.groupId })
        assertEquals(1, result.stations.count { it.station.sourceStationId == "c" })
        assertNotEquals(group.groupId, result.duplicateGroups.first { it.members.any { member -> member.station.sourceStationId == "c" } }.groupId)
        assertTrue(group.members.any { it.alternateStations.isNotEmpty() })
    }

    @Test
    fun knownNameVariantsBecomeOneCanonicalStationAndKeepBestBranding() {
        val withoutLogo = station(
            id = "rock-without-logo",
            country = "FI",
            name = "Radio Rock",
            favicon = null,
            stream = "https://rock.example/primary",
            tags = listOf("rock")
        )
        val withLogo = station(
            id = "rock-with-logo",
            country = "FI",
            name = "RadioRock",
            favicon = "https://rock.example/logo.png",
            stream = "https://rock.example/fallback",
            tags = listOf("rock")
        )

        val result = CatalogQualityEngine().curate(listOf(withoutLogo, withLogo))
        val canonical = result.stations.single()

        assertEquals(1, result.stations.size)
        assertEquals("Radio Rock", canonical.displayName)
        assertEquals("https://rock.example/logo.png", canonical.station.logoUrl)
        assertTrue(canonical.station.stableId.startsWith("canonical:"))
        assertTrue(canonical.station.logoUrls.contains("https://rock.example/logo.png"))
        assertEquals(1, canonical.station.streamAlternatives.size)
        assertEquals(
            1,
            CatalogQualityEngine().topStationsForCategory(
                listOf(withoutLogo, withLogo),
                AaltoCategory.ROCK
            ).size
        )
    }

    @Test
    fun sameNameDifferentLocalDomainsRemainSeparate() {
        val first = station("local-a", "FI", "Local Radio", stream = "https://a.example/stream")
            .copy(homepageUrl = "https://a.example")
        val second = station("local-b", "FI", "LocalRadio", stream = "https://b.example/stream")
            .copy(homepageUrl = "https://b.example")

        val result = CatalogQualityEngine().curate(listOf(first, second))

        assertEquals(2, result.stations.size)
        assertEquals(2, result.stations.map { it.station.stableId }.distinct().size)
    }

    @Test
    fun multiCountryCategoryLanguageQueriesRankGloballyAndMatchAnyCategory() {
        val candidates = listOf(
            station("fi-a", "FI", "Station A", tags = listOf("rock"), clicks = 100, language = "fi"),
            station("fi-b", "FI", "Station B", tags = listOf("pop"), clicks = 10, language = "fi"),
            station("de-c", "DE", "Station C", tags = listOf("rock"), clicks = 200, language = "de"),
            station("de-d", "DE", "Station D", tags = listOf("news"), clicks = 500, language = "de")
        )
        val engine = CatalogQualityEngine()
        val rockPop = engine.curate(
            candidates,
            CatalogFilter(countryCodes = setOf("FI", "DE"), categories = setOf(AaltoCategory.ROCK, AaltoCategory.POP))
        ).stations
        assertEquals(listOf("de-c", "fi-a", "fi-b"), rockPop.map { it.station.sourceStationId })

        val german = engine.topStations(candidates, countries = setOf("FI", "DE"), languages = setOf("de"), limit = 10)
        assertTrue(german.all { it.station.countryCode == "DE" })
        assertEquals(listOf("de-d", "de-c"), german.map { it.station.sourceStationId })
    }

    private fun station(
        id: String,
        country: String,
        name: String,
        tags: List<String> = listOf("pop"),
        clicks: Int = 10,
        votes: Int = 2,
        trend: Int = 1,
        health: Boolean? = true,
        favicon: String? = "https://logo/$id",
        bitrate: Int = 128,
        language: String = "en",
        stream: String = "https://stream/$id",
        region: String? = null
    ) = CatalogStation(
        source = CatalogSource.RADIO_BROWSER,
        sourceStationId = id,
        canonicalName = name,
        streamUrl = stream,
        resolvedStreamUrl = null,
        homepageUrl = "https://home.example/$id",
            logoUrl = favicon,
        countryCode = country,
        countryName = country,
        region = region,
        languages = listOf(language),
        rawTags = tags,
        codec = "MP3",
        bitrateKbps = bitrate,
        votes = votes,
        clickCount = clicks,
        clickTrend = trend,
        lastCheckOk = health,
        lastCheckAt = 1L,
        latitude = null,
        longitude = null
    )
}
