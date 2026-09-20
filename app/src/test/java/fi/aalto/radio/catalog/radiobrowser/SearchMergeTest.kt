package fi.aalto.radio.catalog.radiobrowser

import fi.aalto.radio.catalog.CatalogError
import fi.aalto.radio.catalog.CatalogErrorKind
import fi.aalto.radio.catalog.CatalogResult
import fi.aalto.radio.catalog.CatalogSource
import fi.aalto.radio.catalog.CatalogStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMergeTest {

    private fun station(id: String) = CatalogStation(
        source = CatalogSource.RADIO_BROWSER,
        sourceStationId = id,
        canonicalName = id,
        streamUrl = "https://example.com/$id",
        resolvedStreamUrl = null,
        homepageUrl = null,
        logoUrl = null,
        countryCode = "FI",
        countryName = "Finland",
        region = null,
        languages = emptyList(),
        rawTags = emptyList(),
        codec = null,
        bitrateKbps = null,
        votes = null,
        clickCount = null,
        clickTrend = null,
        lastCheckOk = null,
        lastCheckAt = null,
        latitude = null,
        longitude = null
    )

    private val failure = CatalogResult.Failure(CatalogError(CatalogErrorKind.PROVIDER_FAILURE, "down"))

    @Test
    fun nameMatchesComeFirstAndPlaceMatchesAreNotRepeated() {
        val merged = mergeSearchResults(
            CatalogResult.Success(listOf(station("a"), station("b"))),
            CatalogResult.Success(listOf(station("b"), station("c"))),
            limit = 10
        ) as CatalogResult.Success
        assertEquals(listOf("a", "b", "c"), merged.value.map { it.sourceStationId })
    }

    @Test
    fun oneAnswerIsEnough() {
        val merged = mergeSearchResults(failure, CatalogResult.Success(listOf(station("c"))), limit = 10)
        assertTrue(merged is CatalogResult.Success)
    }

    @Test
    fun bothFailingIsAFailure() {
        assertTrue(mergeSearchResults(failure, failure, limit = 10) is CatalogResult.Failure)
    }

    @Test
    fun theLimitHolds() {
        val merged = mergeSearchResults(
            CatalogResult.Success(listOf(station("a"), station("b"))),
            CatalogResult.Success(listOf(station("c"))),
            limit = 2
        ) as CatalogResult.Success
        assertEquals(2, merged.value.size)
    }
}
