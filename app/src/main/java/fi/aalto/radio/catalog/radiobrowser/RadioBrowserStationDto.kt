package fi.aalto.radio.catalog.radiobrowser

data class RadioBrowserStationDto(
    val stationUuid: String?,
    val name: String?,
    val url: String?,
    val urlResolved: String?,
    val homepage: String?,
    val favicon: String?,
    val country: String?,
    val countryCode: String?,
    val state: String?,
    val language: String?,
    val languageCodes: String?,
    val tags: String?,
    val codec: String?,
    val bitrate: Int?,
    val votes: Int?,
    val clickCount: Int?,
    val clickTrend: Int?,
    val lastCheckOk: Boolean?,
    val lastCheckTime: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val broadcaster: String? = null,
    val network: String? = null
)
