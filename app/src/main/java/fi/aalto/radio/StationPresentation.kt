package fi.aalto.radio

import android.os.Bundle
import android.util.Log
import fi.aalto.radio.catalog.CatalogFreshness
import fi.aalto.radio.catalog.CatalogReadResult
import fi.aalto.radio.catalog.CatalogStation
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

internal const val STATION_TRACE_TAG = "AALTO_STATION_TRACE"

internal data class DiscoveryCategory(
    val label: String,
    val matches: (RadioStation) -> Boolean
)

internal val DiscoveryCategories = listOf(
    DiscoveryCategory("Pop") { station -> stationTags(station).any { it in setOf("pop", "popmusic", "top40", "charts", "hotac") } },
    DiscoveryCategory("Rock") { station -> stationTags(station).any { it.contains("rock") } },
    DiscoveryCategory("Dance & elektroninen") { station -> stationTags(station).any { it in setOf("dance", "edm", "electronic", "elektroninen") } },
    DiscoveryCategory("Hip-hop & R&B") { station -> stationTags(station).any { it in setOf("hip-hop", "hiphop", "rap", "rnb", "r&b") } },
    DiscoveryCategory("Uutiset & puhe") { station -> stationTags(station).any { it in setOf("news", "uutiset", "talk", "spoken", "puhe") } },
    DiscoveryCategory("Klassinen") { station -> stationTags(station).any { it in setOf("klassinen", "classical") } },
    DiscoveryCategory("Jazz & soul") { station -> stationTags(station).any { it in setOf("jazz", "soul") } },
    DiscoveryCategory("Country & folk") { station -> stationTags(station).any { it in setOf("country", "folk") } },
    DiscoveryCategory("Lapset & perhe") { station -> stationTags(station).any { it in setOf("lapset", "children", "family") } },
    DiscoveryCategory("Muut") { station ->
        stationTags(station).none {
            it in setOf(
                "pop", "popmusic", "top40", "charts", "hotac", "rock", "dance", "edm",
                "electronic", "elektroninen", "hip-hop", "hiphop", "rap", "rnb", "r&b",
                "news", "uutiset", "talk", "spoken", "puhe", "klassinen", "classical",
                "jazz", "soul", "country", "folk", "lapset", "children", "family"
            )
        }
    }
)

internal fun stationTrace(stage: String, station: RadioStation?, detail: String? = null) {
    if (!BuildConfig.DEBUG) return
    val suffix = detail?.let { " $it" }.orEmpty()
    if (station == null) {
        Log.d(STATION_TRACE_TAG, "$stage station=null$suffix")
        return
    }
    Log.d(
        STATION_TRACE_TAG,
        "$stage name=${station.name} id=${station.id} " +
            "radioBrowserUuid=${station.radioBrowserStationUuid ?: "-"} " +
            "preferredUrl=${station.preferredStreamUrl}$suffix"
    )
}

internal fun stationsForNowPlaying(
    stations: List<RadioStation>,
    selectedStation: RadioStation,
    maxCount: Int = 5
): List<RadioStation> {
    val uniqueStations = dedupeLogicalStations(stations, selectedStation)
    if (maxCount <= 0) return listOf(selectedStation)
    if (uniqueStations.take(maxCount).any { it.id == selectedStation.id }) {
        return uniqueStations.take(maxCount)
    }
    return (uniqueStations.take(maxCount - 1) + selectedStation).distinctBy { it.id }
}

internal fun stationsForDial(
    favoriteStations: List<RadioStation>,
    selectedStation: RadioStation
): List<RadioStation> {
    return dedupeLogicalStations(favoriteStations, selectedStation)
}

internal fun stationsForRadioList(
    stations: List<RadioStation>,
    selectedStation: RadioStation
): List<RadioStation> {
    return dedupeLogicalStations(stations, selectedStation)
        .distinctBy { it.id }
}

internal fun stationsForRadioHome(
    stations: List<RadioStation>,
    selectedStation: RadioStation,
    maxCount: Int = 10
): List<RadioStation> {
    val uniqueStations = stations.distinctBy { it.stableId }
    if (maxCount <= 0) return emptyList()
    if (uniqueStations.take(maxCount).any { it.stableId == selectedStation.stableId }) {
        return uniqueStations.take(maxCount)
    }
    return (uniqueStations.take(maxCount - 1) + selectedStation)
        .distinctBy { it.stableId }
}

