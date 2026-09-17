package fi.aalto.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

private val ROW_HEIGHT = 52.dp
private const val VISIBLE_ROWS = 3

/**
 * Scrolling hour and minute wheels, like a clock app: the value in the
 * highlighted middle row is the chosen one. Scrolling snaps to whole rows,
 * so a wrong value cannot be left half-selected.
 */
@Composable
internal fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Highlight for the selected row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumberWheel(
                values = (0..23).toList(),
                selected = hour,
                label = stringResourceSafe(R.string.alarm_hours),
                onSelected = { onTimeChange(it, minute) }
            )
            Text(
                text = ".",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.width(20.dp),
                textAlign = TextAlign.Center
            )
            NumberWheel(
                values = (0..59).toList(),
                selected = minute,
                label = stringResourceSafe(R.string.alarm_minutes),
                onSelected = { onTimeChange(hour, it) }
            )
        }
    }
}

@Composable
private fun NumberWheel(
    values: List<Int>,
    selected: Int,
    label: String,
    onSelected: (Int) -> Unit
) {
    val startIndex = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val flingBehavior = rememberSnapFlingBehavior(state)

    // Report the value in the middle row once scrolling has settled.
    LaunchedEffect(state, values) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { (scrolling, index) ->
                if (!scrolling) {
                    values.getOrNull(index)?.let { if (it != selected) onSelected(it) }
                }
            }
    }

    // Follow changes made elsewhere (e.g. reopening the sheet).
    LaunchedEffect(selected) {
        val target = values.indexOf(selected).coerceAtLeast(0)
        if (!state.isScrollInProgress && state.firstVisibleItemIndex != target) {
            state.scrollToItem(target)
        }
    }

    LazyColumn(
        state = state,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = ROW_HEIGHT * ((VISIBLE_ROWS - 1) / 2)),
        modifier = Modifier
            .width(96.dp)
            .height(ROW_HEIGHT * VISIBLE_ROWS)
            .semantics { contentDescription = label }
    ) {
        items(values, key = { it }) { value ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .height(ROW_HEIGHT)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(value),
                    fontSize = if (isSelected) 30.sp else 22.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun stringResourceSafe(id: Int): String = androidx.compose.ui.res.stringResource(id)
