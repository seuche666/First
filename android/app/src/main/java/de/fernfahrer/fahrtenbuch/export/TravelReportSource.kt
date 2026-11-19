package de.fernfahrer.fahrtenbuch.export

import de.fernfahrer.fahrtenbuch.data.ExpenseRepository
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.TrackRepository
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayPlaces
import de.fernfahrer.fahrtenbuch.domain.TravelReport
import de.fernfahrer.fahrtenbuch.domain.TravelReports
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
            places = places(inMonth),
            runningDate = runningDate,
            today = now.toLocalDate(),
        )
    }

    /** Orte je Tag: erster/letzter GPS-Punkt des Tages und Übernachtungsplatz. */
    suspend fun places(dates: List<LocalDate>): Map<LocalDate, DayPlaces> {
        if (dates.isEmpty()) return emptyMap()
        val locator = runCatching { placeLocator() }.getOrNull()
        fun nameAt(latE5: Int, lonE5: Int): String? =
            locator?.nearest(latE5 / 100_000.0, lonE5 / 100_000.0)?.name

        val from = dates.min().atStartOfDay(zone).toEpochSecond()
        val to = dates.max().plusDays(1).atStartOfDay(zone).toEpochSecond()
        // Erster und letzter Punkt je Tag (Breite, Länge in Grad × 100 000)
        val firstPoint = HashMap<LocalDate, Triple<Long, Int, Int>>()
        val lastPoint = HashMap<LocalDate, Triple<Long, Int, Int>>()
        for (chunk in tracks.chunksBetween(from, to)) {
            val p = runCatching { TrackCodec.decode(chunk.data) }.getOrNull() ?: continue
            for (i in 0 until p.size) {
                val t = p.timeSec[i]
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
                if (stay.date !in dates) continue
                val label = op.place.name.ifBlank { nameAt(op.place.latE5, op.place.lonE5) ?: "" }
                if (label.isNotBlank()) stayPlace[stay.date] = label
            }
        }
        return dates.associateWith { d ->
            DayPlaces(
                first = firstPoint[d]?.let { nameAt(it.second, it.third) },
                last = lastPoint[d]?.let { nameAt(it.second, it.third) },
                overnight = stayPlace[d],
            )
        }
    }

    companion object {
        /** Reisekostenabrechnung für die Firma: eintägige Fahrten bis 8 Std. = halber Tag. */
        val COMPANY_RULES = CountingRules(shortSingleDayAsHalf = true)
    }
}
