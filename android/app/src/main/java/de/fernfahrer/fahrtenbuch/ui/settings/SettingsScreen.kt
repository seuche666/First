package de.fernfahrer.fahrtenbuch.ui.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit = {}, onOpenExpenses: () -> Unit = {}) {
    val context = LocalContext.current
    val savedName by vm.driverName.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val reportReminder by vm.reportReminder.collectAsStateWithLifecycle()
    val work by vm.workSettings.collectAsStateWithLifecycle()
    val mapSettings by vm.mapSettings.collectAsStateWithLifecycle()
    var confirmDeleteTracks by remember { mutableStateOf(false) }
    var name by remember(savedName) { mutableStateOf(savedName) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.writeBackup(context, it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.readBackup(context, it) }
    }
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard(title = "Name", icon = Icons.Rounded.Person) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(60)
                            vm.setDriverName(name.trim())
                        },
                        label = { Text("Dein Name (optional)") },
                        supportingText = { Text("Erscheint im Export und später im Spesenbericht.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }

            item {
                SectionCard(title = "Spesentabelle", icon = Icons.Rounded.Euro) {
                    Text(
                        "Deine Beträge je Land für volle Tage und An-/Abreisetage sowie die " +
                            "Übernachtungspauschale im LKW – eine Tabelle pro Jahr.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    FilledTonalButton(onClick = onOpenExpenses, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Rounded.TableChart, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Spesentabelle öffnen")
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Erinnerung an die Reisekostenabrechnung", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Am 1. jedes Monats um 18 Uhr: „Reisekostenabrechnung für … erstellen?“",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = reportReminder, onCheckedChange = vm::setReportReminder)
                    }
                }
            }

            item {
                SectionCard(title = "Datensicherung", icon = Icons.Rounded.Backup) {
                    Text(
                        "Alle Daten liegen nur auf diesem Handy. Speichere regelmäßig eine Sicherung, " +
                            "z. B. in Google Drive oder per E-Mail – und vor allem vor einem Handywechsel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    FilledTonalButton(
                        onClick = { createBackup.launch("Fahrtenbuch_Sicherung_${LocalDate.now()}.json") },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Backup, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sicherung speichern")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Restore, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sicherung wiederherstellen")
                    }
                    Spacer(Modifier.height(14.dp))
                    AutoBackupSection(vm)
                }
            }

            item {
                WorkSettingsCard(work = work, onChange = vm::setWorkSettings)
            }

            item {
                MapSettingsCard(
                    map = mapSettings,
                    onChange = vm::setMapSettings,
                    onPermissionsChanged = vm::syncTracking,
                    onDeleteTracks = { confirmDeleteTracks = true },
                )
            }

            item {
                SectionCard(title = "So zählt die App", icon = Icons.AutoMirrored.Rounded.Rule) {
                    RuleRow("Voller Tag", "24 Stunden von zu Hause weg – ein Tag mitten in einer mehrtägigen Tour.")
                    RuleRow("Halber Tag", "An- oder Abreisetag einer Tour, oder eine eintägige Fahrt mit mehr als 8 Stunden Abwesenheit.")
                    RuleRow(
                        "Unter 8 Std.",
                        "Eintägige Fahrt mit 8 Stunden oder weniger: zählt in Auswertung und Jahresexport (Steuer) " +
                            "nicht und wird dort getrennt ausgewiesen.",
                    )
                    RuleRow(
                        "Firmenregel",
                        "Nur in der Reisekostenabrechnung für die Firma: Jede eintägige Fahrt bekommt den halben Satz, " +
                            "auch unter 8 Stunden.",
                    )
                    RuleRow(
                        "Arbeitszeit",
                        "Arbeitsbeginn und -ende sind nur für die Firma und ändern die Einordnung nicht.",
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Du kannst die Einordnung bei jedem Tag manuell ändern. Euro-Beträge rechnet die App mit " +
                            "deiner Spesentabelle. Im Zweifel frag deinen Steuerberater.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = "Über die App", icon = Icons.Rounded.Info) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Kein Konto, kein Server: Alle Daten bleiben auf deinem Gerät. Internet nur für die Kartenbilder.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Version ${version ?: "?"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmDeleteTracks) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTracks = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null) },
            title = { Text("Alle Strecken löschen?") },
            text = {
                Text(
                    "Alle aufgezeichneten GPS-Strecken werden gelöscht und die Karte ist wieder komplett " +
                        "verdeckt. Tage, Touren und Arbeitszeiten bleiben erhalten. Das kann nicht rückgängig " +
                        "gemacht werden (außer mit einer vorher gespeicherten Datensicherung).",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteTracks = false
                    vm.deleteAllTracks()
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteTracks = false }) { Text("Abbrechen") } },
        )
    }

    pending?.let { data ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text("Sicherung wiederherstellen?") },
            text = {
                val range = data.days.map { it.date }.let { d ->
                    if (d.isEmpty()) "" else " (${Format.date(d.min())} bis ${Format.date(d.max())})"
                }
                val tracksText = data.tracks?.let { t ->
                    val km = Math.round(t.sumOf { it.lengthMeters } / 1000.0).toInt()
                    " und GPS-Strecken über ${Format.km(km)}"
                } ?: ""
                Text(
                    "Die Sicherung enthält ${Format.days(data.days.size)}$range$tracksText.\n\n" +
                        "Alle aktuellen Einträge auf diesem Handy werden dabei ersetzt.",
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmRestore) { Text("Wiederherstellen") } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Abbrechen") } },
        )
    }
}
