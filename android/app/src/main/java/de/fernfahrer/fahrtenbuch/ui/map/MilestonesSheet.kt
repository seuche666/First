package de.fernfahrer.fahrtenbuch.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Milestones

internal fun Milestones.Group.icon(): ImageVector = when (this) {
    Milestones.Group.KM -> Icons.Rounded.Route
    Milestones.Group.COUNTRIES -> Icons.Rounded.Flag
    Milestones.Group.NIGHTS -> Icons.Rounded.Bedtime
    Milestones.Group.TOURS -> Icons.Rounded.LocalShipping
}

/** Übersicht aller Meilensteine: erreichte mit Datum, offene ausgegraut mit Fortschritt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonesSheet(list: List<Milestones.Status>, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val achieved = list.count { it.achieved }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = Color(0xFFF5B400), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Meilensteine", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "$achieved von ${list.size} erreicht",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            for (group in Milestones.Group.entries) {
                val items = list.filter { it.group == group }
                    .sortedWith(compareBy<Milestones.Status>({ !it.achieved }, { it.achievedOn }, { it.target }))
                if (items.isEmpty()) continue
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(group.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                }
                items(items, key = { it.id }) { MilestoneRow(it) }
            }
            if (list.none { it.group == Milestones.Group.COUNTRIES && it.id.startsWith("land_") }) {
                item {
                    Text(
                        "Jedes neu befahrene Land erscheint hier als eigener Meilenstein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MilestoneRow(m: Milestones.Status) {
    val done = m.achieved
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(if (done) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(
                if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (done) m.group.icon() else Icons.Rounded.Lock,
                null,
                tint = if (done) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleSmall)
            if (done) {
                Text(
                    "erreicht am ${Format.date(m.achievedOn!!)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { m.progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Format.number(m.current.coerceAtMost(m.target).toInt())} von ${Format.number(m.target.toInt())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
