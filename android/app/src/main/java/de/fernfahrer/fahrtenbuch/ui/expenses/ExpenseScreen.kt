package de.fernfahrer.fahrtenbuch.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fernfahrer.fahrtenbuch.data.ExpenseRateEntity
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Euro
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.MissingRate
import de.fernfahrer.fahrtenbuch.ui.components.CountryPickerSheet
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

/** Welches Land gerade bearbeitet wird. */
private data class RateEdit(val code: String, val existing: ExpenseRateEntity?)

/** Spesentabelle: eine Tabelle pro Jahr mit Übernachtungspauschale und Beträgen je Land. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    recentCountries: StateFlow<List<String>>,
    onBack: () -> Unit,
    vm: ExpenseViewModel = viewModel(factory = ExpenseViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val recent by recentCountries.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RateEdit?>(null) }
    var editOvernight by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Spesentabelle") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        fun open(code: String) {
            editing = RateEdit(code, s.rates.firstOrNull { it.countryCode == code })
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    s.years.forEach { y ->
                        FilterChip(selected = y == s.year, onClick = { vm.selectYear(y) }, label = { Text("$y") })
                    }
                }
            }

            val table = s.table
            if (table == null) {
                item { CreateTableCard(s.year, s.copySource, vm::createTable) }
                return@LazyColumn
            }

            item {
                OvernightCard(
                    cents = table.overnightCents,
                    overnights = s.overnights,
                    onClick = { editOvernight = true },
                )
            }

            if (s.missing.isNotEmpty()) {
                item { MissingCard(s.year, s.missing, onOpen = ::open) }
            }

            item {
                SectionCard(title = null, contentPadding = PaddingValues(vertical = 20.dp)) {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TableChart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Beträge je Land ${s.year}", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    RateHeader()
                    HorizontalDivider()
                    if (s.rates.isEmpty()) {
                        Text(
                            "Noch keine Länder eingetragen. Tippe auf „Land hinzufügen“ – auch Deutschland trägst du selbst ein.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                    s.rates.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                        RateRow(r, onClick = { open(r.countryCode) })
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { picking = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Land hinzufügen")
                    }
                }
            }

            item {
                Text(
                    "Änderungen gelten nur für ${s.year}. Zum Jahreswechsel wird die Tabelle automatisch ins neue " +
                        "Jahr kopiert; danach sind die Jahre unabhängig voneinander. Beträge in Euro mit Komma, z. B. 14,00.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        if (picking) {
            CountryPickerSheet(
                selected = "",
                recent = recent,
                onSelect = { code ->
                    picking = false
                    open(code)
                },
                onDismiss = { picking = false },
            )
        }
        editing?.let { e ->
            RateDialog(
                year = s.year,
                code = e.code,
                fullDayCents = e.existing?.fullDayCents,
                partialDayCents = e.existing?.partialDayCents,
                isNew = e.existing == null,
                onSave = { full, partial ->
                    vm.saveRate(e.code, full, partial)
                    editing = null
                },
                onDelete = {
                    vm.deleteRate(e.code)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
        if (editOvernight) {
            OvernightDialog(
                year = s.year,
                cents = s.table?.overnightCents,
                onSave = {
                    vm.setOvernight(it)
                    editOvernight = false
                },
                onDismiss = { editOvernight = false },
            )
        }
    }
}

@Composable
private fun CreateTableCard(year: Int, copySource: Int?, onCreate: () -> Unit) {
    SectionCard(title = "Noch keine Tabelle für $year", icon = Icons.Rounded.TableChart) {
        Text(
            buildString {
                append(
                    if (copySource != null) "Beim Anlegen werden alle Länder, Beträge und die Übernachtungspauschale aus $copySource übernommen. "
                    else "Die Tabelle startet leer. ",
                )
                if (year > LocalDate.now().year) append("Spätestens am 1. Januar $year legt die App sie automatisch an.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        FilledTonalButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(if (copySource != null) Icons.Rounded.ContentCopy else Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(if (copySource != null) "Tabelle $year anlegen (aus $copySource)" else "Tabelle $year anlegen")
        }
    }
}

@Composable
private fun OvernightCard(cents: Long?, overnights: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Hotel, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Übernachtungspauschale LKW pro Tag", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (cents == null && overnights > 0) "Noch kein Betrag – ${Format.number(overnights)} Übernachtungen im LKW erfasst"
                    else "Gilt für alle Länder gleich · tippen zum Ändern",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cents == null && overnights > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(Euro.label(cents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MissingCard(year: Int, missing: List<MissingRate>, onOpen: (String) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(10.dp))
                Text("Noch kein Betrag eingetragen", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text(
                "Für diese Länder hast du $year schon Tage erfasst. Tippe ein Land an, um den Betrag einzutragen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            missing.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(m.countryCode) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Countries.flag(m.countryCode), fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Countries.name(m.countryCode),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Format.days(m.days),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

private val AmountWidth = 84.dp

@Composable
private fun RateHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text("Land", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            "Voller Tag\n(24 Std.)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(AmountWidth),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "An-/Abreise\nhalber Tag",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(AmountWidth),
        )
    }
}

@Composable
private fun RateRow(rate: ExpenseRateEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Countries.flag(rate.countryCode), fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            Countries.name(rate.countryCode),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(Euro.label(rate.fullDayCents), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.width(AmountWidth))
        Spacer(Modifier.width(8.dp))
        Text(Euro.label(rate.partialDayCents), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.width(AmountWidth))
    }
}
