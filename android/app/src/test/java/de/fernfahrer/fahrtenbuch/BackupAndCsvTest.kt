package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.Backup
import de.fernfahrer.fahrtenbuch.data.BackupData
import de.fernfahrer.fahrtenbuch.data.BackupFormatException
import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.export.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupAndCsvTest {
    private val data = BackupData(
        tours = listOf(TourEntity(3, ended = true)),
        days = listOf(
            DayEntity(
                1, LocalDate.parse("2026-05-04"), 300, 1440, "FR", 100, 700, "Lyon; \"Kühl\"", tourId = 3,
                workStartMinute = 330, workEndMinute = 1140,
            ),
            DayEntity(2, LocalDate.parse("2026-05-05"), 0, 900, "DE", null, null, "", tourId = 3, dayTypeOverride = DayType.FULL),
            DayEntity(3, LocalDate.parse("2026-05-08"), 480, 720, "DE", 900, 1000, "", workEndMinute = 1440),
        ),
        driverName = "Max Muster",
        rules = CountingRules(shortSingleDayAsHalf = true),
    )

    @Test
    fun sicherungHinUndZurueck() {
        val restored = Backup.fromJson(Backup.toJson(data))
        assertEquals(data, restored)
    }

    @Test(expected = BackupFormatException::class)
    fun fremdeDateiWirdAbgelehnt() {
        Backup.fromJson("{\"app\":\"etwas-anderes\"}")
    }

    /** Sicherung im Format der ersten App-Version (ohne Arbeitszeit und Zählregel). */
    @Test
    fun alteSicherungVersion1WirdGeladen() {
        val v1 = """
            {
              "app": "fahrtenbuch-android",
              "version": 1,
              "exportedAt": "2026-11-09T22:00:00",
              "driverName": "Max",
              "tours": [ { "id": 1, "ended": true } ],
              "days": [
                { "id": 7, "date": "2026-11-02", "startMinute": 300, "endMinute": 1440, "countryCode": "AT",
                  "kmStart": 1000, "kmEnd": 1600, "note": "Wien", "tourId": 1, "dayTypeOverride": null },
                { "id": 8, "date": "2026-11-03", "startMinute": 0, "endMinute": 900, "countryCode": "DE",
                  "kmStart": null, "kmEnd": null, "note": "", "tourId": 1, "dayTypeOverride": "FULL" }
              ]
            }
        """.trimIndent()
        val restored = Backup.fromJson(v1)
        assertEquals(2, restored.days.size)
        assertEquals("Max", restored.driverName)
        assertNull(restored.rules)
        val wien = restored.days.first { it.id == 7L }
        assertEquals("AT", wien.countryCode)
        assertEquals(600, wien.distanceKm)
        assertNull(wien.workStartMinute)
        assertNull(wien.workEndMinute)
        assertEquals(DayType.FULL, restored.days.first { it.id == 8L }.dayTypeOverride)
    }

    @Test
    fun neueSicherungEnthaeltVersionArbeitszeitUndRegel() {
        val json = org.json.JSONObject(Backup.toJson(data))
        assertEquals(Backup.VERSION, json.getInt("version"))
        assertEquals(true, json.getJSONObject("rules").getBoolean("shortSingleDayAsHalf"))
        assertEquals(330, json.getJSONArray("days").getJSONObject(0).getInt("workStartMinute"))
    }

    @Test
    fun csvMitArbeitszeitBegruendungUndRegel() {
        val rules = CountingRules(shortSingleDayAsHalf = true)
        val classified = Classifier.classifyAll(data.days, data.tours, rules)
        val csv = CsvExporter.build(2026, classified, Stats.summarize(classified), "Max", rules)
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains("Zählregel eintägige Fahrten;Firmenregel:"))
        assertTrue(
            csv.contains(
                "04.05.2026;Mo;Frankreich;FR;05:00;24:00;19:00;Halber Tag;Anreisetag der Tour;;;ja;" +
                    "05:30;19:00;13:30;100;700;600;\"Lyon; \"\"Kühl\"\"\"",
            ),
        )
        assertTrue(
            csv.contains(
                "05.05.2026;Di;Deutschland;DE;00:00;15:00;15:00;Voller Tag;" +
                    "Manuell geändert (automatisch wäre: Halber Tag – Rückreisetag der Tour);ja;;ja;;;;;;;",
            ),
        )
        // Eintägig 4 Std. → halber Tag nur nach Firmenregel, markiert
        assertTrue(csv.contains("08.05.2026;Fr;Deutschland;DE;08:00;12:00;4:00;Halber Tag;Eintägige Fahrt bis 8 Stunden – halber Tag nach Firmenregel (Finanzamt: erst über 8 Stunden);;ja;;;24:00;;900;1000;100;"))
        assertTrue(csv.contains("Davon halbe Tage nur nach Firmenregel (eintägig, 8 Std. oder weniger);1"))
        assertTrue(csv.contains("Arbeitszeit gesamt (Std.);13:30"))
    }

    @Test
    fun csvOhneFirmenregelNenntGesetzlicheRegel() {
        val classified = Classifier.classifyAll(data.days, data.tours)
        val csv = CsvExporter.build(2026, classified, Stats.summarize(classified), "", CountingRules())
        assertTrue(csv.contains("Zählregel eintägige Fahrten;Gesetzliche Regel:"))
        assertTrue(csv.contains("08.05.2026;Fr;Deutschland;DE;08:00;12:00;4:00;Zählt nicht;Eintägige Fahrt bis 8 Stunden;;;"))
    }
}
