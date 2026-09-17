package fi.aalto.radio.catalog.quality

import fi.aalto.radio.catalog.CatalogResult
import fi.aalto.radio.catalog.radiobrowser.RadioBrowserCatalogSource
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test

@Ignore("Manual real Radio Browser quality report; never part of the offline unit suite")
class CatalogQualityManualReportTest {
    @Test
    fun reportsTopStationsForCandidateCountries() = runBlocking {
        val source = RadioBrowserCatalogSource()
        val engine = CatalogQualityEngine()
        listOf("FI", "DE", "ES", "GB", "US").forEach { countryCode ->
            when (val result = source.stationsByCountry(countryCode, 10)) {
                is CatalogResult.Success -> {
                    val curated = engine.topStations(result.value, setOf(countryCode), limit = 10)
                    println("Aalto quality $countryCode groups=${engine.curate(result.value).duplicateGroups.size}")
                    curated.forEach { station ->
                        println(
                            "$countryCode | ${station.displayName} | " +
                                "${"%.1f".format(station.qualityScore)} | ${station.primaryCategory} | " +
                                "logo=${station.station.logoUrl != null}"
                        )
                    }
                }
                is CatalogResult.Failure -> println("Aalto quality $countryCode failed=${result.error.kind}")
            }
        }
    }
}
