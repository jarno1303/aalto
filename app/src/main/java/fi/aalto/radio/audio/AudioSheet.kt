package fi.aalto.radio.audio

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Surface
import fi.aalto.radio.AaltoSpaceM
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.aalto.radio.AaltoBlue
import fi.aalto.radio.AaltoSpaceL
import fi.aalto.radio.AaltoSpaceS
import fi.aalto.radio.AaltoSpaceXl
import fi.aalto.radio.AaltoSpaceXs
import fi.aalto.radio.MatchSheetNavigationBar
import fi.aalto.radio.R
import fi.aalto.radio.WheelChoiceDialog
import fi.aalto.radio.plus.Plus

/**
 * Sound: the equalizer, and how loud this station should be compared to the
 * others. Changes take effect while listening and are saved as they are made,
 * the same as the alarm panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioSheet(
    stationId: String?,
    stationName: String?,
    onDismiss: () -> Unit,
    /** The user's own stations: all of them are levelled, measured in the background. */
    ownStations: List<fi.aalto.radio.RadioStation> = emptyList()
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var settings by remember { mutableStateOf(AudioSettings.equalizer(context)) }
    var bands by remember { mutableStateOf(AudioEffects.bands) }
    var gainDb by remember(stationId) { mutableIntStateOf(AudioSettings.stationGainDb(context, stationId)) }
    var choosingPreset by remember { mutableStateOf(false) }
    val plusActive = remember { Plus.access(context).isActive() }
    var autoLevelOn by remember { mutableStateOf(AudioSettings.autoLevelOn(context)) }
    var measured by remember(stationId) { mutableStateOf(stationId?.let { AutoLevel.measured(context, it) }) }
    var measuringProgress by remember(stationId) { mutableStateOf<Float?>(null) }
    // Measuring happens while the sheet is open: follow it, so the result
    // appears by itself instead of the sheet looking stuck.
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(stationId, autoLevelOn) {
        while (true) {
            measured = stationId?.let { AutoLevel.measured(context, it) }
            measuringProgress = AutoLevel.progress(stationId)
            refresh++
            kotlinx.coroutines.delay(1_000)
        }
    }
    // Every own station gets measured, quietly, one after another.
    LaunchedEffect(autoLevelOn, ownStations) {
        if (plusActive && autoLevelOn) LevelSurvey.start(context, ownStations) else LevelSurvey.stop()
    }
    val survey by LevelSurvey.state.collectAsState()
    var referenceName by remember { mutableStateOf(AudioSettings.referenceName(context)) }
    var hasReference by remember { mutableStateOf(AudioSettings.referenceLufs(context) != null) }
    // Once: adjustments made before levelling would now count twice.
    var askOldGains by remember {
        mutableStateOf(
            plusActive && autoLevelOn && !AudioSettings.oldGainsAsked(context) &&
                AudioSettings.stationGains(context).isNotEmpty()
        )
    }
    val presets = remember { AudioEffects.presetNames() }

    fun save(updated: EqualizerSettings) {
        settings = updated
        AudioSettings.saveEqualizer(context, updated)
        AudioEffects.applyEqualizer(updated)
        bands = AudioEffects.bands
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        MatchSheetNavigationBar()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AaltoSpaceXl)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.audio_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---- Automatic levelling (Plus): checked once, here at the entry point ----
            Spacer(modifier = Modifier.height(AaltoSpaceL))
            if (plusActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.audio_auto_level),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoLevelOn,
                        onCheckedChange = { on ->
                            autoLevelOn = on
                            AudioSettings.setAutoLevelOn(context, on)
                            AudioEffects.applyStationGain(context, stationId, smooth = true)
                            if (on && !AudioSettings.oldGainsAsked(context) &&
                                AudioSettings.stationGains(context).isNotEmpty()
                            ) {
                                askOldGains = true
                            }
                        }
                    )
                }
                Text(
                    text = stringResource(R.string.audio_auto_level_what),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (autoLevelOn && stationId != null) {
                    // The result first, in one number; how it is made up under it.
                    val current = measured
                    val autoDb = current?.let { AutoLevel.gainFor(it.lufs, AutoLevel.target(context)) }
                    val totalDb = AudioEffects.totalGainDb(context, stationId)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AaltoSpaceM)
                    ) {
                        Column(modifier = Modifier.padding(AaltoSpaceL)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stationName ?: stringResource(R.string.audio_this_station),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (autoDb != null || gainDb != 0) {
                                    Text(
                                        text = decibelLabel(totalDb),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AaltoBlue
                                    )
                                }
                            }
                            if (autoDb == null) {
                                // Fills as the station is heard: plainly working, not stuck.
                                LinearProgressIndicator(
                                    progress = { measuringProgress ?: 0f },
                                    color = AaltoBlue,
                                    trackColor = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = AaltoSpaceS)
                                )
                            }
                            Text(
                                text = when {
                                    autoDb == null -> stringResource(R.string.audio_auto_level_measuring)
                                    gainDb == 0 -> stringResource(R.string.audio_auto_level_parts_auto, decibelLabel(autoDb))
                                    else -> stringResource(
                                        R.string.audio_auto_level_parts,
                                        decibelLabel(autoDb),
                                        decibelLabel(gainDb)
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = if (hasReference && referenceName != null) {
                                    stringResource(R.string.audio_reference_station, referenceName!!)
                                } else {
                                    stringResource(R.string.audio_reference_default)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = AaltoSpaceS)
                            )
                            // One under the other: side by side the second one was
                                            // squeezed into a column of single letters.
                            Column {
                                val isReference = hasReference && referenceName == stationName
                                if (current != null && !isReference) {
                                    TextButton(onClick = {
                                        AudioSettings.setReference(
                                            context,
                                            AutoLevel.clampTarget(current.lufs),
                                            stationName
                                        )
                                        hasReference = true
                                        referenceName = stationName
                                        AudioEffects.applyStationGain(context, stationId, smooth = true)
                                    }) {
                                        Text(stringResource(R.string.audio_reference_use_this))
                                    }
                                }
                                if (hasReference) {
                                    TextButton(onClick = {
                                        AudioSettings.setReference(context, null, null)
                                        hasReference = false
                                        referenceName = null
                                        AudioEffects.applyStationGain(context, stationId, smooth = true)
                                    }) {
                                        Text(stringResource(R.string.audio_reference_reset))
                                    }
                                }
                            }
                        }
                    }
                }
                if (autoLevelOn && ownStations.isNotEmpty()) {
                    OwnStationsLevels(
                        stations = ownStations,
                        survey = survey,
                        playingId = stationId,
                        playingProgress = measuringProgress,
                        refresh = refresh
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.audio_auto_level),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.audio_auto_level_plus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Station gain: the everyday annoyance, free for everyone ----
            val levelling = plusActive && autoLevelOn
            Spacer(modifier = Modifier.height(AaltoSpaceL))
            Text(
                text = stringResource(
                    if (levelling) R.string.audio_own_adjustment else R.string.audio_station_gain
                ),
                style = MaterialTheme.typography.titleMedium
            )
            if (stationId == null) {
                Text(
                    text = stringResource(R.string.audio_no_station),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stationName.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = decibelLabel(gainDb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (gainDb == 0) MaterialTheme.colorScheme.onSurfaceVariant else AaltoBlue
                    )
                }
                Slider(
                    value = gainDb.toFloat(),
                    onValueChange = { value ->
                        gainDb = value.toInt()
                        AudioSettings.saveStationGainDb(context, stationId, gainDb)
                        AudioEffects.applyStationGain(context, stationId)
                    },
                    // Sync gets the value the user settled on, not every step on the way.
                    onValueChangeFinished = {
                        StationGainSync.onUserChanged(context, stationId, gainDb)
                    },
                    valueRange = AudioSettings.MIN_GAIN_DB.toFloat()..AudioSettings.MAX_GAIN_DB.toFloat(),
                    steps = AudioSettings.MAX_GAIN_DB - AudioSettings.MIN_GAIN_DB - 1,
                    // Still snaps to whole decibels, without sixteen dots on the track.
                    colors = SliderDefaults.colors(
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(
                        if (levelling) R.string.audio_own_adjustment_hint else R.string.audio_station_gain_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = AaltoSpaceL)
            )

            // ---- Equalizer ----
            // The Plus line for the equalizer: checked once, here at the entry point.
            if (plusActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.audio_equalizer),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.enabled,
                        enabled = AudioEffects.available,
                        onCheckedChange = { on -> save(settings.copy(enabled = on)) }
                    )
                }

                if (!AudioEffects.available) {
                    Text(
                        text = stringResource(R.string.audio_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (settings.enabled) {
                    if (presets.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { choosingPreset = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.audio_preset),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = presets.getOrNull(settings.preset)
                                    ?: stringResource(R.string.audio_preset_custom),
                                color = AaltoBlue,
                                maxLines = 1
                            )
                        }
                    }

                    bands.forEach { band ->
                        Text(
                            text = frequencyLabel(band.centerHz),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = band.levelMb.toFloat(),
                            onValueChange = { value ->
                                val levels = bands.map { it.levelMb }.toMutableList()
                                levels[band.index] = value.toInt()
                                save(
                                    settings.copy(
                                        preset = EqualizerSettings.PRESET_CUSTOM,
                                        bandLevels = levels
                                    )
                                )
                            },
                            valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.audio_equalizer),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.audio_equalizer_plus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(AaltoSpaceL))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stationId != null && gainDb != 0) {
                    TextButton(onClick = {
                        gainDb = 0
                        AudioSettings.saveStationGainDb(context, stationId, 0)
                        AudioEffects.applyStationGain(context, stationId)
                        StationGainSync.onUserChanged(context, stationId, 0)
                    }) {
                        Text(stringResource(R.string.audio_reset))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.alarm_done))
                }
            }
            Spacer(modifier = Modifier.height(AaltoSpaceL))
        }
    }

    if (askOldGains) {
        val count = remember { AudioSettings.stationGains(context).size }
        AlertDialog(
            onDismissRequest = {
                askOldGains = false
                AudioSettings.markOldGainsAsked(context)
            },
            title = { Text(stringResource(R.string.audio_old_gains_title)) },
            text = {
                Text(
                    LocalContext.current.resources.getQuantityString(R.plurals.audio_old_gains_text, count, count)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askOldGains = false
                    AudioSettings.markOldGainsAsked(context)
                    // Reset everywhere: the reset syncs like any adjustment.
                    AudioSettings.stationGains(context).keys.forEach { id ->
                        AudioSettings.saveStationGainDb(context, id, 0)
                        StationGainSync.onUserChanged(context, id, 0)
                    }
                    gainDb = 0
                    AudioEffects.applyStationGain(context, stationId, smooth = true)
                }) { Text(stringResource(R.string.audio_old_gains_reset)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askOldGains = false
                    AudioSettings.markOldGainsAsked(context)
                }) { Text(stringResource(R.string.audio_old_gains_keep)) }
            }
        )
    }

    if (choosingPreset) {
        val options = presets + stringResource(R.string.audio_preset_custom)
        WheelChoiceDialog(
            title = stringResource(R.string.audio_preset),
            options = options,
            selectedIndex = if (settings.preset in presets.indices) settings.preset else options.lastIndex,
            onConfirm = { index ->
                choosingPreset = false
                val preset = if (index in presets.indices) index else EqualizerSettings.PRESET_CUSTOM
                save(settings.copy(preset = preset))
            },
            onDismiss = { choosingPreset = false }
        )
    }
}

