package de.fernfahrer.fahrtenbuch.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalShipping
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Period
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.Summary
import de.fernfahrer.fahrtenbuch.domain.WorkPortion
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import de.fernfahrer.fahrtenbuch.ui.ActiveTour
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.DayTypeBadge
import de.fernfahrer.fahrtenbuch.ui.components.SectionCard
import de.fernfahrer.fahrtenbuch.ui.components.StatRow
import de.fernfahrer.fahrtenbuch.ui.components.StatTile
import de.fernfahrer.fahrtenbuch.ui.components.rememberStartWorkFlow
import de.fernfahrer.fahrtenbuch.ui.editor.EditorMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun HomeScreen(vm: MainViewModel, onOpenEditor: (id: Long, mode: EditorMode) -> Unit) {
    val days by vm.days.collectAsStateWithLifecycle()
    val tour by vm.activeTour.collectAsStateWithLifecycle()
    val running by vm.runningShift.collectAsStateWithLifecycle()
    val workByDate by vm.workByDate.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val workSettings by vm.workSettings.collectAsStateWithLifecycle()
    val mapSettings by vm.mapSettings.collectAsStateWithLifecycle()
    val all = days ?: emptyList()
    val today = LocalDate.now()
    val todayEntry = all.firstOrNull { it.day.date == today }

    val week = remember(all) { Period(PeriodType.WEEK, today) }
    val year = remember(all) { Period(PeriodType.YEAR, today) }
    val weekSummary = remember(all, workByDate) { Stats.summarize(all, week, workByDate) }
    val yearSummary = remember(all, workByDate) { Stats.summarize(all, year, workByDate) }

    val startWork = rememberStartWorkFlow(
        gps = workSettings.gpsEnabled,
        reminder = workSettings.reminderEnabled,
        reminderHours = workSettings.reminderHours,
        alreadyExplained = { vm.permissionsExplained },
        markExplained = vm::markPermissionsExplained,
        onStart = vm::startWork,
        tracking = mapSettings.trackingEnabled,
        backgroundExplained = { vm.backgroundLocationExplained },
        markBackgroundExplained = vm::markBackgroundLocationExplained,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) {
                Text("Fahrtenbuch", style = MaterialTheme.typography.headlineLarge)
                Text(
                    Format.longDate(today),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                )
            }
        }

        item {
            AnimatedContent(
                targetState = tour,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
                label = "aktion",
            ) { activeTour ->
                if (activeTour != null) {
                    TourCard(
                        tour = activeTour,
                        onDay = { onOpenEditor(0, EditorMode.TOUR_DAY) },
                        onEnd = { vm.requestEndTour() },
                    )
                } else {
                    CaptureButtons(
                        todayEntered = todayEntry != null,
                        onCapture = { onOpenEditor(todayEntry?.day?.id ?: 0, EditorMode.NORMAL) },
                        onStartTour = { onOpenEditor(0, EditorMode.TOUR_START) },
                    )
                }
            }
        }

        item {
            WorkCard(
                running = running,
                now = now,
                todayPortions = workByDate[today],
                onStart = startWork,
                onEnd = { vm.requestEndWork() },
            )
        }

        item {
            AnimatedVisibility(visible = todayEntry != null) {
                todayEntry?.let { TodayCard(it) { onOpenEditor(it.day.id, EditorMode.NORMAL) } }
            }
        }

        item {
            SummaryCard(
                title = "Diese Woche",
                subtitle = week.label,
                icon = Icons.Rounded.DateRange,
                summary = weekSummary,
            )
        }
        item {
            SummaryCard(
                title = "Jahr ${today.year}",
                subtitle = "seit 1. Januar",
                icon = Icons.Rounded.CalendarMonth,
                summary = yearSummary,
                showAbroad = true,
            )
        }
    }
}

@Composable
private fun CaptureButtons(todayEntered: Boolean, onCapture: () -> Unit, onStartTour: () -> Unit) {
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
private fun TourCard(tour: ActiveTour, onDay: () -> Unit, onEnd: () -> Unit) {
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

/** Arbeitszeit für die Firma: läuft sie, mit Beginn und Dauer; sonst „Arbeit beginnen“. */
@Composable
private fun WorkCard(
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

@Composable
private fun TodayCard(entry: ClassifiedDay, onClick: () -> Unit) {
    val d = entry.day
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Heute erfasst", style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append(Countries.label(d.countryCode))
                        append(" · ${Format.time(d.startMinute)}–${Format.time(d.endMinute)}")
                        d.distanceKm?.let { append(" · ${Format.km(it)}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DayTypeBadge(entry.type, entry.isManual)
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    summary: Summary,
    showAbroad: Boolean = false,
) {
    SectionCard(
        title = title,
        icon = icon,
        trailing = {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        StatTile(
            value = summary.daysAway.toString(),
            label = "Tage unterwegs",
            modifier = Modifier.fillMaxWidth(),
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.height(8.dp))
        StatRow {
            StatTile(summary.full.toString(), "volle Tage", Modifier.weight(1f))
            StatTile(summary.half.toString(), "halbe Tage", Modifier.weight(1f))
            StatTile(Format.number(summary.km), "km", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        StatRow {
            StatTile(summary.overnights.toString(), "Übernachtungen im LKW", Modifier.weight(1f))
            StatTile(Format.duration(summary.workMinutes).removeSuffix(" Std."), "Std. Arbeitszeit", Modifier.weight(1f))
        }
        if (showAbroad) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (summary.abroadDays == 0) {
                    "Noch keine Auslandstage in diesem Jahr."
                } else {
                    "Davon im Ausland: ${Format.days(summary.abroadDays)} in " +
                        summary.abroad.joinToString(", ") { Countries.flag(it.countryCode) + " " + it.total }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
