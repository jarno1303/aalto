package fi.aalto.radio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs


@Composable
private fun SkipButton(
    visible: Boolean,
    isNext: Boolean,
    onClick: () -> Unit
) {
    // Keeps its space when hidden so the play button stays centred.
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (visible) {
            IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = if (isNext) Icons.Filled.SkipNext else Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(
                        if (isNext) R.string.action_next_station else R.string.action_previous_station
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

/**
 * Horizontal swipe on the logo moves to the previous/next own station.
 * The logo follows the finger slightly and springs back.
 */
@Composable
private fun SwipeableStation(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestNext by rememberUpdatedState(onNext)

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = offsetX.value
                alpha = 1f - (abs(offsetX.value) / (thresholdPx * 4f)).coerceIn(0f, 0.4f)
            }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total <= -thresholdPx -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestNext()
                            }
                            total >= thresholdPx -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestPrevious()
                            }
                        }
                        scope.launch { offsetX.animateTo(0f, tween(durationMillis = 160)) }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, tween(durationMillis = 160)) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                        scope.launch { offsetX.snapTo(total * 0.35f) }
                    }
                )
            }
    ) {
        content()
    }
}

/**
 * Station logo with a soft, static halo while playing. No endless pulsing:
 * the halo fades in once, which keeps the screen calm in a car and saves power.
 */