private fun decibelLabel(db: Double): String {
    val rounded = Math.round(db * 2) / 2.0
    val text = if (rounded % 1.0 == 0.0) "${rounded.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", rounded)
    return when {
        rounded > 0 -> "+$text dB"
        rounded < 0 -> "$text dB"
        else -> "0 dB"
    }
}

private fun decibelLabel(db: Int): String = when {
    db > 0 -> "+$db dB"
    else -> "$db dB"
}

private fun frequencyLabel(centerHz: Int): String =
    if (centerHz >= 1000) "${centerHz / 1000} kHz" else "$centerHz Hz"

/**
 * Every own station with its level: done ("−4 dB"), being measured (a bar),
 * waiting, or not measurable. The whole list levels itself; nothing to pick.
 */
@Composable
private fun OwnStationsLevels(
    stations: List<fi.aalto.radio.RadioStation>,
    survey: SurveyState,
    playingId: String?,
    playingProgress: Float?,
    @Suppress("UNUSED_PARAMETER") refresh: Int
) {
    val context = LocalContext.current
    val target = AutoLevel.target(context)
    val unique = stations.distinctBy { it.id }
    val done = unique.count { AutoLevel.measured(context, it.id) != null }
    Text(
        text = stringResource(R.string.audio_own_stations_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = AaltoSpaceL)
    )
    Text(
        text = if (done == unique.size) {
            stringResource(R.string.audio_own_stations_all_done)
        } else {
            stringResource(R.string.audio_own_stations_progress, done, unique.size)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    unique.forEach { station ->
        val value = AutoLevel.measured(context, station.id)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AaltoSpaceXs)
        ) {
            fi.aalto.radio.StationLogo(station = station, size = 32.dp, cornerRadius = 8.dp)
            Spacer(modifier = Modifier.width(AaltoSpaceM))
            Text(
                text = station.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val progress = when {
                value != null -> null
                station.id == survey.currentId -> survey.progress
                station.id == playingId -> playingProgress ?: 0f
                else -> null
            }
            when {
                value != null -> Text(
                    text = decibelLabel(AutoLevel.gainFor(value.lufs, target)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AaltoBlue
                )
                progress != null -> LinearProgressIndicator(
                    progress = { progress },
                    color = AaltoBlue,
                    trackColor = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(64.dp)
                )
                station.id in survey.failed -> Text(
                    text = stringResource(R.string.audio_own_stations_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Text(
                    text = stringResource(R.string.audio_own_stations_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
