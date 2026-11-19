package de.fernfahrer.fahrtenbuch.ui.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.ReportRow

/** Text „Reiseanlass und Reiseweg“ eines Tages ändern oder den automatischen Text wiederherstellen. */
@Composable
internal fun TravelTextDialog(
    row: ReportRow,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(row.date) { mutableStateOf(row.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reiseanlass und Reiseweg") },
        text = {
            Column {
                Text(Format.weekdayDate(row.date), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(300) },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (row.isManual) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Automatisch wäre: ${row.autoText.replace("\n", " / ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onReset) { Text("Automatischen Text wiederherstellen") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