internal fun dedupeLogicalStations(
    stations: List<RadioStation>,
    selectedStation: RadioStation
): List<RadioStation> {
    val result = mutableListOf<RadioStation>()
    val indexesByLogicalKey = mutableMapOf<String, Int>()

    stations.forEach { station ->
        val logicalKeys = logicalStationKeys(station)
        val existingIndex = logicalKeys.firstNotNullOfOrNull(indexesByLogicalKey::get)
        if (existingIndex == null) {
            logicalKeys.forEach { indexesByLogicalKey[it] = result.size }
            result += station
        } else {
            val existing = result[existingIndex]
            logicalKeys.forEach { indexesByLogicalKey[it] = existingIndex }
            result[existingIndex] = when {
                station.id == selectedStation.id -> mergeStationPresentation(station, existing)
                existing.id == selectedStation.id -> mergeStationPresentation(existing, station)
                else -> existing
            }
        }
    }

    if (result.none { it.id == selectedStation.id }) {
        val selectedIndex = logicalStationKeys(selectedStation)
            .firstNotNullOfOrNull(indexesByLogicalKey::get)
        if (selectedIndex != null) {
            result[selectedIndex] = mergeStationPresentation(selectedStation, result[selectedIndex])
        } else {
            result += selectedStation
        }
    }
    return result
}

internal fun mergeStationPresentation(
    selected: RadioStation,
    duplicate: RadioStation
): RadioStation {
    return selected.copy(
        faviconUrl = selected.faviconUrl ?: duplicate.faviconUrl,
        description = selected.description.ifBlank { duplicate.description },
        initials = selected.initials.ifBlank { duplicate.initials }
    )
}

internal fun logicalStationKeys(station: RadioStation): List<String> {
    val radioBrowserId = station.radioBrowserStationUuid?.trim()?.lowercase()
    val country = station.countryCode.trim().uppercase()
    val stream = station.preferredStreamUrl.trim().lowercase().removeSuffix("/")
    val normalizedName = station.name.trim().lowercase().replace(Regex("\\s+"), " ")
    val host = runCatching { URI(stream).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    return buildList {
        if (!radioBrowserId.isNullOrBlank()) add("radio-browser:$radioBrowserId")
        if (stream.isNotBlank()) add("stream:$stream")
        if (radioBrowserId.isNullOrBlank() && stream.isBlank()) {
            add("name:$country:$host:$normalizedName")
        }
    }
}

internal fun stationTags(station: RadioStation): Set<String> {
    return (station.tags + station.category)
        .flatMap { it.lowercase().split(Regex("[^a-z0-9&]+")) }
        .filter { it.isNotBlank() }
        .toSet()
}

internal fun stationMatchesQuery(station: RadioStation, query: String): Boolean {
    val searchableText = buildString {
        append(station.name)
        append(' ')
        append(station.description)
        append(' ')
        append(station.category)
        append(' ')
        append(station.countryCode)
        append(' ')
        append(station.tags.joinToString(" "))
        append(' ')
        append(station.languages.joinToString(" "))
    }.lowercase()
    return query.trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .all(searchableText::contains)
}

/**
 * "Suomi", "Finland" or "Finnland" — the country's name in the phone's own
 * language. Used to be a hand-written Finnish table, which left every country
 * name in Finnish no matter what language the app was in.
 */
internal fun countryName(countryCode: String): String {
    val code = countryCode.trim().uppercase()
    if (!code.matches(Regex("[A-Z]{2}"))) return code
    return Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }
}

/** Countries offered for browsing in the car (Maat tab). */
internal val browsableCountryCodes = listOf(
    "FI", "SE", "NO", "DK", "EE", "DE", "AT", "CH", "GB", "IE", "NL", "BE",
    "FR", "ES", "PT", "IT", "GR", "PL", "TR", "US", "CA", "BR", "AU"
)

/**
 * The country chosen in the app's station browser. Remembered, so the car
 * screen shows popular stations from the same country as the phone.
 */
/** Whether the own-stations list is shown on the home screen. Remembered. */
internal object StationListPreference {
    private const val PREFS = "aalto_home"
    private const val KEY = "list_visible"

    fun get(context: android.content.Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY, true)

    fun set(context: android.content.Context, visible: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, visible)
            .apply()
    }
}

/**
 * Whether the one-time "long press removes a station" hint has been seen.
 * A hint that never goes away is noise, so it is shown once.
 */
internal object HomeHintPreference {
    private const val PREFS = "aalto_home"
    private const val KEY = "long_press_hint_seen"

    fun seen(context: android.content.Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY, false)

    fun markSeen(context: android.content.Context) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, true)
            .apply()
    }
}

/**
 * Whether the "you seem to be in X" suggestion has been turned down for that
 * country. Asked once per country, never again.
 */
/**
 * The countries the user keeps an eye on, in the order they chose. Shown as
 * chips in Search, so switching between them is one tap instead of a menu and
 * a fresh search.
 *
 * Deliberately a list of countries rather than one merged list: merging by
 * listener count would let a big market bury a small one, and a Finn looking
 * for Finnish stations would find German ones.
 */
