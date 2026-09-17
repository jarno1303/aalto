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
internal fun NowPlaying(
    station: RadioStation,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isConnecting: Boolean,
    playbackError: String?,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
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
                    text = playbackStateText(
                        isPlaying = isPlaying,
                        isConnecting = isConnecting,
                        hasError = playbackError != null
                    ),
                    color = when {
                        playbackError != null -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (playbackError != null) FontWeight.Medium else FontWeight.Light
                )
                if (playbackError != null) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(AaltoSpaceS))
                        Text(stringResource(R.string.action_retry))
                    }
                    Spacer(modifier = Modifier.height(AaltoSpaceM))
                } else {
                    Spacer(modifier = Modifier.height(AaltoSpaceXxl))
                }
                NowPlayingPlayPauseButton(
                    isPlaying = isPlaying,
                    isConnecting = isConnecting,
                    playSize = playSize,
                    onClick = onPlayPause
                )
            }
        }
    }
}

@Composable
internal fun NowPlayingLogo(
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
internal fun NowPlayingPlayPauseButton(
    isPlaying: Boolean,
    isConnecting: Boolean,
    playSize: Dp,
    onClick: () -> Unit
) {
    val showPause = isPlaying || isConnecting
    val actionLabel = stringResource(if (showPause) R.string.action_pause else R.string.action_play)
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
                onClickLabel = actionLabel,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.9f),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(playSize * 0.78f)
            )
        }
        Icon(
            imageVector = if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = actionLabel,
            tint = Color.White,
            modifier = Modifier
                .size(if (isConnecting) playSize * 0.36f else playSize * 0.5f)
                .padding(start = if (showPause) 0.dp else 3.dp)
        )
    }
}

@Composable
internal fun NightScreenTrigger(
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
            contentDescription = stringResource(R.string.night_screen_open),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha),
            modifier = Modifier.size(22.dp)
        )
    }
}
