package de.fernfahrer.fahrtenbuch.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.CountryCount
import de.fernfahrer.fahrtenbuch.domain.Period
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodSelector(period: Period, onChange: (Period) -> Unit) {
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PeriodType.entries.forEachIndexed { i, type ->
                SegmentedButton(
                    selected = period.type == type,
                    onClick = { onChange(Period(type, LocalDate.now())) },
                    shape = SegmentedButtonDefaults.itemShape(i, PeriodType.entries.size),
                    label = { Text(type.label, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onChange(period.previous()) },
                enabled = period.type != PeriodType.ALL,
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Vorheriger Zeitraum") }
            AnimatedContent(
                targetState = period.label,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "zeitraum",
            ) { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FilledTonalIconButton(
                onClick = { onChange(period.next()) },
                enabled = period.type != PeriodType.ALL,
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Nächster Zeitraum") }
        }
    }
}

@Composable
internal fun AbroadTable(rows: List<CountryCount>) {
    Column {
        Row(Modifier.padding(bottom = 6.dp)) {
            Text("Land", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Voll", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Halb", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Countries.flag(c.countryCode), fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(Countries.name(c.countryCode), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${c.full}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                Text("${c.half}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
            }
        }
    }
}
