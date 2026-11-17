package de.fernfahrer.fahrtenbuch.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.geo.Overnights

/** Antippen eines Übernachtungsplatzes: Datum, Land und Ort jeder Übernachtung dort. */
@Composable
fun OvernightDialog(cluster: Overnights.Cluster, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? FahrtenbuchApp
    // Land und nächster Ort offline nachschlagen
    val details by produceState<Pair<String?, String?>>(null to null, cluster) {
        value = try {
            val country = cluster.spots.lastOrNull { it.countryCode != null }?.countryCode
                ?: app?.location?.countryLocator()?.countryAt(cluster.lat, cluster.lon)
            val place = app?.location?.placeLocator()?.nearest(cluster.lat, cluster.lon)?.label
            country to place
        } catch (e: Exception) {
            null to null
        }
    }
    val (country, place) = details
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bedtime, null) },
        title = {
            Text(if (cluster.count == 1) "Übernachtung" else "${cluster.count} Übernachtungen hier")
        },
        text = {
            Column {
                Text(
                    listOfNotNull(place, country?.let(Countries::label)).joinToString(" · ").ifEmpty { "Ort wird gesucht …" },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "%.5f, %.5f".format(java.util.Locale.ROOT, cluster.lat, cluster.lon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(cluster.spots.sortedByDescending { it.date }) { spot ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Format.weekdayDate(spot.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                Countries.label(spot.countryCode ?: country ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
