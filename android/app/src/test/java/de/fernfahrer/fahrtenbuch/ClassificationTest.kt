package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.DayType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ClassificationTest {
    private fun day(
        id: Long, date: String, start: Int = 0, end: Int = 1440, tour: Long? = null,
        country: String = "DE", override: DayType? = null,
    ) = DayEntity(
        id = id, date = LocalDate.parse(date), startMinute = start, endMinute = end,
        countryCode = country, tourId = tour, dayTypeOverride = override,
    )

    @Test
    fun eintaegigeFahrtUeber8StundenIstHalberTag() {
        val d = day(1, "2026-03-02", start = 6 * 60, end = 14 * 60 + 1)
        assertEquals(DayType.HALF, Classifier.classifyAll(listOf(d), emptyList()).single().type)
    }

    @Test
    fun eintaegigeFahrtGenau8StundenZaehltNicht() {
        val d = day(1, "2026-03-02", start = 6 * 60, end = 14 * 60)
        assertEquals(DayType.NONE, Classifier.classifyAll(listOf(d), emptyList()).single().type)
    }

    @Test
    fun beendeteTourHatAnUndAbreiseHalbUndMitteVoll() {
        val tour = TourEntity(id = 7, ended = true)
        val days = listOf(
            day(1, "2026-03-02", start = 5 * 60, tour = 7),
            day(2, "2026-03-03", tour = 7, country = "AT"),
            day(3, "2026-03-04", tour = 7, country = "IT"),
            day(4, "2026-03-05", end = 17 * 60, tour = 7),
        )
        val types = Classifier.classifyAll(days, listOf(tour)).associate { it.day.id to it.type }
        assertEquals(DayType.HALF, types[1])
        assertEquals(DayType.FULL, types[2])
        assertEquals(DayType.FULL, types[3])
        assertEquals(DayType.HALF, types[4])
    }

    @Test
    fun laufendeTourLetzterTagIstVoll() {
        val tour = TourEntity(id = 1, ended = false)
        val days = listOf(day(1, "2026-03-02", start = 600, tour = 1), day(2, "2026-03-03", tour = 1))
        val types = Classifier.classifyAll(days, listOf(tour)).associate { it.day.id to it.type }
        assertEquals(DayType.HALF, types[1])
        assertEquals(DayType.FULL, types[2])
    }

    @Test
    fun tourAmSelbenTagBeendetZaehltWieEintaegigeFahrt() {
        val tour = TourEntity(id = 1, ended = true)
        val short = day(1, "2026-03-02", start = 8 * 60, end = 12 * 60, tour = 1)
        assertEquals(DayType.NONE, Classifier.classifyAll(listOf(short), listOf(tour)).single().type)
    }

    @Test
    fun manuelleEinordnungHatVorrang() {
        val d = day(1, "2026-03-02", start = 6 * 60, end = 18 * 60, override = DayType.FULL)
        val c = Classifier.classifyAll(listOf(d), emptyList()).single()
        assertEquals(DayType.HALF, c.autoType)
        assertEquals(DayType.FULL, c.type)
        assertEquals(true, c.isManual)
    }
}
