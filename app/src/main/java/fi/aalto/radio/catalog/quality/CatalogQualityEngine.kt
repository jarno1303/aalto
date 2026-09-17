package fi.aalto.radio.catalog.quality

import fi.aalto.radio.catalog.CatalogStation
import java.net.URI
import java.security.MessageDigest
import kotlin.math.ln

class CatalogQualityEngine(
    private val overrides: Map<String, AaltoStationOverride> = AaltoStationOverrides.entries
) {
    fun curate(
        candidates: List<CatalogStation>,
        filter: CatalogFilter = CatalogFilter()
    ): CuratedCatalog {
        val evaluated = candidates
            .distinctBy { it.sourceQualifiedId }
            .mapNotNull(::evaluate)
        val groups = buildGroups(evaluated)
        val preferredStations = groups.mapNotNull { group ->
            if (group.members.any { member ->
                    filter.matches(member.station.countryCode, member.categories, member.station.languages)
                }
            ) {
                mergeGroup(group)
            } else {
                null
            }
        }.sortedWith(curatedOrdering())
        return CuratedCatalog(preferredStations, groups.sortedBy { it.groupId })
    }

    fun topStations(
        candidates: List<CatalogStation>,
        countries: Set<String> = emptySet(),
        languages: Set<String> = emptySet(),
        limit: Int = 10
    ): List<CuratedStation> {
        return curate(candidates, CatalogFilter(countryCodes = countries, languages = languages)).stations.take(limit.coerceAtLeast(0))
    }

    fun topStationsForCategory(
        candidates: List<CatalogStation>,
        category: AaltoCategory,
        countries: Set<String> = emptySet(),
        languages: Set<String> = emptySet(),
        limit: Int = 10
    ): List<CuratedStation> {
        return curate(
            candidates,
            CatalogFilter(countries, setOf(category), languages)
        ).stations.take(limit.coerceAtLeast(0))
    }

    private fun evaluate(candidate: CatalogStation): CuratedStation? {
        val override = overrides[candidate.sourceQualifiedId]
        if (override?.forceExclude == true) return null
        val cleaned = CatalogMetadataCleaner.clean(candidate.canonicalName)
        val preferredUrl = candidate.preferredStreamUrl?.trim()
        val hasIdentity = candidate.sourceStationId.trim().isNotEmpty()
        val hasName = cleaned.displayName.length >= 2
        val hasStream = preferredUrl?.let { isUsableUrl(it) } == true
        if (!hasIdentity || !hasName || !hasStream) return null
        if (override?.forceInclude != true && (candidate.lastCheckOk == false || cleaned.suspiciousName)) return null

        val displayName = override?.canonicalNameOverride?.trim()?.takeIf { it.isNotEmpty() } ?: cleaned.displayName
        val effectiveStation = candidate.copy(
            canonicalName = displayName,
            homepageUrl = override?.homepageUrlOverride?.trim()?.takeIf { it.isNotEmpty() } ?: candidate.homepageUrl,
            logoUrl = override?.faviconUrlOverride?.trim()?.takeIf { it.isNotEmpty() } ?: candidate.logoUrl
        )
        val taxonomy = override?.categoryOverrides?.let {
            val categories = it.ifEmpty { setOf(AaltoCategory.OTHER) }
            TaxonomyResult(categories, categories.minBy { category -> category.ordinal })
        } ?: CatalogTaxonomy.classify(candidate.rawTags)
        val breakdown = score(effectiveStation, override)
        val groupId = duplicateGroupId(effectiveStation, cleaned.dedupeName)
        return CuratedStation(
            station = effectiveStation,
            displayName = displayName,
            qualityScore = breakdown.total,
            qualityBreakdown = breakdown,
            categories = taxonomy.categories,
            primaryCategory = taxonomy.primaryCategory,
            duplicateGroupId = groupId,
            isPreferredDuplicate = false,
            alternateStations = emptyList(),
            appliedOverride = override
        )
    }

    private fun buildGroups(stations: List<CuratedStation>): List<CanonicalStationGroup> {
        if (stations.isEmpty()) return emptyList()
        val parent = IntArray(stations.size) { it }
        fun find(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        fun union(first: Int, second: Int) {
            val firstRoot = find(first)
            val secondRoot = find(second)
            if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
        }

        val owners = mutableMapOf<String, Int>()
        stations.forEachIndexed { index, curated ->
            duplicateKeys(curated).forEach { key ->
                owners[key]?.let { union(index, it) } ?: owners.put(key, index)
            }
        }

        return stations.indices.groupBy(::find).values.map { memberIndexes ->
            val members = memberIndexes.map { stations[it] }
                .sortedWith(preferredOrdering())
            val preferred = members.first()
            val groupId = canonicalGroupId(members)
            val groupMembers = members.map { member ->
                member.copy(
                    isPreferredDuplicate = member.station.sourceQualifiedId == preferred.station.sourceQualifiedId,
                    alternateStations = members
                        .filter { it.station.sourceQualifiedId != preferred.station.sourceQualifiedId }
                        .map { it.station }
                )
            }
            CanonicalStationGroup(groupId, canonicalDisplayName(members), groupMembers, preferred.station.sourceQualifiedId)
        }
    }

    private fun mergeGroup(group: CanonicalStationGroup): CuratedStation {
        val preferred = group.members.first()
        val members = group.members
        val displayName = group.canonicalName
        val logoUrls = members
            .sortedWith(preferredOrdering())
            .flatMap { member -> member.station.logoUrls }
            .map(String::trim)
            .filter(::isUsableImageUrl)
            .distinct()
        val streamUrls = members
            .sortedWith(preferredOrdering())
            .flatMap { member ->
                listOfNotNull(member.station.preferredStreamUrl, member.station.streamUrl, member.station.resolvedStreamUrl) +
                    member.station.streamAlternatives
            }
            .map(String::trim)
            .filter(::isUsableUrl)
            .distinct()
        val mergedStation = preferred.station.copy(
            canonicalId = group.groupId,
            canonicalName = displayName,
            logoUrl = logoUrls.firstOrNull(),
            logoCandidates = logoUrls.drop(1),
            streamAlternatives = streamUrls.drop(1),
            languages = members.flatMap { it.station.languages }.distinct(),
            rawTags = members.flatMap { it.station.rawTags }.distinct(),
            homepageUrl = members.mapNotNull { it.station.homepageUrl?.trim()?.takeIf(String::isNotBlank) }.firstOrNull()
                ?: preferred.station.homepageUrl,
            broadcaster = members.mapNotNull { it.station.broadcaster?.trim()?.takeIf(String::isNotBlank) }.firstOrNull(),
            network = members.mapNotNull { it.station.network?.trim()?.takeIf(String::isNotBlank) }.firstOrNull()
        )
        val categories = members.flatMap { it.categories }.toSet().ifEmpty { setOf(AaltoCategory.OTHER) }
        return preferred.copy(
            station = mergedStation,
            displayName = displayName,
            duplicateGroupId = group.groupId,
            categories = categories,
            primaryCategory = categories.minBy { it.ordinal },
            isPreferredDuplicate = true,
            alternateStations = members.drop(1).map { it.station }
        )
    }

    private fun duplicateKeys(curated: CuratedStation): List<String> {
        val station = curated.station
        val keys = mutableListOf<String>()
        keys += "source:${station.sourceQualifiedId}"
        station.preferredStreamUrl?.normalizeUrl()?.let { keys += "stream:$it" }
        val country = station.countryCode?.trim()?.uppercase()
        val name = CatalogMetadataCleaner.compactDedupeName(curated.displayName)
        val region = station.region?.trim()?.lowercase().orEmpty()
        if (!country.isNullOrBlank() && name.isNotBlank()) {
            homepageHost(station.homepageUrl)?.let { keys += "homepage:$country:$region:$it:$name" }
            streamHost(station.preferredStreamUrl)?.let { keys += "stream-host:$country:$region:$it:$name" }
            if (region.isNotBlank() && homepageHost(station.homepageUrl) == null && streamHost(station.preferredStreamUrl) == null) {
                keys += "name:$country:$region:$name"
            }
            listOf(station.broadcaster, station.network)
                .mapNotNull { it?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
                .forEach { value -> keys += "network:$country:$value:$name" }
        }
        return keys
    }

    private fun score(station: CatalogStation, override: AaltoStationOverride?): QualityBreakdown {
        val health = when (station.lastCheckOk) {
            true -> 30.0
            false -> 0.0
            null -> 15.0
        }
        val popularity = 20.0 * boundedLog(station.clickCount?.toDouble(), 1_000_000.0)
        val votes = 10.0 * boundedLog(station.votes?.toDouble(), 10_000.0)
        val trend = 10.0 * when (val value = station.clickTrend) {
            null -> 0.5
            else -> (value.coerceIn(-1_000, 1_000) + 1_000) / 2_000.0
        }
        val metadataCompleteness = listOf(
            station.canonicalName.isNotBlank(),
            station.homepageUrl?.isNotBlank() == true,
            station.languages.isNotEmpty(),
            station.rawTags.isNotEmpty()
        ).count { it } / 4.0
        val metadata = 10.0 * metadataCompleteness
        val branding = if (station.logoUrl?.startsWith("http") == true) 5.0 else 0.0
        val audio = 5.0 * audioSignal(station)
        val overrideScore = ((override?.qualityBoost ?: 0.0) - (override?.qualityPenalty ?: 0.0)).coerceIn(-10.0, 10.0)
        return QualityBreakdown(health, popularity, votes, trend, metadata, branding, audio, overrideScore)
    }

    private fun audioSignal(station: CatalogStation): Double {
        val codecSignal = when (station.codec?.trim()?.lowercase()) {
            "mp3", "aac", "aac+", "opus", "vorbis", "ogg", "flac" -> 1.0
            null -> 0.5
            else -> 0.5
        }
        val bitrateSignal = when (val bitrate = station.bitrateKbps) {
            null -> 0.5
            in 1..31 -> 0.35
            in 32..512 -> 1.0
            in 513..1_000 -> 0.7
            else -> 0.1
        }
        return (codecSignal + bitrateSignal) / 2.0
    }

    private fun boundedLog(value: Double?, cap: Double): Double {
        return value?.coerceAtLeast(0.0)?.let { (ln(1.0 + it) / ln(1.0 + cap)).coerceIn(0.0, 1.0) } ?: 0.0
    }

    private fun preferredOrdering(): Comparator<CuratedStation> {
        return compareByDescending<CuratedStation> { it.appliedOverride?.preferredDuplicateMember == true }
            .thenByDescending { it.qualityScore }
            .thenBy { it.station.sourceQualifiedId }
    }

    private fun curatedOrdering(): Comparator<CuratedStation> {
        return compareByDescending<CuratedStation> { it.qualityScore }
            .thenByDescending { it.station.clickCount ?: 0 }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.station.sourceQualifiedId }
    }

    private fun duplicateGroupId(station: CatalogStation, dedupeName: String): String {
        val key = listOf(
            station.countryCode?.trim()?.uppercase().orEmpty(),
            station.region?.trim()?.lowercase().orEmpty(),
            CatalogMetadataCleaner.compactDedupeName(dedupeName)
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return "group:" + digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun canonicalGroupId(members: List<CuratedStation>): String {
        val first = members.first().station
        val key = listOf(
            first.countryCode?.trim()?.uppercase().orEmpty(),
            first.region?.trim()?.lowercase().orEmpty(),
            members.map { CatalogMetadataCleaner.compactDedupeName(it.displayName) }.distinct().sorted().joinToString(","),
            members.flatMap { member ->
                listOfNotNull(
                    homepageHost(member.station.homepageUrl)?.let { "homepage:$it" },
                    streamHost(member.station.preferredStreamUrl)?.let { "stream:$it" },
                    member.station.broadcaster?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let { "broadcaster:$it" },
                    member.station.network?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let { "network:$it" }
                )
            }.distinct().sorted().joinToString(",")
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return "canonical:" + digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun canonicalDisplayName(members: List<CuratedStation>): String {
        return members.map { it.displayName.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .maxWithOrNull(
                compareBy<String> { value -> value.count(Char::isWhitespace) }
                    .thenByDescending(String::length)
                    .thenBy(String::lowercase)
            )
            ?: members.first().displayName
    }

    private fun String.normalizeUrl(): String {
        return trim().lowercase().removeSuffix("/")
    }

    private fun homepageHost(url: String?): String? {
        return runCatching { URI(url?.trim()).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    }

    private fun streamHost(url: String?): String? = homepageHost(url)

    private fun isUsableImageUrl(value: String): Boolean {
        return isUsableUrl(value) && !value.lowercase().endsWith(".svg")
    }

    private fun isUsableUrl(value: String): Boolean {
        return runCatching {
            val uri = URI(value)
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
