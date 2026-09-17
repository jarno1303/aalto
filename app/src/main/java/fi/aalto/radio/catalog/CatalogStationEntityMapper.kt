package fi.aalto.radio.catalog

private object CatalogListCodec {
    fun encode(values: List<String>): String {
        return values.joinToString(separator = "") { "${it.length}:$it" }
    }

    fun decode(encoded: String): List<String> {
        val values = mutableListOf<String>()
        var cursor = 0
        while (cursor < encoded.length) {
            val separator = encoded.indexOf(':', cursor)
            if (separator <= cursor) return emptyList()
            val length = encoded.substring(cursor, separator).toIntOrNull() ?: return emptyList()
            val valueStart = separator + 1
            val valueEnd = valueStart + length
            if (length < 0 || valueEnd > encoded.length) return emptyList()
            values += encoded.substring(valueStart, valueEnd)
            cursor = valueEnd
        }
        return values
    }
}

fun CatalogStation.toEntity(cachedAt: Long): CatalogStationEntity {
    return CatalogStationEntity(
        source = source.name,
        sourceStationId = sourceStationId,
        sourceQualifiedId = sourceQualifiedId,
        canonicalName = canonicalName,
        streamUrl = streamUrl,
        resolvedStreamUrl = resolvedStreamUrl,
        homepageUrl = homepageUrl,
        faviconUrl = logoUrl,
        countryCode = countryCode,
        countryName = countryName,
        region = region,
        languages = CatalogListCodec.encode(languages),
        rawTags = CatalogListCodec.encode(rawTags),
        codec = codec,
        bitrateKbps = bitrateKbps,
        votes = votes,
        clickCount = clickCount,
        clickTrend = clickTrend,
        lastCheckOk = lastCheckOk,
        lastCheckAt = lastCheckAt,
        latitude = latitude,
        longitude = longitude,
        canonicalId = canonicalId,
        logoCandidates = CatalogListCodec.encode(logoCandidates),
        streamAlternatives = CatalogListCodec.encode(streamAlternatives),
        broadcaster = broadcaster,
        network = network,
        cachedAt = cachedAt
    )
}

fun CatalogStationEntity.toDomain(): CatalogStation? {
    val catalogSource = runCatching { CatalogSource.valueOf(source) }.getOrNull() ?: return null
    return CatalogStation(
        source = catalogSource,
        sourceStationId = sourceStationId,
        canonicalName = canonicalName,
        streamUrl = streamUrl,
        resolvedStreamUrl = resolvedStreamUrl,
        homepageUrl = homepageUrl,
        logoUrl = faviconUrl,
        countryCode = countryCode,
        countryName = countryName,
        region = region,
        languages = CatalogListCodec.decode(languages),
        rawTags = CatalogListCodec.decode(rawTags),
        codec = codec,
        bitrateKbps = bitrateKbps,
        votes = votes,
        clickCount = clickCount,
        clickTrend = clickTrend,
        lastCheckOk = lastCheckOk,
        lastCheckAt = lastCheckAt,
        latitude = latitude,
        longitude = longitude,
        canonicalId = canonicalId,
        logoCandidates = CatalogListCodec.decode(logoCandidates),
        streamAlternatives = CatalogListCodec.decode(streamAlternatives),
        broadcaster = broadcaster,
        network = network
    )
}

fun CatalogCountryCacheMetadataEntity.toDomain(): CatalogCacheMetadata? {
    val catalogSource = runCatching { CatalogSource.valueOf(source) }.getOrNull() ?: return null
    return CatalogCacheMetadata(
        source = catalogSource,
        countryCode = countryCode,
        lastSuccessfulRefreshAt = lastSuccessfulRefreshAt,
        lastAttemptAt = lastAttemptAt,
        stationCount = stationCount
    )
}
