package fi.aalto.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Settings: appearance and Aalto Sync. Kept to one small dialog so settings
 * never compete with the listening surface.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsDialog(
    themeMode: AaltoThemeMode,
    onThemeModeChange: (AaltoThemeMode) -> Unit,
    syncState: SyncUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenLanguage: (() -> Unit)?,
    onOpenCountries: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AaltoSpaceM)) {
                // What someone looks for first when the app is in a language
                // they cannot read: put it at the top, not in the middle.
                if (onOpenLanguage != null) {
                    SettingsRow(
                        title = stringResource(R.string.language_title),
                        action = stringResource(R.string.language_open),
                        onClick = onOpenLanguage
                    )
                }
                SettingsRow(
                    title = stringResource(R.string.countries_title),
                    action = stringResource(R.string.audio_open),
                    onClick = onOpenCountries
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = AaltoSpaceXs)
                )

                SettingsSectionTitle(stringResource(R.string.theme_title))
                // A flow row, not a plain row: in a plain row the long first
                // label eats the width and "Tumma" wraps onto three lines.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS),
                    verticalArrangement = Arrangement.spacedBy(AaltoSpaceXs)
                ) {
                    ThemeChoice(AaltoThemeMode.SYSTEM, R.string.theme_system, themeMode, onThemeModeChange)
                    ThemeChoice(AaltoThemeMode.LIGHT, R.string.theme_light, themeMode, onThemeModeChange)
                    ThemeChoice(AaltoThemeMode.DARK, R.string.theme_dark, themeMode, onThemeModeChange)
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = AaltoSpaceXs)
                )

                SettingsSectionTitle(stringResource(R.string.audio_title))
                SettingsRow(
                    title = stringResource(R.string.audio_settings_row),
                    action = stringResource(R.string.audio_open),
                    onClick = onOpenAudio
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = AaltoSpaceXs)
                )

                SettingsSectionTitle(stringResource(R.string.sync_title))
                Text(
                    text = stringResource(R.string.sync_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (syncState.accountEmail != null) {
                    Text(
                        text = syncState.accountEmail,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = syncStatusText(syncState),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (syncState.accountEmail == null) {
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sync_sign_in))
                    }
                } else {
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sync_sign_out))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sync_close)) }
        }
    )
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun ThemeChoice(
    mode: AaltoThemeMode,
    labelRes: Int,
    selectedMode: AaltoThemeMode,
    onSelect: (AaltoThemeMode) -> Unit
) {
    FilterChip(
        selected = mode == selectedMode,
        onClick = { onSelect(mode) },
        label = {
            Text(
                text = stringResource(labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** Maps the coordinator's internal (English) status values to user-facing Finnish text. */
@Composable
internal fun syncStatusText(state: SyncUiState): String = when (state.status) {
    "Connected" -> stringResource(R.string.sync_status_connected)
    "Sync pending" -> stringResource(R.string.sync_status_pending)
    "Sync problem" -> stringResource(R.string.sync_status_problem)
    "Account mismatch" -> stringResource(R.string.sync_status_account_mismatch)
    "Signed out" -> stringResource(R.string.sync_status_signed_out)
    else -> state.status
}

/** One settings line: what it is on the left, what tapping does on the right. */
@Composable
private fun SettingsRow(title: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = action,
            style = MaterialTheme.typography.labelLarge,
            color = AaltoBlue,
            maxLines = 1
        )
    }
}
