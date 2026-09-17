package fi.aalto.radio.catalog

const val DEFAULT_CATALOG_RESULT_LIMIT = 200
const val MAX_CATALOG_RESULT_LIMIT = 300

enum class CatalogErrorKind {
    NETWORK_UNAVAILABLE,
    PROVIDER_FAILURE,
    MALFORMED_RESPONSE,
    INVALID_REQUEST
}

data class CatalogError(
    val kind: CatalogErrorKind,
    val message: String
)

sealed interface CatalogResult<out T> {
    data class Success<T>(val value: T) : CatalogResult<T>
    data class Failure(val error: CatalogError) : CatalogResult<Nothing>
}

interface StationCatalogSource {
    suspend fun stationsByCountry(
        countryCode: String,
        limit: Int = DEFAULT_CATALOG_RESULT_LIMIT
    ): CatalogResult<List<CatalogStation>>

    suspend fun search(
        query: String,
        countryCode: String? = null,
        limit: Int = DEFAULT_CATALOG_RESULT_LIMIT
    ): CatalogResult<List<CatalogStation>>
}
