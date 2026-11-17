package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.Period
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import de.fernfahrer.fahrtenbuch.domain.Stats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private val tour = TourEntity(id = 1, ended = true)
    private val days = listOf(
        DayEntity(1, LocalDate.parse("2026-03-02"), 300, 1440, "DE", 1000, 1500, tourId = 1),
        DayEntity(2, LocalDate.parse("2026-03-03"), 0, 1440, "AT", 1500, 2100, tourId = 1),
        DayEntity(3, LocalDate.parse("2026-03-04"), 0, 1440, "AT", 2100, 2500, tourId = 1),
        DayEntity(4, LocalDate.parse("2026-03-05"), 0, 1000, "DE", 2500, 2900, tourId = 1),
        DayEntity(5, LocalDate.parse("2026-04-10"), 360, 1080, "DE", 3000, 3400),
        DayEntity(6, LocalDate.parse("2026-04-11"), 360, 600, "DE", null, null),
    )
    private val classified = Classifier.classifyAll(days, listOf(tour))

    @Test
    fun jahresuebersicht() {
        val s = Stats.summarize(classified, Period(PeriodType.YEAR, LocalDate.parse("2026-06-01")))
        assertEquals(2, s.full)
        assertEquals(3, s.half)
        assertEquals(1, s.notCounted)
        assertEquals(5, s.daysAway)
        assertEquals(2300, s.km)
        assertEquals(0, s.germany.full)
        assertEquals(3, s.germany.half)
        assertEquals(1, s.abroad.size)
        assertEquals("AT", s.abroad[0].countryCode)
        assertEquals(2, s.abroad[0].full)
    }

    @Test
    fun wocheFiltertZeitraum() {
        val s = Stats.summarize(classified, Period(PeriodType.WEEK, LocalDate.parse("2026-03-04")))
        assertEquals(4, s.daysAway)
        assertEquals(1900, s.km)
    }

    @Test
    fun kmProMonatImJahr() {
        val bars = Stats.kmBars(classified, Period(PeriodType.YEAR, LocalDate.parse("2026-01-01")))
        assertEquals(12, bars.size)
        assertEquals(1900, bars[2].value)
        assertEquals(400, bars[3].value)
    }

    @Test
    fun wochenzeitraumBeginntMontags() {
        val p = Period(PeriodType.WEEK, LocalDate.parse("2026-11-12"))
        assertEquals(LocalDate.parse("2026-11-09"), p.start)
        assertEquals(LocalDate.parse("2026-11-15"), p.end)
    }

    @Test
    fun firmenregelUndArbeitszeitInDerAuswertung() {
        val c = Classifier.classifyAll(days, listOf(tour), de.fernfahrer.fahrtenbuch.domain.CountingRules(true))
        val shifts = listOf(
            WorkShiftEntity(1, LocalDate.parse("2026-04-10"), 360, LocalDate.parse("2026-04-10"), 1080),
            // im Vorjahr: zählt nicht mit
            WorkShiftEntity(2, LocalDate.parse("2025-12-31"), 1380, LocalDate.parse("2026-01-01"), 120),
        )
        val work = WorkSplitter.byDate(shifts, java.time.LocalDateTime.now())
        val s = Stats.summarize(c, Period(PeriodType.YEAR, LocalDate.parse("2026-06-01")), work)
        // Tag 6 (4 Std., eintägig) zählt jetzt als halber Tag nach Firmenregel
        assertEquals(4, s.half)
        assertEquals(0, s.notCounted)
        assertEquals(1, s.companyRuleDays)
        // 12 Std. am 10.04. + 2 Std. am 01.01. (Teil der Schicht über Silvester)
        assertEquals(14 * 60, s.workMinutes)
        assertEquals(3, s.overnights)
    }
}
