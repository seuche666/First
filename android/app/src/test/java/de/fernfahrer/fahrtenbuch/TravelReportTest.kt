package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayPlaces
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.EuroCalc
import de.fernfahrer.fahrtenbuch.domain.ExpenseBook
import de.fernfahrer.fahrtenbuch.domain.ExpenseRate
import de.fernfahrer.fahrtenbuch.domain.ExpenseTable
import de.fernfahrer.fahrtenbuch.domain.MissingAmount
import de.fernfahrer.fahrtenbuch.domain.Period
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.TravelReport
import de.fernfahrer.fahrtenbuch.domain.TravelReports
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import de.fernfahrer.fahrtenbuch.export.TravelReportLayout
import de.fernfahrer.fahrtenbuch.export.TravelReportSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reisekostenabrechnung November 2026 mit Beispieldaten bis heute (19.11.2026):
 * - Tour A 29.10.–03.11. über den Monatswechsel (01.11. Niederlande, 02.11. Deutschland + Niederlande)
 * - 05.11. eintägig 4 Std. (nur Firmenregel: halber Tag), 06.11. eintägig ohne Übernachtung
 * - Tour B ab 09.11., läuft noch; am 12.11. Hotel (keine Nacht im LKW); heute läuft die Arbeitszeit
 */
class TravelReportTest {
    private val today = LocalDate.of(2026, 11, 19)
    private val now = today.atTime(10, 0)
    private val nov = YearMonth.of(2026, 11)
    private val oct = YearMonth.of(2026, 10)

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    private val tourA = TourEntity(1, ended = true)
    private val tourB = TourEntity(2, ended = false)

    private val days: List<DayEntity> = buildList {
        var id = 1L
        // Tour A
        add(DayEntity(id++, d(10, 29), 300, 1440, "DE", tourId = 1))
        add(DayEntity(id++, d(10, 30), 0, 1440, "DE", tourId = 1))
        add(DayEntity(id++, d(10, 31), 0, 1440, "DE", tourId = 1))
        add(DayEntity(id++, d(11, 1), 0, 1440, "NL", tourId = 1))
        add(DayEntity(id++, d(11, 2), 0, 1440, "NL", tourId = 1, countries = "DE,NL"))
        add(DayEntity(id++, d(11, 3), 0, 1020, "DE", tourId = 1))
        // Eintägig
        add(DayEntity(id++, d(11, 5), 360, 600, "DE"))
        add(DayEntity(id++, d(11, 6), 360, 1020, "DE"))
        // Tour B (läuft)
        for (day in 9..19) {
            add(
                DayEntity(
                    id++, d(11, day), if (day == 9) 330 else 0, 1440, "DE", tourId = 2,
                    overnightOverride = if (day == 12) false else null,
                    travelText = if (day == 10) "Kundentermin Hamburg" else null,
                ),
            )
        }
    }

    private val shifts = listOf(
        // Über Mitternacht am Monatsende: Teil bis 24:00 im Oktober, Rest im November
        WorkShiftEntity(1, d(10, 31), 22 * 60, d(11, 1), 3 * 60),
        // Zwei Arbeitszeiten an einem Tag
        WorkShiftEntity(2, d(11, 2), 6 * 60, d(11, 2), 11 * 60),
        WorkShiftEntity(3, d(11, 2), 13 * 60, d(11, 2), 19 * 60 + 30),
        // Heute läuft die Arbeitszeit
        WorkShiftEntity(4, today, 5 * 60, null, null, running = true),
    )

    private val full2026 = ExpenseTable(
        2026, 800,
        mapOf("DE" to ExpenseRate("DE", 2800, 1400), "NL" to ExpenseRate("NL", 3200, 2100)),
    )

    private val places = mapOf(
        d(10, 29) to DayPlaces(first = "Fulda", last = "Seßlach"),
        d(11, 1) to DayPlaces(first = "Venlo", last = "Venlo", overnight = "Autohof Venlo"),
        d(11, 3) to DayPlaces(first = "Bad Hersfeld", last = "Würzburg"),
        d(11, 6) to DayPlaces(first = "Fulda", last = "Fulda"),
        d(11, 9) to DayPlaces(first = "Fulda", last = "Kassel"),
        d(11, 12) to DayPlaces(first = "Hamburg", last = "Bremen"),
    )

    private val companyDays = Classifier.classifyAll(days, listOf(tourA, tourB), TravelReportSource.COMPANY_RULES)

    private fun build(month: YearMonth, book: ExpenseBook = ExpenseBook(listOf(full2026)), name: String = "Max Muster") =
        TravelReports.build(
            month = month,
            name = name,
            days = companyDays,
            tours = listOf(tourA, tourB),
            work = WorkSplitter.byDate(shifts, now),
            book = book,
            places = places,
            runningDate = today,
            today = today,
        )

