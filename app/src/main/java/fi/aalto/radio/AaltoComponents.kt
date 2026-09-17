package fi.aalto.radio

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================
// BOTTOM NAVIGATION
// =============================================================

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
                icon = Icons.Filled.Search,
                onClick = { onTabSelected(1) }
            )
            AaltoNavigationItem(
                selected = selectedTab == 2,
                label = stringResource(R.string.tab_favorites),
                icon = if (selectedTab == 2) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                onClick = { onTabSelected(2) }
            )
        }
    }
}

@Composable
internal fun RowScope.AaltoNavigationItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
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
            // selectable() exposes the selected state to screen readers as well.
            .selectable(
                selected = selected,
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
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

// =============================================================
// TOP BAR
// =============================================================

@Composable
internal fun TopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
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
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================
// SECTION HEADER
// =============================================================

@Composable
internal fun SectionHeader(
    title: String,
    action: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AaltoSpaceXs, top = AaltoSpaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
        if (action != null && onAction != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(AaltoSpaceS))
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
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// =============================================================
// PLAYBACK STATE
// =============================================================

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
 * Small, static "this station is playing" mark. State is never shown by
 * colour alone (AGENTS.md §18), and a static icon keeps the list calm.
 */
@Composable
internal fun NowPlayingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Icon(
        imageVector = Icons.Filled.GraphicEq,
        contentDescription = stringResource(R.string.state_playing),
        tint = AaltoBlue,
        modifier = modifier.size(size)
    )
}

// =============================================================
// MINI PLAYER
// =============================================================

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
    onOpen: () -> Unit,
    onNext: (() -> Unit)? = null
) {
    val showPause = isPlaying || isConnecting
    // Tinted so the player reads as its own area between content and navigation.
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
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
                    size = 44.dp,
                    cornerRadius = 22.dp,
                    circular = true
                )
                Spacer(modifier = Modifier.width(AaltoSpaceM))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
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
                        style = MaterialTheme.typography.bodySmall,
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
                if (onNext != null) {
                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.action_next_station),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// =============================================================
// STATION LOGO
// =============================================================

@Composable
internal fun StationLogo(
    station: RadioStation,
    size: Dp,
    cornerRadius: Dp,
    framed: Boolean = true,
    circular: Boolean = false
) {
    val logo = rememberStationLogo(station)
    val stationColor = Color(station.logoColorArgb)
    val shape = if (circular) CircleShape else RoundedCornerShape(cornerRadius)
    val fallbackTextColor = if (stationColor.luminance() > 0.58f) {
        MaterialTheme.colorScheme.onSurface
    } else {
        stationColor
    }
    // Without a real logo the tile gets a soft tint of the station colour,
    // so fallback tiles look intentional instead of empty.
    val logoSurfaceColor = when {
        // Round tiles get a white disc so transparent logos stay readable.
        logo != null && circular -> Color.White
        logo != null -> Color.Transparent
        else -> stationColor.copy(alpha = 0.16f)
    }
    val logoBorder: BorderStroke? = null

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
        // Short crossfade so the initials placeholder does not pop into the logo.
        Crossfade(
            targetState = logo,
            animationSpec = tween(durationMillis = 180),
            label = "stationLogo"
        ) { bitmap ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        when {
                            // A square logo must fit inside the circle.
                            circular && bitmap != null -> size * 0.15f
                            !framed && bitmap != null -> 0.dp
                            !framed -> AaltoSpaceM
                            else -> AaltoSpaceXs
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = station.initials.ifBlank { "A" },
                        color = fallbackTextColor,
                        fontSize = when {
                            size >= 96.dp -> 28.sp
                            size >= 72.dp -> 18.sp
                            else -> 13.sp
                        },
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberStationLogo(station: RadioStation): ImageBitmap? {
    val context = LocalContext.current
    val stationUuid = station.radioBrowserStationUuid ?: station.id
    val logoUrls = remember(station.id, station.logoUrl, station.logoCandidates) {
        station.logoUrls
    }
    var logo by remember(station.id, logoUrls) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(station.id, stationUuid, logoUrls) {
        // Built-in stations have no logo URL; borrow one from Radio Browser by name.
        val urls = logoUrls.ifEmpty {
            listOfNotNull(StationLogoResolver.lookupLogoUrl(context, station))
        }
        logo = StationLogoResolver.resolve(context, stationUuid, urls)
    }

    return logo
}

// =============================================================
// STATION CARD (home presets / popular)
// =============================================================

@Composable
internal fun StationCard(
    station: RadioStation,
    isSelected: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 112.dp
) {
    val logoSize = width - AaltoSpaceS * 2

    Surface(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(AaltoSurfaceRadius))
            .clickable(
                onClickLabel = stringResource(R.string.action_play_station, station.name),
                onClick = onClick
            ),
        shape = RoundedCornerShape(AaltoSurfaceRadius),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(AaltoSpaceS),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(logoSize)) {
                // Round logo; the selected station gets a blue ring.
                Box(
                    modifier = Modifier
                        .size(logoSize)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) AaltoBlue else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .padding(if (isSelected) 5.dp else 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StationLogo(
                        station = station,
                        size = logoSize - if (isSelected) 10.dp else 2.dp,
                        cornerRadius = logoSize / 2,
                        circular = true
                    )
                }

                // Heart sits on the logo corner so the name gets the full card width.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = stringResource(
                                if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                            ),
                            role = Role.Button,
                            onClick = onFavoriteClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isSelected && isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(AaltoSpaceXs)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        NowPlayingIndicator(size = 16.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(AaltoSpaceS))

            Text(
                text = station.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) AaltoBlue else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// =============================================================
// STATION ROW (search / favorites lists)
// =============================================================

@Composable
internal fun StationRow(
    station: RadioStation,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
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
            .clip(RoundedCornerShape(AaltoSurfaceRadius))
            .clickable(
                onClickLabel = stringResource(R.string.action_play_station, station.name),
                onClick = onClick
            ),
        shape = RoundedCornerShape(AaltoSurfaceRadius),
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
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metadata = stationMetadataLine(station)
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = AaltoSpaceXs)
                    )
                }
            }
            if (isSelected && isPlaying) {
                NowPlayingIndicator(modifier = Modifier.padding(horizontal = AaltoSpaceXs))
            }
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                    ),
                    tint = if (isFavorite) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
