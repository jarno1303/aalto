package fi.aalto.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

private val ROW_HEIGHT = 48.dp
private const val VISIBLE_ROWS = 5

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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT * VISIBLE_ROWS),
        contentAlignment = Alignment.Center
    ) {
        // Centre band marking the selected row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .padding(horizontal = 24.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                )
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EndlessNumberWheel(
                count = 24,
                selected = hour,
                label = stringResource(R.string.alarm_hours),
                onSelected = { onTimeChange(it, minute) }
            )
            Text(
                text = ".",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.width(18.dp),
                textAlign = TextAlign.Center
            )
            EndlessNumberWheel(
                count = 60,
                selected = minute,
                label = stringResource(R.string.alarm_minutes),
                onSelected = { onTimeChange(hour, it) }
            )
        }
    }
}

@Composable
private fun EndlessNumberWheel(
    count: Int,
    selected: Int,
    label: String,
    onSelected: (Int) -> Unit
) {
    // A very long list whose values repeat: scrolling never reaches an end.
    val middle = remember(count) { (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % count }
    val startIndex = remember(count) { middle + selected - VISIBLE_ROWS / 2 }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val flingBehavior = rememberSnapFlingBehavior(state)
    val haptics = LocalHapticFeedback.current
    val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    // The value in the middle row, updated live while scrolling.
    LaunchedEffect(state, count) {
        snapshotFlow {
            val offsetRows = (state.firstVisibleItemScrollOffset / rowHeightPx).roundToInt()
            (state.firstVisibleItemIndex + offsetRows + VISIBLE_ROWS / 2) % count
        }
            .collect { value ->
                if (value != selected) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelected(value)
                }
            }
    }

    // Follow changes made elsewhere, e.g. when the dialog is reopened.
    LaunchedEffect(selected) {
        if (state.isScrollInProgress) return@LaunchedEffect
        val current = (state.firstVisibleItemIndex + VISIBLE_ROWS / 2) % count
        if (current != selected) {
            val delta = ((selected - current + count) % count).let { if (it > count / 2) it - count else it }
            state.scrollToItem(state.firstVisibleItemIndex + delta)
        }
    }

    LazyColumn(
        state = state,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = ROW_HEIGHT * (VISIBLE_ROWS / 2)),
        modifier = Modifier
            .width(88.dp)
            .height(ROW_HEIGHT * VISIBLE_ROWS)
            .fadeEdges()
            .semantics { contentDescription = label }
    ) {
        items(Int.MAX_VALUE) { index ->
            NumberRow(
                value = index % count,
                index = index,
                state = state,
                rowHeightPx = rowHeightPx
            )
        }
    }
}

@Composable
private fun NumberRow(
    value: Int,
    index: Int,
    state: LazyListState,
    rowHeightPx: Float
) {
    // Distance from the centre in rows: 0 = selected, 1 = next one out.
    val distance by remember(index) {
        androidx.compose.runtime.derivedStateOf {
            val layout = state.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null) {
                2f
            } else {
                val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                abs(item.offset + rowHeightPx / 2f - viewportCenter) / rowHeightPx
            }
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
            text = "%02d".format(value),
            fontSize = 28.sp,
            fontWeight = if (distance < 0.5f) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
        )
    }
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
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val flingBehavior = rememberSnapFlingBehavior(state)
    val haptics = LocalHapticFeedback.current
    val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    LaunchedEffect(state, options.size) {
        snapshotFlow {
            val offsetRows = (state.firstVisibleItemScrollOffset / rowHeightPx).roundToInt()
            (state.firstVisibleItemIndex + offsetRows).coerceIn(0, options.lastIndex)
        }
            .collect { index ->
                if (index != selectedIndex) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelected(index)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT * VISIBLE_ROWS),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .padding(horizontal = 24.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                )
        )
        LazyColumn(
            state = state,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = ROW_HEIGHT * (VISIBLE_ROWS / 2)),
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT * VISIBLE_ROWS)
                .fadeEdges()
        ) {
            items(options.size) { index ->
                OptionRow(
                    text = options[index],
                    index = index,
                    state = state,
                    rowHeightPx = rowHeightPx
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    text: String,
    index: Int,
    state: LazyListState,
    rowHeightPx: Float
) {
    val distance by remember(index) {
        androidx.compose.runtime.derivedStateOf {
            val layout = state.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null) {
                2f
            } else {
                val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                abs(item.offset + rowHeightPx / 2f - viewportCenter) / rowHeightPx
            }
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
            fontSize = 22.sp,
            fontWeight = if (distance < 0.5f) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
        )
    }
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
    var picked by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(selectedIndex.coerceAtLeast(0))
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            WheelOptionPicker(
                options = options,
                selectedIndex = picked,
                onSelected = { picked = it }
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(picked) }) {
                Text(stringResource(R.string.alarm_ok))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.alarm_cancel))
            }
        }
    )
}
