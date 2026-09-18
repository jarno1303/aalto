package fi.aalto.radio.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import fi.aalto.radio.R

/**
 * Shown when an alarm rings while the app is already open, where the
 * lock-screen view never appears. The same three ways out as everywhere else:
 * snooze, keep listening, stop.
 */
@Composable
internal fun AlarmRingingDialog(
    stationName: String,
    snoozeMinutes: Int,
    usingFallback: Boolean,
    onSnooze: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        // An alarm is not dismissed by tapping past it: one of the three
        // buttons has to be chosen.
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(text = stringResource(R.string.alarm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (usingFallback) {
                        stringResource(R.string.alarm_fallback_note)
                    } else {
                        stationName
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (snoozeMinutes > 0) {
                    Button(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text(text = stringResource(R.string.alarm_snooze_minutes, snoozeMinutes))
                    }
                }
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(text = stringResource(R.string.alarm_continue))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(text = stringResource(R.string.alarm_dismiss))
                }
            }
        },
        confirmButton = {}
    )
}
