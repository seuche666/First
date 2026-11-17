package de.fernfahrer.fahrtenbuch.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.data.WorkSettings
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard

/** Erinnerung bei langer Arbeitszeit und GPS-Ländererkennung. */
@Composable
internal fun WorkSettingsCard(work: WorkSettings, onChange: (WorkSettings) -> Unit) {
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
