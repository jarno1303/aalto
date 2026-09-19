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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var settings by remember { mutableStateOf(AudioSettings.equalizer(context)) }
    var bands by remember { mutableStateOf(AudioEffects.bands) }
    var gainDb by remember(stationId) { mutableIntStateOf(AudioSettings.stationGainDb(context, stationId)) }
    var choosingPreset by remember { mutableStateOf(false) }
    val plusActive = remember { Plus.access(context).isActive() }
    val presets = remember { AudioEffects.presetNames() }

    fun save(updated: EqualizerSettings) {
        settings = updated
        AudioSettings.saveEqualizer(context, updated)
        AudioEffects.applyEqualizer(updated)
        bands = AudioEffects.bands
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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

            // ---- Station gain: the everyday annoyance, so it comes first ----
            Spacer(modifier = Modifier.height(AaltoSpaceL))
            Text(
                text = stringResource(R.string.audio_station_gain),
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
                    text = stringResource(R.string.audio_station_gain_hint),
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

private fun decibelLabel(db: Int): String = when {
    db > 0 -> "+$db dB"
    else -> "$db dB"
}

private fun frequencyLabel(centerHz: Int): String =
    if (centerHz >= 1000) "${centerHz / 1000} kHz" else "$centerHz Hz"
