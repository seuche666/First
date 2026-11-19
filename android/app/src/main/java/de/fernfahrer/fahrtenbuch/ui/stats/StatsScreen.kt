package de.fernfahrer.fahrtenbuch.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.EuroCalc
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.export.ExportFormat
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard
import de.fernfahrer.fahrtenbuch.ui.components.StatRow
import de.fernfahrer.fahrtenbuch.ui.components.StatTile
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    vm: MainViewModel,
    onOpenExpenses: (Int?) -> Unit = {},
    onOpenReport: () -> Unit = {},
) {
    val days by vm.days.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val all = days ?: emptyList()
    val workByDate by vm.workByDate.collectAsStateWithLifecycle()
    val summary = remember(all, period, workByDate) { Stats.summarize(all, period, workByDate) }
    val kmBars = remember(all, period) { Stats.kmBars(all, period) }
    val book by vm.expenseBook.collectAsStateWithLifecycle()
    val euroPeriod = if (period.type == PeriodType.ALL) null else period
    val euro = remember(all, period, book) { EuroCalc.summarize(all, book, euroPeriod) }
    val missingYear = remember(all, period, book) { EuroCalc.missingYears(all, book, euroPeriod).firstOrNull() }
    val exportYear = if (period.type == PeriodType.ALL) LocalDate.now().year else period.anchor.year

    Scaffold(
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PeriodSelector(period, onChange = vm::setPeriod) }

            item {
                AnimatedContent(
                    targetState = summary,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "zusammenfassung",
                ) { sum ->
                    SectionCard(title = "Überblick", icon = Icons.Rounded.Timeline) {
                        StatTile(
                            value = sum.daysAway.toString(),
                            label = "Tage unterwegs gesamt",
                            modifier = Modifier.fillMaxWidth(),
                            container = MaterialTheme.colorScheme.primary,
                            content = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.height(8.dp))
                        StatRow {
                            StatTile(sum.full.toString(), "volle Tage", Modifier.weight(1f))
                            StatTile(sum.half.toString(), "halbe Tage", Modifier.weight(1f))
                            StatTile(Format.number(sum.km), "km gefahren", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        StatRow {
                            StatTile(sum.overnights.toString(), "Übernachtungen im LKW", Modifier.weight(1f))
                            StatTile(Format.duration(sum.workMinutes).removeSuffix(" Std."), "Std. Arbeitszeit", Modifier.weight(1f))
                        }
                        if (sum.shortDays > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Unter 8 Std.: ${Format.days(sum.shortDays)} eintägig mit 8 Std. oder weniger " +
                                    "(zählen für die Steuer nicht)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val manualNone = sum.notCounted - sum.shortDays
                        if (manualNone > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${Format.days(manualNone)} von Hand auf „zählt nicht“ gesetzt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { EuroCard(euro, missingYear, onOpenExpenses) }

            item {
                SectionCard(title = "Reisekostenabrechnung", icon = Icons.Rounded.Description) {
                    Text(
                        "Monatliche Abrechnung für die Firma als PDF: alle Tage des Monats mit Verpflegung " +
                            "und Übernachtung, vorher Vorschau mit änderbarem Reiseanlass.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onOpenReport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Rounded.Description, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reisekostenabrechnung")
                    }
                }
            }

            item {
                SectionCard(title = "${Countries.flag(Stats.HOME_COUNTRY)} Deutschland", icon = Icons.Rounded.Home) {
                    StatRow {
                        StatTile(summary.germany.full.toString(), "volle Tage", Modifier.weight(1f))
                        StatTile(summary.germany.half.toString(), "halbe Tage", Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionCard(
                    title = "Ausland",
                    icon = Icons.Rounded.Public,
                    trailing = {
                        Text(
                            Format.days(summary.abroadDays),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                ) {
                    if (summary.abroad.isEmpty()) {
                        Text(
                            "Keine Auslandstage in diesem Zeitraum.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        AbroadTable(summary.abroad)
                    }
                }
            }

            item {
                SectionCard(title = "Tage pro Land", icon = Icons.Rounded.BarChart) {
                    if (summary.allCountries.isEmpty()) {
                        Text(
                            "Noch keine Tage in diesem Zeitraum.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        CountryBars(summary.allCountries)
                    }
                }
            }

            item {
                SectionCard(title = Stats.kmChartTitle(period.type), icon = Icons.Rounded.Speed) {
                    BarChart(kmBars)
                }
            }

            item {
                SectionCard(title = "Jahresübersicht $exportYear", icon = Icons.Rounded.IosShare) {
                    Text(
                        "Mit Euro-Beträgen aus der Spesentabelle. Als PDF oder Tabelle (CSV, z. B. für Excel) erstellen und über das Teilen-Menü versenden " +
                            "oder speichern – etwa für die Steuererklärung.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = { vm.exportYear(context, exportYear, ExportFormat.PDF) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, null)
                            Spacer(Modifier.width(8.dp))
                            Text("PDF")
                        }
                        FilledTonalButton(
                            onClick = { vm.exportYear(context, exportYear, ExportFormat.CSV) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Rounded.TableChart, null)
                            Spacer(Modifier.width(8.dp))
                            Text("CSV")
                        }
                    }
                }
            }
        }
    }
}
