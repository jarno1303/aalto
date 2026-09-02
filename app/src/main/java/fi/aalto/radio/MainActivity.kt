package fi.aalto.radio

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private val AaltoBlue = Color(0xFF1769FF)
private val AaltoBackground = Color(0xFFF4F6F8)
private val AaltoSurface = Color(0xFFFBFCFD)
private val AaltoSurfaceQuiet = Color(0xFFF7F9FB)
private val AaltoLine = Color(0xFFE1E7ED)
private val AaltoSelectedSurface = Color(0xFFEAF2FF)
private val AaltoText = Color(0xFF101317)
private val AaltoMuted = Color(0xFF68727D)
private val AaltoHeroStart = Color(0xFF111A24)
private val AaltoHeroEnd = Color(0xFF1B2B3C)
private val AaltoHeroSoft = Color(0xFF253D57)
private val AaltoLogoSurface = Color(0xFFF0F3F6)
private val AaltoNightBlack = Color(0xFF000000)
private val AaltoScreenHorizontalPadding = 18.dp
private val AaltoScreenTopPadding = 14.dp
private val AaltoScreenBottomPadding = 24.dp
private val AaltoRowSpacing = 10.dp
private val AaltoHeroRadius = 22.dp
private val AaltoCardRadius = 14.dp
private val AaltoLogoRadius = 13.dp
private const val STATION_LOGO_CONNECT_TIMEOUT_MS = 2_000
private const val STATION_LOGO_READ_TIMEOUT_MS = 2_500
private const val STATION_LOGO_MAX_BYTES = 512 * 1024

private val stationLogoMemoryCache = ConcurrentHashMap<String, ImageBitmap>()
private val stationLogoFailedUrls = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

private data class FavoriteDragSession(
    val stationId: String,
    val startOrder: List<String>,
    val startPointerYInViewportPx: Float,
    val pointerOffsetInItemPx: Float,
    val itemTopPx: Int,
    val itemHeightPx: Int
)

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
    val coroutineScope = rememberCoroutineScope()
    val radioPlayer = rememberRadioPlayer()
    val syncCoordinator = remember(context) { AaltoAppContainer.syncCoordinator(context) }
    val syncState by syncCoordinator.state.collectAsState()
    var showSyncSettings by rememberSaveable { mutableStateOf(false) }
    var nightScreenActive by remember { mutableStateOf(false) }
    val stations = repository.stations

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

    val selectedStation =
        repository.stationById(selectedStationId)
            ?: stations.first()

    val favoriteStations = buildList {
        favoriteOrder.mapNotNullTo(this) { id -> repository.stationById(id)?.takeIf { it.id in favoriteIds } }
        stations.filter { it.id in favoriteIds && it.id !in favoriteOrder }.sortedBy { it.id }.forEach(::add)
    }
    val homeStations = (favoriteStations + stations).distinctBy { it.id }.take(5)

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
        val trace = AaltoPerf.beginStationTap(station)
        radioPlayer.play(station, trace)
        selectedStationId = station.id

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
            containerColor = AaltoBackground,
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
                    homeStations = homeStations,
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
                    filteredStations = repository.searchStations(searchQuery),
                    selectedStation = selectedStation,
                    favoriteIds = favoriteIds,
                    onStationClick = { station ->
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
    homeStations: List<RadioStation>,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    onFind: () -> Unit,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit,
    onOpenSync: () -> Unit,
    onNightScreen: () -> Unit
) {
    LazyColumn(
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
            TopBar(onOpenSync)
        }

        item {
            NowPlayingCard(
                station = selectedStation,
                isPlaying = isPlaying,
                isFavorite = selectedStation.id in favoriteIds,
                playbackError = playbackError,
                onPlayPause = onPlayPause,
                onFavorite = onFavorite,
                onNightScreen = onNightScreen
            )
        }

        item {
            SectionHeader(
                title = "Asemat",
                action = "Hae",
                onAction = onFind
            )
        }

        items(
            items = homeStations,
            key = { it.id }
        ) { station ->
            StationRow(
                station = station,
                isSelected = station.id == selectedStation.id,
                isFavorite = station.id in favoriteIds,
                onClick = { onStationClick(station) },
                onFavoriteClick = { onStationFavoriteClick(station) }
            )
        }
    }
}

