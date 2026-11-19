package de.fernfahrer.fahrtenbuch.export

import de.fernfahrer.fahrtenbuch.data.ExpenseRepository
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.TrackRepository
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayPlaces
import de.fernfahrer.fahrtenbuch.domain.TravelReport
import de.fernfahrer.fahrtenbuch.domain.TravelReports
import de.fernfahrer.fahrtenbuch.domain.WorkPortion
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec
import de.fernfahrer.fahrtenbuch.location.PlaceLocator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Sammelt alle Daten für die monatliche Reisekostenabrechnung: Tage (nach **Firmenregel** eingeordnet),
 * Arbeitszeiten, Spesentabellen und Orte aus GPS-Strecke und Übernachtungsplätzen.
 */
class TravelReportSource(
    private val repo: Repository,
    private val expenses: ExpenseRepository,
    private val tracks: TrackRepository,
    /** Offline-Ortsnamen; null = keine Ortsnamen. */
    private val placeLocator: suspend () -> PlaceLocator?,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun build(month: YearMonth, name: String, now: LocalDateTime = LocalDateTime.now()): TravelReport {
        val days = repo.getAllDays()
        val tours = repo.getAllTours()
        val classified = Classifier.classifyAll(days, tours, COMPANY_RULES)
        val shifts = repo.getAllShifts()
        val running = shifts.firstOrNull { it.running }
        val runningDate = running?.let { now.toLocalDate() }
        val work = WorkSplitter.byDate(shifts, now)
        val inMonth = days.map { it.date }.filter { YearMonth.from(it) == month }
        return TravelReports.build(
            month = month,
            name = name,
            days = classified,
            tours = tours,
            work = work,
            book = expenses.getBook(),
            places = places(inMonth, work),
            runningDate = runningDate,
            today = now.toLocalDate(),
        )
    }

    /**
     * Orte je Tag: erster/letzter GPS-Punkt des Tages, Übernachtungsplatz und der Ort bei jedem
     * Arbeitsbeginn/-ende ([work]; nächster GPS-Punkt innerhalb von [EVENT_WINDOW_SEC]).
     */
    suspend fun places(
        dates: List<LocalDate>,
        work: Map<LocalDate, List<WorkPortion>> = emptyMap(),
    ): Map<LocalDate, DayPlaces> {
        if (dates.isEmpty()) return emptyMap()
        val locator = runCatching { placeLocator() }.getOrNull()
        fun nameAt(latE5: Int, lonE5: Int): String? =
            locator?.nearest(latE5 / 100_000.0, lonE5 / 100_000.0)?.name

        val from = dates.min().atStartOfDay(zone).toEpochSecond()
        val to = dates.max().plusDays(1).atStartOfDay(zone).toEpochSecond()
        val dateSet = dates.toHashSet()
        // Erster und letzter Punkt je Tag (Breite, Länge in Grad × 100 000)
        val allPoints = ArrayList<Triple<Long, Int, Int>>()
        val firstPoint = HashMap<LocalDate, Triple<Long, Int, Int>>()
        val lastPoint = HashMap<LocalDate, Triple<Long, Int, Int>>()
        for (chunk in tracks.chunksBetween(from - EVENT_WINDOW_SEC, to + EVENT_WINDOW_SEC)) {
            val p = runCatching { TrackCodec.decode(chunk.data) }.getOrNull() ?: continue
            for (i in 0 until p.size) {
                val t = p.timeSec[i]
                if (t < from - EVENT_WINDOW_SEC || t >= to + EVENT_WINDOW_SEC) continue
                allPoints += Triple(t, p.latE5[i], p.lonE5[i])
                if (t < from || t >= to) continue
                val date = java.time.Instant.ofEpochSecond(t).atZone(zone).toLocalDate()
                val f = firstPoint[date]
                if (f == null || t < f.first) firstPoint[date] = Triple(t, p.latE5[i], p.lonE5[i])
                val l = lastPoint[date]
                if (l == null || t > l.first) lastPoint[date] = Triple(t, p.latE5[i], p.lonE5[i])
            }
        }
        val stayPlace = HashMap<LocalDate, String>()
        for (op in repo.getOvernightPlaces()) {
            for (stay in op.stays) {
                if (stay.date !in dateSet) continue
                val label = op.place.name.ifBlank { nameAt(op.place.latE5, op.place.lonE5) ?: "" }
                if (label.isNotBlank()) stayPlace[stay.date] = label
            }
        }
        allPoints.sortBy { it.first }
        val times = LongArray(allPoints.size) { allPoints[it].first }
        fun nameNear(sec: Long): String? {
            if (times.isEmpty()) return null
            var i = java.util.Arrays.binarySearch(times, sec)
            if (i < 0) i = -i - 1
            val best = listOfNotNull(allPoints.getOrNull(i - 1), allPoints.getOrNull(i))
                .minByOrNull { kotlin.math.abs(it.first - sec) } ?: return null
            if (kotlin.math.abs(best.first - sec) > EVENT_WINDOW_SEC) return null
            return nameAt(best.second, best.third)
        }
        return dates.associateWith { d ->
            val dayStart = d.atStartOfDay(zone).toEpochSecond()
            val minutes = work[d].orEmpty().flatMap { listOfNotNull(it.startMinute, it.endMinute) }.toSet()
            DayPlaces(
                at = minutes.mapNotNull { m -> nameNear(dayStart + m * 60L)?.let { m to it } }.toMap(),
                first = firstPoint[d]?.let { nameAt(it.second, it.third) },
                last = lastPoint[d]?.let { nameAt(it.second, it.third) },
                overnight = stayPlace[d],
            )
        }
    }

    companion object {
        /** Reisekostenabrechnung für die Firma: eintägige Fahrten bis 8 Std. = halber Tag. */
        val COMPANY_RULES = CountingRules(shortSingleDayAsHalf = true)

        /** Höchster Zeitabstand zwischen Arbeitsbeginn/-ende und dem GPS-Punkt für den Ortsnamen. */
        const val EVENT_WINDOW_SEC = 45 * 60L
    }
}
