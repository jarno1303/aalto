package fi.aalto.radio

import fi.aalto.radio.catalog.CatalogStation

private const val TAG_SEPARATOR = "|"

object StationIdentity {
    fun fromRadioBrowserStationUuid(stationUuid: String): String {
        return stationUuid.trim()
    }
}

fun RadioStation.toEntity(updatedAt: Long): StationEntity {
    return StationEntity(
        id = id,
        radioBrowserStationUuid = radioBrowserStationUuid,
        name = name,
        description = description,
        initials = initials,
        logoColorArgb = logoColorArgb,
        streamUrl = streamUrl,
        preferredStreamUrl = preferredStreamUrl,
        lastKnownWorkingStreamUrl = lastKnownWorkingStreamUrl,
        faviconUrl = faviconUrl,
        countryCode = countryCode,
        tags = tags.joinToString(separator = TAG_SEPARATOR),
        category = category,
        streamHealthStatus = streamHealthStatus,
        updatedAt = updatedAt
    )
}

fun CatalogStation.toPlayableRadioStationOrNull(): RadioStation? {
    val stream = preferredStreamUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val displayName = canonicalName.trim().takeIf { it.isNotBlank() } ?: return null
    val initials = displayName
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .take(5)

    return RadioStation(
        id = stableId,
        radioBrowserStationUuid = sourceStationId,
        name = displayName,
        description = region ?: countryName.orEmpty(),
        initials = initials.ifBlank { "A" },
        logoColorArgb = 0xFF68727D,
        streamUrl = stream,
        preferredStreamUrl = stream,
        faviconUrl = logoUrl,
        countryCode = countryCode.orEmpty(),
        tags = rawTags,
        category = rawTags.firstOrNull().orEmpty(),
        languages = languages,
        location = listOfNotNull(region?.trim()?.takeIf { it.isNotBlank() }, countryName?.trim()?.takeIf { it.isNotBlank() })
            .distinct()
            .joinToString(", "),
        lastKnownWorkingStreamUrl = stream,
        logoCandidates = logoCandidates,
        streamAlternatives = streamAlternatives
    )
}

fun StationEntity.toDomain(): RadioStation {
    return RadioStation(
        id = id,
        radioBrowserStationUuid = radioBrowserStationUuid,
        name = name,
        description = description,
        initials = initials,
        logoColorArgb = logoColorArgb,
        streamUrl = streamUrl,
        preferredStreamUrl = preferredStreamUrl ?: streamUrl,
        lastKnownWorkingStreamUrl = lastKnownWorkingStreamUrl,
        faviconUrl = faviconUrl,
        countryCode = countryCode.orEmpty(),
        tags = tags?.split(TAG_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
        category = category,
        streamHealthStatus = streamHealthStatus
    )
}