@Composable
internal fun NowPlayingLogo(
    station: RadioStation,
    isPlaying: Boolean,
    logoSize: Dp,
    circular: Boolean = false,
    showHalo: Boolean = true
) {
    val cornerRadius = logoSize * 0.19f
    val haloShape = if (circular) CircleShape else RoundedCornerShape(cornerRadius * 1.06f)
    val haloAlpha by animateFloatAsState(
        targetValue = if (isPlaying && showHalo) 0.14f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "nowPlayingHaloAlpha"
    )

    Box(
        modifier = Modifier.size(if (showHalo) logoSize * 1.14f else logoSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(logoSize * 1.06f)
                .graphicsLayer { alpha = haloAlpha }
                .clip(haloShape)
                .background(AaltoBlue)
        )
        // The change itself lives in StationLogo, so every logo in the app —
        // small player, big card, tiles — changes the same way.
        StationLogo(
            station = station,
            size = logoSize,
            cornerRadius = cornerRadius,
            circular = circular
        )
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
    onNightScreen: () -> Unit,
    labeled: Boolean = false,
    round: Boolean = false
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

    CardAction(
        label = if (labeled) stringResource(R.string.action_label_night) else null,
        round = round
    ) {
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
}

/**
 * −30 s, how far behind live, +30 s. Tapping the "behind" text goes live.
 * Free: 30 seconds back; Aalto Plus: 30 minutes (Timeshift decides).
 */
@Composable
private fun TimeshiftRow(state: fi.aalto.radio.playback.TimeshiftState) {
    val context = LocalContext.current
    // The first time only: say what being behind means and how to get back.
    var hintSeen by remember { mutableStateOf(TimeshiftHint.seen(context)) }
    var hintShown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.live) {
        if (state.live && hintShown && !hintSeen) {
            TimeshiftHint.markSeen(context)
            hintSeen = true
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Three columns: the text sits exactly above the play button, the
        // back button left of it and +30 (or the same empty space) right of
        // it, so the row never looks lopsided.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // At the limit the button greys out but still answers: a free
            // listener is told once, briefly, that further back is Plus. No
            // lasting Plus text on the player.
            val atLimit = !state.live && state.maxBackMs < 1_000L
            IconButton(
                onClick = {
                    if (atLimit) {
                        if (state.atFreeLimit) {
                            android.widget.Toast.makeText(
                                context,
                                R.string.timeshift_plus,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        fi.aalto.radio.playback.Timeshift.back(context, 30)
                    }
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay30,
                    contentDescription = stringResource(R.string.timeshift_back),
                    tint = if (atLimit) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
            }
            Text(
                text = if (state.live) {
                    stringResource(R.string.timeshift_live)
                } else {
                    stringResource(R.string.timeshift_behind, behindLabel(state.behindMs))
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (state.live) FontWeight.Normal else FontWeight.SemiBold,
                color = if (state.live) MaterialTheme.colorScheme.onSurfaceVariant else AaltoBlue,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        enabled = !state.live,
                        onClickLabel = stringResource(R.string.timeshift_go_live),
                        role = Role.Button
                    ) { fi.aalto.radio.playback.Timeshift.live() }
                    .padding(horizontal = AaltoSpaceS, vertical = AaltoSpaceXs)
            )
            // Up to 30 s behind, +30 would only do what "Palaa suoraan" does.
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (state.behindMs > 30_000L) {
                IconButton(
                    onClick = { fi.aalto.radio.playback.Timeshift.forward(context, 30) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forward30,
                        contentDescription = stringResource(R.string.timeshift_forward),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            }
        }
        if (!state.live && !hintSeen) {
            androidx.compose.runtime.SideEffect { hintShown = true }
            Text(
                text = stringResource(R.string.timeshift_hint, behindLabel(state.behindMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = AaltoSpaceL)
            )
        }
    }
}

/** Whether the one-time "you are behind live" explanation has been seen. */
internal object TimeshiftHint {
    private const val PREFS = "aalto_home"
    private const val KEY = "timeshift_hint_seen"

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

/** "0:45", "12:03" */
internal fun behindLabel(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(1)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * A secondary action on the Now Playing card with its name under it, so
 * nobody has to guess what a moon or a clock does. Round (a soft filled
 * circle) in the roomy card, a plain icon in the compact one.
 */
@Composable
internal fun CardAction(
    label: String?,
    round: Boolean,
    labelColor: Color? = null,
    button: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = if (round) {
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            } else {
                Modifier
            },
            contentAlignment = Alignment.Center
        ) {
            button()
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = if (round) AaltoSpaceXs else 0.dp)
            )
        }
    }
}

/** A plain labeled action (song history, sound) for the roomy card. */
@Composable
private fun SimpleCardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    CardAction(label = label, round = true) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * "AAC · 128 kbps": what the stream is, small and quiet, for the people who
 * care about sound and a sign of care for everyone else.
 */
@Composable
internal fun StreamQualityBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/**
 * Compact Now Playing card for the portrait home screen.
 *
 * Logo on white on the left; name, what is on (song title when the stream
 * sends one) and station details on the right; favorite in the corner.
 * One control row: sleep timer, previous / play / next, Night Screen.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun NowPlayingCard(
    station: RadioStation,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isConnecting: Boolean,
    playbackError: String?,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onFavorite: () -> Unit,
    onNightScreen: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    trackTitle: String? = null,
    expanded: Boolean = false,
    onOpenHistory: (() -> Unit)? = null,
    onOpenAudio: (() -> Unit)? = null,
    qualityLabel: String? = null
) {
    stationTrace("ui_current_state", station)
    val canSkip = onPrevious != null && onNext != null
    val sleepMinutes = rememberSleepTimerMinutes()
    val showTrack = trackTitle != null && isPlaying && playbackError == null
    // Small screens and large system fonts get a slightly smaller card, so
    // nothing is pushed off the edge or clipped.
    val configuration = LocalConfiguration.current
    val tight = configuration.screenWidthDp < 360 || configuration.fontScale > 1.3f
    val sidePadding = if (tight) AaltoSpaceM else AaltoSpaceL
    val logoSize = when {
        expanded && tight -> 104.dp
        expanded -> 144.dp
        tight -> 56.dp
        else -> 64.dp
    }
    val nameSize = when {
        expanded && tight -> 20.sp
        expanded -> 24.sp
        else -> 18.sp
    }
    val playSize = when {
        expanded && tight -> 64.dp
        expanded -> 72.dp
        else -> 56.dp
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(
                start = sidePadding,
                end = sidePadding,
                top = AaltoSpaceM,
                bottom = AaltoSpaceS
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo: @Composable () -> Unit = {
                    NowPlayingLogo(
                        station = station,
                        isPlaying = isPlaying,
                        logoSize = logoSize,
                        showHalo = expanded
                    )
                }
                if (canSkip) {
                    SwipeableStation(onPrevious = onPrevious!!, onNext = onNext!!, content = logo)
                } else {
                    logo()
                }

                Spacer(modifier = Modifier.width(if (tight) AaltoSpaceS else AaltoSpaceM))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = nameSize,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Tapping what is playing opens the songs that have played:
                    // the question "what was that?" is asked right here.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (onOpenHistory == null) {
                            Modifier
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    onClickLabel = stringResource(R.string.history_open),
                                    role = Role.Button,
                                    onClick = onOpenHistory
                                )
                        }
                    ) {
                        if (isPlaying && playbackError == null) {
                            NowPlayingIndicator(size = 16.dp)
                            Spacer(modifier = Modifier.width(AaltoSpaceXs))
                        }
                        Text(
                            text = if (showTrack) {
                                trackTitle!!
                            } else {
                                playbackStateText(
                                    isPlaying = isPlaying,
                                    isConnecting = isConnecting,
                                    hasError = playbackError != null
                                )
                            },
                            color = when {
                                playbackError != null -> MaterialTheme.colorScheme.error
                                showTrack -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (showTrack) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            // A long song title scrolls by, as it does on a car
                            // display, instead of ending mid-word.
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .then(if (showTrack) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier)
                        )
                        // No history icon here: the song line itself opens the
                        // history, and the icon only took room from the title.
                    }
                    val badge = qualityLabel?.takeIf { isPlaying && playbackError == null }
                    val details = when {
                        // With the quality badge there is room for the genre only;
                        // the place would be cut to "Pop · …" anyway.
                        badge != null -> stationGenreAndTags(station).ifBlank { stationMetadataLine(station) }
                        else -> stationMetadataLine(station).ifBlank { station.description }
                    }
                    if ((details.isNotBlank() || badge != null) && playbackError == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (details.isNotBlank()) {
                                Text(
                                    text = details,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            if (badge != null) {
                                if (details.isNotBlank()) Spacer(modifier = Modifier.width(6.dp))
                                StreamQualityBadge(badge)
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = AaltoSpaceS)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                        ),
                        tint = if (isFavorite) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

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
            } else {
                Spacer(modifier = Modifier.height(AaltoSpaceS))
            }

            // Rewind: only for streams that can be kept (MP3 / AAC).
            val timeshift by fi.aalto.radio.playback.Timeshift.state.collectAsState()
            if (timeshift.available && playbackError == null) {
                TimeshiftRow(state = timeshift)
            }

            val transport: @Composable () -> Unit = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (tight) 0.dp else AaltoSpaceS)
                ) {
                    SkipButton(visible = canSkip, isNext = false, onClick = { onPrevious?.invoke() })
                    NowPlayingPlayPauseButton(
                        isPlaying = isPlaying,
                        isConnecting = isConnecting,
                        playSize = playSize,
                        onClick = onPlayPause
                    )
                    SkipButton(visible = canSkip, isNext = true, onClick = { onNext?.invoke() })
                }
            }
            if (expanded) {
                // Room to spare: playback in the middle, then the other
                // actions as a row of labelled round buttons.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    transport()
                }
                Spacer(modifier = Modifier.height(AaltoSpaceM))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    SleepTimerButton(active = sleepMinutes != null, labeled = true, round = true, minutesLeft = sleepMinutes)
                    if (onOpenHistory != null) {
                        SimpleCardAction(
                            icon = Icons.Outlined.History,
                            label = stringResource(R.string.action_label_songs),
                            onClick = onOpenHistory
                        )
                    }
                    if (onOpenAudio != null) {
                        SimpleCardAction(
                            icon = Icons.Outlined.GraphicEq,
                            label = stringResource(R.string.action_label_sound),
                            onClick = onOpenAudio
                        )
                    }
                    NightScreenTrigger(onNightScreen = onNightScreen, labeled = true, round = true)
                }
                Spacer(modifier = Modifier.height(AaltoSpaceS))
            } else {
                // Equal sides, so play sits in the exact middle of the card (and
                // under the rewind row) whatever the two labels' lengths.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        SleepTimerButton(active = sleepMinutes != null, labeled = true, minutesLeft = sleepMinutes)
                    }
                    transport()
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        NightScreenTrigger(onNightScreen = onNightScreen, labeled = true)
                    }
                }
            }
        }
    }
}

/** Minutes left on the sleep timer, refreshed every few seconds; null when off. */
@Composable
internal fun rememberSleepTimerMinutes(): Int? {
    val endsAt = SleepTimer.endsAtElapsedMs
    val minutes by produceState<Int?>(initialValue = SleepTimer.remainingMinutes(), endsAt) {
        while (true) {
            value = SleepTimer.remainingMinutes()
            if (endsAt == null) break
            delay(5_000)
        }
    }
    return if (endsAt == null) null else minutes
}

@Composable
internal fun SleepTimerButton(
    active: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labeled: Boolean = false,
    round: Boolean = false,
    /** While the timer runs its label is the time left ("13 min"), in blue. */
    minutesLeft: Int? = null
) {
    val context = LocalContext.current
    var pickerOpen by remember { mutableStateOf(false) }

    CardAction(
        label = when {
            !labeled -> null
            minutesLeft != null -> stringResource(R.string.sleep_timer_minutes, minutesLeft)
            else -> stringResource(R.string.action_label_timer)
        },
        round = round,
        labelColor = if (minutesLeft != null) AaltoBlue else null
    ) {
        IconButton(
            onClick = { pickerOpen = true },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = stringResource(R.string.sleep_timer),
                // Secondary to previous / play / next: same size and weight as the
                // Night Screen moon on the other side, quieter than the playback controls.
                tint = if (active) AaltoBlue else tint.copy(alpha = 0.82f),
                modifier = Modifier.size(22.dp)
            )
        }
    }

    if (pickerOpen) {
        // The same dial as the alarm time, so every time choice feels alike.
        val options = listOf(stringResource(R.string.sleep_timer_off)) +
            SleepTimer.choicesMinutes.map { stringResource(R.string.sleep_timer_minutes, it) }
        val selectedIndex = SleepTimer.selectedMinutes
            ?.let { SleepTimer.choicesMinutes.indexOf(it) + 1 }
            ?.takeIf { it > 0 }
            ?: 0
        WheelChoiceDialog(
            title = stringResource(R.string.sleep_timer),
            options = options,
            selectedIndex = selectedIndex,
            onConfirm = { index ->
                pickerOpen = false
                if (index == 0) {
                    SleepTimer.cancel()
                } else {
                    SleepTimer.start(context, SleepTimer.choicesMinutes[index - 1])
                }
            },
            onDismiss = { pickerOpen = false }
        )
    }
}
