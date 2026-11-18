package de.fernfahrer.fahrtenbuch

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.Backup
import de.fernfahrer.fahrtenbuch.data.PreMigrationBackup
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Prüft, dass Updates der Datenbank keine Einträge verlieren. Die alten Datenbanken werden exakt nach
 * den exportierten Schemata (app/schemas/…/1.json … 3.json) angelegt und mit Daten gefüllt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
        context.deleteDatabase(AppDatabase.DB_NAME)
        File(context.filesDir, PreMigrationBackup.DIR_NAME).deleteRecursively()
    }

    /** Legt eine Datenbank im Stand einer alten App-Version an (Tabellen laut exportiertem Schema). */
    private fun createDatabase(name: String, version: Int, fill: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val schema = JSONObject(
            File("schemas/de.fernfahrer.fahrtenbuch.data.AppDatabase/$version.json").readText(),
        ).getJSONObject("database")
        assertEquals(version, schema.getInt("version"))
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.fill()
            db.version = version
        }
    }

    private fun openWithRoom(name: String, migrations: Array<androidx.room.migration.Migration> = AppDatabase.MIGRATIONS) =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*migrations)
            .allowMainThreadQueries()
            .build()

    /** Datenbank im Stand von App-Version 1.0.8 (Datenbank-Version 3) mit Einträgen. */
    private fun SQLiteDatabase.fillVersion3() {
        execSQL("INSERT INTO tours (id, ended) VALUES (1, 0)")
        execSQL(
            "INSERT INTO days (id, date, startMinute, endMinute, countryCode, kmStart, kmEnd, note, tourId, dayTypeOverride, " +
                "workStartMinute, workEndMinute, countryManual, overnightOverride, kmInterpolated, timesAuto) VALUES " +
                "(1, '2026-11-09', 300, 1440, 'DE', 500000, 500700, 'Abfahrt', 1, NULL, NULL, NULL, 1, NULL, 0, 0), " +
                "(2, '2026-11-10', 0, 1440, 'AT', 500700, 501300, 'Wien', 1, 'FULL', NULL, NULL, 0, 1, 1, 0), " +
                "(3, '2026-11-11', 0, 900, 'IT', 501300, NULL, '', 1, NULL, NULL, NULL, 0, 0, 0, 1)",
        )
        execSQL(
            "INSERT INTO work_shifts (id, startDate, startMinute, endDate, endMinute, running, kmStart, kmEnd) VALUES " +
                "(1, '2026-11-09', 330, '2026-11-10', 120, 0, 500000, 500900), " +
                "(2, '2026-11-11', 360, NULL, NULL, 1, 501300, NULL)",
        )
    }

    @Test
    fun updateVonVersion3Auf4BehaeltAlleEintraege() {
        createDatabase(dbName, 3) { fillVersion3() }
        val room = openWithRoom(dbName)
        try {
            runBlocking {
                val dao = room.dao()
                val days = dao.getAllDays().associateBy { it.id }
                assertEquals(3, days.size)
                with(days[2]!!) {
                    assertEquals("AT", countryCode)
                    assertEquals(500700 to 501300, kmStart to kmEnd)
                    assertEquals("Wien", note)
                    assertEquals(DayType.FULL, dayTypeOverride)
                    assertFalse(countryManual)
                    assertEquals(true, overnightOverride)
                    assertTrue(kmInterpolated)
                }
                assertTrue(days[3]!!.timesAuto)
                assertEquals(false, dao.getTour(1)?.ended)

                val shifts = dao.getAllShifts()
                assertEquals(2, shifts.size)
                assertEquals(LocalDate.parse("2026-11-10") to 120, shifts[0].endDate to shifts[0].endMinute)
                assertEquals(500900, shifts[0].kmEnd)
                // Die laufende Arbeitszeit läuft nach dem Update weiter
                assertEquals(2L, dao.getRunningShift()?.id)

                // Neue, leere Streckentabelle; Strecken lassen sich speichern
                val tracks = room.trackDao()
                assertEquals(0, tracks.count())
                val chunk = de.fernfahrer.fahrtenbuch.data.TrackChunks.build(
                    0, 1L, 0,
                    listOf(
                        de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint.of(48.2, 16.37, 1_000),
                        de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint.of(48.2004, 16.37, 1_002),
                    ),
                    listOf("AT"),
                )
                tracks.insert(chunk)
                assertEquals(1, tracks.count())
                assertEquals("AT", tracks.getAllChunks().single().countries)
            }
            assertEquals(AppDatabase.VERSION, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }

    /** Datenbank im Stand von App-Version 1.0.10 (Datenbank-Version 4) mit Einträgen und einer Strecke. */
    private fun SQLiteDatabase.fillVersion4() {
        fillVersion3()
        val chunk = de.fernfahrer.fahrtenbuch.data.TrackChunks.build(
            0, 77L, 0,
            (0 until 50).map { de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint.of(48.0 + it * 0.0004, 11.0, 1_762_700_000L + it * 2) },
            listOf("DE"),
        )
        val v = android.content.ContentValues().apply {
            put("trackId", chunk.trackId); put("seq", chunk.seq); put("startTime", chunk.startTime); put("endTime", chunk.endTime)
            put("pointCount", chunk.pointCount); put("minLatE5", chunk.minLatE5); put("minLonE5", chunk.minLonE5)
            put("maxLatE5", chunk.maxLatE5); put("maxLonE5", chunk.maxLonE5); put("lengthMeters", chunk.lengthMeters)
            put("countries", chunk.countries); put("data", chunk.data)
        }
        insertOrThrow("track_chunks", null, v)
    }

    @Test
    fun updateVonVersion4Auf5BehaeltAlleEintraegeUndStrecken() {
        createDatabase(dbName, 4) { fillVersion4() }
        val room = openWithRoom(dbName)
        try {
            runBlocking {
                val days = room.dao().getAllDays()
                assertEquals(3, days.size)
                assertEquals("Wien", days.first { it.id == 2L }.note)
                assertEquals(true, days.first { it.id == 2L }.overnightOverride)
                assertEquals(2, room.dao().getAllShifts().size)
                assertEquals(2L, room.dao().getRunningShift()?.id)
                val chunk = room.trackDao().getAllChunks().single()
                assertEquals(50, chunk.pointCount)
                assertEquals(50, de.fernfahrer.fahrtenbuch.domain.geo.TrackCodec.decode(chunk.data).size)

                // Neue, leere Tabellen; Einträge lassen sich speichern
                val stops = room.stopDao()
                assertTrue(stops.getAllStops().isEmpty())
                assertTrue(stops.getAllMilestones().isEmpty())
                stops.insertStop(de.fernfahrer.fahrtenbuch.data.StopLocationEntity(date = LocalDate.parse("2026-11-10"), minute = 1200, latE5 = 4_820_000, lonE5 = 1_637_000, countryCode = "AT"))
                stops.insertMilestones(listOf(de.fernfahrer.fahrtenbuch.data.MilestoneEntity("km_1000", LocalDate.parse("2026-11-10"))))
                assertEquals("AT", stops.getAllStops().single().countryCode)
                assertEquals("km_1000", stops.getAllMilestones().single().id)
            }
            assertEquals(AppDatabase.VERSION, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }

    /** Datenbank im Stand von App-Version 1.0.12 (Datenbank-Version 5) mit Einträgen, Strecke, Standorten. */
    private fun SQLiteDatabase.fillVersion5() {
        fillVersion4()
        execSQL(
            "INSERT INTO stop_locations (id, date, minute, latE5, lonE5, countryCode) VALUES " +
                "(1, '2026-11-09', 1200, 4810000, 1150000, 'DE'), " +
                "(2, '2026-11-10', 1230, 4820000, 1637000, 'AT'), " +
                "(3, '2026-11-11', 900, 4540000, 1100000, 'IT')",
        )
        execSQL("INSERT INTO milestones (id, achievedOn) VALUES ('km_1000', '2026-11-10')")
    }

    @Test
    fun updateVonVersion5Auf6BehaeltAlleEintraegeUndUebernimmtUebernachtungsplaetze() {
        createDatabase(dbName, 5) { fillVersion5() }
        val room = openWithRoom(dbName)
        try {
            runBlocking {
                val days = room.dao().getAllDays()
                assertEquals(3, days.size)
                val wien = days.first { it.id == 2L }
                assertEquals("Wien", wien.note)
                assertEquals("AT", wien.countryCode)
                assertEquals("", wien.countries)
                assertEquals(listOf("AT"), wien.countryRoute)
                assertEquals(true, wien.overnightOverride)
                assertEquals(2, room.dao().getAllShifts().size)
                assertEquals(2L, room.dao().getRunningShift()?.id)
                assertEquals(50, room.trackDao().getAllChunks().single().pointCount)
                assertEquals(3, room.stopDao().getAllStops().size)
                assertEquals("km_1000", room.stopDao().getAllMilestones().single().id)
                // Neue Tabellen sind zunächst leer
                assertTrue(room.stopDao().getAllPlaces().isEmpty())

                // Einmalige Übernahme der bisher berechneten Plätze: Übernachtungstage 9. und 10.11.
                // (am 11.11. keine Übernachtung) – Positionen aus den Standorten beim Arbeitsende
                val repo = de.fernfahrer.fahrtenbuch.data.Repository(room, kotlinx.coroutines.flow.flowOf(CountingRules()))
                val tracks = de.fernfahrer.fahrtenbuch.data.TrackRepository(room)
                de.fernfahrer.fahrtenbuch.data.OvernightImport.importNow(repo, tracks, today = LocalDate.parse("2026-11-18"))
                val places = repo.getOvernightPlaces().sortedBy { it.place.lonE5 }
                assertEquals(2, places.size)
                assertEquals(4_810_000 to 1_150_000, places[0].place.latE5 to places[0].place.lonE5)
                assertEquals(LocalDate.parse("2026-11-09"), places[0].stays.single().date)
                assertEquals("AT", places[1].place.countryCode)
                assertEquals(LocalDate.parse("2026-11-10"), places[1].stays.single().date)
                // Zweiter Aufruf legt nichts doppelt an
                de.fernfahrer.fahrtenbuch.data.OvernightImport.importNow(repo, tracks, today = LocalDate.parse("2026-11-18"))
                assertEquals(2, repo.getOvernightPlaces().size)

                // Neue Felder lassen sich speichern
                repo.updateOvernightPlace(places[1].place.copy(name = "Rastplatz Wien", rating = 5, shower = true, note = "ab 18 Uhr voll"))
                assertEquals("Rastplatz Wien", repo.getOvernightPlaces().first { it.place.id == places[1].place.id }.place.name)
                room.dao().updateDay(wien.withCountries(listOf("DE", "AT", "DE")))
                val updated = room.dao().getAllDays().first { it.id == 2L }
                assertEquals("DE,AT,DE", updated.countries)
                assertEquals("AT", updated.countryCode)
            }
            assertEquals(6, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }

    /** Gegenprobe: Ohne die Migration 5 → 6 lässt Room die Datenbank nicht öffnen (und löscht nichts). */
    @Test
    fun ohneMigration5Auf6SchlaegtDasOeffnenFehl() {
        createDatabase(dbName, 5) { fillVersion5() }
        val room = openWithRoom(
            dbName, arrayOf(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5),
        )
        try {
            val error = runCatching { runBlocking { room.dao().getAllDays() } }.exceptionOrNull()
            assertTrue("Ohne Migration muss das Öffnen fehlschlagen", error is IllegalStateException)
        } finally {
            room.close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(5, db.version)
            db.rawQuery("SELECT COUNT(*) FROM days", null).use { c -> c.moveToFirst(); assertEquals(3, c.getInt(0)) }
            db.rawQuery("SELECT COUNT(*) FROM stop_locations", null).use { c -> c.moveToFirst(); assertEquals(3, c.getInt(0)) }
        }
    }

    @Test
    fun sicherungVorUpdateVonVersion5EnthaeltStandorteUndStrecken() {
        createDatabase(AppDatabase.DB_NAME, 5) { fillVersion5() }
        var publicBytes: ByteArray? = null
        val result = PreMigrationBackup.runIfNeeded(context, AppDatabase.DB_NAME, AppDatabase.VERSION, "Max", null) { _, name, bytes ->
            publicBytes = bytes
            name
        }
        assertEquals(5, result!!.fromVersion)
        val restored = Backup.fromJson(String(publicBytes!!))
        assertEquals(3, restored.days.size)
        assertEquals(3, restored.stops!!.size)
        assertEquals(50, restored.tracks!!.single().pointCount)
        assertEquals(1, restored.milestones!!.size)
        assertNull("alte Datenbank hat noch keine Plätze", restored.places)
    }

    /** Gegenprobe: Ohne die Migration 4 → 5 lässt Room die Datenbank nicht öffnen (und löscht nichts). */
    @Test
    fun ohneMigration4Auf5SchlaegtDasOeffnenFehl() {
        createDatabase(dbName, 4) { fillVersion4() }
        val room = openWithRoom(dbName, arrayOf(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4))
        try {
            val error = runCatching { runBlocking { room.dao().getAllDays() } }.exceptionOrNull()
            assertTrue("Ohne Migration muss das Öffnen fehlschlagen", error is IllegalStateException)
        } finally {
            room.close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(4, db.version)
            db.rawQuery("SELECT COUNT(*) FROM track_chunks", null).use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        }
    }

    @Test
    fun sicherungVorUpdateVonVersion4EnthaeltStrecken() {
        createDatabase(AppDatabase.DB_NAME, 4) { fillVersion4() }
        var publicBytes: ByteArray? = null
        val result = PreMigrationBackup.runIfNeeded(context, AppDatabase.DB_NAME, AppDatabase.VERSION, "Max", null) { _, name, bytes ->
            publicBytes = bytes
            name
        }
        assertEquals(4, result!!.fromVersion)
        val restored = Backup.fromJson(String(publicBytes!!))
        assertEquals(3, restored.days.size)
        assertEquals(2, restored.shifts.size)
        assertEquals(50, restored.tracks!!.single().pointCount)
        assertNull(restored.stops)
    }

    /** Gegenprobe: Ohne die Migration 3 → 4 lässt Room die alte Datenbank nicht öffnen (und löscht nichts). */
    @Test
    fun ohneMigration3Auf4SchlaegtDasOeffnenFehl() {
        createDatabase(dbName, 3) { fillVersion3() }
        val room = openWithRoom(dbName, arrayOf(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3))
        try {
            val error = runCatching { runBlocking { room.dao().getAllDays() } }.exceptionOrNull()
            assertTrue("Ohne Migration muss das Öffnen fehlschlagen", error is IllegalStateException)
        } finally {
            room.close()
        }
        // Die alten Einträge sind unverändert vorhanden
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(3, db.version)
            db.rawQuery("SELECT COUNT(*) FROM days", null).use { c -> c.moveToFirst(); assertEquals(3, c.getInt(0)) }
        }
    }

    @Test
    fun sicherungVorUpdateVonVersion3EnthaeltSchichtenUndTagesfelder() {
        createDatabase(AppDatabase.DB_NAME, 3) { fillVersion3() }
        var publicBytes: ByteArray? = null
        val result = PreMigrationBackup.runIfNeeded(context, AppDatabase.DB_NAME, AppDatabase.VERSION, "Max", null) { _, name, bytes ->
            publicBytes = bytes
            name
        }
        assertEquals(3, result!!.fromVersion)
        val restored = Backup.fromJson(String(publicBytes!!))
        assertEquals(3, restored.days.size)
        assertEquals(2, restored.shifts.size)
        assertEquals(1, restored.shifts.count { it.running })
        assertFalse(restored.days.first { it.id == 2L }.countryManual)
        assertEquals(true, restored.days.first { it.id == 2L }.overnightOverride)
        assertTrue(restored.days.first { it.id == 3L }.timesAuto)
    }

    // ---------------------------------------------------------------------------------------------

    private val heute = LocalDate.now()
    private val sonntag = LocalDate.parse("2026-11-08")
    private val montag = LocalDate.parse("2026-11-09")

    /** Datenbank im Stand der aktuell installierten Version (1.0.6, Datenbank-Version 2) mit Einträgen. */
    private fun SQLiteDatabase.fillVersion2() {
        execSQL("INSERT INTO tours (id, ended) VALUES (1, 1)")
        fun day(id: Int, date: LocalDate, start: Int, end: Int, country: String, kmStart: Int?, kmEnd: Int?,
                note: String, tour: Int?, override: String?, ws: Int?, we: Int?) =
            execSQL(
                "INSERT INTO days (id, date, startMinute, endMinute, countryCode, kmStart, kmEnd, note, tourId, " +
                    "dayTypeOverride, workStartMinute, workEndMinute) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(id, date.toString(), start, end, country, kmStart, kmEnd, note, tour, override, ws, we),
            )
        // Tour mit Auslandstagen
        day(1, LocalDate.parse("2026-11-02"), 300, 1440, "DE", 410000, 410650, "Abfahrt", 1, null, 300, 1110)
        day(2, LocalDate.parse("2026-11-03"), 0, 1440, "AT", 410650, 411200, "Wien", 1, null, 360, 1080)
        day(3, LocalDate.parse("2026-11-04"), 0, 1020, "DE", 411200, 411900, "", 1, "FULL", 0, 960)
        // Von Hand an Mitternacht getrennte Schicht (Sonntag 23:30 – Montag 10:00)
        day(4, sonntag, 1380, 1440, "DE", 412000, 412030, "Nachtschicht", null, null, 1410, 1440)
        day(5, montag, 0, 620, "FR", 412030, 412600, "", null, null, 0, 600)
        // Unvollständig: nur Arbeitsende
        day(6, LocalDate.parse("2026-11-11"), 480, 1000, "DE", null, null, "", null, "NONE", null, 900)
        // Heute: Arbeit begonnen, noch nicht beendet (läuft beim Update)
        day(7, heute, 330, 1440, "DE", 412600, null, "", null, null, 330, null)
    }

    @Test
    fun updateVonVersion2AufAktuellBehaeltAlleEintraege() {
        createDatabase(dbName, 2) { fillVersion2() }
        val room = openWithRoom(dbName)
        try {
            runBlocking {
                val dao = room.dao()
                val days = dao.getAllDays().associateBy { it.id }
                assertEquals(7, days.size)

                // Alle Einträge, Zeiten, Länder und Kilometer unverändert
                with(days[2]!!) {
                    assertEquals(LocalDate.parse("2026-11-03"), date)
                    assertEquals(0 to 1440, startMinute to endMinute)
                    assertEquals("AT", countryCode)
                    assertEquals(410650 to 411200, kmStart to kmEnd)
                    assertEquals("Wien", note)
                    assertEquals(1L, tourId)
                }
                assertEquals(DayType.FULL, days[3]!!.dayTypeOverride)
                assertEquals("FR", days[5]!!.countryCode)
                assertEquals(570, days[5]!!.distanceKm)
                assertEquals(DayType.NONE, days[6]!!.dayTypeOverride)
                assertEquals(412600, days[7]!!.kmStart)
                assertEquals(2500, days.values.sumOf { it.distanceKm ?: 0 })
                assertEquals(true, dao.getTour(1)?.ended)

                // Alte Arbeitszeit-Spalten bleiben als Sicherung erhalten
                assertEquals(1410 to 1440, days[4]!!.legacyWorkStartMinute to days[4]!!.legacyWorkEndMinute)

                // Neue Felder: bestehende Länder gelten als von Hand gewählt (GPS ändert sie nie)
                assertTrue(days.values.all { it.countryManual })
                assertTrue(days.values.all { it.overnightOverride == null && !it.kmInterpolated && !it.timesAuto })

                // Arbeitszeiten als Schichten übernommen, Tagessummen identisch
                val shifts = dao.getAllShifts()
                assertEquals(7, shifts.size)
                val work = WorkSplitter.byDate(shifts.filter { !it.running }, LocalDateTime.now())
                assertEquals(810, WorkSplitter.minutes(work[LocalDate.parse("2026-11-02")]))
                assertEquals(30, WorkSplitter.minutes(work[sonntag]))   // manuell getrennt: bleibt 30 Min.
                assertEquals(600, WorkSplitter.minutes(work[montag]))   // und 10 Std. am Montag
                assertEquals("bis 15:00", WorkSplitter.label(work[LocalDate.parse("2026-11-11")]))

                // Die heute begonnene Arbeitszeit läuft nach dem Update weiter
                val running = dao.getRunningShift()
                assertNotNull(running)
                assertEquals(heute, running!!.startDate)
                assertEquals(330, running.startMinute)
                assertEquals(1, shifts.count { it.running })
            }
            assertEquals(AppDatabase.VERSION, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }

    @Test
    fun updateVonVersion1AufAktuell() {
        createDatabase(dbName, 1) {
            execSQL("INSERT INTO tours (id, ended) VALUES (1, 1)")
            execSQL(
                "INSERT INTO days (id, date, startMinute, endMinute, countryCode, kmStart, kmEnd, note, tourId, dayTypeOverride) " +
                    "VALUES (1, '2026-11-02', 300, 1440, 'AT', 1000, 1600, 'Wien', 1, NULL)",
            )
        }
        val room = openWithRoom(dbName)
        try {
            runBlocking {
                val day = room.dao().getAllDays().single()
                assertEquals("AT", day.countryCode)
                assertEquals(600, day.distanceKm)
                assertEquals("Wien", day.note)
                assertNull(day.legacyWorkStartMinute)
                assertTrue(room.dao().getAllShifts().isEmpty())
            }
            assertEquals(AppDatabase.VERSION, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }

    @Test
    fun vorDerMigrationWirdEineSicherungAngelegt() {
        createDatabase(AppDatabase.DB_NAME, 2) { fillVersion2() }
        var publicName: String? = null
        var publicBytes: ByteArray? = null
        val result = PreMigrationBackup.runIfNeeded(
            context, AppDatabase.DB_NAME, AppDatabase.VERSION, "Max", CountingRules(true),
        ) { _, name, bytes ->
            publicName = name
            publicBytes = bytes
            "Download/Fahrtenbuch/$name"
        }
        assertNotNull(result)
        assertEquals(2, result!!.fromVersion)
        assertTrue(publicName!!.startsWith("Fahrtenbuch_Sicherung_vor_Update_"))

        // 1. Unveränderte Kopie der Datenbank im App-Speicher
        val copy = File(result.internalDir, AppDatabase.DB_NAME)
        assertTrue(copy.exists())
        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(2, db.version)
            db.rawQuery("SELECT COUNT(*) FROM days", null).use { c -> c.moveToFirst(); assertEquals(7, c.getInt(0)) }
        }

        // 2. Wiederherstellbare Datensicherung mit allen Einträgen
        val restored = Backup.fromJson(String(publicBytes!!))
        assertEquals(7, restored.days.size)
        assertEquals("Max", restored.driverName)
        assertEquals("AT", restored.days.first { it.id == 2L }.countryCode)
        assertEquals(7, restored.shifts.size)

        // Danach öffnet Room die Datenbank mit Migration – alles ist noch da
        val room = openWithRoom(AppDatabase.DB_NAME)
        try {
            runBlocking { assertEquals(7, room.dao().getAllDays().size) }
        } finally {
            room.close()
        }
        // Auf dem neuen Stand wird keine weitere Sicherung angelegt
        assertNull(PreMigrationBackup.runIfNeeded(context, AppDatabase.DB_NAME, AppDatabase.VERSION, "", null) { _, _, _ -> null })
    }

    @Test
    fun keineSicherungBeiNeuinstallation() {
        context.deleteDatabase(AppDatabase.DB_NAME)
        assertNull(PreMigrationBackup.runIfNeeded(context, AppDatabase.DB_NAME, AppDatabase.VERSION, "", null) { _, _, _ -> null })
        assertFalse(File(context.filesDir, PreMigrationBackup.DIR_NAME).exists())
    }
}
