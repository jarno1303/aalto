package fi.aalto.radio.catalog.radiobrowser

import fi.aalto.radio.catalog.CatalogError
import fi.aalto.radio.catalog.CatalogErrorKind
import fi.aalto.radio.catalog.CatalogResult
import fi.aalto.radio.catalog.CatalogStation
import fi.aalto.radio.catalog.DEFAULT_CATALOG_RESULT_LIMIT
import fi.aalto.radio.catalog.StationCatalogSource
import fi.aalto.radio.catalog.boundedLimit
import fi.aalto.radio.catalog.normalizeCountryCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RadioBrowserHttpResponse(val statusCode: Int, val body: String)

fun interface RadioBrowserHttpTransport {
    suspend fun get(url: String, userAgent: String): RadioBrowserHttpResponse
}

fun interface RadioBrowserBaseUrlProvider {
    suspend fun baseUrls(): List<String>
}

/**
 * Radio Browser is a handful of volunteer servers behind one DNS name. Asking
 * only "all.api…" meant one address: when that server was down or slow, the
 * whole catalog failed and only the built-in stations were left. Radio
 * Browser's own advice is to look the servers up and try them in turn; the
 * known names are the fallback when DNS lookup itself fails.
 */
class DefaultRadioBrowserBaseUrlProvider(
    private val lookup: suspend () -> List<String> = ::lookUpServerNames
) : RadioBrowserBaseUrlProvider {
    @Volatile
    private var cached: List<String>? = null

    override suspend fun baseUrls(): List<String> {
        cached?.let { return it }
        val found = runCatching { lookup() }.getOrDefault(emptyList())
        return serverOrder(found).also { if (found.isNotEmpty()) cached = it }
    }

    companion object {
        val KNOWN_SERVERS = listOf(
            "fi1.api.radio-browser.info",
            "de1.api.radio-browser.info",
            "de2.api.radio-browser.info",
            "nl1.api.radio-browser.info",
            "at1.api.radio-browser.info"
        )

        /** Looked-up servers in random order, then the known ones not already listed. */
        fun serverOrder(lookedUp: List<String>): List<String> {
            val names = (lookedUp.shuffled() + KNOWN_SERVERS.shuffled())
                .map { it.trim().trimEnd('.').lowercase() }
                .filter { it.endsWith(".api.radio-browser.info") && it != "all.api.radio-browser.info" }
                .distinct()
            return names.map { "https://$it" }
        }

        private suspend fun lookUpServerNames(): List<String> = withContext(Dispatchers.IO) {
            java.net.InetAddress.getAllByName("all.api.radio-browser.info")
                .mapNotNull { address -> runCatching { address.canonicalHostName }.getOrNull() }
                .filter { name -> name.any { it.isLetter() } }
        }
    }
}

