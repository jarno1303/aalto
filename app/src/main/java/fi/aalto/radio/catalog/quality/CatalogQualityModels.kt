package fi.aalto.radio.catalog.quality

import fi.aalto.radio.catalog.CatalogStation

const val QUALITY_ALGORITHM_VERSION = 2

data class QualityBreakdown(
    val health: Double,
    val popularity: Double,
    val votes: Double,
    val trend: Double,
    val metadata: Double,
    val branding: Double,
    val audio: Double,
    val override: Double
) {
    val total: Double
        get() = (health + popularity + votes + trend + metadata + branding + audio + override).coerceIn(0.0, 100.0)
}

data class AaltoStationOverride(
    val sourceQualifiedId: String,
    val canonicalNameOverride: String? = null,
    val faviconUrlOverride: String? = null,
    val homepageUrlOverride: String? = null,
    val categoryOverrides: Set<AaltoCategory>? = null,
    val qualityBoost: Double = 0.0,
    val qualityPenalty: Double = 0.0,
    val forceInclude: Boolean = false,
    val forceExclude: Boolean = false,
    val preferredDuplicateMember: Boolean = false
)

object AaltoStationOverrides {
    val entries: Map<String, AaltoStationOverride> = emptyMap()
}

data class CuratedStation(
    val station: CatalogStation,
    val displayName: String,
    val qualityScore: Double,
    val qualityBreakdown: QualityBreakdown,
    val categories: Set<AaltoCategory>,
    val primaryCategory: AaltoCategory,
    val duplicateGroupId: String,
    val isPreferredDuplicate: Boolean,
    val alternateStations: List<CatalogStation>,
    val appliedOverride: AaltoStationOverride?
)

data class CanonicalStationGroup(
    val groupId: String,
    val canonicalName: String,
    val members: List<CuratedStation>,
    val preferredMemberId: String
)

data class CuratedCatalog(
    val stations: List<CuratedStation>,
    val duplicateGroups: List<CanonicalStationGroup>,
    val algorithmVersion: Int = QUALITY_ALGORITHM_VERSION
)
