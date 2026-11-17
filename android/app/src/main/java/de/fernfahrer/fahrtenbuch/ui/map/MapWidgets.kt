package de.fernfahrer.fahrtenbuch.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.ui.components.StatRow
import de.fernfahrer.fahrtenbuch.ui.components.StatTile

internal class MapHint(
    val id: String,
    val icon: ImageVector,
    val text: String,
    val action: String,
    val onAction: () -> Unit,
)

@Composable
internal fun RecordingChip() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color(0xFFE53935), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Strecke wird aufgezeichnet", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun HintCard(hint: MapHint, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(hint.icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hint.text, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = hint.onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(hint.action)
                }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Hinweis ausblenden") }
        }
    }
}

/** Pflicht-Namensnennung der OpenStreetMap-Daten, mit Link zur Lizenzseite. */
@Composable
internal fun OsmAttribution(modifier: Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.clickable {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        shape = MaterialTheme.shapes.small,
        color = Color.White.copy(alpha = 0.8f),
        contentColor = Color(0xFF333333),
    ) {
        Text("© OpenStreetMap-Mitwirkende", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
internal fun MapStatsCard(stats: MapStats) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            StatRow {
                StatTile(
                    value = Format.number(Math.round(stats.distanceKm).toInt()),
                    label = "km laut GPS",
                    modifier = Modifier.weight(1f),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatTile(
                    value = stats.countries.size.toString(),
                    label = if (stats.countries.size == 1) "Land befahren" else "Länder befahren",
                    modifier = Modifier.weight(1f),
                )
            }
            if (stats.countries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stats.countries.joinToString(" · ") { Countries.label(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            }
        }
    }
}
