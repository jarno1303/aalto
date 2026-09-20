package fi.aalto.radio.catalog

enum class CatalogSource {
    RADIO_BROWSER,

    /** Aallon oma kuratoitu ydin: tarkistetut osoitteet ja pysyvat tunnisteet. */
    AALTO
}

data class CatalogStation(
    val source: CatalogSource,
    val sourceStationId: String,
    val canonicalName: String,
    val streamUrl: String?,
    val resolvedStreamUrl: String?,
    val homepageUrl: String?,
    val logoUrl: String?,
    val countryCode: String?,
    val countryName: String?,
    val region: String?,
    val languages: List<String>,
    val rawTags: List<String>,
    val codec: String?,
    val bitrateKbps: Int?,
    val votes: Int?,
    val clickCount: Int?,
    val clickTrend: Int?,
    val lastCheckOk: Boolean?,
    val lastCheckAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val canonicalId: String? = null,
    val logoCandidates: List<String> = emptyList(),
    val streamAlternatives: List<String> = emptyList(),
    val broadcaster: String? = null,
    val network: String? = null
) {
    val sourceQualifiedId: String
        get() = when (source) {
            CatalogSource.RADIO_BROWSER -> "radio-browser:$sourceStationId"
            CatalogSource.AALTO -> "aalto:$sourceStationId"
        }

    val preferredStreamUrl: String?
        get() = resolvedStreamUrl ?: streamUrl

    val stableId: String
        get() = canonicalId?.trim()?.takeIf { it.isNotBlank() } ?: sourceQualifiedId

    val logoUrls: List<String>
        get() = (listOfNotNull(logoUrl) + logoCandidates).distinct()
}