class RadioBrowserCatalogSource(
    private val baseUrlProvider: RadioBrowserBaseUrlProvider = DefaultRadioBrowserBaseUrlProvider(),
    private val transport: RadioBrowserHttpTransport = UrlConnectionRadioBrowserTransport(),
    private val userAgent: String = "AaltoRadio/0.1 (station catalog client)"
) : StationCatalogSource {
    override suspend fun stationsByCountry(
        countryCode: String,
        limit: Int
    ): CatalogResult<List<CatalogStation>> {
        val normalizedCode = normalizeCountryCode(countryCode)
            ?: return invalidRequest("Invalid country code")
        return request(
            path = "/json/stations/bycountrycodeexact/${encodePath(normalizedCode)}",
            query = commonQuery(limit)
        )
    }

    override suspend fun search(
        query: String,
        countryCode: String?,
        limit: Int
    ): CatalogResult<List<CatalogStation>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return invalidRequest("Search query is blank")
        val normalizedCode = countryCode?.let(::normalizeCountryCode)
            ?: if (countryCode == null) null else return invalidRequest("Invalid country code")
        val base = commonQuery(limit).toMutableMap()
        normalizedCode?.let { base["countrycode"] = it }
        // By name, and by place ("Tampere", "Bayern"): Radio Browser keeps
        // the place in "state", as the contributor wrote it. Both at once;
        // name matches first. Either one answering is enough.
        return coroutineScope {
            val byName = async { request("/json/stations/search", base + ("name" to normalizedQuery)) }
            val byPlace = async { request("/json/stations/search", base + ("state" to normalizedQuery)) }
            mergeSearchResults(byName.await(), byPlace.await(), limit)
        }
    }

    private suspend fun request(path: String, query: Map<String, String>): CatalogResult<List<CatalogStation>> {
        var lastFailure: CatalogError? = null
        for (baseUrl in baseUrlProvider.baseUrls()) {
            try {
                val url = baseUrl.trimEnd('/') + path + queryString(query)
                val response = transport.get(url, userAgent)
                if (response.statusCode !in 200..299) {
                    lastFailure = CatalogError(CatalogErrorKind.PROVIDER_FAILURE, "Radio Browser HTTP ${response.statusCode}")
                    continue
                }
                return try {
                    CatalogResult.Success(parseStations(response.body))
                } catch (_: Exception) {
                    CatalogResult.Failure(CatalogError(CatalogErrorKind.MALFORMED_RESPONSE, "Invalid station response"))
                }
            } catch (_: IOException) {
                lastFailure = CatalogError(CatalogErrorKind.NETWORK_UNAVAILABLE, "Radio Browser unavailable")
            } catch (_: Exception) {
                lastFailure = CatalogError(CatalogErrorKind.PROVIDER_FAILURE, "Radio Browser request failed")
            }
        }
        return CatalogResult.Failure(lastFailure ?: CatalogError(CatalogErrorKind.NETWORK_UNAVAILABLE, "No catalog server available"))
    }

    private fun commonQuery(limit: Int): Map<String, String> {
        return mapOf(
            "order" to "clickcount",
            "reverse" to "true",
            "limit" to boundedLimit(limit).toString(),
            "hidebroken" to "true"
        )
    }

    companion object {
        private fun encodePath(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun queryString(query: Map<String, String>): String {
            return query.entries.joinToString(prefix = "?", separator = "&") {
                "${URLEncoder.encode(it.key, Charsets.UTF_8.name())}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}"
            }
        }

        private fun parseStations(body: String): List<CatalogStation> {
            val array = JSONArray(body)
            return buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val dto = array.optJSONObject(index)?.toDto() ?: continue
                    dto.toCatalogStationOrNull()?.let(::add)
                }
            }
        }

        private fun JSONObject.toDto(): RadioBrowserStationDto {
            return RadioBrowserStationDto(
                stationUuid = optStringOrNull("stationuuid"),
                name = optStringOrNull("name"),
                url = optStringOrNull("url"),
                urlResolved = optStringOrNull("url_resolved"),
                homepage = optStringOrNull("homepage"),
                favicon = optStringOrNull("favicon"),
                country = optStringOrNull("country"),
                countryCode = optStringOrNull("countrycode"),
                state = optStringOrNull("state"),
                language = optStringOrNull("language"),
                languageCodes = optStringOrNull("languagecodes"),
                tags = optStringOrNull("tags"),
                codec = optStringOrNull("codec"),
                bitrate = optIntOrNull("bitrate"),
                votes = optIntOrNull("votes"),
                clickCount = optIntOrNull("clickcount"),
                clickTrend = optIntOrNull("clicktrend"),
                lastCheckOk = optIntOrNull("lastcheckok")?.let { it == 1 },
                lastCheckTime = optLongOrNull("lastchecktime"),
                latitude = optDoubleOrNull("geo_lat"),
                longitude = optDoubleOrNull("geo_long"),
                broadcaster = optStringOrNull("broadcaster") ?: optStringOrNull("server_name"),
                network = optStringOrNull("network")
            )
        }

        private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it != "null" }
        private fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name)) null else optInt(name, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
        private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name)) null else optLong(name, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE }
        private fun JSONObject.optDoubleOrNull(name: String): Double? = if (isNull(name)) null else optDouble(name, Double.NaN).takeUnless { it.isNaN() }
        private fun invalidRequest(message: String): CatalogResult.Failure = CatalogResult.Failure(CatalogError(CatalogErrorKind.INVALID_REQUEST, message))
    }
}

private class UrlConnectionRadioBrowserTransport : RadioBrowserHttpTransport {
    override suspend fun get(url: String, userAgent: String): RadioBrowserHttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            RadioBrowserHttpResponse(connection.responseCode, body)
        } finally {
            connection.disconnect()
        }
    }
}

/** Name matches first, then place matches not already there; the first failure only if both failed. */
internal fun mergeSearchResults(
    byName: CatalogResult<List<CatalogStation>>,
    byPlace: CatalogResult<List<CatalogStation>>,
    limit: Int
): CatalogResult<List<CatalogStation>> {
    val names = (byName as? CatalogResult.Success)?.value
    val places = (byPlace as? CatalogResult.Success)?.value
    if (names == null && places == null) return byName
    return CatalogResult.Success(
        (names.orEmpty() + places.orEmpty()).distinctBy { it.sourceStationId }.take(limit)
    )
}
