package de.fernfahrer.fahrtenbuch.ui.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Format

/** Abwesenheit von zu Hause: Beginn, Ende, „seit Vortag“/„über Nacht“. */
@Composable
internal fun AbsenceSection(s: EditorState, vm: EditorViewModel, onPickStart: () -> Unit, onPickEnd: () -> Unit) {
    FieldCard(icon = Icons.Rounded.Schedule, title = "Abwesend von zu Hause") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeTile("Beginn", Format.time(s.startMinute), Modifier.weight(1f), onClick = onPickStart)
            TimeTile("Ende", Format.time(s.endMinute), Modifier.weight(1f), onClick = onPickEnd)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("Seit Vortag weg", s.startMinute == 0, Modifier.weight(1f)) {
                vm.setAwaySincePreviousDay(it)
            }
            ToggleChip("Über Nacht weg", s.endMinute == MIDNIGHT, Modifier.weight(1f)) {
                vm.setOvernight(it)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Abwesenheit: ${Format.duration(s.absenceMinutes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Arbeitszeiten dieses Tages (Schichten, an Mitternacht geteilt). */
@Composable
internal fun WorkSection(s: EditorState, vm: EditorViewModel) {
    FieldCard(icon = Icons.Rounded.Work, title = "Arbeitszeit (für die Firma)") {
        if (s.work.isEmpty()) {
            Text(
                "An diesem Tag ist keine Arbeitszeit erfasst.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            s.work.forEach { portion ->
                Surface(
                    onClick = { vm.editShift(portion.shiftId) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(portion.label, style = MaterialTheme.typography.titleMedium)
                            val hint = buildList {
                                if (portion.fromPreviousDay) add("seit dem Vortag")
                                if (portion.toNextDay) add("geht am Folgetag weiter")
                                portion.minutes?.let { add(Format.duration(it)) }
                            }.joinToString(" · ")
                            if (hint.isNotEmpty()) {
                                Text(
                                    hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(Icons.Rounded.Edit, "Arbeitszeit bearbeiten", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                "Summe an diesem Tag: ${Format.duration(s.workMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = vm::newShift) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Arbeitszeit hinzufügen")
        }
        Text(
            "Arbeitszeiten über Mitternacht teilt die App automatisch auf die Tage auf. " +
                "Sie haben keinen Einfluss auf die Einordnung für die Steuer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Länder des Tages. Mehrere Länder an einem Tag sind möglich (von Hand oder per GPS beim
 * Grenzübertritt); gezählt wird der Tag für das zuletzt besuchte ausländische Land.
 */
@Composable
internal fun CountrySection(s: EditorState, onPickCountry: (index: Int?) -> Unit, onRemove: (String) -> Unit) {
    val shown = s.shownCountries
    FieldCard(icon = Icons.Rounded.Public, title = if (shown.size > 1) "Länder" else "Land") {
        shown.forEachIndexed { index, code ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Surface(
                onClick = { onPickCountry(index) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Countries.flag(code), fontSize = 30.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Countries.name(code), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                shown.size > 1 && code == s.countryCode -> "Zählt für diesen Tag · tippen zum Ändern"
                                shown.size > 1 -> "Tippen zum Ändern"
                                s.countryManual -> "Von Hand gewählt · tippen zum Ändern"
                                else -> "Automatisch (GPS/Vortag) · tippen zum Ändern"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (shown.size > 1) {
                        IconButton(onClick = { onRemove(code) }) {
                            Icon(Icons.Rounded.Close, "${Countries.name(code)} entfernen")
                        }
                    }
                }
            }
        }
        TextButton(onClick = { onPickCountry(null) }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Weiteres Land")
        }
        Text(
            if (shown.size > 1) {
                "An diesem Tag warst du in mehreren Ländern. Gezählt wird der Tag für " +
                    "${Countries.label(s.countryCode)} (das Ausland hat Vorrang)."
            } else {
                "Bei Grenzübertritten während der Arbeitszeit ergänzt die App das Land automatisch per GPS " +
                    "(nicht, wenn du es hier von Hand änderst)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Kilometerstand morgens und abends. */
@Composable
internal fun KmSection(s: EditorState, vm: EditorViewModel) {
    FieldCard(icon = Icons.Rounded.Speed, title = "Kilometerstand") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = s.kmStart,
                onValueChange = vm::setKmStart,
                label = { Text("Morgens") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = s.kmEnd,
                onValueChange = vm::setKmEnd,
                label = { Text("Abends") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            s.distanceKm?.let {
                "Gefahren: ${Format.km(it)}" + if (s.kmEstimated) " (anteilig geschätzt)" else ""
            } ?: "Gefahrene km werden automatisch berechnet.",
            style = if (s.distanceKm != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (s.distanceKm != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Hinweis auf fehlende Tage der Tour mit Ankreuzfeld zum Ergänzen. */
@Composable
internal fun MissingDaysCard(s: EditorState, vm: EditorViewModel) {
    if (s.missingDates.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { vm.setFillGaps(!s.fillGaps) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = s.fillGaps, onCheckedChange = vm::setFillGaps)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        "${Format.days(s.missingDates.size)} dieser Tour fehlen",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        missingText(s) + " als volle Tage ergänzen (Land wie am Vortag).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Schalter „Übernachtung im LKW“. */
@Composable
internal fun OvernightSection(s: EditorState, vm: EditorViewModel) {
    FieldCard(icon = Icons.Rounded.Hotel, title = "Übernachtung im LKW") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.overnight) "Ja, die Nacht nach diesem Tag im LKW" else "Nein",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    when {
                        s.overnightOverride == null && s.overnightAuto -> "Automatisch: Nacht während der Tour"
                        s.overnightOverride == null -> "Automatisch: keine Tour über Nacht"
                        else -> "Von Hand geändert"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.overnight, onCheckedChange = vm::setOvernightInTruck)
        }
    }
}

/** Einordnung (automatisch oder von Hand). */
@Composable
internal fun ClassificationSection(s: EditorState, vm: EditorViewModel) {
    FieldCard(icon = Icons.Rounded.AutoAwesome, title = "Einordnung") {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth().animateContentSize(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    if (s.override == null) "Automatisch: ${s.autoType.label}" else "Manuell: ${s.override!!.label}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (s.override == null) s.autoReason else "Automatisch wäre: ${s.autoType.label} (${s.autoReason})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val options = listOf<Pair<String, DayType?>>(
            "Auto" to null, "Voll" to DayType.FULL, "Halb" to DayType.HALF, "Keiner" to DayType.NONE,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (label, type) ->
                SegmentedButton(
                    selected = s.override == type,
                    onClick = { vm.setOverride(type) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
    }
}

/** Freie Notiz. */
@Composable
internal fun NoteSection(s: EditorState, vm: EditorViewModel) {
    FieldCard(icon = Icons.Rounded.EditNote, title = "Notiz (optional)") {
        OutlinedTextField(
            value = s.note,
            onValueChange = vm::setNote,
            placeholder = { Text("z. B. Ladung, Kunde, Besonderheiten") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = MaterialTheme.shapes.medium,
        )
    }
}
