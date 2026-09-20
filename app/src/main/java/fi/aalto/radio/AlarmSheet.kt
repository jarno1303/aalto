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
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
 * Wake-up radio panel, following the phone clock app pattern: every change is
 * saved at once (no Save button) and "Valmis" closes it.
 *
 * Laid out as grouped cards: the time and its switch, the week, the station
 * as one row (the full list opens only when changing it), then the rarely
 * touched settings folded into their own card.
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
    onEnableNotifications: () -> Unit = {},
    exactAlarmsAllowed: Boolean = true,
    onAllowExactAlarms: () -> Unit = {},
    fullScreenAllowed: Boolean = true,
    onAllowFullScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingTime by remember { mutableStateOf(false) }
    var editingSnooze by remember { mutableStateOf(false) }
    var choosingStation by remember { mutableStateOf(false) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }

    // Without a saved station the first own station is used.
    val selectedStation = stations.firstOrNull { it.id == settings.stationId } ?: stations.firstOrNull()
    fun withStation(base: AlarmSettings, station: RadioStation?): AlarmSettings =
        if (station == null) base else base.copy(
            stationId = station.id,
            stationName = station.name,
            streamUrl = station.preferredStreamUrl
        )
    val snoozeLabel = if (settings.snoozeMinutes == 0) {
        stringResource(R.string.alarm_snooze_off)
    } else {
        stringResource(R.string.sleep_timer_minutes, settings.snoozeMinutes)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        MatchSheetNavigationBar()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AaltoSpaceL)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.alarm_settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = AaltoSpaceXs)
            )
            Spacer(modifier = Modifier.height(AaltoSpaceM))

            // Time, switch and when it rings.
            AlarmCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = AaltoSpaceL, end = AaltoSpaceL, top = AaltoSpaceM)
                ) {
                    Text(
                        text = formatClock(settings.hour, settings.minute),
                        fontSize = 60.sp,
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
                Text(
                    text = if (settings.enabled && nextRingMillis != null) {
                        alarmCountdownText(context, nextRingMillis)
                    } else {
                        listOf(
                            daysSummary(context, settings.days),
                            stringResource(R.string.alarm_off)
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (settings.enabled) FontWeight.Medium else FontWeight.Normal,
                    color = if (settings.enabled) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = AaltoSpaceL, end = AaltoSpaceL, bottom = AaltoSpaceL)
                )
            }

            // What could stop the alarm from ringing on time, most serious
            // first, each with the one tap that fixes it.
            if (settings.enabled && !exactAlarmsAllowed) {
                AlarmWarningCard(
                    icon = Icons.Outlined.AlarmOff,
                    text = stringResource(R.string.alarm_exact_off),
                    action = stringResource(R.string.alarm_allow_exact),
                    onAction = onAllowExactAlarms
                )
            }
            if (!notificationsEnabled) {
                // Without notifications the alarm can only be stopped from the app.
                AlarmWarningCard(
                    icon = Icons.Outlined.NotificationsOff,
                    text = stringResource(R.string.alarm_notifications_off),
                    action = stringResource(R.string.alarm_allow_notifications),
                    onAction = onEnableNotifications
                )
            }
            if (settings.enabled && notificationsEnabled && !fullScreenAllowed) {
                AlarmWarningCard(
                    icon = Icons.Outlined.Fullscreen,
                    text = stringResource(R.string.alarm_fullscreen_off),
                    action = stringResource(R.string.alarm_allow_fullscreen),
                    onAction = onAllowFullScreen
                )
            }

            // The week
            AlarmSectionLabel(stringResource(R.string.alarm_days))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AaltoSpaceXs),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DayOfWeek.values().forEach { day ->
                    val selected = day in settings.days
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (selected) AaltoBlue else MaterialTheme.colorScheme.surfaceVariant)
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
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = if (settings.days.isEmpty()) {
                    stringResource(R.string.alarm_days_hint)
                } else {
                    daysSummary(context, settings.days)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AaltoSpaceXs, top = AaltoSpaceS)
            )

            // Station: one row; the list opens only when changing it.
            AlarmSectionLabel(stringResource(R.string.alarm_station))
            AlarmCard {
                if (selectedStation == null) {
                    Text(
                        text = stringResource(R.string.alarm_no_stations),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(AaltoSpaceL)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = stations.size > 1,
                                onClickLabel = stringResource(R.string.alarm_change_station),
                                role = Role.Button
                            ) { choosingStation = true }
                            .padding(horizontal = AaltoSpaceL, vertical = AaltoSpaceM),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StationLogo(station = selectedStation, size = 44.dp, cornerRadius = 10.dp)
                        Spacer(modifier = Modifier.width(AaltoSpaceM))
                        Text(
                            text = selectedStation.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (stations.size > 1) {
                            Text(
                                text = stringResource(R.string.alarm_change),
                                style = MaterialTheme.typography.labelLarge,
                                color = AaltoBlue
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = AaltoBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // More settings: rarely changed, so folded away by default.
            Spacer(modifier = Modifier.height(AaltoSpaceL))
            AlarmCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { advancedOpen = !advancedOpen }
                        .padding(horizontal = AaltoSpaceL),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alarm_advanced),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (!advancedOpen) {
                            Text(
                                text = "${stringResource(R.string.alarm_snooze)} $snoozeLabel · " +
                                    stringResource(R.string.alarm_volume, settings.volumePercent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (advancedOpen) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Button) { editingSnooze = true }
                            .padding(horizontal = AaltoSpaceL),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.alarm_snooze_length),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = snoozeLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AaltoBlue
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = AaltoSpaceL)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = AaltoSpaceL, end = AaltoSpaceL, top = AaltoSpaceM),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.alarm_volume_label),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${settings.volumePercent} %",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AaltoBlue
                        )
                    }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AaltoSpaceL)
                    )
                    Text(
                        text = stringResource(R.string.alarm_volume_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AaltoSpaceL)
                    )
                    OutlinedButton(
                        onClick = onTest,
                        enabled = selectedStation != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AaltoSpaceL)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(AaltoSpaceS))
                        Text(stringResource(R.string.alarm_test_now))
                    }
                }
            }

            if (advancedOpen && log.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.alarm_log_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = AaltoSpaceXs, top = AaltoSpaceM)
                )
                Text(
                    text = log.takeLast(8).reversed().joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AaltoSpaceXs)
                )
            }

            Spacer(modifier = Modifier.height(AaltoSpaceXl))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) { Text(stringResource(R.string.alarm_done)) }

            Spacer(modifier = Modifier.height(AaltoSpaceL))
        }
    }

    if (choosingStation) {
        AlarmStationDialog(
            stations = stations,
            selectedId = selectedStation?.id,
            onPick = { station ->
                choosingStation = false
                onChange(withStation(settings, station))
            },
            onDismiss = { choosingStation = false }
        )
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

/** A problem that could stop the alarm, with the tap that fixes it. */
@Composable
private fun AlarmWarningCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    action: String,
    onAction: () -> Unit
) {
    Spacer(modifier = Modifier.height(AaltoSpaceS))
    AlarmCard {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(start = AaltoSpaceL, end = AaltoSpaceL, top = AaltoSpaceM)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(AaltoSpaceM))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(
            onClick = onAction,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = AaltoSpaceXs, bottom = AaltoSpaceXs)
        ) {
            Text(action)
        }
    }
}

/** A grouped settings card: soft fill, no border, rounded like the tiles. */
@Composable
private fun AlarmCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun AlarmSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = AaltoSpaceXs, top = AaltoSpaceXl, bottom = AaltoSpaceS)
    )
}

/** The alarm's station, chosen from the user's own stations. */
@Composable
private fun AlarmStationDialog(
    stations: List<RadioStation>,
    selectedId: String?,
    onPick: (RadioStation) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alarm_station)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                items(items = stations, key = { it.stableId }) { station ->
                    val selected = station.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = selected, role = Role.RadioButton) { onPick(station) }
                            .padding(vertical = AaltoSpaceS, horizontal = AaltoSpaceXs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StationLogo(station = station, size = 36.dp, cornerRadius = 8.dp)
                        Spacer(modifier = Modifier.width(AaltoSpaceM))
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) AaltoBlue else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = AaltoBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.alarm_cancel)) }
        }
    )
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
