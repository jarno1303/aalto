package fi.aalto.radio.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fi.aalto.radio.AaltoDatabase
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogPhase2Test {
    private lateinit var context: Context
    private val databaseNames = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun catalogRoundTripSurvivesReopenAndKeepsCountriesSeparate() = runTest {
        val name = uniqueDatabaseName()
        val first = open(name)
        val cache = RoomStationCatalogCache(first.catalogStationDao())
        cache.putCountry(
            "FI",
            CatalogCacheEntry(
                listOf(
                    station("fi-1", "FI").copy(
                        canonicalId = "canonical:fi-1",
                        logoCandidates = listOf("https://logo/fi-fallback"),
                        streamAlternatives = listOf("https://stream/fi-fallback"),
                        broadcaster = "Aalto",
                        network = "Aalto Network"
                    )
                ),
                100L
            )
        )
        cache.putCountry("DE", CatalogCacheEntry(listOf(station("de-1", "DE", favicon = "https://logo")), 100L))
        first.close()

        val reopened = open(name)
        val reopenedCache = RoomStationCatalogCache(reopened.catalogStationDao())
        val de = requireNotNull(reopenedCache.getCountry("DE"))
        val fi = requireNotNull(reopenedCache.getCountry("FI"))

        assertEquals(listOf("de-1"), de.stations.map { it.sourceStationId })
        assertEquals("https://logo", de.stations.single().logoUrl)
        assertEquals(listOf("fi-1"), fi.stations.map { it.sourceStationId })
        assertEquals("canonical:fi-1", fi.stations.single().canonicalId)
        assertEquals(listOf("https://logo/fi-fallback"), fi.stations.single().logoCandidates)
        assertEquals(listOf("https://stream/fi-fallback"), fi.stations.single().streamAlternatives)
        assertEquals("Aalto", fi.stations.single().broadcaster)
        assertEquals("Aalto Network", fi.stations.single().network)
        assertEquals(100L, requireNotNull(reopenedCache.getCountryMetadata("DE")).lastSuccessfulRefreshAt)
        reopened.close()
    }

    @Test
    fun successfulRefreshReplacesOnlyTheRequestedCountry() = runTest {
        val name = uniqueDatabaseName()
        val database = open(name)
        val cache = RoomStationCatalogCache(database.catalogStationDao())
        cache.putCountry("FI", CatalogCacheEntry(listOf(station("fi-1", "FI")), 1L))
        cache.putCountry("DE", CatalogCacheEntry(listOf(station("de-a", "DE"), station("de-b", "DE")), 1L))

        val source = FakeSource(onCountry = { listOf(station("de-b", "DE"), station("de-c", "DE")) })
        val repository = StationCatalogRepository(source, cache, clock = { 2L })
        repository.refreshCountry("DE", 10)

        assertEquals(listOf("de-b", "de-c"), cache.getCountry("DE")?.stations?.map { it.sourceStationId })
        assertEquals(listOf("fi-1"), cache.getCountry("FI")?.stations?.map { it.sourceStationId })
        repository.close()
        database.close()
    }

    @Test
    fun failedRefreshPreservesCatalogAndLastSuccessfulTimestamp() = runTest {
        val name = uniqueDatabaseName()
        val database = open(name)
        val cache = RoomStationCatalogCache(database.catalogStationDao())
        cache.putCountry("DE", CatalogCacheEntry(listOf(station("de-a", "DE")), 100L))
        val repository = StationCatalogRepository(
            source = FakeSource(failure = CatalogError(CatalogErrorKind.NETWORK_UNAVAILABLE, "offline")),
            cache = cache,
            clock = { 200L }
        )

        val result = repository.refreshCountry("DE", 10)

        assertTrue(result is CatalogResult.Failure)
        assertEquals(listOf("de-a"), cache.getCountry("DE")?.stations?.map { it.sourceStationId })
        assertEquals(100L, cache.getCountryMetadata("DE")?.lastSuccessfulRefreshAt)
        assertEquals(200L, cache.getCountryMetadata("DE")?.lastAttemptAt)
        repository.close()
        database.close()
    }

    @Test
    fun staleCountryIsReturnedImmediatelyAndFreshCountryAvoidsSource() = runTest {
        val cache = InMemoryStationCatalogCache()
        cache.putCountry("DE", CatalogCacheEntry(listOf(station("de-old", "DE")), 1L))
        val source = FakeSource(
            onCountry = { listOf(station("de-new", "DE")) }
        )
        val staleRepository = StationCatalogRepository(source, cache, clock = { 2L }, cacheTtlMs = 0L)
        val staleResult = staleRepository.getStationsByCountry("DE", 10)
        assertEquals(CatalogFreshness.STALE, (staleResult as CatalogReadResult.Success).snapshot.freshness)
        assertEquals(listOf("de-old"), staleResult.snapshot.stations.map { it.sourceStationId })
        staleRepository.close()

        val freshCache = InMemoryStationCatalogCache()
        freshCache.putCountry("DE", CatalogCacheEntry(listOf(station("de-cached", "DE")), 10L))
        var freshCalls = 0
        val freshSource = FakeSource(onCountry = { freshCalls++; listOf(station("de-new", "DE")) })
        val freshRepository = StationCatalogRepository(freshSource, freshCache, clock = { 10L })
        val freshResult = freshRepository.getStationsByCountry("DE", 10)
        assertEquals(CatalogFreshness.FRESH, (freshResult as CatalogReadResult.Success).snapshot.freshness)
        assertEquals(0, freshCalls)
        freshRepository.close()
    }

    private fun open(name: String): AaltoDatabase {
        return Room.databaseBuilder(context, AaltoDatabase::class.java, name)
            .addMigrations(AaltoDatabase.MIGRATION_1_2, AaltoDatabase.MIGRATION_2_3, AaltoDatabase.MIGRATION_3_4, AaltoDatabase.MIGRATION_4_5, AaltoDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
    }

    private fun uniqueDatabaseName(): String {
        return "aalto_catalog_test_${UUID.randomUUID()}".also(databaseNames::add)
    }

    private fun station(id: String, country: String, favicon: String? = null): CatalogStation {
        return CatalogStation(
            source = CatalogSource.RADIO_BROWSER,
            sourceStationId = id,
            canonicalName = id,
            streamUrl = "https://stream/$id",
            resolvedStreamUrl = null,
            homepageUrl = null,
            logoUrl = favicon,
            countryCode = country,
            countryName = country,
            region = null,
            languages = listOf("english", "suomi"),
            rawTags = listOf("pop", "rock|special"),
            codec = "MP3",
            bitrateKbps = 128,
            votes = 1,
            clickCount = 2,
            clickTrend = 3,
            lastCheckOk = true,
            lastCheckAt = 4L,
            latitude = 60.1,
            longitude = 24.9
        )
    }

    private class FakeSource(
        private val onCountry: (() -> List<CatalogStation>)? = null,
        private val failure: CatalogError? = null
    ) : StationCatalogSource {
        override suspend fun stationsByCountry(countryCode: String, limit: Int): CatalogResult<List<CatalogStation>> {
            failure?.let { return CatalogResult.Failure(it) }
            return CatalogResult.Success(onCountry?.invoke().orEmpty().take(limit))
        }

        override suspend fun search(query: String, countryCode: String?, limit: Int): CatalogResult<List<CatalogStation>> {
            return CatalogResult.Success(emptyList())
        }
    }
}