    private fun TravelReport.row(m: Int, day: Int) = rows.single { it.date == d(m, day) }

    @Test
    fun novemberMitBeispieldatenBisHeute() {
        val r = build(nov)
        assertTrue(r.complete)
        // Laufender Tag fehlt (nur Hinweis in der App)
        assertEquals(today, r.skippedRunning)
        assertTrue(r.rows.none { it.date == today })
        assertEquals(
            listOf(1, 2, 3, 5, 6, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18),
            r.rows.map { it.date.dayOfMonth },
        )
        assertEquals(d(11, 1), r.from)
        assertEquals(d(11, 18), r.to)

        // Deutschland + Niederlande an einem Tag → zählt für die Niederlande (Ausland hat Vorrang)
        val nov2 = r.row(11, 2)
        assertEquals("NL", nov2.countryCode)
        assertEquals(DayType.FULL, nov2.type)
        assertEquals(3200L, nov2.mealCents)
        assertEquals(800L, nov2.overnightCents)
        assertEquals(4000L, nov2.sumCents)
        // Zwei Arbeitszeiten untereinander
        assertEquals(listOf(360 to 660, 780 to 1170), nov2.times)

        // Eintägig 4 Std.: in der Abrechnung halber Satz (Firmenregel)
        val nov5 = r.row(11, 5)
        assertEquals(DayType.HALF, nov5.type)
        assertEquals(1400L, nov5.mealCents)
        assertFalse(nov5.hasNight)
        // Ohne Arbeitszeit: Abwesenheit als Beginn/Ende
        assertEquals(listOf(360 to 600), nov5.times)

        // Tag ohne Übernachtung: Übernachtung leer
        val nov6 = r.row(11, 6)
        assertFalse(nov6.hasNight)
        assertNull(nov6.overnightCents)
        assertEquals(1400L, nov6.sumCents)
        assertEquals("Abfahrt Fulda\nAnkunft Fulda", nov6.text)

        // Pauschale beim Folgetag: Nacht 31.10.→01.11. im November, Hotel am 12.11. → 13.11. ohne Pauschale
        assertTrue(r.row(11, 1).hasNight)
        assertFalse(r.row(11, 9).hasNight)
        assertTrue(r.row(11, 12).hasNight)
        assertFalse(r.row(11, 13).hasNight)
        assertEquals(11, r.nights)
        assertEquals(8800L, r.overnightCents)
        assertEquals(800L, r.overnightRate)

        // Summen
        val de = r.countries.single { it.countryCode == "DE" }
        assertEquals(9, de.fullDays)
        assertEquals(4, de.halfDays)
        assertEquals(30800L, de.totalCents)
        val nl = r.countries.single { it.countryCode == "NL" }
        assertEquals(2, nl.fullDays)
        assertEquals(6400L, nl.totalCents)
        assertEquals(37200L, r.mealCents)
        assertEquals(46000L, r.totalCents)
        assertEquals("Reisekosten_2026-11_Max_Muster.pdf", r.fileName)
    }

    @Test
    fun reiseanlassAutomatischUndVonHand() {
        val r = build(nov)
        // Tour über den Monatswechsel: Fortsetzung am 1., Ankunft am Ende
        assertEquals("Fortsetzung der Tour vom 29.10.2026\nÜbernachtung bei Autohof Venlo", r.row(11, 1).text)
        assertEquals("Übernachtung im LKW", r.row(11, 2).text)
        assertEquals("Ankunft Würzburg", r.row(11, 3).text)
        assertEquals("Abfahrt Fulda\nÜbernachtung bei Kassel", r.row(11, 9).text)
        // Hotel statt LKW
        assertEquals("Tour, unterwegs bei Bremen", r.row(11, 12).text)
        // Von Hand geändert hat Vorrang, automatischer Text bleibt bekannt
        val nov10 = r.row(11, 10)
        assertTrue(nov10.isManual)
        assertEquals("Kundentermin Hamburg", nov10.text)
        assertEquals("Übernachtung im LKW", nov10.autoText)
        // Laufende Tour im laufenden Monat: kein „Tour läuft weiter“ mitten im Monat
        assertEquals("Übernachtung im LKW", r.row(11, 18).text)
    }

