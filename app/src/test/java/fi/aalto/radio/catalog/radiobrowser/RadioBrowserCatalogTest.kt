package fi.aalto.radio.catalog.radiobrowser

import fi.aalto.radio.catalog.CatalogErrorKind
import fi.aalto.radio.catalog.CatalogResult
import fi.aalto.radio.catalog.CatalogSource
import fi.aalto.radio.catalog.InMemoryStationCatalogCache
import fi.aalto.radio.catalog.StationCatalogRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RadioBrowserCatalogTest {
    @Test
    fun genreUsesProviderTagFilterRatherThanStationName() = runBlocking {
        var requestedUrl = ""
        val source = RadioBrowserCatalogSource(
            baseUrlProvider = RadioBrowserBaseUrlProvider { listOf("https://mirror.example") },
            transport = RadioBrowserHttpTransport { url, _ ->
                requestedUrl = url
                RadioBrowserHttpResponse(200, "[]")
            }
        )
        source.stationsByTag("jazz", "fi", 100)
        assertTrue(requestedUrl.contains("tag=jazz"))
        assertTrue(requestedUrl.contains("countrycode=FI"))
        assertTrue(requestedUrl.contains("hidebroken=true"))
        assertTrue(!requestedUrl.contains("name="))
    }

    @Test
    fun textSearchAlsoFindsGenreMatchesWhoseNameIsDifferent() = runBlocking {
        val source = RadioBrowserCatalogSource(
            baseUrlProvider = RadioBrowserBaseUrlProvider { listOf("https://mirror.example") },
            transport = RadioBrowserHttpTransport { url, _ ->
                RadioBrowserHttpResponse(200, if (url.contains("tag=jazz")) {
                    """[{"stationuuid":"jazz-id","name":"Blue Note FM","url":"https://example.com/stream","countrycode":"FI","tags":"jazz"}]"""
                } else "[]")
            }
        )
        val result = source.search("jazz", "FI", 100) as CatalogResult.Success
        assertEquals("Blue Note FM", result.value.single().canonicalName)
    }

    @Test
    fun malformedMirrorResponseFallsBackToTheNextServer() = runBlocking {
        val requested = mutableListOf<String>()
        val source = RadioBrowserCatalogSource(
            baseUrlProvider = RadioBrowserBaseUrlProvider { listOf("https://first.example", "https://second.example") },
            transport = RadioBrowserHttpTransport { url, _ ->
                requested += url
                RadioBrowserHttpResponse(200, if (url.startsWith("https://first")) "<html>unavailable</html>" else "[]")
            }
        )
        assertTrue(source.stationsByCountry("FI", 100) is CatalogResult.Success)
        assertEquals(2, requested.size)
    }

    @Test
    fun mapsAndNormalizesStationMetadata() {
        val station = RadioBrowserStationDto(
            stationUuid = "uuid-1",
            name = "  Radio Nova  ",
            url = " http://original.example/stream ",
            urlResolved = " https://resolved.example/stream ",
            homepage = " https://example.com ",
            favicon = " https://example.com/icon.png ",
            country = "Germany",
            countryCode = " de ",
            state = "Berlin",
            language = "German, English",
            languageCodes = "de,en,de",
            tags = "Pop, pop; 80s",
            codec = "MP3",
            bitrate = 128,
            votes = 4,
            clickCount = 20,
            clickTrend = 2,
            lastCheckOk = true,
            lastCheckTime = 123L,
            latitude = 52.5,
            longitude = 13.4
        ).toCatalogStationOrNull()

        assertNotNull(station)
        assertEquals(CatalogSource.RADIO_BROWSER, station?.source)
        assertEquals("uuid-1", station?.sourceStationId)
        assertEquals("radio-browser:uuid-1", station?.sourceQualifiedId)
        assertEquals("DE", station?.countryCode)
        assertEquals("https://resolved.example/stream", station?.preferredStreamUrl)
        assertEquals("https://example.com/icon.png", station?.logoUrl)
        assertEquals(listOf("german", "english", "de", "en"), station?.languages)
        assertEquals(listOf("pop", "80s"), station?.rawTags)
        assertEquals(128, station?.bitrateKbps)
    }

    @Test
    fun blankOptionalFieldsBecomeNullAndOriginalUrlIsFallback() {
        val station = RadioBrowserStationDto(
            stationUuid = "uuid-2",
            name = "Station",
            url = "https://example.com/stream",
            urlResolved = " ",
            homepage = " ",
            favicon = "",
            country = null,
            countryCode = " fi ",
            state = null,
            language = null,
            languageCodes = null,
            tags = null,
            codec = null,
            bitrate = 0,
            votes = -1,
            clickCount = -1,
            clickTrend = null,
            lastCheckOk = null,
            lastCheckTime = -1,
            latitude = 200.0,
            longitude = null
        ).toCatalogStationOrNull()

        assertEquals("https://example.com/stream", station?.preferredStreamUrl)
        assertNull(station?.resolvedStreamUrl)
        assertNull(station?.homepageUrl)
        assertNull(station?.logoUrl)
        assertNull(station?.bitrateKbps)
        assertNull(station?.votes)
        assertNull(station?.latitude)
    }

    @Test
    fun unusableStationIsRejectedAndIdentityUsesUuid() {
        val missingUrl = RadioBrowserStationDto(
            stationUuid = "uuid-3", name = "Station", url = null, urlResolved = null,
            homepage = null, favicon = null, country = null, countryCode = null, state = null,
            language = null, languageCodes = null, tags = null, codec = null, bitrate = null,
            votes = null, clickCount = null, clickTrend = null, lastCheckOk = null,
            lastCheckTime = null, latitude = null, longitude = null
        )
        assertNull(missingUrl.toCatalogStationOrNull())

        val first = missingUrl.copy(url = "https://one.example", name = "Same Name")
            .toCatalogStationOrNull()
        val second = missingUrl.copy(stationUuid = "uuid-4", url = "https://two.example", name = "Same Name")
            .toCatalogStationOrNull()
        assertNotEquals(first?.sourceQualifiedId, second?.sourceQualifiedId)
    }

    @Test
    fun sourceParsesResponseAndBuildsBoundedCountryRequest() = runBlocking {
        var requestedUrl = ""
        val source = RadioBrowserCatalogSource(
            baseUrlProvider = RadioBrowserBaseUrlProvider { listOf("https://mirror.example") },
            transport = RadioBrowserHttpTransport { url, _ ->
                requestedUrl = url
                RadioBrowserHttpResponse(
                    200,
                    "[{\"stationuuid\":\"uuid-5\",\"name\":\"Test\",\"url\":\"https://example.com\",\"countrycode\":\"DE\"}]"
                )
            }
        )

        val result = source.stationsByCountry("de", 999)

        assertTrue(result is CatalogResult.Success)
        assertTrue(requestedUrl.contains("bycountrycodeexact/DE"))
        assertTrue(requestedUrl.contains("limit=300"))
        assertTrue(requestedUrl.contains("hidebroken=true"))
        assertEquals("uuid-5", (result as CatalogResult.Success).value.single().sourceStationId)
    }

    @Test
    fun invalidInputAndProviderFailureAreTyped() = runBlocking {
        val source = RadioBrowserCatalogSource(
            baseUrlProvider = RadioBrowserBaseUrlProvider { listOf("https://mirror.example") },
            transport = RadioBrowserHttpTransport { _, _ -> RadioBrowserHttpResponse(503, "") }
        )

        val invalid = source.stationsByCountry("Germany", 10)
        val failure = source.search("Radio Nova", "DE", 10)

        assertEquals(CatalogErrorKind.INVALID_REQUEST, (invalid as CatalogResult.Failure).error.kind)
        assertEquals(CatalogErrorKind.PROVIDER_FAILURE, (failure as CatalogResult.Failure).error.kind)
    }

    @Test
    fun repositoryReadsFreshCountryCacheWithoutCallingSource() = runBlocking {
        var sourceCalls = 0
        val source = object : fi.aalto.radio.catalog.StationCatalogSource {
            override suspend fun stationsByCountry(countryCode: String, limit: Int): CatalogResult<List<fi.aalto.radio.catalog.CatalogStation>> {
                sourceCalls++
                return CatalogResult.Success(emptyList())
            }

            override suspend fun search(query: String, countryCode: String?, limit: Int): CatalogResult<List<fi.aalto.radio.catalog.CatalogStation>> {
                sourceCalls++
                return CatalogResult.Success(emptyList())
            }
        }
        val cache = InMemoryStationCatalogCache()
        val repository = StationCatalogRepository(source, cache, clock = { 1000L })
        cache.putCountry("de", fi.aalto.radio.catalog.CatalogCacheEntry(emptyList(), 1000L))

        repository.getStationsByCountry("DE", 10)

        assertEquals(0, sourceCalls)
    }
}