internal object OwnCountriesPreference {
    private const val PREFS = "aalto_catalog"
    private const val KEY = "own_countries"
    const val MAX = 8

    fun get(context: android.content.Context): List<String> {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.split(',')
            ?.map { it.trim().uppercase() }
            ?.filter { it.matches(Regex("[A-Z]{2}")) }
            ?.distinct()
            .orEmpty()
        // Nothing chosen yet: the country the phone is in is a good first one.
        return stored.ifEmpty { listOf(RadioCountryPreference.get(context)) }
    }

    fun set(context: android.content.Context, countryCodes: List<String>) {
        val cleaned = countryCodes
            .map { it.trim().uppercase() }
            .filter { it.matches(Regex("[A-Z]{2}")) }
            .distinct()
            .take(MAX)
        context.applicationContext
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, cleaned.joinToString(","))
            .apply()
    }

    fun add(context: android.content.Context, countryCode: String) {
        set(context, get(context) + countryCode)
    }
}

internal object TravelSuggestion {
    private const val PREFS = "aalto_catalog"
    private const val KEY_PREFIX = "travel_declined_"

    fun declined(context: android.content.Context, countryCode: String): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFIX + countryCode, false)

    fun decline(context: android.content.Context, countryCode: String) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX + countryCode, true)
            .apply()
    }
}

internal object RadioCountryPreference {
    private const val PREFS = "aalto_catalog"
    private const val KEY = "country"

    fun get(context: android.content.Context): String =
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.matches(Regex("[A-Z]{2}")) }
            ?: defaultRadioCountryCode(context)

    fun set(context: android.content.Context, code: String) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, code.uppercase())
            .putBoolean(KEY_ALL_OWN, false)
            .apply()
    }

    /**
     * "Kaikki omat maat": search shows every followed country at once. Off by
     * default, so a new user sees their own country's stations, not all of them.
     */
    fun allOwn(context: android.content.Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_ALL_OWN, false)

    fun setAllOwn(context: android.content.Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALL_OWN, on)
            .apply()
    }

    private const val KEY_ALL_OWN = "all_own"
}

/**
 * The country whose stations to suggest when the user has not chosen one:
 * where the phone actually is, not what language it speaks.
 */
internal fun defaultRadioCountryCode(context: android.content.Context): String =
    DeviceCountry.best(context)

internal fun stationDialTitle(station: RadioStation): String {
    return listOf(
        station.name.trim(),
        stationGenreAndTags(station),
        stationLocation(station)
    ).filter { it.isNotBlank() }.joinToString(" - ")
}

/**
 * One genre, in the app's own words: "Pop", not "hits, local, pop".
 *
 * A curated category ("Klassinen & Kulttuuri") is used as it is. Catalog
 * stations only have raw Radio Browser tags, mixed languages and lower case,
 * so they get the matching discovery category instead, the same labels the
 * search chips use. Nothing matched: the first tag, capitalised.
 */
internal fun stationGenreAndTags(station: RadioStation): String {
    val category = station.category.trim()
    if (category.isNotBlank() && category.first().isUpperCase()) return category
    DiscoveryCategories.dropLast(1)
        .firstOrNull { it.matches(station) }
        ?.let { return it.label }
    val tag = (listOf(category) + station.tags).map { it.trim() }.firstOrNull { it.isNotBlank() }
        ?: return ""
    return tag.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/**
 * One entry per station as the user sees it. The catalog can list a station
 * Aalto already ships (Radio Suomipop twice, two hearts, two tiles). Same name
 * in the same country is the same station for the user; the first one wins,
 * so callers put built-in stations first.
 */
internal fun List<RadioStation>.distinctByListing(): List<RadioStation> =
    distinctBy { it.stableId }
        .distinctBy { station ->
            station.name.trim().lowercase(Locale.ROOT) + "|" + station.countryCode.uppercase(Locale.ROOT)
        }

internal fun stationMetadataLine(station: RadioStation): String {
    return listOf(stationGenreAndTags(station), stationLocation(station))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

internal fun stationLocation(station: RadioStation): String {
    val country = countryName(station.countryCode).takeIf { it.isNotBlank() }
    // The catalog often repeats the country in English ("Finland, Suomi").
    val countryNames = if (station.countryCode.isBlank()) {
        emptySet()
    } else {
        val locale = java.util.Locale("", station.countryCode)
        setOfNotNull(
            country,
            locale.getDisplayCountry(java.util.Locale.ENGLISH),
            locale.getDisplayCountry(java.util.Locale("fi"))
        ).flatMap { name -> listOf(name.lowercase(), "the " + name.lowercase()) }.toSet()
    }
    val places = station.location
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() && it.lowercase() !in countryNames }
        .orEmpty()
    return (places + listOfNotNull(country)).distinct().joinToString(", ")
}
