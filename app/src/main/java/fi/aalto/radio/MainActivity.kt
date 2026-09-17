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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AaltoPerf.markAppStart()
        super.onCreate(savedInstanceState)
        // System bar icons follow the light/dark theme; content draws behind the bars.
        enableEdgeToEdge()
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
                Column {
                    AnimatedVisibility(
                        visible = selectedTab != 0,
                        enter = fadeIn(animationSpec = tween(durationMillis = 140)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 100))
                    ) {
                        MiniPlayer(
                            station = selectedStation,
                            isPlaying = radioPlayer.isPlaying,
                            isConnecting = radioPlayer.isConnecting,
                            hasError = radioPlayer.playbackError != null,
                            onPlayPause = { radioPlayer.toggle(selectedStation) },
                            onOpen = { selectedTab = 0 }
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
                0 -> RadioScreen(
                    paddingValues = paddingValues,
                    selectedStation = selectedStation,
                    isPlaying = radioPlayer.isPlaying,
                    isConnecting = radioPlayer.isConnecting,
                    playbackError = radioPlayer.playbackError,
                    favoriteIds = favoriteIds,
                    stations = radioHomeStations,
                    onPlayPause = { radioPlayer.toggle(selectedStation) },
                    onRetry = { playStation(selectedStation, openNowPlaying = false) },
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
                        // Stay in search so the user can try several stations; the mini player shows what plays.
                        playStation(station, openNowPlaying = false)
                    },
                    onStationFavoriteClick = ::toggleFavorite
                )

                2 -> FavoritesScreen(
                    paddingValues = paddingValues,
                    favoriteStations = favoriteStations,
                    selectedStation = selectedStation,
                    onStationClick = { station ->
                        playStation(station, openNowPlaying = false)
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
