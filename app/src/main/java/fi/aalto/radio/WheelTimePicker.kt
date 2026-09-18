package fi.aalto.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aalto.radio.alarm.formatClock
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private val ROW_HEIGHT = 48.dp

/**
 * Five rows normally, three on a short screen or with large system fonts, so
 * the dial and the dialog's buttons always fit on the screen together.
 */
@Composable
private fun wheelRows(): Int {
    val configuration = LocalConfiguration.current
    return if (configuration.screenHeightDp < 640 || configuration.fontScale > 1.3f) 3 else 5
}

/**
 * The item nearest the middle of the viewport, read from the measured layout.
 *
 * Deliberately geometric: LazyListState's own first-visible bookkeeping shifts
 * with the content padding, which is what made the dial report a value a couple
 * of rows away from the one under the marker.
 */
private fun centeredIndex(layout: LazyListLayoutInfo): Int? {
    val items = layout.visibleItemsInfo
    if (items.isEmpty()) return null
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    return items.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
}

/** How far the given item is from the middle, in pixels (positive = below). */
private fun offsetFromCenter(layout: LazyListLayoutInfo, index: Int): Float? {
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    return item.offset + item.size / 2f - center
}

/** Scrolls the wanted value under the marker, by pixels measured from the layout. */
private suspend fun LazyListState.centerOnValue(
    wanted: Int,
    valueCount: Int,
    endless: Boolean,
    rowPx: Float
) {
    val layout = layoutInfo
    val index = centeredIndex(layout) ?: return
    val current = if (endless) Math.floorMod(index, valueCount) else index.coerceIn(0, valueCount - 1)
    val drift = offsetFromCenter(layout, index) ?: 0f
    val steps = if (endless) {
        val forward = Math.floorMod(wanted - current, valueCount)
        if (forward > valueCount / 2) forward - valueCount else forward
    } else {
        wanted - current
    }
    if (steps == 0 && abs(drift) < 1f) return
    scrollBy(steps * rowPx + drift)
}

/**
 * One dial. [endless] turns it into a wheel with no first or last item, the
 * way hours and minutes behave on a physical clock.
 */
@Composable
private fun Wheel(
    valueCount: Int,
    selected: Int,
    endless: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    fontSize: TextUnit = 28.sp,
    text: (Int) -> String
) {
    val rows = wheelRows()
    val itemCount = if (endless) Int.MAX_VALUE else valueCount
    val base = remember(valueCount, endless) {
        if (endless) (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % valueCount else 0
    }
    // A starting guess only; the exact position is corrected from the layout
    // below, so a padding convention cannot put the wrong number under the marker.
    val state = rememberLazyListState(initialFirstVisibleItemIndex = base + selected)
    val flingBehavior = rememberSnapFlingBehavior(state)
    val haptics = LocalHapticFeedback.current
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelected by rememberUpdatedState(onSelected)
    // True while the wheel is moved by code, so that move is not read back as a choice.
    var adjusting by remember { mutableStateOf(false) }

    fun valueAt(index: Int): Int =
        if (endless) Math.floorMod(index, valueCount) else index.coerceIn(0, valueCount - 1)

    // What sits under the marker is the choice.
    LaunchedEffect(state, valueCount, endless) {
        snapshotFlow { centeredIndex(state.layoutInfo) }
            .collect { index ->
                if (index == null || adjusting) return@collect
                val value = valueAt(index)
                if (value != currentSelected) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnSelected(value)
                }
            }
    }

    // Put the wanted value under the marker: when opening, and after a change
    // made elsewhere.
    LaunchedEffect(state, valueCount, endless) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        adjusting = true
        runCatching { state.centerOnValue(currentSelected, valueCount, endless, rowPx) }
        adjusting = false

        snapshotFlow { currentSelected to state.isScrollInProgress }
            .collect { (wanted, scrolling) ->
                if (scrolling) return@collect
                adjusting = true
                runCatching { state.centerOnValue(wanted, valueCount, endless, rowPx) }
                adjusting = false
            }
    }

    LazyColumn(
        state = state,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = ROW_HEIGHT * (rows / 2)),
        modifier = modifier
            .height(ROW_HEIGHT * rows)
            .fadeEdges()
            .then(
                if (label == null) Modifier else Modifier.semantics { contentDescription = label }
            )
    ) {
        items(itemCount) { index ->
            WheelRow(
                text = text(valueAt(index)),
                index = index,
                state = state,
                rowHeightPx = rowPx,
                fontSize = fontSize
            )
        }
    }
}

