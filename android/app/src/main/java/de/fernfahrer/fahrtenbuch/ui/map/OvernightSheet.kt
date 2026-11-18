package de.fernfahrer.fahrtenbuch.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.data.OvernightPlace
import de.fernfahrer.fahrtenbuch.data.OvernightPlaceEntity
import de.fernfahrer.fahrtenbuch.data.OvernightStayEntity
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format

/** Nächster Ort zu einem Platz (offline), z. B. „3 km von Fulda“; null solange gesucht wird. */
@Composable
internal fun rememberPlaceLabel(lat: Double, lon: Double): String? {
    val app = LocalContext.current.applicationContext as? FahrtenbuchApp
    val label by produceState<String?>(null, lat, lon) {
        value = try {
            app?.location?.placeLocator()?.nearest(lat, lon)?.label
        } catch (e: Exception) {
            null
        }
    }
    return label
}

/** Anzeigename eines Platzes: eigener Name, sonst der nächste Ort. */
internal fun placeTitle(place: OvernightPlaceEntity, nearest: String?): String =
    place.name.ifBlank { nearest ?: "Übernachtungsplatz" }

/**
 * Antippen eines Übernachtungsplatzes: Name, Notizen (Sterne, Ausstattung, Freitext), einzelne
 * Übernachtungen; Umbenennen, Versetzen und Löschen. Änderungen werden sofort gespeichert.
 * Betrifft nur die Karte – die Zählung „Übernachtung im LKW“ bleibt unverändert.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OvernightSheet(
    place: OvernightPlace,
    onUpdate: (OvernightPlaceEntity) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDeleteStay: (OvernightStayEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val current by rememberUpdatedState(place.place)
    val p = place.place
    val nearest = rememberPlaceLabel(p.lat, p.lon)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember(p.id) { mutableStateOf(p.note) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var stayToDelete by remember { mutableStateOf<OvernightStayEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Kopf: Name, Ort, Land, Anzahl
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(placeTitle(p, nearest), style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(
                            nearest?.takeIf { p.name.isNotBlank() },
                            p.countryCode?.let(Countries::label),
                            if (place.count == 1) "1 Übernachtung" else "${place.count} Übernachtungen",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { renaming = true }) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Umbenennen")
                }
                OutlinedButton(onClick = onMove) {
                    Icon(Icons.Rounded.OpenWith, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Versetzen")
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Löschen")
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Notizen
            Text("Notizen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (i in 1..5) {
                    IconButton(onClick = { onUpdate(current.copy(rating = if (current.rating == i) 0 else i)) }) {
                        Icon(
                            if (i <= p.rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "$i von 5 Sternen",
                            tint = if (i <= p.rating) Color(0xFFF5A623) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureChip("Dusche", p.shower) { onUpdate(current.copy(shower = it)) }
                FeatureChip("WC", p.toilet) { onUpdate(current.copy(toilet = it)) }
                FeatureChip("Restaurant/Imbiss", p.food) { onUpdate(current.copy(food = it)) }
                FeatureChip("Sicher/bewacht", p.secure) { onUpdate(current.copy(secure = it)) }
                FeatureChip("Ruhig", p.quiet) { onUpdate(current.copy(quiet = it)) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it.take(1000)
                    onUpdate(current.copy(note = note))
                },
                label = { Text("Freitext") },
                placeholder = { Text("z. B. ab 18 Uhr voll") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (p.hasNotes || note.isNotBlank()) {
                TextButton(onClick = {
                    note = ""
                    onUpdate(current.withoutNotes())
                }) { Text("Notizen löschen") }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Einzelne Übernachtungen
            Text("Übernachtungen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Löschen entfernt nur den Kartenpunkt; die Zählung der Übernachtungen im LKW bleibt unverändert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (stay in place.stays.sortedWith(compareByDescending<OvernightStayEntity> { it.date }.thenByDescending { it.minute })) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.weekdayDate(stay.date), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { stayToDelete = stay }) {
                        Icon(Icons.Rounded.DeleteOutline, "Übernachtung vom ${Format.weekdayDate(stay.date)} löschen")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "%.5f, %.5f".format(java.util.Locale.ROOT, p.lat, p.lon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (renaming) {
        RenameDialog(
            initial = p.name,
            suggestion = nearest,
            onSave = {
                renaming = false
                onUpdate(current.copy(name = it.trim()))
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text("Platz löschen?") },
            text = {
                Text(
                    "„${placeTitle(p, nearest)}“ mit ${if (place.count == 1) "1 Übernachtung" else "${place.count} Übernachtungen"} " +
                        "und allen Notizen wird von der Karte gelöscht. Die Zählung der Übernachtungen im LKW bleibt unverändert.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
    stayToDelete?.let { stay ->
        val last = place.count <= 1
        AlertDialog(
            onDismissRequest = { stayToDelete = null },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text("Übernachtung löschen?") },
            text = {
                Text(
                    "Übernachtung vom ${Format.weekdayDate(stay.date)} von der Karte löschen?" +
                        if (last) " Es ist die einzige an diesem Platz – der Platz wird mit seinen Notizen gelöscht." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    stayToDelete = null
                    onDeleteStay(stay)
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { stayToDelete = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun FeatureChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
private fun RenameDialog(initial: String, suggestion: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, null) },
        title = { Text("Platz umbenennen") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    label = { Text("Name") },
                    placeholder = { Text("z. B. Autohof Fulda") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Leer lassen, um wieder den nächsten Ort anzuzeigen" + (suggestion?.let { " ($it)" } ?: "") + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
