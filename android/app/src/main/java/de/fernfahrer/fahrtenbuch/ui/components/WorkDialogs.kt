package de.fernfahrer.fahrtenbuch.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.ui.KmPrompt
import kotlinx.coroutines.delay

/**
 * Fragt beim Beenden nach dem aktuellen km-Stand. Feld sofort aktiv (Zahlentastatur),
 * letzter bekannter km-Stand als markierter Vorschlag. „Später“ beendet trotzdem.
 */
@Composable
fun KmDialog(
    prompt: KmPrompt,
    onSave: (Int) -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = prompt.suggestion?.toString().orEmpty()
    var value by remember(prompt) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    var error by remember(prompt) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    fun trySave() {
        val km = value.text.toIntOrNull()
        error = when {
            km == null -> "Bitte den km-Stand eingeben – oder „Später“ wählen."
            prompt.minimum != null && km < prompt.minimum ->
                "Der km-Stand ist kleiner als der Startwert (${Format.km(prompt.minimum)}). Bitte prüfen."
            else -> null
        }
        if (error == null && km != null) onSave(km)
    }

    LaunchedEffect(prompt) {
        delay(150)
        runCatching { focus.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Speed, null) },
        title = {
            Text(
                when (prompt.kind) {
                    KmPrompt.Kind.END_WORK -> "Arbeit beenden"
                    KmPrompt.Kind.END_TOUR -> "Tour beenden"
                },
            )
        },
        text = {
            Column {
                Text(
                    buildString {
                        append("Aktueller Kilometerstand um ${Format.time(prompt.at.hour * 60 + prompt.at.minute)} Uhr")
                        if (prompt.kind == KmPrompt.Kind.END_TOUR && prompt.endsRunningWork) {
                            append(" – die laufende Arbeitszeit wird mit beendet")
                        }
                        append(":")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { v ->
                        value = v.copy(text = v.text.filter(Char::isDigit).take(7))
                        error = null
                    },
                    label = { Text("km-Stand") },
                    suffix = { Text("km") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(
                            error ?: prompt.minimum?.let { "Startwert: ${Format.km(it)}" } ?: "",
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { trySave() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = ::trySave) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Später") } },
    )
}

/** Benötigte Berechtigungen für Erinnerung (Benachrichtigungen) und GPS-Land, die noch fehlen. */
@Composable
fun rememberMissingWorkPermissions(gps: Boolean, reminder: Boolean): () -> List<String> {
    val context = LocalContext.current
    return {
        buildList {
            fun missing(p: String) = ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED
            if (gps && missing(Manifest.permission.ACCESS_FINE_LOCATION) && missing(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (reminder && Build.VERSION.SDK_INT >= 33 && missing(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * Ablauf für „Arbeit beginnen“: Beim ersten Mal erklärt ein Dialog, wofür die App Standort und
 * Benachrichtigungen braucht, dann folgt die Android-Abfrage. Danach (egal ob erlaubt) wird die
 * Arbeit begonnen. Gibt eine Funktion zurück, die den Ablauf startet.
 */
@Composable
fun rememberStartWorkFlow(
    gps: Boolean,
    reminder: Boolean,
    reminderHours: Int,
    alreadyExplained: () -> Boolean,
    markExplained: () -> Unit,
    onStart: (java.time.LocalDateTime) -> Unit,
): () -> Unit {
    val missing = rememberMissingWorkPermissions(gps, reminder)
    var pendingAt by remember { mutableStateOf<java.time.LocalDateTime?>(null) }
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        markExplained()
        pendingAt?.let(onStart)
        pendingAt = null
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Bevor es losgeht") },
            text = {
                Column {
                    Text("Damit die App dich unterstützen kann, fragt Android gleich nach diesen Erlaubnissen:")
                    Spacer(Modifier.height(12.dp))
                    if (gps) {
                        PermissionRow(
                            Icons.Rounded.LocationOn,
                            "Standort – nur bei Arbeitsbeginn und -ende, um das Land vorzuschlagen. " +
                                "Kein GPS im Hintergrund, kein Internet.",
                        )
                    }
                    if (reminder) {
                        PermissionRow(
                            Icons.Rounded.Notifications,
                            "Benachrichtigungen – Erinnerung, wenn die Arbeit länger als $reminderHours Std. läuft.",
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Du kannst beides ablehnen; die App funktioniert dann wie bisher mit Auswahl von Hand.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(missing().toTypedArray())
                }) { Text("Weiter") }
            },
        )
    }

    return {
        val at = java.time.LocalDateTime.now()
        val needed = missing()
        if (needed.isNotEmpty() && !alreadyExplained()) {
            pendingAt = at
            showRationale = true
        } else {
            onStart(at)
        }
    }
}

@Composable
private fun PermissionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
