package de.fernfahrer.fahrtenbuch

import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.AutoBackupSettings.Interval
import de.fernfahrer.fahrtenbuch.data.Backup
import de.fernfahrer.fahrtenbuch.data.BackupFolder
import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.OvernightImport
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.StopLocationEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.data.TrackChunks
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.geo.TrackPoint
import de.fernfahrer.fahrtenbuch.ui.map.MapIcons
import de.fernfahrer.fahrtenbuch.ui.map.MapViewModel
import de.fernfahrer.fahrtenbuch.work.AutoBackup
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Automatisches Backup, Übernachtungsplätze bearbeiten/löschen, Übernahme alter Plätze, Diesel, Sterne-Symbol. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoBackupAndPlacesTest {
    private val app = ApplicationProvider.getApplicationContext<FahrtenbuchApp>()
    private val zone = ZoneId.systemDefault()

    /** Ordner im Speicher statt Android-Ordnerauswahl. */
    private class FakeFolder : BackupFolder {
        val files = LinkedHashMap<String, ByteArray>()
        override fun list() = files.keys.map { BackupFolder.Entry(it, it) }
        override fun write(name: String, bytes: ByteArray) {
            files[name] = bytes
        }
        override fun delete(entry: BackupFolder.Entry) {
            files.remove(entry.id)
        }
    }

    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun backupFaelligNachAbstand() {
        val last = LocalDateTime.parse("2026-11-10T03:00")
        assertTrue("noch nie gesichert", AutoBackup.isDue(0, Interval.DAILY, last))
        assertFalse(AutoBackup.isDue(ms(last), Interval.DAILY, last.plusHours(12)))
        assertTrue(AutoBackup.isDue(ms(last), Interval.DAILY, last.plusHours(23)))
        assertFalse(AutoBackup.isDue(ms(last), Interval.WEEKLY, last.plusDays(6)))
        assertTrue(AutoBackup.isDue(ms(last), Interval.WEEKLY, last.plusDays(7)))
        assertFalse(AutoBackup.isDue(ms(last), Interval.MONTHLY, last.plusDays(25)))
        assertTrue(AutoBackup.isDue(ms(last), Interval.MONTHLY, last.plusMonths(1)))
        assertTrue("Uhr zurückgestellt", AutoBackup.isDue(ms(last), Interval.MONTHLY, last.minusDays(1)))
    }

    @Test
    fun aeltereAutomatischeBackupsWerdenGeloescht() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = Repository(db, flowOf(CountingRules()))
            val d = LocalDate.parse("2026-11-10")
            db.dao().insertDay(DayEntity(date = d, startMinute = 300, endMinute = 1200, countryCode = "DE"))
            db.trackDao().insert(TrackChunks.build(0, 1L, 0, (0 until 20).map { TrackPoint.of(50.0 + it * 0.0004, 9.0, 1_762_700_000L + it) }, listOf("DE")))
            val place = repo.addOvernightStay(d, 1200, 5_055_000, 967_000, "DE")
            repo.updateOvernightPlace(place.copy(name = "Autohof Fulda", rating = 3, quiet = true, note = "ab 18 Uhr voll"))
            val data = repo.exportData()

            val folder = FakeFolder()
            folder.write("Eigene Notiz.txt", ByteArray(1)) // fremde Dateien bleiben unberührt
            var t = LocalDateTime.parse("2026-11-01T10:00")
            repeat(7) {
                AutoBackup.writeAndPrune(folder, data, t, keep = 5)
                t = t.plusDays(1)
            }
            val auto = folder.files.keys.filter { it.startsWith(AutoBackup.PREFIX) }.sorted()
            assertEquals(5, auto.size)
            assertEquals("Fahrtenbuch-Auto-2026-11-03_100000.json", auto.first())
            assertEquals("Fahrtenbuch-Auto-2026-11-07_100000.json", auto.last())
            assertTrue(folder.files.containsKey("Eigene Notiz.txt"))

            // Gleiches Format wie die manuelle Sicherung, inklusive Strecken, Plätzen und Notizen
            val restored = Backup.fromJson(String(folder.files.getValue(auto.last())))
            assertEquals(1, restored.days.size)
            assertEquals(20, restored.tracks!!.single().pointCount)
            val p = restored.places!!.single()
            assertEquals("Autohof Fulda", p.place.name)
            assertEquals(3, p.place.rating)
            assertTrue(p.place.quiet)
            assertEquals("ab 18 Uhr voll", p.place.note)
            assertEquals(d, p.stays.single().date)
        } finally {
            db.close()
        }
    }

    @Test
    fun plaetzeBearbeitenUndLoeschen() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = Repository(db, flowOf(CountingRules()))
            val d = LocalDate.parse("2026-11-10")
            val a = repo.addOvernightStay(d, 1200, 5_000_000, 900_000, "DE")
            repo.addOvernightStay(d.plusDays(1), 1250, 5_000_100, 900_050, "DE") // ca. 12 m daneben
            repo.addOvernightStay(d.plusDays(2), 1300, 5_000_000, 903_000, "DE") // ca. 2 km weg
            assertEquals(listOf(2, 1), repo.getOvernightPlaces().map { it.count })

            // Versetzen und umbenennen
            repo.updateOvernightPlace(a.copy(latE5 = 5_001_000, name = "Rastplatz"))
            val moved = repo.getOvernightPlaces().first { it.place.id == a.id }
            assertEquals(5_001_000, moved.place.latE5)
            assertEquals("Rastplatz", moved.place.name)

            // Einzelne Übernachtung löschen: Platz bleibt mit einer
            repo.deleteOvernightStay(moved.stays.first().id)
            assertEquals(1, repo.getOvernightPlaces().first { it.place.id == a.id }.count)
            // Letzte Übernachtung löschen: Platz verschwindet
            repo.deleteOvernightStay(repo.getOvernightPlaces().first { it.place.id == a.id }.stays.single().id)
            assertEquals(1, repo.getOvernightPlaces().size)
            // Ganzen Platz löschen
            repo.deleteOvernightPlace(repo.getOvernightPlaces().single().place.id)
            assertTrue(repo.getOvernightPlaces().isEmpty())
            assertTrue(db.stopDao().getAllStays().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun uebernahmeAlterPlaetzeOhneDenHeutigenTag() {
        val start = LocalDate.parse("2026-11-15")
        val today = start.plusDays(3)
        // Tour seit 4 Tagen, läuft noch: jeder Tag ist Übernachtung im LKW
        val days = (0..3).map { DayEntity(id = it + 1L, date = start.plusDays(it.toLong()), startMinute = 0, endMinute = 1440, countryCode = "DE", tourId = 1) }
        val classified = Classifier.classifyAll(days, listOf(TourEntity(1, ended = false)), CountingRules())
        val stops = listOf(StopLocationEntity(1, start, 1200, 5_000_000, 900_000, "DE"))
        // GPS: letzter Punkt am 16. bei Kassel, am 18. (heute) unterwegs bei Würzburg
        fun chunk(id: Long, date: LocalDate, lat: Double, lon: Double) = TrackChunks.build(
            0, id, 0,
            (0 until 5).map { TrackPoint.of(lat + it * 0.0004, lon, date.atTime(12, 0).atZone(zone).toEpochSecond() + it * 60) },
            listOf("DE"),
        )
        val tracks = listOf(chunk(1, start.plusDays(1), 51.31, 9.48), chunk(2, today, 49.79, 9.95))
        val places = OvernightImport.compute(classified, stops, tracks, today, zone)
        val dates = places.flatMap { p -> p.stays.map { it.date } }.sorted()
        assertEquals("15. (Standort) und 16. (GPS); 17. ohne Position, heute ausgelassen", listOf(start, start.plusDays(1)), dates)
        assertTrue(places.none { p -> p.place.latE5 in 4_970_000..4_990_000 })
    }

    @Test
    fun dieselAusTachoKilometern() {
        val days = listOf(
            DayEntity(id = 1, date = LocalDate.parse("2026-11-10"), startMinute = 0, endMinute = 1440, countryCode = "DE", kmStart = 100_000, kmEnd = 100_650),
            DayEntity(id = 2, date = LocalDate.parse("2026-11-11"), startMinute = 0, endMinute = 900, countryCode = "NL", kmStart = 100_650, kmEnd = 101_200),
            DayEntity(id = 3, date = LocalDate.parse("2026-11-12"), startMinute = 0, endMinute = 900, countryCode = "DE", kmStart = 101_200),
        )
        val classified = Classifier.classifyAll(days, emptyList(), CountingRules())
        // 1 200 km × 27 l / 100 km = 324 l; 1 200 × 31,4 / 100 = 376,8 → 377
        assertEquals(324L, MapViewModel.dieselLiters(classified, 27.0))
        assertEquals(377L, MapViewModel.dieselLiters(classified, 31.4))
        assertEquals(0L, MapViewModel.dieselLiters(emptyList(), 27.0))
    }

    @Test
    fun bewertetePlaetzeZeigenSterneAmSymbol() {
        val plain = MapIcons.overnight(2f, 1)
        val rated = MapIcons.overnight(2f, 1, rating = 3)
        assertTrue("Sterne unter dem Symbol", rated.height > plain.height)
        // Kreis genau in der Mitte (Anker „Mitte“ sitzt auf dem Platz)
        assertEquals(255, Color.alpha(rated.getPixel(rated.width / 2, rated.height / 2)))
        // Gelb-orange Sternfarbe im unteren Teil vorhanden
        val orange = (0 until rated.width).any { x ->
            (rated.height * 3 / 4 until rated.height).any { y ->
                val c = rated.getPixel(x, y)
                Color.alpha(c) > 200 && Color.red(c) > 220 && Color.green(c) in 130..200 && Color.blue(c) < 90
            }
        }
        assertTrue(orange)
    }
}
