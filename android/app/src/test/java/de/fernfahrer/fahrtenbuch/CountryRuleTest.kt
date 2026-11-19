package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayCountries
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.export.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Länder-Regel „Ausland hat Vorrang“: pro Tag zählt genau ein Land. */
class CountryRuleTest {
    private val mo = LocalDate.parse("2026-11-16")
    private val tour = TourEntity(1, ended = true)

    private fun day(id: Long, date: LocalDate, route: List<String>, start: Int = 0, end: Int = 1440, manual: Boolean = false) =
        DayEntity(id, date, start, end, route.first(), null, null, tourId = 1, countryManual = manual).withCountries(route)

    @Test
    fun nurDeutschlandZaehltAlsDeutschland() {
        assertEquals("DE", DayCountries.countingCountry(day(1, mo, listOf("DE"))))
    }

    @Test
    fun tagMitDeutschlandUndNiederlandenZaehltNurFuerDieNiederlande() {
        val days = listOf(
            day(1, mo, listOf("DE"), start = 300),
            day(2, mo.plusDays(1), listOf("DE", "NL", "DE")),
            day(3, mo.plusDays(2), listOf("DE"), end = 900),
        )
        val classified = Classifier.classifyAll(days, listOf(tour))
        val nl = classified.first { it.day.id == 2L }
        assertEquals("NL", nl.country)
        assertEquals(listOf("DE", "NL"), nl.day.allCountries) // Rohdaten bleiben erhalten

        val s = Stats.summarize(classified)
        assertEquals(1, s.abroad.single { it.countryCode == "NL" }.full)
        // Deutschland zählt an diesem Tag nicht: nur An- und Abreisetag
        assertEquals(0, s.germany.full)
        assertEquals(2, s.germany.half)
        assertEquals(3, s.daysAway)

        // Im Export steht pro Tag nur ein Land
        val csv = CsvExporter.build(2026, classified, s, "", CountingRules())
        assertTrue(csv.contains("17.11.2026;Di;Niederlande;NL;00:00"))
        assertFalse(csv.contains("Alle Länder"))
        assertFalse(csv.contains("Niederlande;NL;Deutschland"))
    }

    @Test
    fun zweiAuslaendischeStaatenZaehltDerZuletztBesuchte() {
        assertEquals("NL", DayCountries.countingCountry(day(1, mo, listOf("DE", "BE", "NL"))))
        assertEquals("IT", DayCountries.countingCountry(day(2, mo, listOf("AT", "IT", "DE"))))
        assertEquals("AT", DayCountries.countingCountry(day(3, mo, listOf("IT", "AT"))))
        val classified = Classifier.classifyAll(listOf(day(4, mo, listOf("FR", "BE", "DE"))), listOf(tour))
        assertEquals("BE", classified.single().country)
        assertEquals("BE", Stats.summarize(classified).abroad.single().countryCode)
    }

    /** Rückwirkend: auch ein älterer Eintrag mit gespeichertem „DE“ wird aus allen Ländern neu berechnet. */
    @Test
    fun aeltereEintraegeWerdenNeuBerechnet() {
        val old = DayEntity(1, mo, 0, 1440, "DE", null, null, countries = "DE,PL")
        assertEquals("PL", DayCountries.countingCountry(old))
        assertEquals("DE", old.countryCode) // Rohdaten unverändert
        // Ohne Länderliste (vor Version 6): das gespeicherte Land
        assertEquals("AT", DayCountries.countingCountry(DayEntity(2, mo, 0, 1440, "AT", null, null)))
    }

    /** Von Hand festgelegtes Land bleibt maßgeblich. */
    @Test
    fun vonHandGewaehltesLandHatVorrang() {
        val manual = day(1, mo, listOf("DE"), manual = true)
        assertEquals("DE", DayCountries.countingCountry(manual))
    }
}
