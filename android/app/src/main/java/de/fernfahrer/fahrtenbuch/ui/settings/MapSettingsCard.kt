package de.fernfahrer.fahrtenbuch.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import de.fernfahrer.fahrtenbuch.domain.Format
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.data.MapSettings
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard
import de.fernfahrer.fahrtenbuch.ui.map.TrackingTexts
import de.fernfahrer.fahrtenbuch.ui.map.rememberTrackingPermissions

/** Streckenaufzeichnung, Korridorbreite, Berechtigungen und Löschen der Strecken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapSettingsCard(
    map: MapSettings,
    onChange: (MapSettings) -> Unit,
    onPermissionsChanged: () -> Unit,
    onDeleteTracks: () -> Unit,
) {
    val permissions = rememberTrackingPermissions(onChanged = onPermissionsChanged)
    SectionCard(title = "Karte", icon = Icons.Rounded.Map) {
        SettingSwitch(
            title = "Strecke während der Arbeitszeit aufzeichnen",
            text = "Startet automatisch bei „Arbeit beginnen“ und stoppt bei „Arbeit beenden“. Funktioniert " +
                "offline (nur GPS des Handys). Die Punkte bleiben auf dem Handy und sind in der Datensicherung enthalten.",
            checked = map.trackingEnabled,
            onChange = { onChange(map.copy(trackingEnabled = it)) },
        )
        if (map.trackingEnabled) {
            if (!permissions.location) {
                PermissionHint(TrackingTexts.LOCATION_MISSING, "Erlauben", permissions.requestLocation)
            } else if (!permissions.background) {
                PermissionHint(TrackingTexts.BACKGROUND_MISSING, "Immer erlauben", permissions.requestBackground)
            }
            if (!permissions.batteryOk) {
                PermissionHint(TrackingTexts.BATTERY, "Ausnahme erlauben", permissions.openBattery)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Breite der freigelegten Strecke", style = MaterialTheme.typography.titleSmall)
        Text(
            "Gilt auch für alle bisherigen Strecken. Beim Rauszoomen bleiben die Strecken immer sichtbar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MapSettings.CORRIDOR_OPTIONS.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = map.corridorMeters == m,
                    onClick = { onChange(map.copy(corridorMeters = m)) },
                    shape = SegmentedButtonDefaults.itemShape(i, MapSettings.CORRIDOR_OPTIONS.size),
                    label = { Text(if (m >= 1000) "${m / 1000} km" else "$m m", maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        FuelField(map.fuelPer100Km) { onChange(map.copy(fuelPer100Km = it)) }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onDeleteTracks, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Rounded.DeleteForever, null)
            Spacer(Modifier.width(8.dp))
            Text("Alle Strecken löschen")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Kartendaten © OpenStreetMap-Mitwirkende. Ortsnamen: GeoNames (CC BY 4.0), offline in der App. Die Kartenbilder werden bei Bedarf aus dem Internet " +
                "geladen und zwischengespeichert; dabei wird nur der angezeigte Kartenausschnitt abgefragt, " +
                "nie deine Strecken.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dieselverbrauch für die Kachel „Liter Diesel“ (aus den eingetragenen Tacho-km). */
@Composable
private fun FuelField(value: Double, onChange: (Double) -> Unit) {
    var text by remember { mutableStateOf(Format.decimal(value)) }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed in MapSettings.MIN_FUEL..MapSettings.MAX_FUEL
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() || it == ',' || it == '.' }.take(5)
            text.replace(',', '.').toDoubleOrNull()
                ?.takeIf { it in MapSettings.MIN_FUEL..MapSettings.MAX_FUEL }
                ?.let(onChange)
        },
        label = { Text("Dieselverbrauch") },
        suffix = { Text("l / 100 km") },
        singleLine = true,
        isError = !valid,
        supportingText = {
            Text(
                if (valid) "Für die Kachel „Liter Diesel“ unter der Karte, berechnet aus den eingetragenen Kilometerständen."
                else "Bitte einen Wert zwischen 1 und 100 eingeben.",
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
