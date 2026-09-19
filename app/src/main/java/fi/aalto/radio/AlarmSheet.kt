package fi.aalto.radio

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import fi.aalto.radio.alarm.dayShort
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var editingSnooze by remember { mutableStateOf(false) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }

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
        MatchSheetNavigationBar()
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
                    maxLines = 1,
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
                // Calm but unmissable: a neutral box with the warning colour
                // only on its first line, not a whole pink panel.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AaltoSpaceS)
                ) {
                    Column(modifier = Modifier.padding(AaltoSpaceM)) {
                        Text(
                            text = stringResource(R.string.alarm_notifications_off),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
            // The week as one row of seven, like a clock app: chips wrapped 4 + 3.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AaltoSpaceXs),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceXs)
            ) {
                DayOfWeek.values().forEach { day ->
                    val selected = day in settings.days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (selected) AaltoBlue else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (selected) AaltoBlue else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .selectable(
                                selected = selected,
                                role = Role.Checkbox,
                                onClick = {
                                    val days = if (selected) settings.days - day else settings.days + day
                                    onChange(withStation(settings, selectedStation).copy(days = days))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayShort(day),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.alarm_days_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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

            // Advanced: rarely changed, so folded away by default.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(top = AaltoSpaceS)
                    .clickable(role = Role.Button) { advancedOpen = !advancedOpen },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.alarm_advanced),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (advancedOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { editingSnooze = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.alarm_snooze_length),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (settings.snoozeMinutes == 0) {
                            stringResource(R.string.alarm_snooze_off)
                        } else {
                            stringResource(R.string.sleep_timer_minutes, settings.snoozeMinutes)
                        },
                        color = AaltoBlue
                    )
                }

                Text(
                    text = stringResource(R.string.alarm_volume, settings.volumePercent),
                    modifier = Modifier.padding(top = AaltoSpaceS)
                )
                Slider(
                    value = settings.volumePercent.toFloat(),
                    onValueChange = { value ->
                        onChange(withStation(settings, selectedStation).copy(volumePercent = value.toInt()))
                    },
                    valueRange = 10f..100f,
                    steps = 8,
                    // Snaps in tens, without dots on the track (as in the audio sheet).
                    colors = SliderDefaults.colors(
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.alarm_volume_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(
                    onClick = onTest,
                    enabled = selectedStation != null,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(stringResource(R.string.alarm_test_now)) }

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
            }

            Spacer(modifier = Modifier.height(AaltoSpaceL))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.alarm_done)) }

            Spacer(modifier = Modifier.height(AaltoSpaceL))
        }
    }

    if (editingSnooze) {
        val options = snoozeChoicesMinutes.map { minutes ->
            if (minutes == 0) {
                stringResource(R.string.alarm_snooze_off)
            } else {
                stringResource(R.string.sleep_timer_minutes, minutes)
            }
        }
        WheelChoiceDialog(
            title = stringResource(R.string.alarm_snooze_length),
            options = options,
            selectedIndex = snoozeChoicesMinutes.indexOf(settings.snoozeMinutes).coerceAtLeast(0),
            onConfirm = { index ->
                editingSnooze = false
                onChange(
                    withStation(settings, selectedStation).copy(snoozeMinutes = snoozeChoicesMinutes[index])
                )
            },
            onDismiss = { editingSnooze = false }
        )
    }

    if (editingTime) {
        // Scroll wheels: no typing needed. Confirming also turns the alarm on,
        // because choosing a time is how someone says they want to be woken.
        WheelTimeDialog(
            initialHour = settings.hour,
            initialMinute = settings.minute,
            onConfirm = { pickedHour, pickedMinute ->
                editingTime = false
                onChange(
                    withStation(settings, selectedStation).copy(
                        hour = pickedHour,
                        minute = pickedMinute,
                        enabled = selectedStation != null
                    )
                )
            },
            onDismiss = { editingTime = false }
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
        else -> days.sorted().joinToString(", ") { dayShort(it) }
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
                "${dayShort(time.dayOfWeek)} ${formatClock(time.hour, time.minute)}"
            )
        }
        hours > 0 -> context.getString(R.string.alarm_rings_in_hours, hours.toInt(), minutes.toInt())
        else -> context.getString(R.string.alarm_rings_in_minutes, minutes.toInt())
    }
}
