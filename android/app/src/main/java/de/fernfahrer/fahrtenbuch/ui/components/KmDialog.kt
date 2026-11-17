package de.fernfahrer.fahrtenbuch.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
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
