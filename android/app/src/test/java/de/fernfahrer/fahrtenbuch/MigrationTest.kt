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
            assertEquals(4, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
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
