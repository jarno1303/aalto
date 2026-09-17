package fi.aalto.radio.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fi.aalto.radio.AaltoDatabase
import fi.aalto.radio.catalog.radiobrowser.RadioBrowserCatalogSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@Ignore("Manual real Radio Browser smoke test; never part of the offline unit suite")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RadioBrowserManualSmokeTest {
    @Test
    fun fetchesAndPersistsCandidateCountries() = runBlocking {
        val source = RadioBrowserCatalogSource()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "aalto_catalog_smoke_${UUID.randomUUID()}"
        val database = Room.databaseBuilder(context, AaltoDatabase::class.java, databaseName)
            .addMigrations(AaltoDatabase.MIGRATION_1_2, AaltoDatabase.MIGRATION_2_3, AaltoDatabase.MIGRATION_3_4, AaltoDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val cache = RoomStationCatalogCache(database.catalogStationDao())
            listOf("FI", "DE", "ES", "GB", "US").forEach { countryCode ->
                val result = source.stationsByCountry(countryCode, 10)
                assertTrue("$countryCode: $result", result is CatalogResult.Success)
                val stations = (result as CatalogResult.Success).value
                cache.putCountry(countryCode, CatalogCacheEntry(stations, System.currentTimeMillis()))
            println("Radio Browser $countryCode count=${stations.size} logo=${stations.count { it.logoUrl != null }}")
                assertEquals(countryCode, stations.firstOrNull()?.countryCode)
            }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
