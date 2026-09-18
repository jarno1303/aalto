package fi.aalto.radio.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** The history as screen state, so the caller needs three lines, not thirty. */
internal class TrackHistoryState(
    private val tracks: State<List<PlayedTrack>>,
    private val onClear: () -> Unit
) {
    val items: List<PlayedTrack> get() = tracks.value

    fun clear() = onClear()
}

@Composable
internal fun rememberTrackHistory(): TrackHistoryState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(context) { TrackHistory.observe(context.applicationContext) }
    val tracks = flow.collectAsState(initial = emptyList())
    return remember(tracks, context) {
        TrackHistoryState(
            tracks = tracks,
            onClear = {
                scope.launch { runCatching { TrackHistory.clear(context.applicationContext) } }
            }
        )
    }
}
