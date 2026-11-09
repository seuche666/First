package de.fernfahrer.fahrtenbuch.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.CountryPickerSheet
import de.fernfahrer.fahrtenbuch.ui.components.DatePickerDialogFor
import de.fernfahrer.fahrtenbuch.ui.components.TimePickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onDone: (message: String?) -> Unit,
    vm: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
    mainVm: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val recent by mainVm.recentCountries.collectAsStateWithLifecycle()

    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    var pickEnd by rememberSaveable { mutableStateOf(false) }
    var pickCountry by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(s.finished) {
        if (s.finished) onDone(s.finishedMessage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.title) },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    if (!s.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.DeleteOutline, "Eintrag löschen")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    AnimatedVisibility(visible = s.error != null) {
                        Row(
                            Modifier.padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(s.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Button(
                        onClick = vm::save,
                        enabled = !s.loading,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (s.mode) {
                                EditorMode.TOUR_START -> "Tour starten"
                                EditorMode.TOUR_END -> "Tour beenden"
                                else -> "Speichern"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TourBanner(s)

            // Datum
            BigField(
                icon = Icons.Rounded.CalendarMonth,
                label = "Datum",
                value = Format.longDate(s.date),
                onClick = { pickDate = true },
            )

            // Zeiten
            FieldCard(icon = Icons.Rounded.Schedule, title = "Abwesend von zu Hause") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeTile("Beginn", Format.time(s.startMinute), Modifier.weight(1f)) { pickStart = true }
                    TimeTile("Ende", Format.time(s.endMinute), Modifier.weight(1f)) { pickEnd = true }
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

            // Land
            FieldCard(icon = Icons.Rounded.Public, title = "Land") {
                Surface(
                    onClick = { pickCountry = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Countries.flag(s.countryCode), fontSize = 30.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Countries.name(s.countryCode), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tippen zum Ändern",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Maßgeblich ist meist das Land, in dem du vor 24 Uhr zuletzt warst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Kilometer
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
                    s.distanceKm?.let { "Gefahren: ${Format.km(it)}" } ?: "Gefahrene km werden automatisch berechnet.",
                    style = if (s.distanceKm != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (s.distanceKm != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Fehlende Tage der Tour
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

            // Einordnung
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

            // Notiz
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
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickDate) {
        DatePickerDialogFor(s.date, onDismiss = { pickDate = false }) {
            pickDate = false
            vm.setDate(it)
        }
    }
    if (pickStart) {
        TimePickerDialog("Beginn der Abwesenheit", s.startMinute, onDismiss = { pickStart = false }) {
            pickStart = false
            vm.setStart(it)
        }
    }
    if (pickEnd) {
        TimePickerDialog("Ende der Abwesenheit", if (s.endMinute == MIDNIGHT) 0 else s.endMinute, onDismiss = { pickEnd = false }) {
            pickEnd = false
            vm.setEnd(it)
        }
    }
    if (pickCountry) {
        CountryPickerSheet(
            selected = s.countryCode,
            recent = recent,
            onSelect = {
                vm.setCountry(it)
                pickCountry = false
            },
            onDismiss = { pickCountry = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text("Eintrag löschen?") },
            text = { Text("Der Eintrag vom ${Format.weekdayDate(s.date)} wird endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

private fun missingText(s: EditorState): String {
    val dates = s.missingDates
    return if (dates.size == 1) Format.weekdayDate(dates.first())
    else "${Format.shortDate(dates.first())} bis ${Format.date(dates.last())}"
}

@Composable
private fun TourBanner(s: EditorState) {
    val text = when (s.mode) {
        EditorMode.TOUR_START -> "Heute beginnt deine Tour. Der erste Tag zählt als Anreisetag (halber Tag)."
        EditorMode.TOUR_DAY -> s.tourStart?.let { "Tour seit ${Format.weekdayDate(it)}. Tage mitten in der Tour zählen als volle Tage." }
        EditorMode.TOUR_END -> "Heute endet deine Tour. Der letzte Tag zählt als Rückreisetag (halber Tag)."
        EditorMode.NORMAL -> s.tourStart?.let { "Dieser Tag gehört zur Tour ab ${Format.weekdayDate(it)}." }
    } ?: return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalShipping, null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FieldCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun BigField(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TimeTile(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        modifier = modifier,
    )
}