@Composable
private fun WheelRow(
    text: String,
    index: Int,
    state: LazyListState,
    rowHeightPx: Float,
    fontSize: TextUnit
) {
    // Distance from the middle in rows: 0 = chosen, 1 = the next one out.
    val distance by remember(index, state) {
        derivedStateOf {
            val offset = offsetFromCenter(state.layoutInfo, index)
            if (offset == null) 2f else abs(offset) / rowHeightPx
        }
    }
    val scale = (1f - distance * 0.16f).coerceIn(0.66f, 1f)
    val alpha = (1f - distance * 0.34f).coerceIn(0.22f, 1f)

    Box(
        modifier = Modifier
            .height(ROW_HEIGHT)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = if (distance < 0.5f) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
        )
    }
}

/** The band that marks the chosen row. */
@Composable
private fun CenterMarker() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .padding(horizontal = 16.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
    )
}

/** Soft fade at the top and bottom, so numbers slide out of view instead of being cut. */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.22f to Color.Black,
                0.78f to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

/**
 * Hour and minute wheels that turn endlessly in both directions, in the style
 * of a physical dial: the middle row is the chosen value, numbers further out
 * shrink and fade, and every number gives a small haptic tick.
 */
@Composable
internal fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = wheelRows()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT * rows),
        contentAlignment = Alignment.Center
    ) {
        CenterMarker()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Wheel(
                valueCount = 24,
                selected = hour,
                endless = true,
                onSelected = { onTimeChange(it, minute) },
                modifier = Modifier.width(80.dp),
                label = stringResource(R.string.alarm_hours)
            ) { "%02d".format(it) }
            Text(
                text = stringResource(R.string.time_separator),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(16.dp),
                textAlign = TextAlign.Center
            )
            Wheel(
                valueCount = 60,
                selected = minute,
                endless = true,
                onSelected = { onTimeChange(hour, it) },
                modifier = Modifier.width(80.dp),
                label = stringResource(R.string.alarm_minutes)
            ) { "%02d".format(it) }
        }
    }
}

/**
 * The same dial for a short list of choices (snooze length, sleep timer).
 * Does not wrap around, because the list has a first and a last item.
 */
@Composable
internal fun WheelOptionPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val rows = wheelRows()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT * rows),
        contentAlignment = Alignment.Center
    ) {
        CenterMarker()
        Wheel(
            valueCount = options.size,
            selected = selectedIndex.coerceIn(0, options.lastIndex),
            endless = false,
            onSelected = onSelected,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 22.sp
        ) { options.getOrElse(it) { "" } }
    }
}

/**
 * Alarm time in a dialog. The chosen time is shown big at the top, so what the
 * dial is doing is visible at a glance, and the two buttons say plainly what
 * leaves the dialog.
 */
@Composable
internal fun WheelTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by rememberSaveable { mutableIntStateOf(initialHour) }
    var minute by rememberSaveable { mutableIntStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = formatClock(hour, minute),
                style = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            WheelTimePicker(
                hour = hour,
                minute = minute,
                onTimeChange = { newHour, newMinute ->
                    hour = newHour
                    minute = newMinute
                }
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(hour, minute) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.alarm_set))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.alarm_cancel))
            }
        }
    )
}

/** Dialog with one dial: used for snooze length and the sleep timer. */
@Composable
internal fun WheelChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by rememberSaveable { mutableIntStateOf(selectedIndex.coerceAtLeast(0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            WheelOptionPicker(
                options = options,
                selectedIndex = picked,
                onSelected = { picked = it }
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(picked) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.alarm_ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.alarm_cancel))
            }
        }
    )
}
