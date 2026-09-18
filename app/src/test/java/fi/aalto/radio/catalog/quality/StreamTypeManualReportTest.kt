package fi.aalto.radio.catalog.quality

import fi.aalto.radio.catalog.CatalogResult
import fi.aalto.radio.catalog.CatalogStation
import fi.aalto.radio.catalog.radiobrowser.RadioBrowserCatalogSource
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test

/**
 * Answers one question before any work starts on rewinding live radio: how
 * many of the stations people actually listen to are HLS?
 *
 * HLS streams can be rewound inside their live window with what ExoPlayer
 * already does. Plain ICY/MP3 streams cannot be rewound at all without a ring
 * buffer of our own, which is several times the work. The split decides
 * whether "rewind" is a cheap feature or an expensive one.
 *
 * Manual: hits the real Radio Browser API, so it is never part of the offline
 * suite. Run it with
 *   ./gradlew testDebugUnitTest --tests "*StreamTypeManualReportTest*" "-Daalto.manual=true"
 *
 * In PowerShell the -D argument has to be quoted, or AALTO_MANUAL=true can be
 * set in the environment instead.
 */
class StreamTypeManualReportTest {

    @Test
    fun reportsHlsShareOfPopularStations() = runBlocking {
        // Skipped in the normal suite; asked for with -Daalto.manual=true.
        Assume.assumeTrue(System.getProperty("aalto.manual") == "true")
        listOf("FI", "SE", "DE").forEach { countryCode ->
            when (val result = source.stationsByCountry(countryCode, LIMIT)) {
                is CatalogResult.Success -> report(countryCode, result.value)
                is CatalogResult.Failure -> println("$countryCode | FAILED | ${result.error}")
            }
        }
    }

    private fun report(countryCode: String, stations: List<CatalogStation>) {
        // Most listened first, the same order the popular list is built from.
        val ordered = stations.sortedByDescending { it.clickCount ?: 0 }
        val hls = ordered.count(::isHls)
        println("=== $countryCode: HLS $hls / ${ordered.size} ===")
        ordered.forEachIndexed { index, station ->
            println(
                "$countryCode | ${index + 1} | ${station.canonicalName} | " +
                    "hls=${isHls(station)} | codec=${station.codec ?: "-"} | " +
                    "${station.bitrateKbps ?: 0} kbps | ${tail(station)}"
            )
        }
        // The top of the list is what matters: nobody rewinds station 37.
        val topTen = ordered.take(10)
        println("$countryCode | top10 HLS ${topTen.count(::isHls)} / ${topTen.size}")
    }

    private fun isHls(station: CatalogStation): Boolean {
        val url = station.resolvedStreamUrl ?: station.streamUrl.orEmpty()
        return station.codec?.contains("hls", ignoreCase = true) == true ||
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
    }

    private fun tail(station: CatalogStation): String {
        val url = station.resolvedStreamUrl ?: station.streamUrl ?: return "-"
        return url.substringBefore('?').takeLast(40)
    }

    private val source = RadioBrowserCatalogSource()

    private companion object {
        const val LIMIT = 40
    }
}
