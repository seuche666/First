package de.fernfahrer.fahrtenbuch.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Summary
import de.fernfahrer.fahrtenbuch.ui.components.DayTypeBadge
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard
import de.fernfahrer.fahrtenbuch.ui.components.StatRow
import de.fernfahrer.fahrtenbuch.ui.components.StatTile

@Composable
internal fun TodayCard(entry: ClassifiedDay, onClick: () -> Unit) {
    val d = entry.day
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Heute erfasst", style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append(Countries.label(d.countryCode))
                        append(" · ${Format.time(d.startMinute)}–${Format.time(d.endMinute)}")
                        d.distanceKm?.let { append(" · ${Format.km(it)}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DayTypeBadge(entry.type, entry.isManual)
        }
    }
}

@Composable
internal fun SummaryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    summary: Summary,
    showAbroad: Boolean = false,
) {
    SectionCard(
        title = title,
        icon = icon,
        trailing = {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        StatTile(
            value = summary.daysAway.toString(),
            label = "Tage unterwegs",
            modifier = Modifier.fillMaxWidth(),
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.height(8.dp))
        StatRow {
            StatTile(summary.full.toString(), "volle Tage", Modifier.weight(1f))
            StatTile(summary.half.toString(), "halbe Tage", Modifier.weight(1f))
            StatTile(Format.number(summary.km), "km", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        StatRow {
            StatTile(summary.overnights.toString(), "Übernachtungen im LKW", Modifier.weight(1f))
            StatTile(Format.duration(summary.workMinutes).removeSuffix(" Std."), "Std. Arbeitszeit", Modifier.weight(1f))
        }
        if (showAbroad) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (summary.abroadDays == 0) {
                    "Noch keine Auslandstage in diesem Jahr."
                } else {
                    "Davon im Ausland: ${Format.days(summary.abroadDays)} in " +
                        summary.abroad.joinToString(", ") { Countries.flag(it.countryCode) + " " + it.total }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
