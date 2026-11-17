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
fun rememberMissingWorkPermissions(gps: Boolean, reminder: Boolean, tracking: Boolean = false): () -> List<String> {
    val context = LocalContext.current
    return {
        buildList {
            fun missing(p: String) = ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED
            // Für die Strecke braucht es den genauen Standort, für das Land reicht der ungefähre.
            if ((tracking && missing(Manifest.permission.ACCESS_FINE_LOCATION)) ||
                (gps && missing(Manifest.permission.ACCESS_FINE_LOCATION) && missing(Manifest.permission.ACCESS_COARSE_LOCATION))
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if ((reminder || tracking) && Build.VERSION.SDK_INT >= 33 && missing(Manifest.permission.POST_NOTIFICATIONS)) {
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
    tracking: Boolean = false,
    backgroundExplained: () -> Boolean = { true },
    markBackgroundExplained: () -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val missing = rememberMissingWorkPermissions(gps, reminder, tracking)
    var pendingAt by remember { mutableStateOf<java.time.LocalDateTime?>(null) }
    var showRationale by remember { mutableStateOf(false) }
    var showBackground by remember { mutableStateOf(false) }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Nach dem Start: einmalig erklären, warum „Immer erlauben“ für die Aufzeichnung hilft. */
    fun maybeAskBackground() {
        if (tracking && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundExplained() &&
            de.fernfahrer.fahrtenbuch.work.Tracking.hasLocationPermission(context) &&
            !de.fernfahrer.fahrtenbuch.work.Tracking.hasBackgroundPermission(context)
        ) {
            showBackground = true
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        markExplained()
        pendingAt?.let(onStart)
        pendingAt = null
        maybeAskBackground()
    }

    if (showBackground) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.LocationOn, null) },
            title = { Text("Standort „Immer erlauben“?") },
            text = {
                Column {
                    Text(
                        "Die Strecke wird jetzt während der Arbeitszeit für deine Karte aufgezeichnet – auch wenn " +
                            "die App im Hintergrund ist (du siehst dann eine Benachrichtigung).",
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Mit „Immer erlauben“ kann Android die Aufzeichnung auch nach einem Neustart des Handys " +
                            "oder einem App-Update selbst fortsetzen. Aufgezeichnet wird trotzdem nur während der " +
                            "Arbeitszeit, und die Daten bleiben auf dem Handy.",
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Android öffnet dazu die Einstellung „Standortberechtigung“ – dort „Immer zulassen“ wählen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackground = false
                    markBackgroundExplained()
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("Einstellung öffnen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackground = false
                    markBackgroundExplained()
                }) { Text("Nicht jetzt") }
            },
        )
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Bevor es losgeht") },
            text = {
                Column {
                    Text("Damit die App dich unterstützen kann, fragt Android gleich nach diesen Erlaubnissen:")
                    Spacer(Modifier.height(12.dp))
                    if (tracking) {
                        PermissionRow(
                            Icons.Rounded.LocationOn,
                            "Standort (genau) – während der Arbeitszeit wird deine Strecke für die Karte " +
                                "aufgezeichnet" + (if (gps) " und das Land vorgeschlagen" else "") +
                                ". Nur GPS, kein Internet, die Daten bleiben auf dem Handy.",
                        )
                    } else if (gps) {
                        PermissionRow(
                            Icons.Rounded.LocationOn,
                            "Standort – nur bei Arbeitsbeginn und -ende, um das Land vorzuschlagen. " +
                                "Kein GPS im Hintergrund, kein Internet.",
                        )
                    }
                    if (reminder || tracking) {
                        PermissionRow(
                            Icons.Rounded.Notifications,
                            "Benachrichtigungen – " + listOfNotNull(
                                if (reminder) "Erinnerung, wenn die Arbeit länger als $reminderHours Std. läuft" else null,
                                if (tracking) "Anzeige, dass die Strecke aufgezeichnet wird" else null,
                            ).joinToString("; ") + ".",
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
            maybeAskBackground()
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
