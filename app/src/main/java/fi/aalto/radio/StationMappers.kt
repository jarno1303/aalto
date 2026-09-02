package fi.aalto.radio

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
