package fi.aalto.radio.catalog

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class CatalogSearchRequest(
    val query: String,
    val countries: List<String>,
    val tags: Set<String> = emptySet()
) {
    val active: Boolean get() = query.isNotBlank() || tags.isNotEmpty()
}

data class CatalogSearchState(
    val request: CatalogSearchRequest,
    val loading: Boolean = request.active,
    val stations: List<CatalogStation> = emptyList(),
    val worldStations: List<CatalogStation> = emptyList(),
    val error: CatalogError? = null
) {
    // A new query must never display the old query's worldwide results,
    // including the frame before its LaunchedEffect starts.
    fun forRequest(current: CatalogSearchRequest): CatalogSearchState =
        if (request == current) this else CatalogSearchState(current)
}

class CatalogSearchLoader(private val repository: StationCatalogRepository) {
    fun search(request: CatalogSearchRequest): Flow<CatalogSearchState> = flow {
        var state = CatalogSearchState(request)
        emit(state)
        if (!request.active) return@flow
        delay(300)

        val home = fetch(request, request.countries)
        val stations = home.flatMap { it.stations() }.distinctBy { it.stableId }
        val homeError = home.firstNotNullOfOrNull { (it as? CatalogReadResult.Failure)?.error }
        val searchWorld = request.query.isNotBlank() && stations.count { it.matches(request) } < 5
        state = state.copy(loading = searchWorld, stations = stations, error = homeError)
        emit(state)
        if (searchWorld) {
            val worldwide = fetch(request, listOf(null))
            val countries = request.countries.map(String::uppercase).toSet()
            state = state.copy(
                loading = false,
                worldStations = worldwide.flatMap { it.stations() }
                    // The UI applies the complete query/genre selection. Provider
                    // aliases cannot express local exclusions such as "Other".
                    .filter { it.countryCode?.uppercase() !in countries }
                    .distinctBy { it.stableId },
                error = homeError ?: worldwide.firstNotNullOfOrNull { (it as? CatalogReadResult.Failure)?.error }
            )
            emit(state)
        }
    }.catch {
        // Flow.catch does not intercept cancellation of a superseded search.
        emit(CatalogSearchState(request, loading = false,
            error = CatalogError(CatalogErrorKind.PROVIDER_FAILURE, "Search unavailable")))
    }

    private suspend fun fetch(request: CatalogSearchRequest, countries: List<String?>): List<CatalogReadResult> =
        coroutineScope {
            val permits = Semaphore(3)
            countries.flatMap { country ->
                buildList {
                    if (request.query.isNotBlank()) {
                        add(async { permits.withPermit { repository.searchStations(request.query, country, 100) } })
                    }
                    request.tags.forEach { tag ->
                        add(async { permits.withPermit { repository.getStationsByTag(tag, country, 100) } })
                    }
                }
            }.map { it.await() }
        }
}

private fun CatalogReadResult.stations(): List<CatalogStation> =
    (this as? CatalogReadResult.Success)?.snapshot?.stations.orEmpty()

private fun CatalogStation.matches(request: CatalogSearchRequest): Boolean {
    val text = (listOf(canonicalName, region.orEmpty(), countryName.orEmpty(), countryCode.orEmpty()) +
        rawTags + languages).joinToString(" ").lowercase()
    val words = request.query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    return words.all(text::contains) && (request.tags.isEmpty() ||
        request.tags.any { tag -> rawTags.any { it.contains(tag, ignoreCase = true) } })
}
