package fi.aalto.radio.catalog.core

import fi.aalto.radio.catalog.CatalogSource
import fi.aalto.radio.catalog.CatalogStation

/**
 * Kuratoitu asema laatumoottorin ymmartamaan muotoon.
 *
 * [CatalogStation.canonicalId] asetetaan tassa, ja laatumoottori kunnioittaa
 * sita: `canonicalGroupId` palauttaa kuratoidun tunnisteen sellaisenaan sen
 * sijaan etta laskisi tiivisteen ryhman kokoonpanosta. Siksi tunniste pysyy
 * samana vaikka Radio Browseriin ilmestyisi tai katoaisi kaksoiskappaleita.
 */
fun CoreCatalogStation.toCatalogStation(countryCode: String): CatalogStation {
    val ensisijainen = streamUrls.first()
    return CatalogStation(
        source = CatalogSource.AALTO,
        sourceStationId = id,
        canonicalId = id,
        canonicalName = name,
        streamUrl = ensisijainen,
        resolvedStreamUrl = ensisijainen,
        streamAlternatives = streamUrls.drop(1),
        homepageUrl = null,
        logoUrl = null,
        countryCode = countryCode,
        countryName = null,
        region = null,
        languages = emptyList(),
        rawTags = emptyList(),
        codec = null,
        bitrateKbps = null,
        votes = null,
        clickCount = null,
        clickTrend = null,
        // Ytimen osoitteet on tarkistettu kasin, joten laatumoottori ei saa
        // pudottaa niita terveyspisteiden puutteessa.
        lastCheckOk = true,
        lastCheckAt = null,
        latitude = null,
        longitude = null,
        broadcaster = broadcaster,
        network = broadcaster
    )
}

fun CoreCatalog.toCatalogStations(): List<CatalogStation> {
    return stations.map { it.toCatalogStation(countryCode) }
}
