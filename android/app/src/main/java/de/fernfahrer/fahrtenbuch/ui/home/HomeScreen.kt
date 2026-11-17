package de.fernfahrer.fahrtenbuch.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fernfahrer.fahrtenbuch.domain.Format
import de.fernfahrer.fahrtenbuch.domain.Period
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import de.fernfahrer.fahrtenbuch.ui.components.rememberStartWorkFlow
import de.fernfahrer.fahrtenbuch.ui.editor.EditorMode
import java.time.LocalDate

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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp)) {
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
