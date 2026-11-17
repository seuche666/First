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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.automirrored.rounded.Rule
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
import androidx.compose.material3.Slider
import androidx.compose.material.icons.rounded.Work
import de.fernfahrer.fahrtenbuch.data.WorkSettings
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val savedName by vm.driverName.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val work by vm.workSettings.collectAsStateWithLifecycle()
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
        topBar = { LargeTopAppBar(title = { Text("Einstellungen") }, scrollBehavior = scroll) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard(title = "Fahrer", icon = Icons.Rounded.Person) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(60)
                            vm.setDriverName(name.trim())
                        },
                        label = { Text("Dein Name (erscheint im Export)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    )
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
                }
            }

            item {
                WorkSettingsCard(work = work, onChange = vm::setWorkSettings)
            }

            item {
                SectionCard(title = "So zählt die App", icon = Icons.AutoMirrored.Rounded.Rule) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Eintägige Fahrten unter 8 Stunden als halben Tag zählen",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Firmenregel. Achtung: Das Finanzamt erkennt einen halben Tag erst bei mehr als " +
                                    "8 Stunden an. Betroffene Tage werden in Auswertung und Export markiert.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = rules.shortSingleDayAsHalf, onCheckedChange = vm::setShortSingleDayAsHalf)
                    }
                    RuleRow("Voller Tag", "24 Stunden von zu Hause weg – ein Tag mitten in einer mehrtägigen Tour.")
                    if (rules.shortSingleDayAsHalf) {
                        RuleRow(
                            "Halber Tag",
                            "An- oder Abreisetag einer Tour, oder jede eintägige Fahrt (Firmenregel aktiv).",
                        )
                    } else {
                        RuleRow("Halber Tag", "An- oder Abreisetag einer Tour, oder eine eintägige Fahrt mit mehr als 8 Stunden Abwesenheit.")
                        RuleRow("Zählt nicht", "Eintägige Fahrt mit 8 Stunden oder weniger.")
                    }
                    RuleRow(
                        "Arbeitszeit",
                        "Arbeitsbeginn und -ende sind nur für die Firma und ändern die Einordnung nicht.",
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Du kannst die Einordnung bei jedem Tag manuell ändern. Die App zählt nur Tage – " +
                            "Steuerbeträge werden nicht berechnet. Im Zweifel frag deinen Steuerberater.",
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
                            "Kein Konto, kein Server: Alles bleibt offline auf deinem Gerät.",
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

    pending?.let { data ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text("Sicherung wiederherstellen?") },
            text = {
                val range = data.days.map { it.date }.let { d ->
                    if (d.isEmpty()) "" else " (${Format.date(d.min())} bis ${Format.date(d.max())})"
                }
                Text(
                    "Die Sicherung enthält ${Format.days(data.days.size)}$range.\n\n" +
                        "Alle aktuellen Einträge auf diesem Handy werden dabei ersetzt.",
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmRestore) { Text("Wiederherstellen") } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun RuleRow(title: String, text: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Erinnerung bei langer Arbeitszeit und GPS-Ländererkennung. */
@Composable
private fun WorkSettingsCard(work: WorkSettings, onChange: (WorkSettings) -> Unit) {
    SectionCard(title = "Arbeitszeit", icon = Icons.Rounded.Work) {
        SettingSwitch(
            title = "Erinnerung bei langer Arbeitszeit",
            text = "Benachrichtigung „Arbeit läuft seit ${work.reminderHours} Std. – vergessen zu beenden?“ " +
                "mit „Arbeit beenden“ und „Läuft noch“ (erneut nach 3 Std.).",
            checked = work.reminderEnabled,
            onChange = { onChange(work.copy(reminderEnabled = it)) },
        )
        if (work.reminderEnabled) {
            Text("Erinnern nach ${work.reminderHours} Stunden", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = work.reminderHours.toFloat(),
                onValueChange = { onChange(work.copy(reminderHours = it.toInt())) },
                valueRange = de.fernfahrer.fahrtenbuch.data.Settings.MIN_HOURS.toFloat()..de.fernfahrer.fahrtenbuch.data.Settings.MAX_HOURS.toFloat(),
                steps = de.fernfahrer.fahrtenbuch.data.Settings.MAX_HOURS - de.fernfahrer.fahrtenbuch.data.Settings.MIN_HOURS - 1,
            )
            PermissionStatus(
                permissions = if (android.os.Build.VERSION.SDK_INT >= 33) listOf(android.Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
                missingText = "Benachrichtigungen sind für die App nicht erlaubt – die Erinnerung kann nicht erscheinen.",
            )
        }
        Spacer(Modifier.height(8.dp))
        SettingSwitch(
            title = "Land per GPS erkennen",
            text = "Bei Arbeitsbeginn und -ende (und beim Öffnen der App nach Mitternacht) wird das Land " +
                "einmalig per GPS ermittelt und vorgeschlagen – offline, ohne Dauer-GPS. Von Hand gewählte " +
                "Länder haben immer Vorrang.",
            checked = work.gpsEnabled,
            onChange = { onChange(work.copy(gpsEnabled = it)) },
        )
        if (work.gpsEnabled) {
            PermissionStatus(
                permissions = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION),
                missingText = "Standort ist für die App nicht erlaubt – das Land wird wie bisher von Hand gewählt.",
                anyGranted = true,
            )
        }
    }
}

@Composable
private fun SettingSwitch(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Zeigt an, ob eine Android-Berechtigung fehlt, mit Button zum Erlauben bzw. zu den App-Einstellungen. */
@Composable
private fun PermissionStatus(permissions: List<String>, missingText: String, anyGranted: Boolean = false) {
    if (permissions.isEmpty()) return
    val context = LocalContext.current
    fun granted(): Boolean {
        val results = permissions.map {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return if (anyGranted) results.any { it } else results.all { it }
    }
    var ok by remember { mutableStateOf(granted()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        ok = granted()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ok = granted()
        if (!ok) {
            // Dauerhaft abgelehnt: zu den App-Einstellungen
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    if (!ok) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                missingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { launcher.launch(permissions.toTypedArray()) }) { Text("Erlauben") }
        }
    }
}
