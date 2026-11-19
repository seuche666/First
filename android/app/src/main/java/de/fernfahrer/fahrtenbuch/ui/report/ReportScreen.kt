package de.fernfahrer.fahrtenbuch.ui.report

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Euro
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.ReportRow
import de.fernfahrer.fahrtenbuch.domain.TravelReport
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard
import java.time.YearMonth

/**
 * Reisekostenabrechnung: Monat wählen, Vorschau aller Tage (Reiseanlass änderbar), Warnung bei
 * fehlenden Beträgen, dann PDF teilen oder speichern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    initialMonth: YearMonth?,
    onBack: () -> Unit,
    onOpenExpenses: (Int) -> Unit,
    vm: ReportViewModel = viewModel(factory = ReportViewModel.Factory),
) {
    LaunchedEffect(initialMonth) { initialMonth?.let(vm::setMonth) }
    val month by vm.month.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ReportRow?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.save(context, uri)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Reisekostenabrechnung") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthSelector(month, canNext = month.isBefore(vm.latestMonth), onChange = vm::setMonth) }
            val r = report
            if (r == null || YearMonth.from(r.month.atDay(1)) != month) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                return@LazyColumn
            }
            r.skippedRunning?.let { d ->
                item {
                    Notice(
                        icon = Icons.Rounded.Info,
                        text = "${Format.weekdayDate(d)} fehlt noch, weil die Arbeitszeit gerade läuft. " +
                            "Der Tag kommt nach „Arbeit beenden“ automatisch dazu.",
                        error = false,
                    )
                }
            }
            if (r.rows.isEmpty()) {
                item {
                    Notice(Icons.Rounded.Info, "In ${Format.monthYear(month.atDay(1))} gibt es keine Tage für die Abrechnung.", error = false)
                }
                return@LazyColumn
            }
            if (!r.complete) {
                item { MissingCard(r, onOpenExpenses) }
            }
            item { TotalsCard(r) }
            item {
                Text(
                    "Tage (${r.rows.size}) – Reiseanlass antippen zum Ändern",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(r.rows, key = { it.date.toEpochDay() }) { row -> RowCard(row, onEdit = { editing = row }) }
            item {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.share(context) },
                        enabled = !busy && r.complete,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Rounded.IosShare, null)
                        Spacer(Modifier.width(8.dp))
                        Text("PDF teilen")
                    }
                    FilledTonalButton(
                        onClick = { saveLauncher.launch(r.fileName) },
                        enabled = !busy && r.complete,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Speichern")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Datei: ${r.fileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { row ->
        TravelTextDialog(
            row = row,
            onSave = { text ->
                vm.setText(row.date, text.takeIf { it.trim() != row.autoText })
                editing = null
            },
            onReset = {
                vm.setText(row.date, null)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MonthSelector(month: YearMonth, canNext: Boolean, onChange: (YearMonth) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(month.minusMonths(1)) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Vorheriger Monat")
            }
            Text(
                Format.monthYear(month.atDay(1)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = canNext) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Nächster Monat")
            }
        }
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, error: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MissingCard(r: TravelReport, onOpenExpenses: (Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null)
                Spacer(Modifier.width(10.dp))
                Text("Betrag fehlt – in Spesentabelle eintragen", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Die Abrechnung wird erst erstellt, wenn alle Beträge eingetragen sind. Es fehlt:",
                style = MaterialTheme.typography.bodyMedium,
            )
            r.missing.forEach { Text("• ${it.label}", style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onOpenExpenses(r.missing.first().year) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Rounded.TableChart, null)
                Spacer(Modifier.width(8.dp))
                Text("Zur Spesentabelle")
            }
        }
    }
}

@Composable
private fun TotalsCard(r: TravelReport) {
    SectionCard(title = null) {
        val from = r.from
        val to = r.to
        if (from != null && to != null) {
            Text(
                "vom ${Format.date(from)} bis ${Format.date(to)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        r.countries.forEach { c ->
            Line(
                "${Countries.flag(c.countryCode)} ${Countries.name(c.countryCode)}",
                listOfNotNull(
                    if (c.fullDays > 0) "${c.fullDays} voll" else null,
                    if (c.halfDays > 0) "${c.halfDays} halb" else null,
                ).joinToString(" · "),
                if (r.complete) Euro.money(c.totalCents) else "",
            )
        }
        if (r.nights > 0) {
            Line("🌙 Übernachtung im LKW", if (r.nights == 1) "1 Nacht" else "${r.nights} Nächte", if (r.complete) Euro.money(r.overnightCents) else "")
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            color = if (r.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (r.complete) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Gesamtsumme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (r.complete) Euro.money(r.totalCents) else "unvollständig",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Line(title: String, detail: String, amount: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(amount, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RowCard(row: ReportRow, onEdit: () -> Unit) {
    Card(
        onClick = onEdit,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Format.weekdayDate(row.date), style = MaterialTheme.typography.titleSmall)
                    Text(
                        row.times.joinToString("  ·  ") { (s, e) -> "${Format.time(s)}–${Format.time(e)}" } +
                            "  ·  " + Countries.label(row.countryCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Euro.money(row.sumCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(row.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Edit, contentDescription = "Reiseanlass ändern",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (row.isManual) {
                Text("von Hand geändert", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row {
                Amount(
                    "Verpflegung" + when (row.type) {
                        DayType.FULL -> " (voll)"
                        DayType.HALF -> " (halb)"
                        DayType.NONE -> ""
                    },
                    when {
                        row.mealMissing -> null
                        row.mealCents == null -> "–"
                        else -> Euro.money(row.mealCents)
                    },
                    Modifier.weight(1f),
                )
                Amount(
                    "Übernachtung",
                    when {
                        row.overnightMissing -> null
                        !row.hasNight -> "–"
                        else -> Euro.money(row.overnightCents ?: 0)
                    },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Amount(label: String, value: String?, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value == null) {
            Text("Betrag fehlt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } else {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Startmonat aus der Navigation lesen („2026-10“). */
internal fun parseMonth(value: String?): YearMonth? = value?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
