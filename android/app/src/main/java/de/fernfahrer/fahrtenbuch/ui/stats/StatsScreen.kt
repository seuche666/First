package de.fernfahrer.fahrtenbuch.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.CountryCount
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Period
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.ui.ExportFormat
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard
import de.fernfahrer.fahrtenbuch.ui.components.StatRow
import de.fernfahrer.fahrtenbuch.ui.components.StatTile
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel) {
    val days by vm.days.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val all = days ?: emptyList()
    val summary = remember(all, period) { Stats.summarize(all, period) }
    val kmBars = remember(all, period) { Stats.kmBars(all, period) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val exportYear = if (period.type == PeriodType.ALL) LocalDate.now().year else period.anchor.year

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { LargeTopAppBar(title = { Text("Auswertung") }, scrollBehavior = scroll) },
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
                        if (sum.companyRuleDays > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Davon ${Format.days(sum.companyRuleDays)} nur nach Firmenregel halb " +
                                    "(eintägig, 8 Std. oder weniger – das Finanzamt verlangt mehr als 8 Std.)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (sum.workMinutes > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Erfasste Arbeitszeit: ${Format.duration(sum.workMinutes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (sum.notCounted > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "${Format.days(sum.notCounted)} mit 8 Std. oder weniger (zählen nicht)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                        "Als PDF oder Tabelle (CSV, z. B. für Excel) erstellen und über das Teilen-Menü versenden " +
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(period: Period, onChange: (Period) -> Unit) {
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
private fun AbroadTable(rows: List<CountryCount>) {
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
