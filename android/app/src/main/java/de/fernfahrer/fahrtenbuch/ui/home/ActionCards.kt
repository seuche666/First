package de.fernfahrer.fahrtenbuch.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.ui.ActiveTour

@Composable
internal fun CaptureButtons(todayEntered: Boolean, onCapture: () -> Unit, onStartTour: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onCapture,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            shape = MaterialTheme.shapes.extraLarge,
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            Icon(if (todayEntered) Icons.Rounded.Edit else Icons.Rounded.AddCircle, null, Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (todayEntered) "Heute bearbeiten" else "Heute erfassen",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (todayEntered) "Eintrag ändern" else "Eintägige Fahrt oder Tag eintragen",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        OutlinedButton(
            onClick = onStartTour,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(Icons.Rounded.LocalShipping, null)
            Spacer(Modifier.width(10.dp))
            Text("Mehrtägige Tour starten", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun TourCard(tour: ActiveTour, onDay: () -> Unit, onEnd: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        Icons.Rounded.LocalShipping, null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Tour läuft · Tag ${tour.dayNumber}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "seit ${Format.weekdayDate(tour.startDate)} · zuletzt ${Countries.label(tour.lastCountry)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onDay,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Icon(if (tour.todayEntered) Icons.Rounded.Edit else Icons.Rounded.PlayArrow, null, Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (tour.todayEntered) "Heute bearbeiten" else "Heute erfassen",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onEnd,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Rounded.Flag, null)
                Spacer(Modifier.width(10.dp))
                Text("Tour beenden", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
