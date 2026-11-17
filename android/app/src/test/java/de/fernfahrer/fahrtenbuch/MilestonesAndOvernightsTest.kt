package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.MilestoneEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.data.TrackChunkMeta
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.MilestoneTracker
import de.fernfahrer.fahrtenbuch.domain.Milestones
import de.fernfahrer.fahrtenbuch.domain.geo.Overnights
import de.fernfahrer.fahrtenbuch.location.PlaceLocator
import de.fernfahrer.fahrtenbuch.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** Meilensteine (Schwellen, Datum, stilles Freischalten), Übernachtungsplätze und Ortsliste. */
class MilestonesAndOvernightsTest {
    private val start = LocalDate.parse("2026-03-02")

    /** Tour über [n] Tage ab [from] (alle Tage außer dem letzten mit Übernachtung). */
    private fun tour(id: Long, from: LocalDate, n: Int, country: String = "DE", firstDayId: Long = id * 100) =
        (0 until n).map { i ->
            DayEntity(firstDayId + i, from.plusDays(i.toLong()), if (i == 0) 300 else 0, if (i == n - 1) 900 else 1440, country, tourId = id)
        }

    private fun meta(id: Long, date: LocalDate, km: Double, countries: String) =
        TrackChunkMeta(id, id, 0, date.atStartOfDay().toEpochSecond(ZoneOffset.UTC) + 3600, 100, km * 1000, countries)

    private fun statusOf(list: List<Milestones.Status>, id: String) = list.single { it.id == id }

    @Test
    fun meilensteineMitDatumAusDenDaten() {
        // 3 Touren: 4 Tage (DE), 5 Tage (AT), 3 Tage (DE) → 3 + 4 + 2 = 9 Übernachtungen
        val days = tour(1, start, 4) + tour(2, start.plusDays(10), 5, "AT") + tour(3, start.plusDays(20), 3)
        val tours = listOf(TourEntity(1, true), TourEntity(2, true), TourEntity(3, true))
        val classified = Classifier.classifyAll(days, tours, CountingRules())
        val meta = listOf(
            meta(1, start, 600.0, "DE"),
            meta(2, start.plusDays(10), 300.0, "AT,DE"),
            meta(3, start.plusDays(12), 200.0, "IT"),
        )
        val list = Milestones.evaluate(MainViewModel.milestoneInput(classified, meta, ZoneOffset.UTC))

        // 1.000 km am Tag, an dem die Summe 1.000 überschreitet (600 + 300 + 200)
        assertEquals(start.plusDays(12), statusOf(list, "km_1000").achievedOn)
        assertNull(statusOf(list, "km_5000").achievedOn)
        assertEquals(1100L, statusOf(list, "km_5000").current)

        // Länder: aus GPS-Strecke und erfassten Tagen, jeweils das erste Datum
        assertEquals(start, statusOf(list, "land_DE").achievedOn)
        assertEquals(start.plusDays(10), statusOf(list, "land_AT").achievedOn)
        assertEquals(start.plusDays(12), statusOf(list, "land_IT").achievedOn)
        assertEquals(3L, statusOf(list, "laender_5").current)

        // Übernachtungen: 9 → 10 noch offen
        assertNull(statusOf(list, "naechte_10").achievedOn)
        assertEquals(9L, statusOf(list, "naechte_10").current)

        // Erste Tour mit 5 Tagen: am 5. Tag der zweiten Tour
        assertEquals(start.plusDays(14), statusOf(list, "tour_5").achievedOn)
    }

    @Test
    fun tourMitVierTagenReichtNichtFuerDenTourMeilenstein() {
        val classified = Classifier.classifyAll(tour(1, start, 4), listOf(TourEntity(1, true)), CountingRules())
        val st = statusOf(Milestones.evaluate(MainViewModel.milestoneInput(classified, emptyList())), "tour_5")
        assertNull(st.achievedOn)
        assertEquals(4L, st.current)
        assertEquals(0.8f, st.progress, 0.001f)
    }

    @Test
    fun zehnteUebernachtungSchaltetMeilensteinFrei() {
        val days = tour(1, start, 11)
        val classified = Classifier.classifyAll(days, listOf(TourEntity(1, false)), CountingRules())
        val list = Milestones.evaluate(MainViewModel.milestoneInput(classified, emptyList()))
        assertEquals(start.plusDays(9), statusOf(list, "naechte_10").achievedOn)
    }

