package fi.aalto.radio.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.aalto.radio.AaltoSpaceL
import fi.aalto.radio.AaltoSpaceM
import fi.aalto.radio.AaltoSpaceS
import fi.aalto.radio.AaltoSpaceXl
import fi.aalto.radio.AaltoSpaceXs
import fi.aalto.radio.R
import fi.aalto.radio.plus.Plus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The songs that have played, newest first. Tapping one offers to look it up
 * in a music service, which is the whole point: hearing something good on the
 * radio and wanting it later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistorySheet(
    tracks: List<PlayedTrack>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchFor by remember { mutableStateOf<PlayedTrack?>(null) }
    val context = LocalContext.current
    // The Plus line for history: checked once, here at the entry point.
    val plusActive = remember { Plus.access(context).isActive() }
    val view = remember(tracks, plusActive) {
        HistoryWindow.view(tracks, plusActive, System.currentTimeMillis(), ZoneId.systemDefault())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AaltoSpaceXl)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // One weight only: two weighted children split the row in half
                    // and cut the title ("Soitetut …") though there was room.
                    modifier = Modifier.weight(1f)
                )
                if (tracks.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text(text = stringResource(R.string.history_clear), maxLines = 1)
                    }
                }
            }

            if (view.visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AaltoSpaceL)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(items = view.visible, key = { it.id }) { track ->
                        TrackRow(track = track, onClick = { searchFor = track })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            if (view.hiddenEarlier > 0) {
                Text(
                    text = stringResource(R.string.history_plus_earlier),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AaltoSpaceM)
                )
            }

            Spacer(modifier = Modifier.height(AaltoSpaceL))
        }
    }

    searchFor?.let { track ->
        SearchDialog(track = track, onDismiss = { searchFor = null })
    }
}

@Composable
private fun TrackRow(track: PlayedTrack, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AaltoSpaceM)
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(AaltoSpaceXs))
        Text(
            text = listOf(playedAtText(track.playedAt), track.stationName)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Where to look the song up. No accounts, no API keys: a plain search link. */
@Composable
private fun SearchDialog(track: PlayedTrack, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AaltoSpaceS)) {
                OutlinedButton(
                    onClick = {
                        openSearch(context, SPOTIFY_SEARCH, track.title)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(text = stringResource(R.string.history_search_spotify))
                }
                OutlinedButton(
                    onClick = {
                        openSearch(context, YOUTUBE_SEARCH, track.title)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(text = stringResource(R.string.history_search_youtube))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.alarm_cancel))
            }
        }
    )
}

private fun openSearch(context: Context, template: String, query: String) {
    val url = template + Uri.encode(query)
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Today: the time. Earlier: the date and the time. */
private fun playedAtText(epochMs: Long): String {
    val moment = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val time = moment.format(TIME_FORMAT)
    return if (moment.toLocalDate() == LocalDate.now()) time else "${moment.format(DATE_FORMAT)} $time"
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H.mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.")

private const val SPOTIFY_SEARCH = "https://open.spotify.com/search/"
private const val YOUTUBE_SEARCH = "https://www.youtube.com/results?search_query="
