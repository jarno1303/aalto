package fi.aalto.radio

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import fi.aalto.radio.catalog.CatalogFreshness
import fi.aalto.radio.catalog.CatalogReadResult
import fi.aalto.radio.catalog.CatalogResult
import fi.aalto.radio.catalog.CatalogStation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAB_RADIO = 0
private const val TAB_SEARCH = 1
private const val TAB_FAVORITES = 2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AaltoPerf.markAppStart()
        super.onCreate(savedInstanceState)
        AaltoThemePreferences.load(this)
        enableEdgeToEdge()
        setContent {
            val darkTheme = AaltoThemePreferences.mode.isDark()

            // Content draws behind the system bars; bar icons follow the app theme,
            // including when the user overrides the system light/dark setting.
            LaunchedEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            AaltoTheme(darkTheme = darkTheme) {
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
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Saveable so the Night Screen survives rotation (e.g. mounting the phone in a car holder).
    var nightScreenActive by rememberSaveable { mutableStateOf(false) }
    var catalogStations by remember { mutableStateOf<List<CatalogStation>>(emptyList()) }
    var catalogSearchStations by remember { mutableStateOf<List<CatalogStation>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var recentStations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }

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
        mutableStateOf(TAB_RADIO)
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
                        is CatalogResult.Success -> {
                            catalogStations = refresh.value
                        }
                        is CatalogResult.Failure -> Unit
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
    val popularStations = remember(radioStations, selectedStation) {
        stationsForRadioHome(radioStations, selectedStation, maxCount = 10)
    }

    // Recently played, refreshed whenever the playing station changes.
    LaunchedEffect(repository, selectedStationId) {
        recentStations = runCatching { repository.recentStations() }.getOrDefault(emptyList())
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
            selectedTab = TAB_RADIO
        }

        coroutineScope.launch {
            runCatching {
                repository.recordRecentlyPlayed(station.id)
            }
        }
    }

    // Previous/next follow the user's own station order (preset mental model).
    val canStepPresets = favoriteStations.size >= 2
    fun stepPreset(direction: Int) {
        if (favoriteStations.size < 2) return
        val currentIndex = favoriteStations.indexOfFirst { it.stableId == selectedStation.stableId }
        val nextIndex = if (currentIndex < 0) {
            if (direction > 0) 0 else favoriteStations.lastIndex
        } else {
            Math.floorMod(currentIndex + direction, favoriteStations.size)
        }
        playStation(favoriteStations[nextIndex], openNowPlaying = false)
    }
    val onPrevious: (() -> Unit)? = if (canStepPresets) ({ stepPreset(-1) }) else null
    val onNext: (() -> Unit)? = if (canStepPresets) ({ stepPreset(1) }) else null

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
                Column {
                    AnimatedVisibility(
                        visible = selectedTab != TAB_RADIO,
                        enter = fadeIn(animationSpec = tween(durationMillis = 140)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 100))
                    ) {
                        MiniPlayer(
                            station = selectedStation,
                            isPlaying = radioPlayer.isPlaying,
                            isConnecting = radioPlayer.isConnecting,
                            hasError = radioPlayer.playbackError != null,
                            onPlayPause = { radioPlayer.toggle(selectedStation) },
                            onOpen = { selectedTab = TAB_RADIO }
                        )
                    }
                    AaltoBottomNavigation(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        ) { paddingValues ->
            when (selectedTab) {
                TAB_RADIO -> RadioScreen(
                    paddingValues = paddingValues,
                    selectedStation = selectedStation,
                    isPlaying = radioPlayer.isPlaying,
                    isConnecting = radioPlayer.isConnecting,
                    playbackError = radioPlayer.playbackError,
                    favoriteIds = favoriteIds,
                    favoriteStations = favoriteStations,
                    popularStations = popularStations,
                    onPlayPause = { radioPlayer.toggle(selectedStation) },
                    onRetry = { playStation(selectedStation, openNowPlaying = false) },
                    onFavorite = { toggleFavorite(selectedStation) },
                    onFind = { selectedTab = TAB_SEARCH },
                    onStationClick = { station ->
                        playStation(station, openNowPlaying = false)
                    },
                    onStationFavoriteClick = ::toggleFavorite,
                    onOpenSettings = { showSettings = true },
                    onNightScreen = { nightScreenActive = true },
                    onPrevious = onPrevious,
                    onNext = onNext
                )

                TAB_SEARCH -> SearchScreen(
                    paddingValues = paddingValues,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    stations = stations,
                    recentStations = recentStations,
                    radioCountryCode = radioCountryCode,
                    onRadioCountryChange = { radioCountryCode = it },
                    selectedStation = selectedStation,
                    isPlaying = radioPlayer.isPlaying,
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
                        // Stay in search so the user can try several stations; the mini player shows what plays.
                        playStation(station, openNowPlaying = false)
                    },
                    onStationFavoriteClick = ::toggleFavorite
                )

                TAB_FAVORITES -> FavoritesScreen(
                    paddingValues = paddingValues,
                    favoriteStations = favoriteStations,
                    selectedStation = selectedStation,
                    isPlaying = radioPlayer.isPlaying,
                    onStationClick = { station ->
                        playStation(station, openNowPlaying = false)
                    },
                    onStationFavoriteClick = ::toggleFavorite,
                    onReorder = { orderedIds ->
                        coroutineScope.launch {
                            val committed = runCatching { repository.reorderFavorites(orderedIds) }.getOrDefault(false)
                            if (committed) syncCoordinator.requestSync()
                        }
                    },
                    onFind = { selectedTab = TAB_SEARCH }
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
                isConnecting = radioPlayer.isConnecting,
                playbackError = radioPlayer.playbackError,
                onPlayPause = { radioPlayer.toggle(selectedStation) },
                onPrevious = onPrevious,
                onNext = onNext,
                onExit = { nightScreenActive = false }
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            themeMode = AaltoThemePreferences.mode,
            onThemeModeChange = { mode -> AaltoThemePreferences.update(context, mode) },
            syncState = syncState,
            onSignIn = {
                (context as? ComponentActivity)?.let { activity ->
                    coroutineScope.launch { syncCoordinator.signIn(activity) }
                }
            },
            onSignOut = syncCoordinator::signOut,
            onDismiss = { showSettings = false }
        )
    }
}
