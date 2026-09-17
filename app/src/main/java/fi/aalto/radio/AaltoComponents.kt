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

@Composable
internal fun AaltoBottomNavigation(
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
                label = stringResource(R.string.tab_radio),
                icon = Icons.Outlined.Radio,
                onClick = { onTabSelected(0) }
            )
            AaltoNavigationItem(
                selected = selectedTab == 1,
                label = stringResource(R.string.tab_search),
                icon = Icons.Default.Search,
                onClick = { onTabSelected(1) }
            )
            AaltoNavigationItem(
                selected = selectedTab == 2,
                label = stringResource(R.string.tab_favorites),
                icon = Icons.Outlined.FavoriteBorder,
                onClick = { onTabSelected(2) }
            )
        }
    }
}

@Composable
internal fun RowScope.AaltoNavigationItem(
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
internal fun TopBar(onOpenSync: () -> Unit) {
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
            text = stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenSync) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.sync_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StationLogo(
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
internal fun rememberStationLogo(station: RadioStation): androidx.compose.ui.graphics.ImageBitmap? {
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
internal fun playbackStateText(
    isPlaying: Boolean,
    isConnecting: Boolean,
    hasError: Boolean
): String = stringResource(
    when {
        hasError -> R.string.state_error
        isConnecting -> R.string.state_connecting
        isPlaying -> R.string.state_playing
        else -> R.string.state_paused
    }
)

/**
 * Compact now-playing bar shown above the bottom navigation on Search and Favorites,
 * so the user always sees what plays and can pause without leaving the list.
 */
@Composable
internal fun MiniPlayer(
    station: RadioStation,
    isPlaying: Boolean,
    isConnecting: Boolean,
    hasError: Boolean,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit
) {
    val showPause = isPlaying || isConnecting
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable(
                        onClickLabel = stringResource(R.string.action_open_now_playing),
                        onClick = onOpen
                    )
                    .padding(start = AaltoSpaceL, end = AaltoSpaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StationLogo(
                    station = station,
                    size = 40.dp,
                    cornerRadius = 10.dp
                )
                Spacer(modifier = Modifier.width(AaltoSpaceM))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playbackStateText(isPlaying, isConnecting, hasError),
                        color = if (hasError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = AaltoBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (showPause) R.string.action_pause else R.string.action_play
                            ),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(
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
internal fun StationCard(
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
                onClickLabel = stringResource(R.string.action_play_station, station.name),
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
                        contentDescription = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add),
                        tint = if (isFavorite) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun StationRow(
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
                onClickLabel = stringResource(R.string.action_play_station, station.name),
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
                        stringResource(R.string.favorite_remove)
                    } else {
                        stringResource(R.string.favorite_add)
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
