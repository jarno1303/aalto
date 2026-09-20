package fi.aalto.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fi.aalto.radio.plus.Plus
import kotlinx.coroutines.launch

/**
 * "Lisää oma asema" at the end of the favourites. The Plus line for own
 * stations is checked here, once, when the row is tapped (docs/AALTO_PLUS.md):
 * stations already added keep playing and syncing whatever the entitlement.
 */
@Composable
internal fun AddCustomStationRow(style: AddCustomStationStyle = AddCustomStationStyle.ROW) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<CustomStationDialog?>(null) }
    // The Plus line: checked here, when the user asks, never shown unasked.
    val open = {
        dialog = if (Plus.access(context).isActive()) {
            CustomStationDialog.ADD
        } else {
            CustomStationDialog.PLUS
        }
    }

    when (style) {
        AddCustomStationStyle.ROW -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AaltoSurfaceRadius))
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { open() }
                .padding(horizontal = AaltoSpaceL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = AaltoBlue,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(AaltoSpaceM))
            Text(
                text = stringResource(R.string.custom_add),
                style = MaterialTheme.typography.bodyLarge,
                color = AaltoBlue
            )
        }

        // Next to the Favorites title: always in sight, however long the list.
        AddCustomStationStyle.PILL -> Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(AaltoBlue.copy(alpha = 0.12f))
                .clickable(
                    onClickLabel = stringResource(R.string.custom_add),
                    role = Role.Button
                ) { open() }
                .heightIn(min = 36.dp)
                .padding(start = AaltoSpaceS, end = AaltoSpaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = AaltoBlue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(AaltoSpaceXs))
            Text(
                text = stringResource(R.string.custom_add_short),
                style = MaterialTheme.typography.labelLarge,
                color = AaltoBlue,
                maxLines = 1
            )
        }

        // Quiet and always there, next to the Search title.
        AddCustomStationStyle.ICON -> IconButton(onClick = { open() }) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.custom_add),
                tint = AaltoBlue,
                modifier = Modifier.size(24.dp)
            )
        }

        // Where the need arises: a search that did not find the station.
        AddCustomStationStyle.SEARCH_HINT -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AaltoSpaceS)
        ) {
            Text(
                text = stringResource(R.string.custom_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { open() }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(AaltoSpaceS))
                Text(stringResource(R.string.custom_search_action))
            }
        }
    }

    when (dialog) {
        CustomStationDialog.ADD -> CustomStationEditor(existing = null, onDismiss = { dialog = null })
        CustomStationDialog.PLUS -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.custom_plus_title)) },
            text = { Text(stringResource(R.string.custom_plus_body)) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.alarm_done)) }
            }
        )
        null -> Unit
    }
}

internal enum class AddCustomStationStyle { ROW, PILL, ICON, SEARCH_HINT }

private enum class CustomStationDialog { ADD, PLUS }

private enum class CustomStationError { INVALID, NOT_RADIO, UNREACHABLE }

/**
 * Add a station, or, with [existing], change its address or name. Editing is
 * never behind Plus: a station whose address moved would otherwise be locked.
 */
@Composable
internal fun CustomStationEditor(existing: RadioStation?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(existing?.preferredStreamUrl.orEmpty()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<CustomStationError?>(null) }

    fun add() {
        val normalized = CustomStations.normalizeUrl(url)
        if (normalized == null) {
            error = CustomStationError.INVALID
            return
        }
        checking = true
        error = null
        scope.launch {
            when (val result = StreamProbe.check(normalized)) {
                is StreamProbeResult.Ok -> {
                    val finalName = name.trim().ifBlank { CustomStations.defaultName(normalized, result.announcedName) }
                    runCatching {
                        val repository = AaltoAppContainer.stationRepository(context)
                        if (existing == null) {
                            repository.addCustomStation(finalName, normalized)
                        } else {
                            repository.updateCustomStation(existing.id, finalName, normalized)
                            // The last address that worked is the old one: forget it.
                            fi.aalto.radio.playback.StreamMemory.clear(context, existing.id)
                        }
                        AaltoAppContainer.syncCoordinator(context).requestSync()
                    }
                    checking = false
                    onDismiss()
                }
                StreamProbeResult.NotRadio -> {
                    checking = false
                    error = CustomStationError.NOT_RADIO
                }
                StreamProbeResult.Unreachable -> {
                    checking = false
                    error = CustomStationError.UNREACHABLE
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = { Text(stringResource(if (existing == null) R.string.custom_add else R.string.custom_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AaltoSpaceS)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text(stringResource(R.string.custom_url)) },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    enabled = !checking,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.custom_name)) },
                    singleLine = true,
                    enabled = !checking,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                val message = when (error) {
                    CustomStationError.INVALID -> stringResource(R.string.custom_error_invalid)
                    CustomStationError.NOT_RADIO -> stringResource(R.string.custom_error_not_radio)
                    CustomStationError.UNREACHABLE -> stringResource(R.string.custom_error_unreachable)
                    null -> if (checking) stringResource(R.string.custom_checking) else stringResource(R.string.custom_hint)
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = ::add, enabled = !checking && url.isNotBlank()) {
                if (checking) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text(stringResource(if (existing == null) R.string.custom_add_confirm else R.string.custom_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !checking) { Text(stringResource(R.string.alarm_cancel)) }
        }
    )
}
