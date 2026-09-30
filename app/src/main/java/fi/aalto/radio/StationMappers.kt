package fi.aalto.radio

import fi.aalto.radio.catalog.CatalogStation

private const val TAG_SEPARATOR = "|"

private object StationListCodec {
    fun encode(values: List<String>): String =
        values.joinToString(separator = "") { value -> "${value.length}:$value" }

    fun decode(encoded: String): List<String> {
        val values = mutableListOf<String>()
        var cursor = 0
        while (cursor < encoded.length) {
            val separator = encoded.indexOf(':', cursor)
            if (separator <= cursor) return emptyList()
            val length = encoded.substring(cursor, separator).toIntOrNull() ?: return emptyList()
            val start = separator + 1
            val end = start + length
            if (length < 0 || end > encoded.length) return emptyList()
            values += encoded.substring(start, end)
            cursor = end
        }
        return values
    }
}

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
        updatedAt = updatedAt,
        streamAlternatives = StationListCodec.encode(streamAlternatives),
        declaredCodec = declaredCodec,
        declaredBitrateKbps = declaredBitrateKbps
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
        streamAlternatives = streamAlternatives,
        declaredCodec = codec?.trim()?.takeIf { it.isNotBlank() },
        declaredBitrateKbps = bitrateKbps?.takeIf { it > 0 }
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
        streamHealthStatus = streamHealthStatus,
        streamAlternatives = StationListCodec.decode(streamAlternatives),
        declaredCodec = declaredCodec,
        declaredBitrateKbps = declaredBitrateKbps
    )
}
