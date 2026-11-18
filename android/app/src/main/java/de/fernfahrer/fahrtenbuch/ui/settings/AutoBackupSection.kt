package de.fernfahrer.fahrtenbuch.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fernfahrer.fahrtenbuch.data.AutoBackupSettings
import de.fernfahrer.fahrtenbuch.data.SafBackupFolder
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LAST_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'")

/** Automatisches Backup: Schalter, Abstand, Speicherort, Anzahl, letztes Backup, „Jetzt sichern“. */
@Composable
internal fun AutoBackupSection(vm: MainViewModel) {
    val context = LocalContext.current
    val auto by vm.autoBackup.collectAsStateWithLifecycle()
    val folder by vm.backupFolder.collectAsStateWithLifecycle()
    val last by vm.lastAutoBackup.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val folderName = remember(folder) {
        folder?.let { SafBackupFolder.displayName(context.contentResolver, Uri.parse(it)) ?: "Gewählter Ordner" }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.setBackupFolder(context, it) }
    }

    Column {
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        SettingSwitch(
            title = "Automatisches Backup",
            text = "Sichert regelmäßig im Hintergrund alle Daten (inklusive GPS-Strecken, Übernachtungsplätzen " +
                "und Notizen) in einen Ordner – auch wenn die App geschlossen ist. Schlägt es fehl, kommt eine Benachrichtigung.",
            checked = auto.enabled,
            onChange = { on ->
                vm.setAutoBackup(auto.copy(enabled = on))
                if (on && folder == null) runCatching { pickFolder.launch(null) }
            },
        )
        Spacer(Modifier.height(6.dp))
        Text("Abstand", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AutoBackupSettings.Interval.entries.forEachIndexed { i, interval ->
                SegmentedButton(
                    selected = auto.interval == interval,
                    onClick = { vm.setAutoBackup(auto.copy(interval = interval)) },
                    shape = SegmentedButtonDefaults.itemShape(i, AutoBackupSettings.Interval.entries.size),
                    icon = {},
                    label = { Text(interval.label, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Aufbewahrte Backups", style = MaterialTheme.typography.titleSmall)
        Text(
            "Ältere automatische Backups werden gelöscht.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AutoBackupSettings.KEEP_OPTIONS.forEachIndexed { i, n ->
                SegmentedButton(
                    selected = auto.keep == n,
                    onClick = { vm.setAutoBackup(auto.copy(keep = n)) },
                    shape = SegmentedButtonDefaults.itemShape(i, AutoBackupSettings.KEEP_OPTIONS.size),
                    icon = {},
                    label = { Text(n.toString(), maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Speicherort", style = MaterialTheme.typography.titleSmall)
        Text(
            folderName ?: "Noch kein Ordner gewählt (z. B. ein Ordner auf dem Handy oder in Google Drive).",
            style = MaterialTheme.typography.bodyMedium,
            color = if (folderName == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { runCatching { pickFolder.launch(null) } }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Rounded.Folder, null)
            Spacer(Modifier.width(8.dp))
            Text(if (folder == null) "Ordner wählen" else "Anderen Ordner wählen")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Letztes Backup: " + if (last > 0L) {
                LAST_FORMAT.format(Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()))
            } else {
                "noch keins"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { if (folder == null) runCatching { pickFolder.launch(null) } else vm.backupNow() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.CloudUpload, null)
            Spacer(Modifier.width(8.dp))
            Text("Jetzt sichern")
        }
    }
}
