package fi.aalto.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.aalto.radio.alarm.AlarmSettings
import fi.aalto.radio.alarm.finnishDayShort
import java.time.DayOfWeek

/**
 * Wake-up radio settings: on/off, time, weekdays and station.
 * Stations come from the user's own stations (plus the current one).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmDialog(
    initial: AlarmSettings,
    stations: List<RadioStation>,
    onSave: (AlarmSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember { mutableStateOf(initial.enabled || !initial.hasStation) }
    var days by remember { mutableStateOf(initial.days) }
    var stationId by remember {
        mutableStateOf(initial.stationId ?: stations.firstOrNull()?.id)
    }
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alarm_settings_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AaltoSpaceS)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.alarm_enabled),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                TimeInput(state = timeState)

                Text(
                    text = stringResource(
                        if (days.isEmpty()) R.string.alarm_repeat_once else R.string.alarm_repeat_days
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val week = DayOfWeek.values().toList()
                listOf(week.take(4), week.drop(4)).forEach { rowDays ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AaltoSpaceXs)) {
                        rowDays.forEach { day ->
                            FilterChip(
                                selected = day in days,
                                onClick = {
                                    days = if (day in days) days - day else days + day
                                },
                                label = { Text(finnishDayShort.getValue(day)) }
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.alarm_station),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = AaltoSpaceS)
                )
                if (stations.isEmpty()) {
                    Text(
                        text = stringResource(R.string.alarm_no_stations),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column {
                    stations.forEach { station ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.RadioButton) { stationId = station.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = station.id == stationId,
                                onClick = { stationId = station.id }
                            )
                            Text(
                                text = station.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = stations.any { it.id == stationId },
                onClick = {
                    val station = stations.firstOrNull { it.id == stationId }
                    onSave(
                        initial.copy(
                            enabled = enabled && station != null,
                            hour = timeState.hour,
                            minute = timeState.minute,
                            days = days,
                            stationId = station?.id,
                            stationName = station?.name,
                            streamUrl = station?.preferredStreamUrl
                        )
                    )
                }
            ) { Text(stringResource(R.string.alarm_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sync_close)) }
        }
    )
}
