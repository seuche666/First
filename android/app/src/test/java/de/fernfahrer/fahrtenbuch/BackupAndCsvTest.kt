package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.Backup
import de.fernfahrer.fahrtenbuch.data.BackupData
import de.fernfahrer.fahrtenbuch.data.BackupFormatException
import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.data.WorkSettings
import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import de.fernfahrer.fahrtenbuch.export.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BackupAndCsvTest {
    private val so = LocalDate.parse("2026-05-03")
    private val mo = so.plusDays(1)
    private val data = BackupData(
        tours = listOf(TourEntity(3, ended = true)),
        days = listOf(
            DayEntity(
                1, mo, 300, 1440, "FR", 100, 700, "Lyon; \"Kühl\"", tourId = 3,
                countryManual = false, kmInterpolated = true,
            ),
            DayEntity(2, mo.plusDays(1), 0, 900, "DE", 700, 900, "", tourId = 3, dayTypeOverride = DayType.FULL),
            DayEntity(3, mo.plusDays(4), 480, 720, "DE", 900, 1000, "", overnightOverride = true, legacyWorkEndMinute = 1440),
        ),
        driverName = "Max Muster",
        rules = CountingRules(shortSingleDayAsHalf = true),
        shifts = listOf(
            WorkShiftEntity(1, mo, 330, mo.plusDays(1), 120, kmStart = 100, kmEnd = 760),
            WorkShiftEntity(2, mo.plusDays(4), 470, null, null, running = true, kmStart = 900),
        ),
        workSettings = WorkSettings(reminderEnabled = false, reminderHours = 12, gpsEnabled = false),
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
              "app": "fahrtenbuch-android", "version": 1, "driverName": "Max",
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
        assertNull(restored.workSettings)
        assertTrue(restored.shifts.isEmpty())
        val wien = restored.days.first { it.id == 7L }
        assertEquals("AT", wien.countryCode)
        assertEquals(600, wien.distanceKm)
        assertTrue(wien.countryManual)
        assertEquals(DayType.FULL, restored.days.first { it.id == 8L }.dayTypeOverride)
    }

    /** Sicherung aus App-Version 1.0.6 (Arbeitszeit pro Tag, auch von Hand an Mitternacht getrennt). */
    @Test
    fun alteSicherungVersion2WirdGeladenUndArbeitszeitUebernommen() {
        val v2 = """
            {
              "app": "fahrtenbuch-android", "version": 2, "driverName": "Max",
              "rules": { "shortSingleDayAsHalf": true },
              "tours": [],
              "days": [
                { "id": 1, "date": "2026-11-15", "startMinute": 1380, "endMinute": 1440, "countryCode": "DE",
                  "kmStart": 1000, "kmEnd": 1030, "note": "", "tourId": null, "dayTypeOverride": null,
                  "workStartMinute": 1410, "workEndMinute": 1440 },
                { "id": 2, "date": "2026-11-16", "startMinute": 0, "endMinute": 620, "countryCode": "AT",
                  "kmStart": 1030, "kmEnd": 1600, "note": "", "tourId": null, "dayTypeOverride": null,
                  "workStartMinute": 0, "workEndMinute": 600 }
              ]
            }
        """.trimIndent()
        val restored = Backup.fromJson(v2)
        assertEquals(true, restored.rules?.shortSingleDayAsHalf)
        assertEquals(1410, restored.days[0].legacyWorkStartMinute)
        assertEquals(2, restored.shifts.size)
        val work = WorkSplitter.byDate(restored.shifts, LocalDateTime.now())
        assertEquals(30, WorkSplitter.minutes(work[LocalDate.parse("2026-11-15")]))
        assertEquals(600, WorkSplitter.minutes(work[LocalDate.parse("2026-11-16")]))
    }

    @Test
    fun neueSicherungEnthaeltSchichtenUndEinstellungen() {
        val json = org.json.JSONObject(Backup.toJson(data))
        assertEquals(Backup.VERSION, json.getInt("version"))
        assertEquals(2, json.getJSONArray("shifts").length())
        assertEquals(true, json.getJSONArray("shifts").getJSONObject(1).getBoolean("running"))
        assertEquals(12, json.getJSONObject("workSettings").getInt("reminderHours"))
        assertEquals(false, json.getJSONArray("days").getJSONObject(0).getBoolean("countryManual"))
    }

    /** Sicherung aus App-Version 1.0.8 (Format 3, ohne Strecken): vorhandene Strecken bleiben beim Laden erhalten. */
    @Test
    fun alteSicherungVersion3OhneStreckenWirdGeladen() {
        val v3 = org.json.JSONObject(Backup.toJson(data)).apply {
            put("version", 3)
            remove("tracks")
            remove("mapSettings")
        }.toString()
        val restored = Backup.fromJson(v3)
        assertEquals(data.days, restored.days)
        assertEquals(data.shifts, restored.shifts)
        assertNull(restored.tracks)
        assertNull(restored.mapSettings)
    }

    @Test
    fun sicherungMitStreckenUndKarteneinstellungen() {
        val points = (0 until 300).map { i ->
            de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint.of(48.0 + i * 0.0004, 11.0 + i * 0.0001, 1_700_000_000L + i * 2)
        }
        val chunk = de.fernfahrer.fahrtenbuch.data.TrackChunks.build(5, 99L, 0, points, listOf("DE", "AT"))
        val withTracks = data.copy(
            tracks = listOf(chunk),
            mapSettings = de.fernfahrer.fahrtenbuch.data.MapSettings(trackingEnabled = false, corridorMeters = 1000),
        )
        val json = Backup.toJson(withTracks)
        val restored = Backup.fromJson(json)
        assertEquals(withTracks.mapSettings, restored.mapSettings)
        val t = restored.tracks!!.single()
        assertEquals(99L, t.trackId)
        assertEquals(300, t.pointCount)
        assertEquals("AT,DE", t.countries)
        assertEquals(chunk.lengthMeters, t.lengthMeters, 0.001)
        assertEquals(chunk.minLatE5 to chunk.maxLonE5, t.minLatE5 to t.maxLonE5)
        assertTrue(chunk.data.contentEquals(t.data))
        assertEquals(points, de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec.decode(t.data).toList())
        // Kompakt: deutlich unter 10 Byte pro Punkt (Base64 eingerechnet)
        assertTrue(org.json.JSONObject(json).getJSONArray("tracks").getJSONObject(0).getString("points").length < 300 * 10)
    }

    @Test(expected = BackupFormatException::class)
    fun beschaedigteStreckeWirdAbgelehnt() {
        val json = org.json.JSONObject(Backup.toJson(data)).apply {
            put("tracks", org.json.JSONArray().put(org.json.JSONObject().put("trackId", 1).put("seq", 0).put("points", "AAAA")))
        }.toString()
        Backup.fromJson(json)
    }

    @Test
    fun csvMitArbeitszeitUebernachtungUndGeschaetztenKm() {
        val rules = CountingRules(shortSingleDayAsHalf = true)
        val classified = Classifier.classifyAll(data.days, data.tours, rules)
        val work = WorkSplitter.byDate(data.shifts.filter { !it.running }, LocalDateTime.now())
        val summary = Stats.summarize(classified, null, work)
        val csv = CsvExporter.build(2026, classified, summary, "Max", rules, work)
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains("Zählregel eintägige Fahrten;Firmenregel:"))
        assertTrue(csv.contains(";Übernachtung im LKW;Arbeitszeit;Arbeitszeit (Std.);km morgens;km abends;km gefahren;km anteilig geschätzt;Notiz"))
        // Montag: Tour-Anreisetag, Übernachtung, Arbeit 05:30–24:00, km geschätzt
        assertTrue(
            csv.contains(
                "04.05.2026;Mo;Frankreich;FR;05:00;24:00;19:00;Halber Tag;Anreisetag der Tour;;;ja;ja;" +
                    "05:30–24:00;18:30;100;700;600;ja;\"Lyon; \"\"Kühl\"\"\"",
            ),
        )
        // Dienstag: Rückreisetag (manuell voll), Arbeit 00:00–02:00 aus der Schicht über Mitternacht
        assertTrue(csv.contains("05.05.2026;Di;Deutschland;DE;00:00;15:00;15:00;Voller Tag;Manuell geändert"))
        assertTrue(csv.contains(";ja;;ja;;00:00–02:00;2:00;700;900;200;;"))
        assertTrue(csv.contains("Übernachtungen im LKW;2"))
        assertTrue(csv.contains("Arbeitszeit gesamt (Std.);20:30"))
    }
}
