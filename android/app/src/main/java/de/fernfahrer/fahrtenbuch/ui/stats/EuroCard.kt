package de.fernfahrer.fahrtenbuch.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Euro
import de.fernfahrer.fahrtenbuch.domain.EuroSummary
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard

/** Hinweis für fehlende Beträge (statt 0 €). */
internal const val MISSING_TEXT = "Betrag fehlt – in Spesentabelle eintragen"

/**
 * Euro-Beträge des Zeitraums: Verpflegung je Land, Übernachtungspauschale, Gesamtsumme.
 * Fehlt ein Betrag, steht statt einer Summe der Hinweis mit Knopf zur Spesentabelle.
 */
@Composable
internal fun EuroCard(euro: EuroSummary, missingYear: Int?, onOpenExpenses: (Int?) -> Unit) {
    SectionCard(title = "Spesen in Euro", icon = Icons.Rounded.Euro) {
        if (euro.isEmpty) {
            Text(
                "Keine Tage mit Spesen in diesem Zeitraum.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        euro.countries.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            EuroRow(
                lead = Countries.flag(c.countryCode),
                title = Countries.name(c.countryCode),
                detail = listOfNotNull(
                    if (c.fullDays > 0) "${c.fullDays} voll" else null,
                    if (c.halfDays > 0) "${c.halfDays} halb" else null,
                ).joinToString(" · "),
                cents = c.totalCents,
            )
        }
        if (euro.nights > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            EuroRow(
                lead = "🌙",
                title = "Übernachtungspauschale",
                detail = if (euro.nights == 1) "1 Nacht im LKW" else "${euro.nights} Nächte im LKW",
                cents = euro.overnightCents,
            )
        }
        Spacer(Modifier.height(10.dp))
        SumLine("Verpflegung", euro.mealCents)
        SumLine("Übernachtung", if (euro.nights > 0) euro.overnightCents else 0L)
        Spacer(Modifier.height(8.dp))
        val total = euro.totalCents
        Surface(
            color = if (total != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (total != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (total != null) "Gesamt" else "Gesamtsumme unvollständig",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (total != null) {
                    Text(Euro.money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!euro.complete) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Für mindestens einen Betrag fehlt noch der Eintrag in der Spesentabelle" +
                    (missingYear?.let { " $it" } ?: "") + ". Es wird nichts mit 0 € gerechnet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { onOpenExpenses(missingYear) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Rounded.TableChart, null)
                Spacer(Modifier.width(8.dp))
                Text("Zur Spesentabelle")
            }
        }
    }
}

@Composable
private fun EuroRow(lead: String, title: String, detail: String, cents: Long?) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(lead, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cents == null) {
                Text(MISSING_TEXT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (cents != null) {
            Text(Euro.money(cents), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun SumLine(label: String, cents: Long?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            cents?.let(Euro::money) ?: "fehlt",
            style = MaterialTheme.typography.bodyMedium,
            color = if (cents == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
