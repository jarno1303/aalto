package fi.aalto.radio.catalog

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "catalog_stations",
    primaryKeys = ["sourceQualifiedId"],
    indices = [
        Index(value = ["source", "countryCode"]),
        Index(value = ["countryCode"])
    ]
)
data class CatalogStationEntity(
    val source: String,
    val sourceStationId: String,
    val sourceQualifiedId: String,
    val canonicalName: String,
    val streamUrl: String?,
    val resolvedStreamUrl: String?,
    val homepageUrl: String?,
    val faviconUrl: String?,
    val countryCode: String?,
    val countryName: String?,
    val region: String?,
    val languages: String,
    val rawTags: String,
    val codec: String?,
    val bitrateKbps: Int?,
    val votes: Int?,
    val clickCount: Int?,
    val clickTrend: Int?,
    val lastCheckOk: Boolean?,
    val lastCheckAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val canonicalId: String?,
    val logoCandidates: String,
    val streamAlternatives: String,
    val broadcaster: String?,
    val network: String?,
    val cachedAt: Long
)

@Entity(
    tableName = "catalog_country_cache_metadata",
    primaryKeys = ["source", "countryCode"]
)
data class CatalogCountryCacheMetadataEntity(
    val source: String,
    val countryCode: String,
    val lastSuccessfulRefreshAt: Long?,
    val lastAttemptAt: Long?,
    val stationCount: Int
)
