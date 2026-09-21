package fi.aalto.radio.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSearchReliabilityTest {
    @Test
    fun carSizedCacheIsImmediatelyVisibleButRequiresPhoneRefill() = runTest {
        val requests = mutableListOf<Int>()
        val source = FakeSource(country = { _, limit ->
            requests += limit
            CatalogResult.Success((1..150).map { station("radio-$it") }.take(limit))
        })
        val repository = StationCatalogRepository(source, clock = { 1000L })
        repository.getStationsByCountry("FI", 40)
        val phone = repository.getStationsByCountry("FI", 100).snapshot()
        assertEquals(40, phone.stations.size)
        assertTrue(phone.needsRefresh)
        assertEquals(listOf(40), requests) // cache read did not wait on the network
        repository.refreshCountry("FI", 100)
        val refreshed = repository.getStationsByCountry("FI", 100).snapshot()
        assertEquals(100, refreshed.stations.size)
        assertFalse(refreshed.needsRefresh)
        assertEquals(listOf(40, 100), requests)
        repository.close()
    }

    @Test
    fun genuinelyShortCountryDoesNotRefreshForever() = runTest {
        val repository = StationCatalogRepository(FakeSource(country = { _, _ ->
            CatalogResult.Success(listOf(station("small-country")))
        }), clock = { 1000L })
        repository.getStationsByCountry("FI", 100)
        assertFalse(repository.getStationsByCountry("FI", 100).snapshot().needsRefresh)
        repository.close()
    }

    @Test
    fun smallerRefreshDoesNotShrinkTheSharedCountryCache() = runTest {
        val requests = mutableListOf<Int>()
        val repository = StationCatalogRepository(FakeSource(country = { _, limit ->
            requests += limit
            CatalogResult.Success((1..150).map { station("radio-$it") }.take(limit))
        }), clock = { 1000L })
        repository.refreshCountry("FI", 100)
        val small = repository.refreshCountry("FI", 40) as CatalogResult.Success
        assertEquals(40, small.value.size)
        assertEquals(100, repository.getStationsByCountry("FI", 100).snapshot().stations.size)
        assertEquals(listOf(100, 100), requests)
        repository.close()
    }

    @Test
    fun failedRefillKeepsTheCachedStations() = runTest {
        val cache = InMemoryStationCatalogCache()
        cache.putCountry("FI", CatalogCacheEntry(listOf(station("saved")), 1000L))
        val repository = StationCatalogRepository(FakeSource(), cache, clock = { 1000L })
        assertTrue(repository.getStationsByCountry("FI", 100).snapshot().needsRefresh)
        assertTrue(repository.refreshCountry("FI", 100) is CatalogResult.Failure)
        assertEquals("saved", repository.getStationsByCountry("FI", 100).snapshot().stations.single().sourceStationId)
        repository.close()
    }

    @Test
    fun searchCacheDoesNotReuseFortyResultsForAHundred() = runTest {
        val requests = mutableListOf<Int>()
        val source = FakeSource(text = { _, _, limit ->
            requests += limit
            CatalogResult.Success((1..150).map { station("radio-$it") }.take(limit))
        })
        val repository = StationCatalogRepository(source, clock = { 1000L })
        repository.searchStations("radio", "FI", 40)
        assertEquals(100, repository.searchStations("radio", "FI", 100).snapshot().stations.size)
        repository.searchStations("radio", "FI", 100)
        assertEquals(listOf(40, 100), requests)
        repository.close()
    }

    @Test
    fun genreSearchFindsStationsOutsideThePopularCountryListAndUsesOr() = runTest {
        val tags = mutableListOf<String>()
        val source = FakeSource(tag = { tag, country, _ ->
            assertEquals("FI", country)
            tags += tag
            CatalogResult.Success(listOf(station("only-$tag", tags = listOf(tag))))
        })
        val repository = StationCatalogRepository(source)
        val result = CatalogSearchLoader(repository)
            .search(CatalogSearchRequest("", listOf("FI"), setOf("rock", "jazz"))).toList()
        assertTrue(result.first().loading)
        assertFalse(result.last().loading)
        assertNull(result.last().error)
        assertEquals(setOf("rock", "jazz"), tags.toSet())
        assertEquals(setOf("only-rock", "only-jazz"), result.last().stations.map { it.sourceStationId }.toSet())
        repository.close()
    }

    @Test
    fun offlineSearchIsAnErrorInsteadOfAnEmptySuccessfulSearch() = runTest {
        val repository = StationCatalogRepository(FakeSource())
        val result = CatalogSearchLoader(repository).search(CatalogSearchRequest("missing", listOf("FI"))).toList()
        assertTrue(result.first().loading)
        assertFalse(result.last().loading)
        assertNotNull(result.last().error)
        repository.close()
    }

    @Test
    fun successfulEmptySearchHasNoError() = runTest {
        val repository = StationCatalogRepository(FakeSource(text = { _, _, _ -> CatalogResult.Success(emptyList()) }))
        val state = CatalogSearchLoader(repository).search(CatalogSearchRequest("missing", listOf("FI"))).toList().last()
        assertFalse(state.loading)
        assertNull(state.error)
        assertTrue(state.stations.isEmpty())
        repository.close()
    }

    @Test
    fun newQueryAndCountryImmediatelyHideOldWorldwideResults() {
        val old = CatalogSearchRequest("Berlin", listOf("FI"))
        val state = CatalogSearchState(old, loading = false, worldStations = listOf(station("Berlin", "DE")))
        val next = state.forRequest(old.copy(query = "Tokyo"))
        assertTrue(next.loading)
        assertTrue(next.worldStations.isEmpty())
        assertTrue(state.forRequest(old.copy(countries = listOf("DE"))).worldStations.isEmpty())
        assertFalse(state.forRequest(old.copy(query = "")).loading)
    }

    @Test
    fun clearedSearchDoesNotMakeRequests() = runTest {
        var calls = 0
        val repository = StationCatalogRepository(FakeSource(text = { _, _, _ -> calls++; failure }))
        val state = CatalogSearchLoader(repository).search(CatalogSearchRequest("", listOf("FI"))).toList().single()
        assertFalse(state.loading)
        assertEquals(0, calls)
        repository.close()
    }

    @Test
    fun worldwideFailureKeepsHomeMatches() = runTest {
        val repository = StationCatalogRepository(FakeSource(text = { _, country, _ ->
            if (country == "FI") CatalogResult.Success(listOf(station("needle"))) else failure
        }))
        val states = CatalogSearchLoader(repository).search(CatalogSearchRequest("needle", listOf("FI"))).toList()
        assertTrue(states.any { it.loading && it.stations.isNotEmpty() })
        assertEquals("needle", states.last().stations.single().sourceStationId)
        assertNotNull(states.last().error)
        repository.close()
    }

    @Test
    fun worldwideCandidatesRemainAvailableForLocalOtherGenreFiltering() = runTest {
        val repository = StationCatalogRepository(FakeSource(
            text = { _, country, _ ->
                CatalogResult.Success(if (country == null) {
                    listOf(station("needle", "DE", tags = listOf("experimental")))
                } else emptyList())
            },
            tag = { _, _, _ -> CatalogResult.Success(emptyList()) }
        ))
        // Selecting Rock + Other sends a rock alias, but Other is a local
        // exclusion. Do not discard its text matches before the UI sees them.
        val state = CatalogSearchLoader(repository)
            .search(CatalogSearchRequest("needle", listOf("FI"), setOf("rock"))).toList().last()
        assertEquals("needle", state.worldStations.single().sourceStationId)
        assertNull(state.error)
        repository.close()
    }

    @Test
    fun supersededSearchIsCancelledWithoutPublishingAFakeError() = runTest {
        var started = false
        val repository = StationCatalogRepository(FakeSource(text = { _, _, _ ->
            started = true
            awaitCancellation()
        }))
        val states = mutableListOf<CatalogSearchState>()
        val job = launch { CatalogSearchLoader(repository).search(CatalogSearchRequest("old", listOf("FI"))).toList(states) }
        runCurrent()
        advanceTimeBy(300)
        runCurrent()
        assertTrue(started)
        job.cancel()
        runCurrent()
        assertTrue(states.all { it.error == null })
        repository.close()
    }

    private fun CatalogReadResult.snapshot() = (this as CatalogReadResult.Success).snapshot

    private class FakeSource(
        val country: suspend (String, Int) -> CatalogResult<List<CatalogStation>> = { _, _ -> failure },
        val text: suspend (String, String?, Int) -> CatalogResult<List<CatalogStation>> = { _, _, _ -> failure },
        val tag: suspend (String, String?, Int) -> CatalogResult<List<CatalogStation>> = { _, _, _ -> failure }
    ) : StationCatalogSource {
        override suspend fun stationsByCountry(countryCode: String, limit: Int) = country(countryCode, limit)
        override suspend fun search(query: String, countryCode: String?, limit: Int) = text(query, countryCode, limit)
        override suspend fun stationsByTag(tag: String, countryCode: String?, limit: Int) = this.tag(tag, countryCode, limit)
    }

    companion object {
        private val failure = CatalogResult.Failure(CatalogError(CatalogErrorKind.NETWORK_UNAVAILABLE, "offline"))
        private fun station(id: String, country: String = "FI", tags: List<String> = emptyList()) = CatalogStation(
            source = CatalogSource.RADIO_BROWSER, sourceStationId = id, canonicalName = "Radio $id",
            streamUrl = "https://$id.example/stream", resolvedStreamUrl = null, homepageUrl = null,
            logoUrl = null, countryCode = country, countryName = country, region = null, languages = emptyList(),
            rawTags = tags, codec = "MP3", bitrateKbps = 128, votes = 0, clickCount = 0, clickTrend = 0,
            lastCheckOk = true, lastCheckAt = null, latitude = null, longitude = null
        )
    }
}