    @Test
    fun oktoberMitTourUeberDenMonatswechsel() {
        val r = build(oct)
        assertTrue(r.complete)
        assertNull(r.skippedRunning)
        assertEquals(listOf(29, 30, 31), r.rows.map { it.date.dayOfMonth })
        assertEquals("Abfahrt Fulda\nÜbernachtung bei Seßlach", r.row(10, 29).text)
        assertEquals("Übernachtung im LKW\nTour läuft weiter", r.row(10, 31).text)
        // Anreisetag halb, ohne Pauschale (die Nacht gehört zum 30.10.)
        assertEquals(DayType.HALF, r.row(10, 29).type)
        assertFalse(r.row(10, 29).hasNight)
        assertTrue(r.row(10, 30).hasNight)
        // Schicht über Mitternacht: Teil bis 24:00 im Oktober …
        assertEquals(listOf(1320 to 1440), r.row(10, 31).times)
        // … der Rest im November
        assertEquals(listOf(0 to 180), build(nov).row(11, 1).times)
        // Oktober: 29. halb (14,00), 30. + 31. voll (je 28,00) und je eine Nacht (8,00)
        assertEquals(1400L + 2 * 2800L + 2 * 800L, r.totalCents)
    }

    @Test
    fun fehlenderBetragWirdGemeldetStattNullEuro() {
        val noNl = ExpenseBook(listOf(full2026.copy(rates = mapOf("DE" to ExpenseRate("DE", 2800, 1400)), overnightCents = null)))
        val r = build(nov, noNl)
        assertFalse(r.complete)
        assertTrue(r.row(11, 2).mealMissing)
        assertNull(r.row(11, 2).mealCents)
        assertTrue(r.row(11, 2).overnightMissing)
        assertEquals(
            listOf(MissingAmount(2026, "NL", DayType.FULL), MissingAmount(2026, null, null)),
            r.missing,
        )
        assertEquals("Niederlande: voller Tag (2026)", r.missing[0].label)
        assertEquals("Übernachtungspauschale (2026)", r.missing[1].label)
        // Ganz ohne Tabelle für das Jahr
        assertFalse(build(nov, ExpenseBook(emptyList())).complete)
    }

    @Test
    fun monatOhneTage() {
        val r = build(YearMonth.of(2026, 9))
        assertTrue(r.rows.isEmpty())
        assertEquals("Reisekosten_2026-09.pdf", TravelReports.fileName(YearMonth.of(2026, 9), " "))
    }

    /** Viele Tage: sauberer Umbruch auf zwei Seiten, Kopfzeile je Seite, Summe nur am Ende. */
    @Test
    fun monatMitUmbruchAufZweiSeiten() {
        val august = (1..31).map { day ->
            DayEntity(100L + day, LocalDate.of(2026, 8, day), 0, 1440, if (day % 3 == 0) "NL" else "DE", tourId = 9)
        }
        val augShifts = august.map {
            WorkShiftEntity(it.id, it.date, 6 * 60, it.date, 18 * 60)
        }
        val classified = Classifier.classifyAll(august, listOf(TourEntity(9, ended = true)), TravelReportSource.COMPANY_RULES)
        val r = TravelReports.build(
            YearMonth.of(2026, 8), "Max", classified, listOf(TourEntity(9, ended = true)),
            WorkSplitter.byDate(augShifts, now), ExpenseBook(listOf(full2026)), today = today,
        )
        assertEquals(31, r.rows.size)
        assertTrue(r.complete)

        val measure: (String) -> Float = { it.length * 4.6f }
        val heights = r.rows.map { TravelReportLayout.rowHeight(TravelReportLayout.cells(it, measure)) }
        val pages = TravelReportLayout.paginate(heights, TravelReportLayout.summaryHeight(r))
        assertTrue("mindestens zwei Seiten", pages.size >= 2)
        // Jede Zeile genau einmal, in Reihenfolge, keine Zeile zerteilt
        assertEquals((0 until 31).toList(), pages.flatMap { it.rows.toList() })
        assertTrue(pages.first().rows.count() > 10)
        // Zusammenfassung und Summe nur auf der letzten Seite
        assertEquals(listOf(true), pages.filter { it.summary }.map { it.summary })
        assertTrue(pages.last().summary)
        // Jede Seite passt
        val bottom = TravelReportLayout.PAGE_H - TravelReportLayout.MARGIN - TravelReportLayout.FOOTER
        pages.forEachIndexed { i, p ->
            val top = TravelReportLayout.MARGIN +
                (if (i == 0) TravelReportLayout.FIRST_HEADER else TravelReportLayout.NEXT_HEADER) + TravelReportLayout.TABLE_HEAD
            val used = p.rows.sumOf { heights[it].toDouble() } + if (p.summary) TravelReportLayout.summaryHeight(r) else 0f
            assertTrue("Seite ${i + 1} läuft über", top + used <= bottom)
        }
        // Zellen: Beginn und Ende untereinander, Beträge ohne €-Zeichen
        val cells = TravelReportLayout.cells(r.rows[1], measure)
        assertEquals(listOf("06:00 –", "18:00"), cells[1])
        assertEquals(listOf("Deutschland"), cells[3])
        assertEquals(listOf("28,00"), cells[4])
        assertEquals(listOf("8,00"), cells[5])
        assertEquals(listOf("36,00"), cells[6])
        // Langer Reiseanlass wird umgebrochen
        val long = r.rows[2].copy(manualText = "Kundentermin in Rotterdam und anschließend Leergut nach Venlo")
        assertTrue(TravelReportLayout.cells(long, measure)[2].size > 1)
    }

