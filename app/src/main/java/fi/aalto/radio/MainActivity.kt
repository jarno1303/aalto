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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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

private val AaltoBlue = Color(0xFF1769FF)
private val AaltoLightBackground = Color(0xFFF4F6F8)
private val AaltoLightSurface = Color(0xFFFBFCFD)
private val AaltoLightLine = Color(0xFFE1E7ED)
private val AaltoLightSelectedSurface = Color(0xFFEAF2FF)
private val AaltoLightText = Color(0xFF101317)
private val AaltoLightMuted = Color(0xFF68727D)
private val AaltoLightLogoSurface = Color(0xFFF0F3F6)
private val AaltoDarkBackground = Color(0xFF0E1114)
private val AaltoDarkSurface = Color(0xFF171B20)
private val AaltoDarkLine = Color(0xFF2B333B)
private val AaltoDarkSelectedSurface = Color(0xFF1A2A42)
private val AaltoDarkText = Color(0xFFF3F6F8)
private val AaltoDarkMuted = Color(0xFF9BA6B2)
private val AaltoDarkLogoSurface = Color(0xFF232A31)
private val AaltoNightBlack = Color(0xFF000000)
private val AaltoSpaceXs = 4.dp
private val AaltoSpaceS = 8.dp
private val AaltoSpaceM = 12.dp
private val AaltoSpaceL = 16.dp
private val AaltoSpaceXl = 24.dp
private val AaltoSpaceXxl = 32.dp
private val AaltoScreenHorizontalPadding = AaltoSpaceL
private val AaltoScreenTopPadding = AaltoSpaceL
private val AaltoScreenBottomPadding = AaltoSpaceXl
private val AaltoRowSpacing = AaltoSpaceS
private val AaltoSurfaceRadius = 14.dp
private val AaltoLogoRadius = 12.dp
private const val STATION_TRACE_TAG = "AALTO_STATION_TRACE"

private data class FavoriteDragSession(
    val stationId: String,
    val startOrder: List<String>,
    val startPointerYInViewportPx: Float,
    val pointerOffsetInItemPx: Float,
    val itemTopPx: Int,
    val itemHeightPx: Int
)

private data class DiscoveryCategory(
    val label: String,
    val matches: (RadioStation) -> Boolean
)

