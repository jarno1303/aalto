package fi.aalto.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Which countries the user follows. A plain list of checkboxes: nothing is
 * merged, so each country keeps its own most-listened order.
 *
 * Lives behind Settings rather than on the browsing screen, because choosing
 * countries is something you do once, and browsing is something you do daily.
 */
@Composable
internal fun CountryPickerDialog(
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val codes = remember(selected) { (selected + browsableCountryCodes).distinct() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.countries_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(items = codes, key = { it }) { code ->
                    val isOn = code in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Checkbox) {
                                val updated = if (isOn) selected - code else selected + code
                                // At least one country, or browsing has nothing to show.
                                if (updated.isNotEmpty()) onSelectedChange(updated)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isOn, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(AaltoSpaceS))
                        CountryFlag(code)
                        Spacer(modifier = Modifier.width(AaltoSpaceS))
                        Text(
                            text = countryName(code),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.alarm_done))
            }
        }
    )
}
