package de.fernfahrer.fahrtenbuch.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.ui.components.DatePickerDialogFor
import de.fernfahrer.fahrtenbuch.ui.components.TimePickerDialog

/** Arbeitszeit (Schicht) bearbeiten: Beginn und Ende mit Datum, km-Stände. */
@Composable
fun ShiftDialog(
    draft: ShiftDraft,
    onChange: ((ShiftDraft) -> ShiftDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pick by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.isNew) "Arbeitszeit hinzufügen" else "Arbeitszeit bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Beginn", style = MaterialTheme.typography.labelLarge)
                DateTimeRow(
                    date = Format.weekdayDate(draft.startDate),
                    time = Format.time(draft.startMinute),
                    onDate = { pick = "startDate" },
                    onTime = { pick = "startTime" },
                )
                Spacer(Modifier.height(12.dp))
                Text("Ende", style = MaterialTheme.typography.labelLarge)
                if (draft.running) {
                    Text(
                        "Läuft noch – beenden über „Arbeit beenden“ auf dem Startbildschirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    DateTimeRow(
                        date = Format.weekdayDate(draft.endDate),
                        time = Format.time(draft.endMinute),
                        onDate = { pick = "endDate" },
                        onTime = { pick = "endTime" },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.kmStart,
                        onValueChange = { v -> onChange { it.copy(kmStart = v.filter(Char::isDigit).take(7)) } },
                        label = { Text("km Beginn") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    if (!draft.running) {
                        OutlinedTextField(
                            value = draft.kmEnd,
                            onValueChange = { v -> onChange { it.copy(kmEnd = v.filter(Char::isDigit).take(7)) } },
                            label = { Text("km Ende") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    "Geht die Arbeitszeit über Mitternacht, werden Zeit und km anteilig auf die Tage verteilt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                draft.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                if (!draft.isNew) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Arbeitszeit löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )

    when (pick) {
        "startDate" -> DatePickerDialogFor(draft.startDate, onDismiss = { pick = null }) { d ->
            pick = null
            onChange { it.copy(startDate = d, endDate = if (it.endDate.isBefore(d)) d else it.endDate) }
        }
        "endDate" -> DatePickerDialogFor(draft.endDate, onDismiss = { pick = null }) { d ->
            pick = null
            onChange { it.copy(endDate = d) }
        }
        "startTime" -> TimePickerDialog("Arbeitsbeginn", draft.startMinute, onDismiss = { pick = null }) { m ->
            pick = null
            onChange { it.copy(startMinute = m) }
        }
        "endTime" -> TimePickerDialog("Arbeitsende", draft.endMinute.coerceAtMost(1439), onDismiss = { pick = null }) { m ->
            pick = null
            onChange { it.copy(endMinute = m) }
        }
    }
}

@Composable
private fun DateTimeRow(date: String, time: String, onDate: () -> Unit, onTime: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Surface(
            onClick = onDate,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1.6f),
        ) {
            Text(date, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Surface(
            onClick = onTime,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f),
        ) {
            Text(time, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}
