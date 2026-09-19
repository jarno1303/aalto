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
import androidx.compose.ui.text.style.TextAlign
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

internal data class FavoriteDragSession(
    val stationId: String,
    val startOrder: List<String>,
    val startPointerYInViewportPx: Float,
    val pointerOffsetInItemPx: Float,
    val itemTopPx: Int,
    val itemHeightPx: Int
)

@Composable
internal fun FavoritesScreen(
    paddingValues: PaddingValues,
    favoriteStations: List<RadioStation>,
    selectedStation: RadioStation,
    isPlaying: Boolean,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit,
    onReorder: (List<String>) -> Unit,
    onFind: () -> Unit
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
            Column(verticalArrangement = Arrangement.spacedBy(AaltoSpaceXs)) {
                Text(
                    text = stringResource(R.string.tab_favorites),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
                if (favoriteStations.size > 1) {
                    Text(
                        text = stringResource(R.string.favorites_reorder_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (favoriteStations.isEmpty()) {
            item {
                // Calm empty state with one clear next step instead of onboarding.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AaltoSpaceXxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AaltoSpaceS)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = stringResource(R.string.favorites_empty),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.favorites_empty_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(AaltoSpaceS))
                    Button(onClick = onFind) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(AaltoSpaceS))
                        Text(stringResource(R.string.action_find_stations))
                    }
                }
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
                    isPlaying = isPlaying,
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
                    isDragging = isDragging,
                    quietFavorite = true
                )
            }
        }

        // An Int key (saveable, and not a String: drag-and-drop treats String
        // keys as station ids).
        item(key = ADD_CUSTOM_STATION_KEY, contentType = "add-custom") {
            AddCustomStationRow()
        }
    }
}

private const val ADD_CUSTOM_STATION_KEY = Int.MIN_VALUE

internal fun favoriteItemInfo(
    listState: LazyListState,
    stationId: String
) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == stationId }

internal fun favoriteVisibleAnchors(listState: LazyListState): List<FavoriteItemAnchor> {
    return listState.layoutInfo.visibleItemsInfo.mapNotNull { itemInfo ->
        val stationId = itemInfo.key as? String ?: return@mapNotNull null
        FavoriteItemAnchor(
            id = stationId,
            topPx = itemInfo.offset,
            heightPx = itemInfo.size
        )
    }
}

internal fun favoriteDragTranslationYPx(
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

internal fun favoriteAutoScrollDeltaPx(
    distanceIntoEdgePx: Float,
    edgePx: Float,
    maxDeltaPx: Float
): Float {
    val edgeProgress = (distanceIntoEdgePx / edgePx).coerceIn(0f, 1f)
    return maxDeltaPx * (0.18f + 0.82f * edgeProgress * edgeProgress)
}
