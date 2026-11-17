package de.fernfahrer.fahrtenbuch.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.WorkPortion
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import java.time.Duration
import java.time.LocalDateTime

/** Arbeitszeit für die Firma: läuft sie, mit Beginn und Dauer; sonst „Arbeit beginnen“. */
@Composable
internal fun WorkCard(
    running: WorkShiftEntity?,
    now: LocalDateTime,
    todayPortions: List<WorkPortion>?,
    onStart: () -> Unit,
    onEnd: () -> Unit,
) {
    val start = running?.start
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (running != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (running != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Work, null)
                Spacer(Modifier.width(10.dp))
                Text("Arbeitszeit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (running != null) {
                    Box(Modifier.size(10.dp).padding(1.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.fillMaxSize()) {}
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("läuft", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (running != null && start != null) {
                val elapsed = Duration.between(start, now).toMinutes().coerceAtLeast(0).toInt()
                val sinceText = if (start.toLocalDate() == now.toLocalDate()) {
                    "seit ${Format.time(running.startMinute!!)} Uhr"
                } else {
                    "seit ${Format.weekdayShort(start.toLocalDate())}., ${Format.time(running.startMinute!!)} Uhr"
                }
                Text("Arbeit läuft $sinceText", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${Format.duration(elapsed)}" + (running.kmStart?.let { " · Start-km ${Format.number(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onEnd,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Arbeit beenden", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    if (todayPortions.isNullOrEmpty()) {
                        "Heute noch keine Arbeitszeit erfasst."
                    } else {
                        "Heute: ${WorkSplitter.label(todayPortions)} · ${Format.duration(WorkSplitter.minutes(todayPortions))}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Arbeit beginnen", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