private val DiscoveryCategories = listOf(
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

private fun stationTrace(stage: String, station: RadioStation?, detail: String? = null) {
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

private fun dedupeLogicalStations(
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

private fun mergeStationPresentation(
    selected: RadioStation,
    duplicate: RadioStation
): RadioStation {
    return selected.copy(
        faviconUrl = selected.faviconUrl ?: duplicate.faviconUrl,
        description = selected.description.ifBlank { duplicate.description },
        initials = selected.initials.ifBlank { duplicate.initials }
    )
}

private fun logicalStationKeys(station: RadioStation): List<String> {
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

private fun stationTags(station: RadioStation): Set<String> {
    return (station.tags + station.category)
        .flatMap { it.lowercase().split(Regex("[^a-z0-9&]+")) }
        .filter { it.isNotBlank() }
        .toSet()
}

private fun stationMatchesQuery(station: RadioStation, query: String): Boolean {
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

private fun countryName(countryCode: String): String {
    return when (countryCode.uppercase()) {
        "FI" -> "Suomi"
        "DE" -> "Saksa"
        "ES" -> "Espanja"
        "GB" -> "Iso-Britannia"
        "SE" -> "Ruotsi"
        "NO" -> "Norja"
        "US" -> "Yhdysvallat"
        "FR" -> "Ranska"
        "IT" -> "Italia"
        "NL" -> "Alankomaat"
        "TR" -> "Turkki"
        else -> countryCode.uppercase()
    }
}

private fun defaultRadioCountryCode(): String {
    return Locale.getDefault().country
        .trim()
        .uppercase()
        .takeIf { it.matches(Regex("[A-Z]{2}")) }
        ?: "FI"
}

internal fun stationDialTitle(station: RadioStation): String {
    return listOf(
        station.name.trim(),
        stationGenreAndTags(station),
        stationLocation(station)
    ).filter { it.isNotBlank() }.joinToString(" - ")
}

private fun stationGenreAndTags(station: RadioStation): String {
    return buildList {
        station.category.trim().takeIf { it.isNotBlank() }?.let(::add)
        station.tags.map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { tag ->
                if (none { it.equals(tag, ignoreCase = true) }) add(tag)
            }
    }.take(3).joinToString(", ")
}

private fun stationMetadataLine(station: RadioStation): String {
    return listOf(stationGenreAndTags(station), stationLocation(station))
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun stationLocation(station: RadioStation): String {
    return listOfNotNull(
        station.location?.trim()?.takeIf { it.isNotBlank() },
        countryName(station.countryCode).takeIf { it.isNotBlank() }
    ).distinct().joinToString(", ")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AaltoPerf.markAppStart()
        super.onCreate(savedInstanceState)
        setContent {
            AaltoTheme {
                AaltoApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AaltoAppContainer.syncCoordinator(this).onForeground()
    }

    override fun onStop() {
        AaltoAppContainer.syncCoordinator(this).onBackground()
        super.onStop()
    }
}

@Composable
private fun AaltoApp() {
    val context = LocalContext.current
    val repository = remember(context) {
        AaltoAppContainer.stationRepository(context)
    }
    val catalogRepository = remember(context) {
        AaltoAppContainer.stationCatalogRepository(context)
    }
    val coroutineScope = rememberCoroutineScope()
    val radioPlayer = rememberRadioPlayer()
    val syncCoordinator = remember(context) { AaltoAppContainer.syncCoordinator(context) }
    val syncState by syncCoordinator.state.collectAsState()
    var showSyncSettings by rememberSaveable { mutableStateOf(false) }
    var nightScreenActive by remember { mutableStateOf(false) }
    var catalogStations by remember { mutableStateOf<List<CatalogStation>>(emptyList()) }
    var catalogSearchStations by remember { mutableStateOf<List<CatalogStation>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }

    var selectedStationId by rememberSaveable {
        mutableStateOf(StationCatalog.DEFAULT_STATION_ID)
    }

    var favoriteIds by remember {
        mutableStateOf(repository.initialFavoriteIds())
    }

    var favoriteOrder by remember {
        mutableStateOf(repository.initialFavoriteIds().toList())
    }

    var selectedTab by rememberSaveable {
        mutableStateOf(0)
    }

    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    var radioCountryCode by rememberSaveable {
        mutableStateOf(defaultRadioCountryCode())
    }

    LaunchedEffect(catalogRepository, radioCountryCode) {
        catalogLoading = true
        catalogError = null
        catalogStations = emptyList()

        when (val result = catalogRepository.getStationsByCountry(radioCountryCode, limit = 100)) {
            is CatalogReadResult.Success -> {
                catalogStations = result.snapshot.stations
                if (result.snapshot.freshness == CatalogFreshness.STALE) {
                    when (val refresh = catalogRepository.refreshCountry(radioCountryCode, limit = 100)) {
                        is fi.aalto.radio.catalog.CatalogResult.Success -> {
                            catalogStations = refresh.value
                        }
                        is fi.aalto.radio.catalog.CatalogResult.Failure -> Unit
                    }
                }
            }
            is CatalogReadResult.Failure -> {
                catalogError = result.error.message
            }
        }
        catalogLoading = false
    }

    LaunchedEffect(catalogRepository, radioCountryCode, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            catalogSearchStations = emptyList()
            return@LaunchedEffect
        }

        delay(300)
        when (val result = catalogRepository.searchStations(query, radioCountryCode, limit = 100)) {
            is CatalogReadResult.Success -> catalogSearchStations = result.snapshot.stations
            is CatalogReadResult.Failure -> catalogSearchStations = emptyList()
        }
    }

    val catalogRadioStations = remember(catalogStations, catalogSearchStations) {
        (catalogStations + catalogSearchStations)
            .mapNotNull { it.toPlayableRadioStationOrNull() }
            .distinctBy { it.stableId }
    }
    LaunchedEffect(catalogRadioStations) {
        repository.registerCatalogStations(catalogRadioStations)
    }
    val stations = remember(repository.stations, catalogRadioStations) {
        (repository.stations + catalogRadioStations).distinctBy { it.stableId }
    }

    val selectedStation = repository.stationById(selectedStationId) ?: run {
        val fallback = stations.first()
        stationTrace(
            stage = "selected_station_fallback",
            station = fallback,
            detail = "requestedId=$selectedStationId"
        )
        fallback
    }

    val favoriteStations = buildList {
        favoriteOrder.mapNotNullTo(this) { id -> repository.stationById(id)?.takeIf { it.id in favoriteIds } }
        stations.filter { it.id in favoriteIds && it.id !in favoriteOrder }.sortedBy { it.id }.forEach(::add)
    }
    val radioStations = stationsForRadioList(
        stations = favoriteStations + stations,
        selectedStation = selectedStation
    )
    val radioHomeStations = remember(radioStations, selectedStation) {
        stationsForRadioHome(radioStations, selectedStation, maxCount = 10)
    }

    LaunchedEffect(Unit) {
        withFrameNanos {
            AaltoPerf.reportMainUiReady()
        }
    }

    LaunchedEffect(repository) {
        repository.prepareLocalData()
        repository.observeFavoriteIds().collect { persistedFavoriteIds ->
            favoriteIds = persistedFavoriteIds
        }
    }

    LaunchedEffect(repository) {
        repository.observeFavoriteOrder().collect { favoriteOrder = it }
    }

    LaunchedEffect(syncCoordinator) {
        syncCoordinator.onForeground()
    }

    fun playStation(station: RadioStation, openNowPlaying: Boolean) {
        stationTrace("final_playback_callback", station)
        val trace = AaltoPerf.beginStationTap(station)
        radioPlayer.play(station, trace)
        if (StationCatalog.stationById(station.id) == null) {
            repository.registerCatalogStations(listOf(station))
        }
        selectedStationId = station.id
        stationTrace("ui_current_state_after_click", station)

        if (openNowPlaying) {
            selectedTab = 0
        }

        coroutineScope.launch {
            runCatching {
                repository.recordRecentlyPlayed(station.id)
            }
        }
    }

    fun toggleFavorite(station: RadioStation) {
        val previousFavoriteIds = favoriteIds
        val updatedFavoriteIds = FavoriteIds.toggle(favoriteIds, station.id)
        favoriteIds = updatedFavoriteIds

        coroutineScope.launch {
            val result = runCatching {
                if (station.id in previousFavoriteIds) {
                    repository.removeFavorite(station.id)
                } else {
                    repository.addFavorite(station.id)
                }
            }

            result.onSuccess { committed ->
                if (committed) {
                    syncCoordinator.requestSync()
                } else {
                    favoriteIds = previousFavoriteIds
                }
            }.onFailure {
                favoriteIds = previousFavoriteIds
            }
        }
    }

    NightScreenSystemBars(active = nightScreenActive)
    BackHandler(enabled = nightScreenActive) {
        nightScreenActive = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AaltoBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { paddingValues ->
            when (selectedTab) {
                0 -> RadioScreen(
                    paddingValues = paddingValues,
                    selectedStation = selectedStation,
                    isPlaying = radioPlayer.isPlaying,
                    playbackError = radioPlayer.playbackError,
                    favoriteIds = favoriteIds,
                    stations = radioHomeStations,
                    onPlayPause = { radioPlayer.toggle(selectedStation) },
                    onFavorite = { toggleFavorite(selectedStation) },
                    onFind = { selectedTab = 1 },
                    onStationClick = { station ->
                        playStation(station, openNowPlaying = false)
                    },
                    onStationFavoriteClick = ::toggleFavorite,
                    onOpenSync = { showSyncSettings = true },
                    onNightScreen = { nightScreenActive = true }
                )

                1 -> SearchScreen(
                    paddingValues = paddingValues,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    stations = stations,
                    radioCountryCode = radioCountryCode,
                    onRadioCountryChange = { radioCountryCode = it },
                    selectedStation = selectedStation,
                    favoriteIds = favoriteIds,
                    catalogStations = catalogStations,
                    catalogLoading = catalogLoading,
                    catalogError = catalogError,
                    onStationClick = { station ->
                        stationTrace(
                            stage = "search_lookup",
                            station = station,
                            detail = "requestedId=${station.id} found=true"
                        )
                        playStation(station, openNowPlaying = true)
                    },
                    onStationFavoriteClick = ::toggleFavorite
                )

                2 -> FavoritesScreen(
                    paddingValues = paddingValues,
                    favoriteStations = favoriteStations,
                    selectedStation = selectedStation,
                    onStationClick = { station ->
                        playStation(station, openNowPlaying = true)
                    },
                    onStationFavoriteClick = ::toggleFavorite,
                    onReorder = { orderedIds ->
                        coroutineScope.launch {
                            val committed = runCatching { repository.reorderFavorites(orderedIds) }.getOrDefault(false)
                            if (committed) syncCoordinator.requestSync()
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = nightScreenActive,
            enter = fadeIn(animationSpec = tween(durationMillis = 140)),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            NightScreen(
                station = selectedStation,
                isPlaying = radioPlayer.isPlaying,
                playbackError = radioPlayer.playbackError,
                onExit = { nightScreenActive = false }
            )
        }
    }

    if (showSyncSettings) {
        SyncSettingsDialog(
            state = syncState,
            onSignIn = {
                (context as? ComponentActivity)?.let { activity ->
                    coroutineScope.launch { syncCoordinator.signIn(activity) }
                }
            },
            onSignOut = syncCoordinator::signOut,
            onDismiss = { showSyncSettings = false }
        )
    }
}

@Composable
private fun RadioScreen(
    paddingValues: PaddingValues,
    selectedStation: RadioStation,
    isPlaying: Boolean,
    playbackError: String?,
    favoriteIds: Set<String>,
    stations: List<RadioStation>,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    onFind: () -> Unit,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit,
    onOpenSync: () -> Unit,
    onNightScreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(
                start = AaltoScreenHorizontalPadding,
                end = AaltoScreenHorizontalPadding,
                top = AaltoScreenTopPadding,
                bottom = AaltoScreenBottomPadding
            ),
        verticalArrangement = Arrangement.spacedBy(AaltoSpaceS)
    ) {
        TopBar(onOpenSync)

        NowPlaying(
            station = selectedStation,
            isPlaying = isPlaying,
            isFavorite = selectedStation.stableId in favoriteIds,
            playbackError = playbackError,
            onPlayPause = onPlayPause,
            onFavorite = onFavorite,
            onNightScreen = onNightScreen,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        SectionHeader(
            title = "Suositut asemat",
            action = "Hae",
            onAction = onFind
        )

        val carouselState = rememberLazyListState()
        val snapFlingBehavior = rememberSnapFlingBehavior(carouselState)
        LazyRow(
            state = carouselState,
            flingBehavior = snapFlingBehavior,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AaltoSpaceXs, vertical = AaltoSpaceXs),
            horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS)
        ) {
            items(
                items = stations.distinctBy { it.stableId },
                key = { it.stableId },
                contentType = { "station-card" }
            ) { station ->
                StationCard(
                    station = station,
                    isSelected = station.stableId == selectedStation.stableId,
                    isFavorite = station.stableId in favoriteIds,
                    onClick = { onStationClick(station) },
                    onFavoriteClick = { onStationFavoriteClick(station) }
                )
            }
        }
    }
}

@Composable
private fun SearchScreen(
    paddingValues: PaddingValues,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    stations: List<RadioStation>,
    radioCountryCode: String,
    onRadioCountryChange: (String) -> Unit,
    selectedStation: RadioStation,
    favoriteIds: Set<String>,
    catalogStations: List<CatalogStation>,
    catalogLoading: Boolean,
    catalogError: String?,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit
) {
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf("Kaikki") }

    val searchableStations = remember(stations, catalogStations, selectedStation) {
        stationsForRadioList(
            stations = stations + catalogStations.mapNotNull { it.toPlayableRadioStationOrNull() },
            selectedStation = selectedStation
        ).distinctBy { it.stableId }
    }
    val countryCodes = (listOf("FI", "DE", "SE", "NO", "GB", "US") +
        searchableStations.map { it.countryCode.uppercase() })
        .filter { it.isNotBlank() }
        .distinct()
    val activeCountryCode = radioCountryCode.takeIf { it in countryCodes } ?: countryCodes.firstOrNull().orEmpty()
    val activeCategory = DiscoveryCategories.firstOrNull { it.label == categoryFilter }
    val listState = rememberLazyListState()
    val resultStations = remember(
        searchableStations,
        activeCountryCode,
        categoryFilter,
        searchQuery
    ) {
        searchableStations
            .filter { it.countryCode.equals(activeCountryCode, ignoreCase = true) }
            .filter { station -> activeCategory?.matches?.invoke(station) ?: true }
            .filter { searchQuery.isBlank() || stationMatchesQuery(it, searchQuery) }
            .distinctBy { it.stableId }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(
            start = AaltoScreenHorizontalPadding,
            end = AaltoScreenHorizontalPadding,
            top = AaltoScreenTopPadding,
            bottom = AaltoScreenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AaltoRowSpacing)
    ) {
        item {
            Text(
                text = "Hae",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Asema tai genre") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(AaltoSurfaceRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = AaltoBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { countryMenuExpanded = true }) {
                        Text(
                            text = countryName(activeCountryCode),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = countryMenuExpanded,
                        onDismissRequest = { countryMenuExpanded = false }
                    ) {
                        countryCodes.forEach { countryCode ->
                            DropdownMenuItem(
                                text = { Text(countryName(countryCode)) },
                                onClick = {
                                    onRadioCountryChange(countryCode)
                                    countryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (catalogLoading) {
            item {
                Text(
                    text = "Ladataan asemia...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        if (catalogError != null && catalogStations.isEmpty()) {
            item {
                Text(
                    text = "Katalogi ei ole saatavilla. Paikalliset asemat toimivat silti.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (listOf("Kaikki") + DiscoveryCategories.map { it.label }).forEach { label ->
                    FilterChip(
                        selected = categoryFilter == label,
                        onClick = { categoryFilter = label },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }

        item {
            Text(
                text = if (searchQuery.isBlank() && categoryFilter == "Kaikki") "Asemat" else "Tulokset",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = AaltoSpaceS)
            )
        }

        items(
            items = resultStations,
            key = { station -> station.stableId },
            contentType = { "station-row" }
        ) { station ->
            stationTrace("search_row", station)
            StationRow(
                station = station,
                isSelected = station.stableId == selectedStation.stableId,
                isFavorite = station.stableId in favoriteIds,
                onClick = {
                    stationTrace("search_click", station)
                    onStationClick(station)
                },
                onFavoriteClick = { onStationFavoriteClick(station) }
            )
        }

        if (resultStations.isEmpty()) {
            item {
                Text(
                    text = "Ei asemia tällä rajauksella",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = AaltoSpaceL)
                )
            }
        }
    }
}

@Composable
private fun FavoritesScreen(
    paddingValues: PaddingValues,
    favoriteStations: List<RadioStation>,
    selectedStation: RadioStation,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val favoriteStationIds = favoriteStations.map { it.id }
    var workingOrder by remember(favoriteStationIds) {
        mutableStateOf(favoriteStationIds)
    }
    var dragSession by remember { mutableStateOf<FavoriteDragSession?>(null) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }
    var listHeightPx by remember { mutableIntStateOf(0) }
    var suppressedClickStationId by remember { mutableStateOf<String?>(null) }
    val latestFavoriteStationIds = rememberUpdatedState(favoriteStationIds)
    val latestOnReorder = rememberUpdatedState(onReorder)
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowExtentPx = with(density) { 78.dp.toPx() }
    val autoScrollEdgePx = with(density) { 76.dp.toPx() }
    val maxAutoScrollPerFramePx = with(density) { 18.dp.toPx() }
    val stationsById = favoriteStations.associateBy { it.id }
    val visibleStations = FavoriteReorder.normalizeWorkingOrder(
        orderedIds = workingOrder,
        activeFavoriteIds = favoriteStationIds
    ).mapNotNull { stationsById[it] }
    val draggingStationId = dragSession?.stationId

    LaunchedEffect(favoriteStationIds) {
        if (dragSession == null) {
            workingOrder = FavoriteReorder.normalizeWorkingOrder(
                orderedIds = workingOrder,
                activeFavoriteIds = favoriteStationIds
            )
        }
    }

    LaunchedEffect(suppressedClickStationId) {
        val stationId = suppressedClickStationId ?: return@LaunchedEffect
        delay(250)
        if (suppressedClickStationId == stationId) {
            suppressedClickStationId = null
        }
    }

    fun updateDragTarget() {
        val session = dragSession ?: return
        val orderedIds = workingOrder
        val itemHeightPx = favoriteItemInfo(listState, session.stationId)?.size
            ?: session.itemHeightPx
        val draggedTopPx = session.startPointerYInViewportPx +
            dragDistancePx -
            session.pointerOffsetInItemPx
        val draggedCenterPx = draggedTopPx + itemHeightPx / 2f
        val targetIndex = FavoriteReorder.targetIndexForDraggedCenter(
            orderedIds = orderedIds,
            draggedId = session.stationId,
            draggedCenterPx = draggedCenterPx,
            visibleAnchors = favoriteVisibleAnchors(listState),
            fallbackItemExtentPx = rowExtentPx
        )
        val nextOrder = FavoriteReorder.moveDraggedItem(
            orderedIds = orderedIds,
            draggedId = session.stationId,
            targetIndex = targetIndex
        )
        if (nextOrder != orderedIds) {
            workingOrder = nextOrder
        }
    }

    fun clearDrag(commit: Boolean) {
        val session = dragSession ?: return
        val committedOrder = FavoriteReorder.normalizeWorkingOrder(
            orderedIds = workingOrder,
            activeFavoriteIds = latestFavoriteStationIds.value
        )
        dragSession = null
        dragDistancePx = 0f
        suppressedClickStationId = session.stationId

        if (commit) {
            workingOrder = committedOrder
            if (committedOrder != session.startOrder) {
                if (BuildConfig.DEBUG) {
                    Log.d("AALTO_SYNC", "favorite_reorder_commit_requested order=$committedOrder")
                }
                latestOnReorder.value(committedOrder)
            }
        } else {
            workingOrder = FavoriteReorder.normalizeWorkingOrder(
                orderedIds = session.startOrder,
                activeFavoriteIds = latestFavoriteStationIds.value
            )
        }
    }

    LaunchedEffect(dragSession?.stationId) {
        while (dragSession != null) {
            withFrameNanos { }
            val session = dragSession ?: break
            if (listHeightPx <= 0) continue

            val pointerY = session.startPointerYInViewportPx + dragDistancePx
            val topDistance = autoScrollEdgePx - pointerY
            val bottomDistance = pointerY - (listHeightPx - autoScrollEdgePx)
            val scrollDelta = when {
                topDistance > 0f -> -favoriteAutoScrollDeltaPx(
                    distanceIntoEdgePx = topDistance,
                    edgePx = autoScrollEdgePx,
                    maxDeltaPx = maxAutoScrollPerFramePx
                )
                bottomDistance > 0f -> favoriteAutoScrollDeltaPx(
                    distanceIntoEdgePx = bottomDistance,
                    edgePx = autoScrollEdgePx,
                    maxDeltaPx = maxAutoScrollPerFramePx
                )
                else -> 0f
            }

            if (scrollDelta != 0f) {
                listState.scrollBy(scrollDelta)
                updateDragTarget()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .onSizeChanged { listHeightPx = it.height },
        contentPadding = PaddingValues(
            start = AaltoScreenHorizontalPadding,
            end = AaltoScreenHorizontalPadding,
            top = AaltoScreenTopPadding,
            bottom = AaltoScreenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AaltoRowSpacing)
    ) {
        item {
            Text(
                text = "Suosikit",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (favoriteStations.isEmpty()) {
            item {
                Text(
                    text = "Ei suosikkeja viela",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = AaltoSpaceXs, bottom = AaltoSpaceXs)
                )
            }
        } else {
            items(
                items = visibleStations,
                key = { it.stableId },
                contentType = { "station-row" }
            ) { station ->
                val isDragging = draggingStationId == station.id
                val rowModifier = Modifier
                    .then(
                        if (isDragging) {
                            Modifier
                        } else {
                            Modifier.animateItem(
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    )
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        val session = dragSession
                        translationY = if (isDragging && session != null) {
                            favoriteDragTranslationYPx(
                                listState = listState,
                                session = session,
                                dragDistancePx = dragDistancePx
                            )
                        } else {
                            0f
                        }
                    }
                    .pointerInput(station.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val itemInfo = favoriteItemInfo(listState, station.id)
                                val itemTopPx = itemInfo?.offset ?: 0
                                val itemHeightPx = itemInfo?.size ?: size.height
                                dragSession = FavoriteDragSession(
                                    stationId = station.id,
                                    startOrder = workingOrder,
                                    startPointerYInViewportPx = itemTopPx + offset.y,
                                    pointerOffsetInItemPx = offset.y,
                                    itemTopPx = itemTopPx,
                                    itemHeightPx = itemHeightPx
                                )
                                dragDistancePx = 0f
                                suppressedClickStationId = station.id
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragCancel = {
                                clearDrag(commit = false)
                            },
                            onDragEnd = {
                                clearDrag(commit = true)
                            },
                            onDrag = { change, dragAmount ->
                                if (dragSession?.stationId != station.id) return@detectDragGesturesAfterLongPress
                                change.consume()
                                dragDistancePx += dragAmount.y
                                updateDragTarget()
                            }
                        )
                    }
                StationRow(
                    station = station,
                    isSelected = station.id == selectedStation.id,
                    isFavorite = true,
                    onClick = {
                        if (dragSession == null && suppressedClickStationId != station.id) {
                            onStationClick(station)
                        }
                    },
                    onFavoriteClick = {
                        if (dragSession == null && suppressedClickStationId != station.id) {
                            onStationFavoriteClick(station)
                        }
                    },
                    modifier = rowModifier,
                    isDragging = isDragging
                )
            }
        }
    }
}

private fun favoriteItemInfo(
    listState: LazyListState,
    stationId: String
) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == stationId }

private fun favoriteVisibleAnchors(listState: LazyListState): List<FavoriteItemAnchor> {
    return listState.layoutInfo.visibleItemsInfo.mapNotNull { itemInfo ->
        val stationId = itemInfo.key as? String ?: return@mapNotNull null
        FavoriteItemAnchor(
            id = stationId,
            topPx = itemInfo.offset,
            heightPx = itemInfo.size
        )
    }
}

private fun favoriteDragTranslationYPx(
    listState: LazyListState,
    session: FavoriteDragSession,
    dragDistancePx: Float
): Float {
    val itemTopPx = favoriteItemInfo(listState, session.stationId)?.offset ?: session.itemTopPx
    val desiredTopPx = session.startPointerYInViewportPx +
        dragDistancePx -
        session.pointerOffsetInItemPx
    return desiredTopPx - itemTopPx
}

private fun favoriteAutoScrollDeltaPx(
    distanceIntoEdgePx: Float,
    edgePx: Float,
    maxDeltaPx: Float
): Float {
    val edgeProgress = (distanceIntoEdgePx / edgePx).coerceIn(0f, 1f)
    return maxDeltaPx * (0.18f + 0.82f * edgeProgress * edgeProgress)
}

@Composable
private fun AaltoBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = AaltoSpaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AaltoNavigationItem(
                selected = selectedTab == 0,
                label = "Radio",
                icon = Icons.Outlined.Radio,
                onClick = { onTabSelected(0) }
            )
            AaltoNavigationItem(
                selected = selectedTab == 1,
                label = "Hae",
                icon = Icons.Default.Search,
                onClick = { onTabSelected(1) }
            )
            AaltoNavigationItem(
                selected = selectedTab == 2,
                label = "Suosikit",
                icon = Icons.Outlined.FavoriteBorder,
                onClick = { onTabSelected(2) }
            )
        }
    }
}

@Composable
private fun RowScope.AaltoNavigationItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 140),
        label = "navigationItemColor"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .clickable(
                onClickLabel = label,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) AaltoBlue else Color.Transparent)
        )
        Spacer(modifier = Modifier.height(AaltoSpaceXs))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun TopBar(onOpenSync: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(AaltoBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Radio,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(AaltoSpaceS))

        Text(
            text = "Aalto",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenSync) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Aalto Sync",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncSettingsDialog(
    state: SyncUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aalto Sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keep your stations on all your devices.")
                if (state.accountEmail != null) Text(state.accountEmail)
                Text(state.error ?: state.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            if (state.accountEmail == null) {
                Button(onClick = onSignIn) { Text("Sign in with Google") }
            } else {
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
@Suppress("DEPRECATION")
private fun NightScreenSystemBars(active: Boolean) {
    val activity = LocalActivity.current ?: return

    DisposableEffect(activity, active) {
        val window = activity.window
        val previousStatusBarColor = window.statusBarColor
        val previousNavigationBarColor = window.navigationBarColor
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller.isAppearanceLightNavigationBars

        if (active) {
            window.statusBarColor = AaltoNightBlack.toArgb()
            window.navigationBarColor = AaltoNightBlack.toArgb()
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }

        onDispose {
            window.statusBarColor = previousStatusBarColor
            window.navigationBarColor = previousNavigationBarColor
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavigationBars
        }
    }
}

@Composable
private fun NightScreen(
    station: RadioStation,
    isPlaying: Boolean,
    playbackError: String?,
    onExit: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val playbackText = when {
        playbackError != null -> "Toisto keskeytynyt"
        isPlaying -> "Toistaa"
        else -> "Tauko"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AaltoNightBlack)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Palaa normaaliin näkymään",
                onClick = onExit
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = AaltoSpaceXxl, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(station.logoColorArgb).copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = station.initials,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = station.name,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = playbackText,
                color = if (playbackError == null) {
                    Color.White.copy(alpha = 0.36f)
                } else {
                    Color(0xFFFFB8B8).copy(alpha = 0.58f)
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StationLogo(
    station: RadioStation,
    size: Dp,
    cornerRadius: Dp,
    framed: Boolean = true
) {
    val logo = rememberStationLogo(station)
    val stationColor = Color(station.logoColorArgb)
    val shape = RoundedCornerShape(cornerRadius)
    val fallbackTextColor = if (stationColor.luminance() > 0.58f) {
        MaterialTheme.colorScheme.onSurface
    } else {
        stationColor
    }

    val logoSurfaceColor = when {
        !framed -> Color.Transparent
        logo != null -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val logoBorder = when {
        !framed || logo != null -> null
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(logoSurfaceColor)
            .then(
                if (logoBorder != null) {
                    Modifier.border(logoBorder, shape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    when {
                        !framed && logo != null -> 0.dp
                        !framed -> AaltoSpaceM
                        else -> AaltoSpaceXs
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (logo != null) {
                Image(
                    bitmap = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = station.initials.ifBlank { "A" },
                    color = fallbackTextColor,
                    fontSize = if (!framed) 28.sp else 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun rememberStationLogo(station: RadioStation): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val stationUuid = station.radioBrowserStationUuid ?: station.id
    val logoUrl = station.logoUrl
    val logoUrls = remember(station.id, logoUrl, station.logoCandidates) {
        station.logoUrls
    }
    var logo by remember(station.id, logoUrls) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(station.id, stationUuid, logoUrls) {
        logo = StationLogoResolver.resolve(context, stationUuid, logoUrls)
    }

    return logo
}

@Composable
private fun NowPlaying(
    station: RadioStation,
    isPlaying: Boolean,
    isFavorite: Boolean,
    playbackError: String?,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    onNightScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    stationTrace("ui_current_state", station)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onFavorite,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = if (isFavorite) {
                        "Poista suosikeista"
                    } else {
                        "Lisaa suosikki"
                    },
                    tint = if (isFavorite) {
                        AaltoBlue
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
            NightScreenTrigger(onNightScreen = onNightScreen)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val logoSize = minOf(148.dp, maxWidth * 0.44f, maxHeight * 0.40f).coerceAtLeast(104.dp)
            val playSize = if (maxHeight < 300.dp) 76.dp else 88.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NowPlayingLogo(
                    station = station,
                    isPlaying = isPlaying,
                    logoSize = logoSize
                )

                Spacer(modifier = Modifier.height(AaltoSpaceXl))

                Text(
                    text = station.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AaltoSpaceL)
                )

                Spacer(modifier = Modifier.height(AaltoSpaceS))

                Text(
                    text = when {
                        playbackError != null -> "Häiriö"
                        isPlaying -> "Nyt soi"
                        else -> "Tauko"
                    },
                    color = when {
                        playbackError != null -> Color(0xFFFFB8B8)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Light
                )

                Spacer(modifier = Modifier.height(AaltoSpaceXxl))

                NowPlayingPlayPauseButton(
                    isPlaying = isPlaying,
                    playSize = playSize,
                    onClick = onPlayPause
                )
            }
        }
    }
}

@Composable
private fun NowPlayingLogo(
    station: RadioStation,
    isPlaying: Boolean,
    logoSize: Dp
) {
    val cornerRadius = logoSize * 0.19f
    val pulseScale: Float
    val haloScale: Float
    val haloAlpha: Float
    if (isPlaying) {
        val pulse = rememberInfiniteTransition(label = "nowPlayingPulse")
        val animatedPulseScale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.028f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "nowPlayingLogoScale"
        )
        val animatedHaloScale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.055f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "nowPlayingHaloScale"
        )
        val animatedHaloAlpha by pulse.animateFloat(
            initialValue = 0.10f,
            targetValue = 0.20f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "nowPlayingHaloAlpha"
        )
        pulseScale = animatedPulseScale
        haloScale = animatedHaloScale
        haloAlpha = animatedHaloAlpha
    } else {
        pulseScale = 1f
        haloScale = 1f
        haloAlpha = 0f
    }

    Box(
        modifier = Modifier.size(logoSize * 1.14f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(logoSize)
                .graphicsLayer {
                    scaleX = haloScale
                    scaleY = haloScale
                    alpha = haloAlpha
                }
                .clip(RoundedCornerShape(cornerRadius))
                .background(AaltoBlue)
        )
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
        ) {
            StationLogo(
                station = station,
                size = logoSize,
                cornerRadius = cornerRadius,
                framed = false
            )
        }
    }
}

@Composable
private fun NowPlayingPlayPauseButton(
    isPlaying: Boolean,
    playSize: Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "nowPlayingPlayScale"
    )

    Box(
        modifier = Modifier
            .size(playSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(AaltoBlue)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = if (isPlaying) "Tauko" else "Jatka",
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Tauko" else "Jatka",
            tint = Color.White,
            modifier = Modifier
                .size(playSize * 0.5f)
                .padding(start = if (isPlaying) 0.dp else 3.dp)
        )
    }
}

@Composable
private fun NightScreenTrigger(
    onNightScreen: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "nightScreenTriggerScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0.82f,
        animationSpec = tween(durationMillis = 120),
        label = "nightScreenTriggerIconAlpha"
    )

    IconButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onNightScreen()
        },
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = Icons.Outlined.DarkMode,
            contentDescription = "Pimennä näyttö",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AaltoSpaceXs,
                top = AaltoSpaceM,
                bottom = 0.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (action != null && onAction != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable(
                        onClickLabel = action,
                        role = Role.Button,
                        onClick = onAction
                    )
                    .padding(horizontal = AaltoSpaceS),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = action,
                    color = AaltoBlue,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StationCard(
    station: RadioStation,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(104.dp)
            .height(164.dp)
            .clickable(
                onClickLabel = "Toista ${station.name}",
                onClick = onClick
            ),
        shape = RoundedCornerShape(AaltoSurfaceRadius),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isSelected) {
            BorderStroke(1.dp, AaltoBlue.copy(alpha = 0.24f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AaltoSpaceS),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StationLogo(
                station = station,
                size = 88.dp,
                cornerRadius = AaltoLogoRadius
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = station.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Poista suosikeista" else "Lisaa suosikki",
                        tint = if (isFavorite) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StationRow(
    station: RadioStation,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "stationRowScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "stationRowElevation"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> AaltoBlue.copy(alpha = 0.24f)
            isDragging -> AaltoBlue.copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 160),
        label = "stationRowBorder"
    )

    val rowColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isDragging -> MaterialTheme.colorScheme.surface
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 160),
        label = "stationRowSurface"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                onClickLabel = "Toista ${station.name}",
                onClick = onClick
            ),
        shape = if (isSelected || isDragging) {
            RoundedCornerShape(AaltoSurfaceRadius)
        } else {
            RoundedCornerShape(0.dp)
        },
        color = rowColor,
        shadowElevation = elevation,
        border = if (isSelected || isDragging) {
            BorderStroke(1.dp, borderColor)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(horizontal = AaltoSpaceS, vertical = AaltoSpaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationLogo(
                station = station,
                size = 46.dp,
                cornerRadius = AaltoLogoRadius
            )

            Spacer(modifier = Modifier.width(AaltoSpaceM))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val metadata = stationMetadataLine(station)
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = AaltoSpaceXs)
                    )
                }
            }

            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = if (isFavorite) {
                        "Poista suosikeista"
                    } else {
                        "Lisaa suosikki"
                    },
                    tint = if (isFavorite) {
                        AaltoBlue
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun AaltoTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) {
            androidx.compose.material3.darkColorScheme(
                primary = AaltoBlue,
                onPrimary = Color.White,
                background = AaltoDarkBackground,
                onBackground = AaltoDarkText,
                surface = AaltoDarkSurface,
                onSurface = AaltoDarkText,
                surfaceVariant = AaltoDarkLogoSurface,
                onSurfaceVariant = AaltoDarkMuted,
                outline = AaltoDarkLine,
                primaryContainer = AaltoDarkSelectedSurface,
                onPrimaryContainer = AaltoDarkText
            )
        } else {
            androidx.compose.material3.lightColorScheme(
                primary = AaltoBlue,
                onPrimary = Color.White,
                background = AaltoLightBackground,
                onBackground = AaltoLightText,
                surface = AaltoLightSurface,
                onSurface = AaltoLightText,
                surfaceVariant = AaltoLightLogoSurface,
                onSurfaceVariant = AaltoLightMuted,
                outline = AaltoLightLine,
                primaryContainer = AaltoLightSelectedSurface,
                onPrimaryContainer = AaltoLightText
            )
        },
        content = content
    )
}
