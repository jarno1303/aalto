package fi.aalto.radio

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aalto.radio.alarm.AlarmSettings
import fi.aalto.radio.alarm.finnishDayShort
import fi.aalto.radio.alarm.formatClock
import fi.aalto.radio.alarm.snoozeChoicesMinutes
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Wake-up radio panel, following the phone clock app pattern:
 * big time + switch on one row, every change is saved at once
 * (no Save button), a one-line summary, and "Valmis" to close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmSheet(
    settings: AlarmSettings,
    nextRingMillis: Long?,
    stations: List<RadioStation>,
    onChange: (AlarmSettings) -> Unit,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    log: List<String>,
    notificationsEnabled: Boolean = true,
    onEnableNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingTime by remember { mutableStateOf(false) }

    // Without a saved station the first own station is used.
    val selectedStation = stations.firstOrNull { it.id == settings.stationId } ?: stations.firstOrNull()
    fun withStation(base: AlarmSettings, station: RadioStation?): AlarmSettings =
        if (station == null) base else base.copy(
            stationId = station.id,
            stationName = station.name,
            streamUrl = station.preferredStreamUrl
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AaltoSpaceXl)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.alarm_settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Big time + switch
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatClock(settings.hour, settings.minute),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    color = if (settings.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            onClickLabel = stringResource(R.string.alarm_change_time),
                            onClick = { editingTime = true }
                        )
                )
                Switch(
                    checked = settings.enabled,
                    enabled = selectedStation != null,
                    onCheckedChange = { on ->
                        onChange(withStation(settings, selectedStation).copy(enabled = on))
                    }
                )
            }

            // Summary: days · station, then when it rings
            Text(
                text = listOfNotNull(
                    daysSummary(context, settings.days),
                    selectedStation?.name
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (settings.enabled && nextRingMillis != null) {
                    alarmCountdownText(context, nextRingMillis)
                } else {
                    stringResource(R.string.alarm_off)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (settings.enabled) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (!notificationsEnabled) {
                // Without notifications the alarm can only be stopped from the app.
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AaltoSpaceS)
                ) {
                    Column(modifier = Modifier.padding(AaltoSpaceM)) {
                        Text(
                            text = stringResource(R.string.alarm_notifications_off),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = onEnableNotifications) {
                            Text(stringResource(R.string.alarm_allow_notifications))
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = AaltoSpaceL)
            )

            // Days
            Text(
                text = stringResource(R.string.alarm_days),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(AaltoSpaceXs))
            val week = DayOfWeek.values().toList()
            listOf(week.take(4), week.drop(4)).forEach { rowDays ->
                Row(horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS)) {
                    rowDays.forEach { day ->
                        FilterChip(
                            selected = day in settings.days,
                            onClick = {
                                val days = if (day in settings.days) settings.days - day else settings.days + day
                                onChange(withStation(settings, selectedStation).copy(days = days))
                            },
                            label = { Text(finnishDayShort.getValue(day)) }
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.alarm_days_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Snooze length
            Text(
                text = stringResource(R.string.alarm_snooze_length),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = AaltoSpaceL)
            )
            Spacer(modifier = Modifier.height(AaltoSpaceXs))
            Row(horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS)) {
                snoozeChoicesMinutes.forEach { minutes ->
                    FilterChip(
                        selected = settings.snoozeMinutes == minutes,
                        onClick = {
                            onChange(withStation(settings, selectedStation).copy(snoozeMinutes = minutes))
                        },
                        label = { Text(stringResource(R.string.sleep_timer_minutes, minutes)) }
                    )
                }
            }

            // Station
            Text(
                text = stringResource(R.string.alarm_station),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = AaltoSpaceL)
            )
            if (stations.isEmpty()) {
                Text(
                    text = stringResource(R.string.alarm_no_stations),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            stations.forEach { station ->
                val selected = station.id == selectedStation?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.RadioButton) {
                            onChange(withStation(settings, station))
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onChange(withStation(settings, station)) }
                    )
                    Text(
                        text = station.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(AaltoSpaceL))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.alarm_done)) }

            // Secondary: try the sound
            TextButton(
                onClick = onTest,
                enabled = selectedStation != null,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text(stringResource(R.string.alarm_test_now)) }

            // Troubleshooting log, debug builds only
            if (log.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.alarm_log_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AaltoSpaceS)
                )
                Text(
                    text = log.takeLast(8).reversed().joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(AaltoSpaceL))
        }
    }

    if (editingTime) {
        val timeState = rememberTimePickerState(
            initialHour = settings.hour,
            initialMinute = settings.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingTime = false },
            title = { Text(stringResource(R.string.alarm_change_time)) },
            text = { TimeInput(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    editingTime = false
                    // Choosing a time means the user wants the alarm on.
                    onChange(
                        withStation(settings, selectedStation).copy(
                            hour = timeState.hour,
                            minute = timeState.minute,
                            enabled = selectedStation != null
                        )
                    )
                }) { Text(stringResource(R.string.alarm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editingTime = false }) {
                    Text(stringResource(R.string.alarm_cancel))
                }
            }
        )
    }
}

internal fun daysSummary(context: Context, days: Set<DayOfWeek>): String {
    val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    return when {
        days.isEmpty() -> context.getString(R.string.alarm_days_once)
        days.size == 7 -> context.getString(R.string.alarm_days_every)
        days == weekdays -> context.getString(R.string.alarm_days_weekdays)
        days == weekend -> context.getString(R.string.alarm_days_weekend)
        else -> days.sorted().joinToString(", ") { finnishDayShort.getValue(it) }
    }
}

/** "Herätys soi 7 t 30 min kuluttua", or the weekday and time when it is days away. */
internal fun alarmCountdownText(context: Context, epochMs: Long): String {
    val minutesLeft = ((epochMs - System.currentTimeMillis() + 59_999L) / 60_000L).coerceAtLeast(0L)
    val hours = minutesLeft / 60
    val minutes = minutesLeft % 60
    return when {
        hours >= 24 -> {
            val time: ZonedDateTime = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
            context.getString(
                R.string.alarm_rings_at,
                "${finnishDayShort.getValue(time.dayOfWeek)} ${formatClock(time.hour, time.minute)}"
            )
        }
        hours > 0 -> context.getString(R.string.alarm_rings_in_hours, hours.toInt(), minutes.toInt())
        else -> context.getString(R.string.alarm_rings_in_minutes, minutes.toInt())
    }
}
