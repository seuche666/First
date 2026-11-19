package de.fernfahrer.fahrtenbuch.ui.expenses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Euro

/** Eingabefeld für einen Euro-Betrag mit Komma (leer erlaubt). */
@Composable
internal fun EuroField(label: String, value: String, onChange: (String) -> Unit, last: Boolean = false) {
    val valid = Euro.isValid(value)
    OutlinedTextField(
        value = value,
        onValueChange = { t -> onChange(t.filter { it.isDigit() || it == ',' || it == '.' }.take(9)) },
        label = { Text(label) },
        suffix = { Text("€") },
        placeholder = { Text("z. B. 14,00") },
        isError = !valid,
        supportingText = if (!valid) ({ Text("Bitte Betrag wie 14,00 eingeben") }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
}

private fun Long?.toInput(): String = this?.let(Euro::format).orEmpty()

/** Beträge eines Landes bearbeiten. [isNew] = Land steht noch nicht in der Tabelle. */
@Composable
internal fun RateDialog(
    year: Int,
    code: String,
    fullDayCents: Long?,
    partialDayCents: Long?,
    isNew: Boolean,
    onSave: (full: Long?, partial: Long?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var full by remember { mutableStateOf(fullDayCents.toInput()) }
    var partial by remember { mutableStateOf(partialDayCents.toInput()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = Euro.isValid(full) && Euro.isValid(partial)

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text("${Countries.name(code)} löschen?") },
            text = { Text("Das Land und seine Beträge werden aus der Spesentabelle $year entfernt. Andere Jahre bleiben unverändert.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(Countries.flag(code), fontSize = 32.sp) },
        title = { Text(Countries.name(code)) },
        text = {
            Column {
                Text(
                    "Spesentabelle $year",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                EuroField("Voller Tag (24 Std.)", full, { full = it })
                Spacer(Modifier.height(8.dp))
                EuroField("An-/Abreisetag bzw. halber Tag", partial, { partial = it }, last = true)
                if (!isNew) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        Text(" Land löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(Euro.parse(full), Euro.parse(partial)) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Übernachtungspauschale LKW pro Tag bearbeiten. */
@Composable
internal fun OvernightDialog(year: Int, cents: Long?, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(cents.toInput()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Hotel, null) },
        title = { Text("Übernachtungspauschale LKW") },
        text = {
            Column {
                Text(
                    "Betrag pro Übernachtung im LKW, gilt $year für alle Länder gleich.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                EuroField("Pro Tag", text, { text = it }, last = true)
            }
        },
        confirmButton = {
            TextButton(enabled = Euro.isValid(text), onClick = { onSave(Euro.parse(text)) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
