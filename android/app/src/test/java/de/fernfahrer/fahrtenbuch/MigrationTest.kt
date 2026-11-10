package de.fernfahrer.fahrtenbuch

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.domain.DayType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Prüft, dass ein Update von Datenbank-Version 1 (erste App-Version) auf Version 2
 * keine Daten verliert. Die alte Datenbank wird exakt nach dem exportierten Schema 1 angelegt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"

    private fun createVersion1Database() {
        context.deleteDatabase(dbName)
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        val schema = JSONObject(
            File("schemas/de.fernfahrer.fahrtenbuch.data.AppDatabase/1.json").readText(),
        ).getJSONObject("database")
        assertEquals(1, schema.getInt("version"))

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

            // Daten wie aus der alten App
            db.execSQL("INSERT INTO tours (id, ended) VALUES (1, 1)")
            db.execSQL(
                "INSERT INTO days (id, date, startMinute, endMinute, countryCode, kmStart, kmEnd, note, tourId, dayTypeOverride) " +
                    "VALUES (1, '2026-11-02', 300, 1440, 'AT', 1000, 1600, 'Wien', 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO days (id, date, startMinute, endMinute, countryCode, kmStart, kmEnd, note, tourId, dayTypeOverride) " +
                    "VALUES (2, '2026-11-03', 0, 900, 'DE', 1600, 2100, '', 1, 'FULL')",
            )
            db.version = 1
        }
    }

    @Test
    fun migration1auf2BehaeltAlleDaten() {
        createVersion1Database()

        // Room öffnet die alte Datenbank mit der Migration und prüft dabei das neue Schema.
        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = room.dao()
                val days = dao.getAllDays()
                assertEquals(2, days.size)

                val wien = days.first { it.id == 1L }
                assertEquals(LocalDate.parse("2026-11-02"), wien.date)
                assertEquals("AT", wien.countryCode)
                assertEquals(600, wien.distanceKm)
                assertEquals("Wien", wien.note)
                assertEquals(1L, wien.tourId)
                assertNull(wien.workStartMinute)
                assertNull(wien.workEndMinute)
                assertEquals(DayType.FULL, days.first { it.id == 2L }.dayTypeOverride)
                assertEquals(true, dao.getTour(1)?.ended)

                // Neue Felder lassen sich nach der Migration speichern
                dao.updateDay(wien.copy(workStartMinute = 330, workEndMinute = 1110))
                val updated = dao.getDay(1)!!
                assertEquals(330, updated.workStartMinute)
                assertEquals(1110, updated.workEndMinute)
            }
            assertEquals(2, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }
}
