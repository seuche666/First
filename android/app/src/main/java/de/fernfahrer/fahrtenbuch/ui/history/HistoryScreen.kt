package de.fernfahrer.fahrtenbuch.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.DayTypeBadge
import de.fernfahrer.fahrtenbuch.ui.components.EmptyState
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: MainViewModel, onOpenDay: (Long) -> Unit, onAdd: () -> Unit) {
    val days by vm.days.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val groups = remember(days) {
        (days ?: emptyList()).groupBy { YearMonth.from(it.day.date) }.toSortedMap(compareByDescending { it })
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { LargeTopAppBar(title = { Text("Verlauf") }, scrollBehavior = scroll) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Tag nachtragen") },
            )
        },
    ) { padding ->
        when {
            days == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            groups.isEmpty() -> EmptyState(
                icon = Icons.Rounded.History,
                title = "Noch keine Einträge",
                text = "Erfasse deinen ersten Tag auf dem Startbildschirm oder trage einen Tag nach.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { (month, entries) ->
                    stickyHeader(key = month.toString()) {
                        MonthHeader(month, entries)
                    }
                    items(entries, key = { it.day.id }) { entry ->
                        DayCard(entry, onClick = { onOpenDay(entry.day.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

/** Arbeitszeit als kurzer Text, z. B. "Arbeit 06:10–17:45". */
private fun workText(start: Int?, end: Int?): String? = when {
    start != null && end != null -> "Arbeit ${Format.time(start)}–${Format.time(end)}"
    start != null -> "Arbeit ab ${Format.time(start)}"
    end != null -> "Arbeit bis ${Format.time(end)}"
    else -> null
}

@Composable
private fun MonthHeader(month: YearMonth, entries: List<ClassifiedDay>) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                Format.monthYear(month.atDay(1)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            val full = entries.count { it.type == DayType.FULL }
            val half = entries.count { it.type == DayType.HALF }
            Text(
                "$full voll · $half halb · ${Format.km(entries.sumOf { it.day.distanceKm ?: 0 })}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayCard(entry: ClassifiedDay, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val d = entry.day
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.widthIn(min = 54.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        d.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        Format.weekdayShort(d.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Countries.flag(d.countryCode), fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Countries.name(d.countryCode),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (d.tourId != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.LocalShipping, "Tour",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    buildString {
                        append("${Format.time(d.startMinute)}–${Format.time(d.endMinute)}")
                        d.distanceKm?.let { append(" · ${Format.km(it)}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                workText(d.workStartMinute, d.workEndMinute)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (d.note.isNotBlank()) {
                    Text(
                        d.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            DayTypeBadge(entry.type, entry.isManual)
        }
    }
}