@Composable
private fun SearchScreen(
    paddingValues: PaddingValues,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filteredStations: List<RadioStation>,
    selectedStation: RadioStation,
    favoriteIds: Set<String>,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit
) {
    LazyColumn(
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
                color = AaltoText,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Asema tai genre") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = AaltoMuted
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(AaltoCardRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AaltoSurface,
                    unfocusedContainerColor = AaltoSurface,
                    disabledContainerColor = AaltoSurface,
                    focusedBorderColor = AaltoBlue,
                    unfocusedBorderColor = AaltoLine
                )
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tuloksia ${filteredStations.size}",
                color = AaltoMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        items(
            items = filteredStations,
            key = { it.id }
        ) { station ->
            StationRow(
                station = station,
                isSelected = station.id == selectedStation.id,
                isFavorite = station.id in favoriteIds,
                onClick = { onStationClick(station) },
                onFavoriteClick = { onStationFavoriteClick(station) }
            )
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
    var workingOrder by remember {
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
                color = AaltoText,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (favoriteStations.isEmpty()) {
            item {
                Text(
                    text = "Ei suosikkeja viela",
                    color = AaltoMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
        } else {
            items(
                items = visibleStations,
                key = { it.id }
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
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = AaltoBlue,
        selectedTextColor = AaltoText,
        indicatorColor = AaltoBlue.copy(alpha = 0.10f),
        unselectedIconColor = AaltoMuted,
        unselectedTextColor = AaltoMuted
    )

    NavigationBar(
        modifier = Modifier.height(72.dp),
        containerColor = AaltoSurface,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Home,
                    contentDescription = "Radio",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "Radio",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        )

        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Hae",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "Hae",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        )

        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            colors = itemColors,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = "Suosikit",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Text(
                    text = "Suosikit",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == 2) FontWeight.SemiBold else FontWeight.Medium
                )
            }
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

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "Aalto",
            color = AaltoText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenSync) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Aalto Sync",
                tint = AaltoMuted
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
                Text(state.error ?: state.status, color = AaltoMuted)
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
            .padding(horizontal = 32.dp, vertical = 40.dp),
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
    hero: Boolean = false
) {
    val logo = rememberStationLogo(station.faviconUrl)
    val stationColor = Color(station.logoColorArgb)
    val shape = RoundedCornerShape(cornerRadius)
    val fallbackTextColor = if (stationColor.luminance() > 0.58f) {
        AaltoText
    } else {
        stationColor
    }

    Surface(
        modifier = Modifier.size(size),
        shape = shape,
        color = if (hero) Color.White.copy(alpha = 0.94f) else AaltoLogoSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (hero) Color.White.copy(alpha = 0.20f) else AaltoLine
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (hero) 9.dp else 6.dp),
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
                    text = station.initials,
                    color = fallbackTextColor,
                    fontSize = if (hero) 16.sp else 10.sp,
                    lineHeight = if (hero) 18.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun rememberStationLogo(faviconUrl: String?): ImageBitmap? {
    val logoUrl = remember(faviconUrl) { normalizedLogoUrl(faviconUrl) }
    var logo by remember(logoUrl) {
        mutableStateOf(logoUrl?.let(stationLogoMemoryCache::get))
    }

    LaunchedEffect(logoUrl) {
        if (logoUrl == null || logo != null || stationLogoFailedUrls.contains(logoUrl)) {
            return@LaunchedEffect
        }

        val cachedLogo = stationLogoMemoryCache[logoUrl]
        if (cachedLogo != null) {
            logo = cachedLogo
            return@LaunchedEffect
        }

        val loadedLogo = loadStationLogo(logoUrl)
        if (loadedLogo != null) {
            stationLogoMemoryCache[logoUrl] = loadedLogo
            logo = loadedLogo
        } else {
            stationLogoFailedUrls.add(logoUrl)
        }
    }

    return logo
}

private fun normalizedLogoUrl(faviconUrl: String?): String? {
    val value = faviconUrl?.trim().orEmpty()
    if (value.isBlank()) return null
    return if (value.startsWith("https://") || value.startsWith("http://")) value else null
}

private suspend fun loadStationLogo(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null

    try {
        connection = URL(url).openConnection() as? HttpURLConnection ?: return@withContext null
        connection.connectTimeout = STATION_LOGO_CONNECT_TIMEOUT_MS
        connection.readTimeout = STATION_LOGO_READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true

        if (connection.responseCode !in 200..299) {
            return@withContext null
        }

        val bytes = connection.inputStream.use { inputStream ->
            readLogoBytes(inputStream)
        } ?: return@withContext null

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private fun readLogoBytes(inputStream: InputStream): ByteArray? {
    val outputStream = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var totalBytes = 0

    while (true) {
        val readBytes = inputStream.read(buffer)
        if (readBytes < 0) break

        totalBytes += readBytes
        if (totalBytes > STATION_LOGO_MAX_BYTES) {
            return null
        }

        outputStream.write(buffer, 0, readBytes)
    }

    return outputStream.toByteArray()
}

@Composable
private fun NowPlayingCard(
    station: RadioStation,
    isPlaying: Boolean,
    isFavorite: Boolean,
    playbackError: String?,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    onNightScreen: () -> Unit
) {
    val detailText = if (playbackError == null) {
        station.description.trim()
    } else {
        "Toisto keskeytynyt"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(194.dp)
            .clip(RoundedCornerShape(AaltoHeroRadius))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        AaltoHeroStart,
                        AaltoHeroEnd,
                        AaltoHeroSoft
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaybackStatusChip(
                    isPlaying = isPlaying,
                    playbackError = playbackError
                )

                Spacer(modifier = Modifier.weight(1f))

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
                            Color.White.copy(alpha = 0.94f)
                        } else {
                            Color.White.copy(alpha = 0.60f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                NightScreenTrigger(onNightScreen = onNightScreen)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StationLogo(
                    station = station,
                    size = 78.dp,
                    cornerRadius = 20.dp,
                    hero = true
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = station.name,
                        color = Color.White,
                        fontSize = 23.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (detailText.isNotBlank()) {
                        Text(
                            text = detailText,
                            color = if (playbackError == null) {
                                Color.White.copy(alpha = 0.68f)
                            } else {
                                Color(0xFFFFD8D8).copy(alpha = 0.88f)
                            },
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = if (isPlaying) "Tauko" else "Jatka",
                            role = Role.Button,
                            onClick = onPlayPause
                        ),
                    color = Color.White,
                    shape = CircleShape,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = if (isPlaying) {
                                "Tauko"
                            } else {
                                "Jatka"
                            },
                            tint = AaltoBlue,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackStatusChip(
    isPlaying: Boolean,
    playbackError: String?
) {
    val label = when {
        playbackError != null -> "HAIRIO"
        isPlaying -> "NYT SOI"
        else -> "TAUKO"
    }
    val contentColor = when {
        playbackError != null -> Color(0xFFFFD8D8)
        isPlaying -> Color(0xFFC9F7DE)
        else -> Color.White.copy(alpha = 0.70f)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.11f))
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun NightScreenTrigger(onNightScreen: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "nightScreenTriggerScale"
    )
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.18f else 0.08f,
        animationSpec = tween(durationMillis = 120),
        label = "nightScreenTriggerSurface"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 0.78f,
        animationSpec = tween(durationMillis = 120),
        label = "nightScreenTriggerIconAlpha"
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (pressed) -6f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "nightScreenTriggerIcon"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Pimennä näyttö",
                role = Role.Button,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNightScreen()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = surfaceAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.DarkMode,
                contentDescription = null,
                tint = Color.White.copy(alpha = iconAlpha),
                modifier = Modifier
                    .size(19.dp)
                    .graphicsLayer {
                        rotationZ = iconRotation
                        translationY = if (pressed) 0.6f else 0f
                    }
            )
        }
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
                start = 4.dp,
                top = 12.dp,
                bottom = 0.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = AaltoText,
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
                    .padding(horizontal = 8.dp),
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
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) AaltoSelectedSurface else AaltoSurface,
        animationSpec = tween(durationMillis = 160),
        label = "stationRowContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) AaltoBlue.copy(alpha = 0.28f) else AaltoLine,
        animationSpec = tween(durationMillis = 160),
        label = "stationRowBorder"
    )

    Card(
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
        shape = RoundedCornerShape(AaltoCardRadius),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationLogo(
                station = station,
                size = 46.dp,
                cornerRadius = AaltoLogoRadius
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    color = AaltoText,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (station.description.isNotBlank()) {
                    Text(
                        text = station.description,
                        color = AaltoMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))

                PlayingMiniChip()

                Spacer(modifier = Modifier.width(2.dp))
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
                        AaltoMuted
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayingMiniChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = AaltoBlue.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, AaltoBlue.copy(alpha = 0.16f))
    ) {
        Text(
            text = "SOI",
            color = AaltoBlue,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AaltoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = AaltoBlue,
            onPrimary = Color.White,
            background = AaltoBackground,
            onBackground = AaltoText,
            surface = Color.White,
            onSurface = AaltoText,
            onSurfaceVariant = AaltoMuted
        ),
        content = content
    )
}
