package de.fernfahrer.fahrtenbuch.ui.editor

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.CountryPickerSheet
import de.fernfahrer.fahrtenbuch.ui.components.DatePickerDialogFor
import de.fernfahrer.fahrtenbuch.ui.components.TimePickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onDone: (message: String?) -> Unit,
    mainVm: MainViewModel,
    vm: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val shiftDraft by vm.shift.collectAsStateWithLifecycle()
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
            AbsenceSection(s, vm, onPickStart = { pickStart = true }, onPickEnd = { pickEnd = true })

            // Arbeitszeit für die Firma (Schichten, an Mitternacht geteilt)
            WorkSection(s, vm)

            // Land
            CountrySection(s, onPickCountry = { pickCountry = true })

            // Kilometer
            KmSection(s, vm)

            // Fehlende Tage der Tour
            MissingDaysCard(s, vm)

            // Übernachtung im LKW
            OvernightSection(s, vm)

            // Einordnung
            ClassificationSection(s, vm)

            // Notiz
            NoteSection(s, vm)

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
    shiftDraft?.let { draft ->
        ShiftDialog(
            draft = draft,
            onChange = vm::updateShift,
            onSave = vm::saveShift,
            onDelete = vm::deleteShift,
            onDismiss = vm::closeShift,
        )
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
