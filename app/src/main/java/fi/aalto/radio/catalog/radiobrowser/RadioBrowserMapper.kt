package fi.aalto.radio.catalog.radiobrowser

import fi.aalto.radio.catalog.CatalogSource
import fi.aalto.radio.catalog.CatalogStation

fun RadioBrowserStationDto.toCatalogStationOrNull(): CatalogStation? {
    val stationId = stationUuid.clean() ?: return null
    val canonicalName = name.clean() ?: return null
    val originalUrl = url.clean().takeIf(::isUsableStreamUrl)
    val resolvedUrl = urlResolved.clean().takeIf(::isUsableStreamUrl)
    if (resolvedUrl == null && originalUrl == null) return null

    return CatalogStation(
        source = CatalogSource.RADIO_BROWSER,
        sourceStationId = stationId,
        canonicalName = canonicalName,
        streamUrl = originalUrl,
        resolvedStreamUrl = resolvedUrl,
        homepageUrl = homepage.clean(),
        logoUrl = favicon.clean(),
        countryCode = countryCode.clean()?.uppercase(),
        countryName = country.clean(),
        region = state.clean(),
        languages = parseValues(language, languageCodes),
        rawTags = parseValues(tags),
        codec = codec.clean(),
        bitrateKbps = bitrate?.takeIf { it > 0 },
        votes = votes?.takeIf { it >= 0 },
        clickCount = clickCount?.takeIf { it >= 0 },
        clickTrend = clickTrend,
        lastCheckOk = lastCheckOk,
        lastCheckAt = lastCheckTime?.takeIf { it >= 0 },
        latitude = latitude?.takeIf { it in -90.0..90.0 },
        longitude = longitude?.takeIf { it in -180.0..180.0 },
        broadcaster = broadcaster.clean(),
        network = network.clean()
    )
}

private fun String?.clean(): String? {
    return this?.trim()?.takeIf { it.isNotEmpty() }
}

private fun parseValues(vararg values: String?): List<String> {
    return values.asSequence()
        .filterNotNull()
        .flatMap { it.split(',', ';', '|').asSequence() }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::lowercase)
        .distinct()
        .toList()
}

private fun isUsableStreamUrl(value: String?): Boolean {
    return value?.let { it.startsWith("http://") || it.startsWith("https://") } == true
}