    /** Auswertung (Steuer): gesetzliche Regel, Euro je Land, Pauschale, fehlende Beträge ohne 0 €. */
    @Test
    fun euroInDerAuswertung() {
        val legal = Classifier.classifyAll(days, listOf(tourA, tourB), CountingRules())
        val book = ExpenseBook(listOf(full2026))
        val month = Period(PeriodType.MONTH, d(11, 1))
        val euro = EuroCalc.summarize(legal, book, month)
        val de = euro.countries.single { it.countryCode == "DE" }
        // 05.11. (4 Std.) zählt für die Steuer nicht → nur 3 halbe Tage DE (3., 6., 9.)
        assertEquals(3, de.halfDays)
        // Tour B läuft: 10.–19. voll (heute zählt in der Auswertung mit)
        assertEquals(10, de.fullDays)
        assertEquals(10 * 2800L + 3 * 1400L, de.totalCents)
        assertEquals(6400L, euro.countries.single { it.countryCode == "NL" }.totalCents)
        // Nächte: 1.–3. und 10.–19. ohne 13. (Hotel am 12.) = 12
        assertEquals(12, euro.nights)
        assertEquals(9600L, euro.overnightCents)
        assertEquals(10 * 2800L + 3 * 1400L + 6400L + 9600L, euro.totalCents)
        assertEquals(euro.nights, Stats.summarize(legal, month).overnights)
        assertEquals(1, Stats.summarize(legal, month).shortDays)

        // Betrag fehlt → keine 0 €, Summe unvollständig, Jahr für den Knopf
        val missing = ExpenseBook(listOf(full2026.copy(rates = mapOf("DE" to ExpenseRate("DE", 2800, null)))))
        val e2 = EuroCalc.summarize(legal, missing, month)
        assertNull(e2.countries.single { it.countryCode == "DE" }.totalCents)
        assertNull(e2.totalCents)
        assertFalse(e2.complete)
        assertEquals(listOf("DE", "NL"), e2.missingCountries.sorted())
        assertEquals(listOf(2026), EuroCalc.missingYears(legal, missing, month))

        // Jahreswechsel: jeder Tag mit der Tabelle seines Jahres
        val jan = DayEntity(500, LocalDate.of(2027, 1, 4), 300, 1000, "DE")
        val both = Classifier.classifyAll(listOf(jan) + days, listOf(tourA, tourB), CountingRules())
        val book2 = ExpenseBook(listOf(full2026, ExpenseTable(2027, 900, mapOf("DE" to ExpenseRate("DE", 3000, 1500)))))
        val all = EuroCalc.summarize(both, book2, null)
        assertTrue(all.complete)
        val deAll = all.countries.single { it.countryCode == "DE" }
        assertEquals(12 * 2800L + 4 * 1400L + 1500L, deAll.totalCents)
    }

    @Test
    fun erinnerungAmErstenUm18Uhr() {
        val r = de.fernfahrer.fahrtenbuch.work.ReportReminder
        assertEquals(LocalDateTime.of(2026, 12, 1, 18, 0), r.nextFire(now))
        assertEquals(LocalDateTime.of(2026, 12, 1, 18, 0), r.nextFire(LocalDateTime.of(2026, 12, 1, 9, 0)))
        assertEquals(LocalDateTime.of(2027, 1, 1, 18, 0), r.nextFire(LocalDateTime.of(2026, 12, 1, 18, 0)))
        // Am 1. vor 18 Uhr noch nicht, ab 18 Uhr für den Vormonat
        assertNull(r.dueMonth(LocalDateTime.of(2026, 12, 1, 17, 59), null))
        assertEquals(nov, r.dueMonth(LocalDateTime.of(2026, 12, 1, 18, 0), null))
        assertEquals(nov, r.dueMonth(LocalDateTime.of(2026, 12, 3, 8, 0), "2026-10"))
        // Schon erinnert oder zu spät im Monat
        assertNull(r.dueMonth(LocalDateTime.of(2026, 12, 2, 8, 0), "2026-11"))
        assertNull(r.dueMonth(LocalDateTime.of(2026, 12, 9, 8, 0), null))
        assertEquals("Oktober", r.monthName(oct))
    }
}