    // ---------- Meldungen: beim ersten Mal still, danach nur Neues ----------

    @Test
    fun ersteAuswertungStillDanachNurNeueMeldungen() = runBlocking {
        val stored = ArrayList<MilestoneEntity>()
        var initialized = false
        val tracker = MilestoneTracker({ stored.toList() }, { stored += it }, { initialized }, { initialized = true })

        fun input(km: Double, countries: String) = Milestones.evaluate(
            Milestones.Input(listOf(start to km * 1000), countries.split(',').associateWith { start }, emptyList(), emptyList()),
        )

        // Vorhandene Daten beim ersten Start: 6.000 km, 2 Länder → still freigeschaltet
        val first = tracker.check(input(6_000.0, "DE,AT"))
        assertTrue(first.isEmpty())
        assertEquals(setOf("km_1000", "km_5000", "land_DE", "land_AT"), stored.map { it.id }.toSet())

        // Gleicher Stand: nichts Neues
        assertTrue(tracker.check(input(6_000.0, "DE,AT")).isEmpty())

        // Neues Land und 10.000 km → zwei Meldungen
        val next = tracker.check(input(10_500.0, "DE,AT,PL"))
        assertEquals(setOf("km_10000", "land_PL"), next.map { it.id }.toSet())

        // Nach dem Wiederherstellen einer Sicherung: still
        tracker.silenceNext()
        assertTrue(tracker.check(input(30_000.0, "DE,AT,PL,CZ")).isEmpty())
        assertTrue(stored.any { it.id == "km_25000" })
    }

    // ---------- Übernachtungsplätze ----------

    private fun spot(day: Int, lat: Double, lon: Double, cc: String? = "DE") =
        Overnights.Spot(start.plusDays(day.toLong()), Math.round(lat * 1e5).toInt(), Math.round(lon * 1e5).toInt(), cc)

    @Test
    fun uebernachtungenAmSelbenPlatzWerdenZusammengefasst() {
        val raststaette = listOf(
            spot(0, 49.79400, 9.95000),
            spot(7, 49.79500, 9.95100),   // ≈ 135 m entfernt
            spot(14, 49.79350, 9.94900),  // ≈ 90 m
        )
        val anderer = spot(3, 49.79400 + 0.004, 9.95000) // ≈ 445 m nördlich → eigener Platz
        val clusters = Overnights.cluster(raststaette + anderer)
        assertEquals(2, clusters.size)
        val big = clusters.maxBy { it.count }
        assertEquals(3, big.count)
        assertEquals(1, clusters.minBy { it.count }.count)
    }

    @Test
    fun standortBeimArbeitsendeHatVorrangVorGpsStrecke() {
        val d0 = start
        val d1 = start.plusDays(1)
        val d2 = start.plusDays(2)
        val stops = mapOf(d0 to spot(0, 50.0, 8.0))
        val track = mapOf(d0 to spot(0, 51.0, 9.0, null), d1 to spot(1, 52.0, 10.0, null))
        val spots = Overnights.spots(listOf(d0, d1, d2), stops, track)
        assertEquals(2, spots.size)                // d2: weder Standort noch Strecke
        assertEquals(5_000_000, spots[0].latE5)     // gespeicherter Standort
        assertEquals(5_200_000, spots[1].latE5)     // letzter GPS-Punkt (ältere Tage)
    }

    // ---------- Offline-Ortsliste ----------

    @Test
    fun naechsterOrtAusDerOfflineListe() {
        val locator = File("src/main/assets/places.bin").inputStream().use(PlaceLocator::load)
        assertTrue(locator.size > 50_000)
        val wuerzburg = locator.nearest(49.7913, 9.9534)!!
        assertEquals("Würzburg", wuerzburg.name)
        assertEquals("München", locator.nearest(48.137, 11.575)!!.name)
        assertTrue(wuerzburg.distanceKm < 2)
        assertEquals(wuerzburg.name, wuerzburg.label)
        // Raststätte an der A3 zwischen Orten: „x km von …“
        val rast = locator.nearest(49.90, 9.60)!!
        assertTrue(rast.label, rast.label.contains(" km von ") || rast.distanceKm < 2)
        // Mitten auf dem Atlantik: kein Ort
        assertNull(locator.nearest(45.0, -20.0))
    }
}
